-- ============================================================================
-- HAKOT VERSION 2.4 — ACCOUNT OTPs HARDENING MIGRATION (FINAL HARDENED)
-- Migration File: 20250302_v24_account_otps_hardening.sql
-- Target Database: PostgreSQL 17.11 / Supabase Project Staging
-- Description: Corrects OTP state machine, enforces uniform 2-step profile-first locking,
--              implements 32-byte recovery token hashing, 60-second operation lease fencing,
--              blocks issuance on UPDATING/UNCERTAIN, isolates execution to service_role,
--              and atomically finalizes the specific associated password_reset_request on OTP completion.
-- ============================================================================

BEGIN;

-- 1. Safely update account_otps table schema with status constraint, lease columns, and ON DELETE RESTRICT reset_request_id FK
ALTER TABLE public.account_otps
    DROP CONSTRAINT IF EXISTS account_otps_status_check;

ALTER TABLE public.account_otps
    ADD COLUMN IF NOT EXISTS recovery_token_hash TEXT,
    ADD COLUMN IF NOT EXISTS operation_id UUID,
    ADD COLUMN IF NOT EXISTS updating_locked_until TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS reset_request_id UUID;

-- Migration-safe foreign key update: drop any existing FK on account_otps.reset_request_id referencing password_reset_requests.id regardless of constraint name
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN
        SELECT tc.constraint_name
        FROM information_schema.table_constraints tc
        JOIN information_schema.key_column_usage kcu
          ON tc.constraint_name = kcu.constraint_name
         AND tc.table_schema = kcu.table_schema
        JOIN information_schema.constraint_column_usage ccu
          ON ccu.constraint_name = tc.constraint_name
         AND ccu.table_schema = tc.table_schema
        WHERE tc.table_schema = 'public'
          AND tc.table_name = 'account_otps'
          AND tc.constraint_type = 'FOREIGN KEY'
          AND kcu.column_name = 'reset_request_id'
          AND ccu.table_schema = 'public'
          AND ccu.table_name = 'password_reset_requests'
          AND ccu.column_name = 'id'
    LOOP
        EXECUTE format('ALTER TABLE public.account_otps DROP CONSTRAINT %I', r.constraint_name);
    END LOOP;
END $$;

ALTER TABLE public.account_otps
    ADD CONSTRAINT account_otps_reset_request_id_fkey
    FOREIGN KEY (reset_request_id)
    REFERENCES public.password_reset_requests(id)
    ON DELETE RESTRICT;

ALTER TABLE public.account_otps
    ADD CONSTRAINT account_otps_status_check
    CHECK (status IN ('ISSUED', 'VERIFIED', 'COMPLETED', 'EXPIRED', 'SUPERSEDED', 'MAX_ATTEMPTS_EXCEEDED', 'UPDATING', 'UNCERTAIN'));

-- Legacy OTP preflight check: ensure no unassociated active PASSWORD_RESET OTP records exist
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM public.account_otps
        WHERE code_type = 'PASSWORD_RESET'
          AND reset_request_id IS NULL
          AND status IN ('ISSUED', 'VERIFIED', 'UPDATING', 'UNCERTAIN')
    ) THEN
        RAISE EXCEPTION 'Preflight Failure: Legacy unassociated PASSWORD_RESET OTP records with NULL reset_request_id found in account_otps. Manual administrative reconciliation required before applying OTP hardening migration.';
    END IF;
END $$;

-- ============================================================================
-- SECTION 2: TRANSACTIONAL HELPER RPCs (UNIFORM 2-STEP LOCKING & SERVICE ROLE EXCLUSIVE)
-- ============================================================================

-- 2.1 RPC: issue_account_otp
-- Action: Locks profiles 1st (FOR UPDATE), freshly re-validates application/reset eligibility,
--         checks active UPDATING/UNCERTAIN operations for user_id, supersedes ALL active ISSUED/VERIFIED codes
--         account-wide across code types, binds reset_request_id for PASSWORD_RESET, and inserts new OTP record.
-- Note: Administrative audit logging for code issuance is handled directly by the trusted Edge Function
--       using the System Owner's authenticated session actor ID.
CREATE OR REPLACE FUNCTION public.issue_account_otp(
    p_application_id UUID,
    p_user_id UUID,
    p_email TEXT,
    p_code_hash TEXT,
    p_code_type TEXT,
    p_ttl_minutes INT DEFAULT 30
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_otp_id UUID;
    v_clean_email TEXT;
    v_app RECORD;
    v_profile RECORD;
    v_reset_req RECORD;
    v_target_user_id UUID;
    v_reset_request_id UUID := NULL;
BEGIN
    v_clean_email := LOWER(TRIM(COALESCE(p_email, '')));
    IF v_clean_email = '' OR p_code_hash IS NULL OR TRIM(p_code_hash) !~* '^[0-9a-f]{64}$' THEN
        RAISE EXCEPTION 'Validation failed: Email and valid 64-character hex HMAC code_hash are required.';
    END IF;

    IF p_code_type IS NULL OR p_code_type NOT IN ('ACTIVATION', 'PASSWORD_RESET') THEN
        RAISE EXCEPTION 'Validation failed: Invalid or NULL code_type.';
    END IF;

    IF p_ttl_minutes IS NULL OR p_ttl_minutes < 5 OR p_ttl_minutes > 60 THEN
        RAISE EXCEPTION 'Validation failed: p_ttl_minutes must be between 5 and 60 minutes.';
    END IF;

    -- Resolve initial target Auth user ID snapshot based on code_type
    IF p_code_type = 'ACTIVATION' THEN
        IF p_application_id IS NULL THEN
            RAISE EXCEPTION 'Validation failed: p_application_id is required for ACTIVATION codes.';
        END IF;

        SELECT auth_user_id INTO v_target_user_id
        FROM public.account_applications
        WHERE id = p_application_id;

        IF NOT FOUND OR v_target_user_id IS NULL THEN
            RAISE EXCEPTION 'Validation failed: Application not found or auth_user_id not mapped.';
        END IF;

    ELSIF p_code_type = 'PASSWORD_RESET' THEN
        IF p_user_id IS NULL THEN
            RAISE EXCEPTION 'Validation failed: p_user_id is required for PASSWORD_RESET codes.';
        END IF;

        v_target_user_id := p_user_id;
    END IF;

    -- LOCK STEP 1: Lock canonical public.profiles row FIRST (FOR UPDATE)
    SELECT * INTO v_profile
    FROM public.profiles
    WHERE id = v_target_user_id
    FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Validation failed: Target profile record not found for user ID %.', v_target_user_id;
    END IF;

    IF v_profile.account_status IS NULL OR v_profile.account_status NOT IN ('active', 'pending') THEN
        RAISE EXCEPTION 'Validation failed: Target account status is not eligible for code issuance.';
    END IF;

    -- FRESH RE-VALIDATION AFTER LOCK ACQUISITION
    IF p_code_type = 'ACTIVATION' THEN
        SELECT * INTO v_app
        FROM public.account_applications
        WHERE id = p_application_id;

        IF NOT FOUND OR v_app.status <> 'APPROVED' OR v_app.organization_id IS NULL OR v_app.auth_user_id IS NULL THEN
            RAISE EXCEPTION 'Validation failed: Application is not approved or fully provisioned for activation.';
        END IF;

        IF v_app.auth_user_id <> v_target_user_id OR LOWER(TRIM(v_app.email)) <> v_clean_email THEN
            RAISE EXCEPTION 'Validation failed: Application email or auth_user_id mismatch.';
        END IF;

        IF v_app.organization_id IS DISTINCT FROM v_profile.organization_id THEN
            RAISE EXCEPTION 'Validation failed: Application organization_id does not match user profile organization_id.';
        END IF;

    ELSIF p_code_type = 'PASSWORD_RESET' THEN
        -- Require a freshly queried password_reset_request explicitly APPROVED by System Owner
        SELECT * INTO v_reset_req
        FROM public.password_reset_requests
        WHERE user_id = v_target_user_id AND LOWER(email) = v_clean_email AND status = 'APPROVED'
        ORDER BY requested_at DESC LIMIT 1;

        IF NOT FOUND THEN
            RAISE EXCEPTION 'Validation failed: No System Owner-approved password reset request found.';
        END IF;

        IF v_profile.organization_id IS DISTINCT FROM v_reset_req.organization_id THEN
            RAISE EXCEPTION 'Validation failed: Approved reset request organization does not match user profile organization.';
        END IF;

        v_reset_request_id := v_reset_req.id;
    END IF;

    -- BLOCK ISSUANCE if any active or uncertain update is in progress for this user (checked under parent lock)
    IF EXISTS (
        SELECT 1 FROM public.account_otps
        WHERE user_id = v_target_user_id
          AND status IN ('UPDATING', 'UNCERTAIN')
    ) THEN
        RAISE EXCEPTION 'Validation failed: Account has an active or uncertain password update in progress. Manual reconciliation required.';
    END IF;

    -- LOCK STEP 2: Supersede ALL prior active codes (ISSUED, VERIFIED) for this user account-wide across code types
    UPDATE public.account_otps
    SET status = 'SUPERSEDED'
    WHERE user_id = v_target_user_id
      AND status IN ('ISSUED', 'VERIFIED');

    -- Insert new OTP record (binding reset_request_id for PASSWORD_RESET)
    INSERT INTO public.account_otps (
        application_id,
        user_id,
        reset_request_id,
        email,
        code_hash,
        code_type,
        status,
        expires_at
    ) VALUES (
        p_application_id,
        v_target_user_id,
        v_reset_request_id,
        v_clean_email,
        TRIM(p_code_hash),
        p_code_type,
        'ISSUED',
        pg_catalog.now() + (p_ttl_minutes || ' minutes')::INTERVAL
    )
    RETURNING id INTO v_otp_id;

    RETURN v_otp_id;
END;
$$;

REVOKE EXECUTE ON FUNCTION public.issue_account_otp(UUID, UUID, TEXT, TEXT, TEXT, INT) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.issue_account_otp(UUID, UUID, TEXT, TEXT, TEXT, INT) TO service_role;

-- 2.2 RPC: verify_account_otp
-- Action: Resolves user, locks profiles 1st (FOR UPDATE), locks account_otps 2nd (FOR UPDATE),
--         validates 64-hex HMAC code digest, and transitions ISSUED -> VERIFIED.
CREATE OR REPLACE FUNCTION public.verify_account_otp(
    p_email TEXT,
    p_code_hash TEXT,
    p_code_type TEXT
)
RETURNS TABLE(success BOOLEAN, otp_id UUID, application_id UUID, user_id UUID, error_message TEXT)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_snapshot RECORD;
    v_profile RECORD;
    v_record RECORD;
    v_clean_email TEXT;
    v_actor_id UUID;
BEGIN
    v_clean_email := LOWER(TRIM(COALESCE(p_email, '')));
    IF v_clean_email = '' OR p_code_hash IS NULL OR TRIM(p_code_hash) !~* '^[0-9a-f]{64}$' THEN
        RETURN QUERY SELECT FALSE, NULL::UUID, NULL::UUID, NULL::UUID, 'Validation failed: Email and valid 64-character hex HMAC code_hash are required.'::TEXT;
        RETURN;
    END IF;

    IF p_code_type IS NULL OR p_code_type NOT IN ('ACTIVATION', 'PASSWORD_RESET') THEN
        RETURN QUERY SELECT FALSE, NULL::UUID, NULL::UUID, NULL::UUID, 'Validation failed: Invalid or NULL code_type.'::TEXT;
        RETURN;
    END IF;

    -- Unlocked snapshot lookup to resolve user_id
    SELECT * INTO v_snapshot
    FROM public.account_otps
    WHERE LOWER(email) = v_clean_email
      AND code_type = p_code_type
      AND status = 'ISSUED'
    ORDER BY created_at DESC
    LIMIT 1;

    IF NOT FOUND THEN
        RETURN QUERY SELECT FALSE, NULL::UUID, NULL::UUID, NULL::UUID, 'Invalid or expired single-use code.'::TEXT;
        RETURN;
    END IF;

    -- LOCK STEP 1: Lock canonical public.profiles row FIRST (FOR UPDATE)
    SELECT * INTO v_profile
    FROM public.profiles
    WHERE id = v_snapshot.user_id
    FOR UPDATE;

    IF NOT FOUND THEN
        RETURN QUERY SELECT FALSE, NULL::UUID, NULL::UUID, NULL::UUID, 'Profile record not found for account.'::TEXT;
        RETURN;
    END IF;

    -- LOCK STEP 2: Lock matching public.account_otps row SECOND (FOR UPDATE)
    SELECT * INTO v_record
    FROM public.account_otps
    WHERE id = v_snapshot.id
    FOR UPDATE;

    IF NOT FOUND OR v_record.user_id <> v_profile.id THEN
        RETURN QUERY SELECT FALSE, NULL::UUID, NULL::UUID, NULL::UUID, 'OTP record mismatch during row locking.'::TEXT;
        RETURN;
    END IF;

    IF v_record.status <> 'ISSUED' THEN
        RETURN QUERY SELECT FALSE, v_record.id, v_record.application_id, v_record.user_id, 'Code is no longer in ISSUED state.'::TEXT;
        RETURN;
    END IF;

    -- Check Expiration TTL
    IF v_record.expires_at < pg_catalog.now() THEN
        UPDATE public.account_otps SET status = 'EXPIRED' WHERE id = v_record.id;
        RETURN QUERY SELECT FALSE, v_record.id, v_record.application_id, v_record.user_id, 'The single-use code has expired. Please request a new code.'::TEXT;
        RETURN;
    END IF;

    -- Check Max Failed Attempts
    IF v_record.attempts_count >= v_record.max_attempts THEN
        UPDATE public.account_otps SET status = 'MAX_ATTEMPTS_EXCEEDED' WHERE id = v_record.id;
        RETURN QUERY SELECT FALSE, v_record.id, v_record.application_id, v_record.user_id, 'Maximum verification attempts exceeded. Please request a new code.'::TEXT;
        RETURN;
    END IF;

    -- Verify HMAC Code Hash
    IF v_record.code_hash <> TRIM(p_code_hash) THEN
        UPDATE public.account_otps
        SET attempts_count = attempts_count + 1,
            status = CASE WHEN attempts_count + 1 >= max_attempts THEN 'MAX_ATTEMPTS_EXCEEDED' ELSE 'ISSUED' END
        WHERE id = v_record.id;

        RETURN QUERY SELECT FALSE, v_record.id, v_record.application_id, v_record.user_id, 'Incorrect code. Please check and try again.'::TEXT;
        RETURN;
    END IF;

    -- Mark VERIFIED
    UPDATE public.account_otps
    SET status = 'VERIFIED',
        verified_at = pg_catalog.now()
    WHERE id = v_record.id;

    -- Audit actor is auth.uid() if authenticated session exists
    v_actor_id := auth.uid();
    IF v_actor_id IS NOT NULL THEN
        INSERT INTO public.audit_logs (actor_id, action, table_name, record_id, details)
        VALUES (v_actor_id, 'VERIFY_ACCOUNT_OTP', 'account_otps', v_record.id, jsonb_build_object('email', v_clean_email, 'code_type', p_code_type, 'status', 'VERIFIED'));
    END IF;

    RETURN QUERY SELECT TRUE, v_record.id, v_record.application_id, v_record.user_id, NULL::TEXT;
END;
$$;

REVOKE EXECUTE ON FUNCTION public.verify_account_otp(TEXT, TEXT, TEXT) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.verify_account_otp(TEXT, TEXT, TEXT) TO service_role;

-- 2.3 RPC: register_recovery_token
-- Action: Locks profiles 1st, locks account_otps 2nd, and stores SHA-256 recovery token hash for VERIFIED record exactly once.
CREATE OR REPLACE FUNCTION public.register_recovery_token(
    p_otp_id UUID,
    p_recovery_token_hash TEXT
)
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_snapshot RECORD;
    v_profile RECORD;
    v_record RECORD;
BEGIN
    IF p_otp_id IS NULL OR p_recovery_token_hash IS NULL OR TRIM(p_recovery_token_hash) !~* '^[0-9a-f]{64}$' THEN
        RETURN FALSE;
    END IF;

    -- Unlocked snapshot lookup to resolve user_id
    SELECT user_id INTO v_snapshot
    FROM public.account_otps
    WHERE id = p_otp_id;

    IF NOT FOUND THEN
        RETURN FALSE;
    END IF;

    -- LOCK STEP 1: Lock canonical public.profiles row FIRST (FOR UPDATE)
    SELECT id INTO v_profile
    FROM public.profiles
    WHERE id = v_snapshot.user_id
    FOR UPDATE;

    IF NOT FOUND THEN
        RETURN FALSE;
    END IF;

    -- LOCK STEP 2: Lock matching public.account_otps row SECOND (FOR UPDATE)
    SELECT * INTO v_record
    FROM public.account_otps
    WHERE id = p_otp_id
    FOR UPDATE;

    IF NOT FOUND OR v_record.user_id <> v_profile.id THEN
        RETURN FALSE;
    END IF;

    -- Strictly one-time registration: status must be VERIFIED, token hash must be NULL, and unexpired
    IF v_record.status <> 'VERIFIED' OR v_record.recovery_token_hash IS NOT NULL OR v_record.expires_at < pg_catalog.now() THEN
        RETURN FALSE;
    END IF;

    UPDATE public.account_otps
    SET recovery_token_hash = TRIM(p_recovery_token_hash)
    WHERE id = p_otp_id;

    RETURN TRUE;
END;
$$;

REVOKE EXECUTE ON FUNCTION public.register_recovery_token(UUID, TEXT) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.register_recovery_token(UUID, TEXT) TO service_role;

-- 2.4 RPC: authorize_password_update
-- Action: Locks profiles 1st, locks account_otps 2nd, validates recovery token hash and VERIFIED status,
--         acquires exclusive 60-second UPDATING lease with operation_id. Freezes expired leases to UNCERTAIN.
CREATE OR REPLACE FUNCTION public.authorize_password_update(
    p_otp_id UUID,
    p_recovery_token_hash TEXT,
    p_operation_id UUID
)
RETURNS TABLE(success BOOLEAN, application_id UUID, user_id UUID, error_message TEXT)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_snapshot RECORD;
    v_profile RECORD;
    v_record RECORD;
BEGIN
    IF p_otp_id IS NULL OR p_operation_id IS NULL OR p_recovery_token_hash IS NULL OR TRIM(p_recovery_token_hash) !~* '^[0-9a-f]{64}$' THEN
        RETURN QUERY SELECT FALSE, NULL::UUID, NULL::UUID, 'Validation failed: Invalid parameters.'::TEXT;
        RETURN;
    END IF;

    -- Unlocked snapshot lookup to resolve user_id
    SELECT user_id INTO v_snapshot
    FROM public.account_otps
    WHERE id = p_otp_id;

    IF NOT FOUND THEN
        RETURN QUERY SELECT FALSE, NULL::UUID, NULL::UUID, 'Invalid verification session.'::TEXT;
        RETURN;
    END IF;

    -- LOCK STEP 1: Lock canonical public.profiles row FIRST (FOR UPDATE)
    SELECT id INTO v_profile
    FROM public.profiles
    WHERE id = v_snapshot.user_id
    FOR UPDATE;

    IF NOT FOUND THEN
        RETURN QUERY SELECT FALSE, NULL::UUID, NULL::UUID, 'Profile record not found.'::TEXT;
        RETURN;
    END IF;

    -- LOCK STEP 2: Lock matching public.account_otps row SECOND (FOR UPDATE)
    SELECT * INTO v_record
    FROM public.account_otps
    WHERE id = p_otp_id
    FOR UPDATE;

    IF NOT FOUND OR v_record.user_id <> v_profile.id THEN
        RETURN QUERY SELECT FALSE, NULL::UUID, NULL::UUID, 'OTP record mismatch during row locking.'::TEXT;
        RETURN;
    END IF;

    -- Check if active UPDATING lease has expired or updating_locked_until IS NULL -> transition to UNCERTAIN
    IF v_record.status = 'UPDATING' AND (v_record.updating_locked_until IS NULL OR v_record.updating_locked_until < pg_catalog.now()) THEN
        UPDATE public.account_otps SET status = 'UNCERTAIN' WHERE id = v_record.id;
        RETURN QUERY SELECT FALSE, v_record.application_id, v_record.user_id, 'Previous update session expired into uncertainty. Manual reconciliation required.'::TEXT;
        RETURN;
    END IF;

    IF v_record.status IN ('UNCERTAIN', 'COMPLETED', 'SUPERSEDED') THEN
        RETURN QUERY SELECT FALSE, v_record.application_id, v_record.user_id, ('Session is in terminal or uncertain state: ' || v_record.status)::TEXT;
        RETURN;
    END IF;

    IF v_record.status <> 'VERIFIED' THEN
        RETURN QUERY SELECT FALSE, v_record.application_id, v_record.user_id, 'Session not in VERIFIED state.'::TEXT;
        RETURN;
    END IF;

    IF v_record.expires_at < pg_catalog.now() THEN
        UPDATE public.account_otps SET status = 'EXPIRED' WHERE id = v_record.id;
        RETURN QUERY SELECT FALSE, v_record.application_id, v_record.user_id, 'Verification session expired.'::TEXT;
        RETURN;
    END IF;

    IF v_record.recovery_token_hash IS DISTINCT FROM TRIM(p_recovery_token_hash) THEN
        RETURN QUERY SELECT FALSE, v_record.application_id, v_record.user_id, 'Invalid recovery token authorization.'::TEXT;
        RETURN;
    END IF;

    -- Acquire exclusive UPDATING lease with operation_id
    UPDATE public.account_otps
    SET status = 'UPDATING',
        operation_id = p_operation_id,
        updating_locked_until = pg_catalog.now() + INTERVAL '60 seconds'
    WHERE id = v_record.id;

    RETURN QUERY SELECT TRUE, v_record.application_id, v_record.user_id, NULL::TEXT;
END;
$$;

REVOKE EXECUTE ON FUNCTION public.authorize_password_update(UUID, TEXT, UUID) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.authorize_password_update(UUID, TEXT, UUID) TO service_role;

-- 2.5 RPC: complete_account_otp
-- Action: Locks profiles 1st, locks account_otps 2nd, validates operation_id and recovery_token_hash,
--         transitions UPDATING -> COMPLETED. Idempotent for retries with matching operation_id and token hash.
--         When completing a PASSWORD_RESET OTP, also atomically transitions the specific bound reset_request_id to COMPLETED.
CREATE OR REPLACE FUNCTION public.complete_account_otp(
    p_otp_id UUID,
    p_recovery_token_hash TEXT,
    p_operation_id UUID
)
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_snapshot RECORD;
    v_profile RECORD;
    v_record RECORD;
    v_reset_count INT;
BEGIN
    IF p_otp_id IS NULL OR p_operation_id IS NULL OR p_recovery_token_hash IS NULL OR TRIM(p_recovery_token_hash) !~* '^[0-9a-f]{64}$' THEN
        RETURN FALSE;
    END IF;

    -- Unlocked snapshot lookup to resolve user_id
    SELECT user_id INTO v_snapshot
    FROM public.account_otps
    WHERE id = p_otp_id;

    IF NOT FOUND THEN
        RETURN FALSE;
    END IF;

    -- LOCK STEP 1: Lock canonical public.profiles row FIRST (FOR UPDATE)
    SELECT id INTO v_profile
    FROM public.profiles
    WHERE id = v_snapshot.user_id
    FOR UPDATE;

    IF NOT FOUND THEN
        RETURN FALSE;
    END IF;

    -- LOCK STEP 2: Lock matching public.account_otps row SECOND (FOR UPDATE)
    SELECT * INTO v_record
    FROM public.account_otps
    WHERE id = p_otp_id
    FOR UPDATE;

    IF NOT FOUND OR v_record.user_id <> v_profile.id THEN
        RETURN FALSE;
    END IF;

    -- Idempotent retry: if already COMPLETED with matching operation_id and recovery_token_hash, verify exact reset_request_id status and return TRUE
    IF v_record.status = 'COMPLETED' AND v_record.operation_id = p_operation_id AND v_record.recovery_token_hash = TRIM(p_recovery_token_hash) THEN
        IF v_record.code_type = 'PASSWORD_RESET' THEN
            IF v_record.reset_request_id IS NULL THEN
                RETURN FALSE;
            END IF;

            IF NOT EXISTS (
                SELECT 1 FROM public.password_reset_requests
                WHERE id = v_record.reset_request_id AND status = 'COMPLETED'
            ) THEN
                RETURN FALSE;
            END IF;
        END IF;
        RETURN TRUE;
    END IF;

    -- Normal completion transition: UPDATING -> COMPLETED
    IF v_record.status = 'UPDATING' AND v_record.operation_id = p_operation_id AND v_record.recovery_token_hash = TRIM(p_recovery_token_hash) THEN
        UPDATE public.account_otps
        SET status = 'COMPLETED',
            completed_at = pg_catalog.now()
        WHERE id = v_record.id;

        -- If code_type is PASSWORD_RESET, atomically transition the exact bound reset_request_id to COMPLETED
        IF v_record.code_type = 'PASSWORD_RESET' THEN
            IF v_record.reset_request_id IS NULL THEN
                RAISE EXCEPTION 'State Machine Violation: PASSWORD_RESET OTP record is missing reset_request_id.';
            END IF;

            UPDATE public.password_reset_requests
            SET status = 'COMPLETED'
            WHERE id = v_record.reset_request_id
              AND status = 'APPROVED';

            GET DIAGNOSTICS v_reset_count = ROW_COUNT;

            IF v_reset_count <> 1 THEN
                RAISE EXCEPTION 'State Machine Violation: Expected exactly one APPROVED password reset request for ID %, found %.', v_record.reset_request_id, v_reset_count;
            END IF;
        END IF;

        RETURN TRUE;
    END IF;

    RETURN FALSE;
END;
$$;

REVOKE EXECUTE ON FUNCTION public.complete_account_otp(UUID, TEXT, UUID) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.complete_account_otp(UUID, TEXT, UUID) TO service_role;

-- 2.6 RPC: fail_password_update
-- Action: Locks profiles 1st, locks account_otps 2nd, transitions UPDATING -> UNCERTAIN for unconfirmed or failed updates.
CREATE OR REPLACE FUNCTION public.fail_password_update(
    p_otp_id UUID,
    p_recovery_token_hash TEXT,
    p_operation_id UUID
)
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_snapshot RECORD;
    v_profile RECORD;
    v_record RECORD;
BEGIN
    IF p_otp_id IS NULL OR p_operation_id IS NULL OR p_recovery_token_hash IS NULL OR TRIM(p_recovery_token_hash) !~* '^[0-9a-f]{64}$' THEN
        RETURN FALSE;
    END IF;

    -- Unlocked snapshot lookup to resolve user_id
    SELECT user_id INTO v_snapshot
    FROM public.account_otps
    WHERE id = p_otp_id;

    IF NOT FOUND THEN
        RETURN FALSE;
    END IF;

    -- LOCK STEP 1: Lock canonical public.profiles row FIRST (FOR UPDATE)
    SELECT id INTO v_profile
    FROM public.profiles
    WHERE id = v_snapshot.user_id
    FOR UPDATE;

    IF NOT FOUND THEN
        RETURN FALSE;
    END IF;

    -- LOCK STEP 2: Lock matching public.account_otps row SECOND (FOR UPDATE)
    SELECT * INTO v_record
    FROM public.account_otps
    WHERE id = p_otp_id
    FOR UPDATE;

    IF NOT FOUND OR v_record.user_id <> v_profile.id THEN
        RETURN FALSE;
    END IF;

    -- All failed or unconfirmed updates transition to UNCERTAIN (blocking automatic retries)
    IF v_record.status = 'UPDATING' AND v_record.operation_id = p_operation_id AND v_record.recovery_token_hash = TRIM(p_recovery_token_hash) THEN
        UPDATE public.account_otps
        SET status = 'UNCERTAIN'
        WHERE id = v_record.id;
        RETURN TRUE;
    END IF;

    RETURN FALSE;
END;
$$;

REVOKE EXECUTE ON FUNCTION public.fail_password_update(UUID, TEXT, UUID) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.fail_password_update(UUID, TEXT, UUID) TO service_role;

COMMIT;
