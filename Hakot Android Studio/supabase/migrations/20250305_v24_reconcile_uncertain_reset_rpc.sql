-- ============================================================================
-- MIGRATION: V2.4 Reconcile Uncertain Password Reset RPC & Trigger Adjustment
-- Target Database: PostgreSQL / Supabase Project HAKOT-Staging (jczbkpyktncwirlymkom)
-- ============================================================================

-- 1. Adjust password reset validation trigger to permit APPROVED -> REJECTED for uncertain reset reconciliation
CREATE OR REPLACE FUNCTION public.v24_fn_validate_password_reset_request()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
BEGIN
    IF TG_OP = 'UPDATE' THEN
        -- 1. Field Immutability Protection
        IF NEW.user_id IS DISTINCT FROM OLD.user_id THEN
            RAISE EXCEPTION 'Immutability Violation: Password reset request user_id cannot be modified.';
        END IF;

        IF NEW.organization_id IS DISTINCT FROM OLD.organization_id THEN
            RAISE EXCEPTION 'Immutability Violation: Password reset request organization_id cannot be modified.';
        END IF;

        IF NEW.email IS DISTINCT FROM OLD.email THEN
            RAISE EXCEPTION 'Immutability Violation: Password reset request email cannot be modified.';
        END IF;

        IF NEW.reason IS DISTINCT FROM OLD.reason THEN
            RAISE EXCEPTION 'Immutability Violation: Password reset request original reason cannot be modified.';
        END IF;

        -- 2. Post-Review Metadata Immutability Protection
        IF OLD.reviewed_by IS NOT NULL AND NEW.reviewed_by IS DISTINCT FROM OLD.reviewed_by THEN
            RAISE EXCEPTION 'Immutability Violation: Password reset request reviewed_by cannot be modified once set.';
        END IF;

        IF OLD.reviewed_at IS NOT NULL AND NEW.reviewed_at IS DISTINCT FROM OLD.reviewed_at THEN
            RAISE EXCEPTION 'Immutability Violation: Password reset request reviewed_at cannot be modified once set.';
        END IF;

        -- 3. Transition to APPROVED or REJECTED from PENDING Requires BOTH Metadata Fields
        IF OLD.status = 'PENDING' AND NEW.status IN ('APPROVED', 'REJECTED') THEN
            IF OLD.reviewed_by IS NOT NULL OR OLD.reviewed_at IS NOT NULL THEN
                RAISE EXCEPTION 'State Machine Violation: PENDING request must not possess review metadata prior to review.';
            END IF;

            IF NEW.reviewed_by IS NULL OR NEW.reviewed_at IS NULL THEN
                RAISE EXCEPTION 'Metadata Violation: Both reviewed_by and reviewed_at must be non-NULL when transitioning from PENDING to %.', NEW.status;
            END IF;
        END IF;

        -- 4. Review Metadata Changes Allowed ONLY During Initial PENDING -> APPROVED/REJECTED Transition
        IF (NEW.reviewed_by IS DISTINCT FROM OLD.reviewed_by OR NEW.reviewed_at IS DISTINCT FROM OLD.reviewed_at) THEN
            IF NOT (OLD.status = 'PENDING' AND NEW.status IN ('APPROVED', 'REJECTED') AND OLD.reviewed_by IS NULL AND OLD.reviewed_at IS NULL AND NEW.reviewed_by IS NOT NULL AND NEW.reviewed_at IS NOT NULL) THEN
                RAISE EXCEPTION 'Metadata Violation: Review metadata can only be populated together during initial approval or rejection from PENDING.';
            END IF;
        END IF;

        -- 5. Enforce State Machine Transitions (Allow APPROVED -> APPROVED, COMPLETED, or REJECTED for reconciliation)
        IF OLD.status = 'PENDING' AND NEW.status NOT IN ('PENDING', 'APPROVED', 'REJECTED') THEN
            RAISE EXCEPTION 'Invalid Status Transition: PENDING password reset request can only transition to APPROVED or REJECTED.';
        ELSIF OLD.status = 'APPROVED' AND NEW.status NOT IN ('APPROVED', 'COMPLETED', 'REJECTED') THEN
            RAISE EXCEPTION 'Invalid Status Transition: APPROVED password reset request can only transition to COMPLETED or REJECTED.';
        ELSIF OLD.status IN ('REJECTED', 'COMPLETED') AND NEW.status IS DISTINCT FROM OLD.status THEN
            RAISE EXCEPTION 'Invalid Status Transition: Terminal password reset request status (%) cannot be changed.', OLD.status;
        END IF;
    END IF;

    RETURN NEW;
END;
$$;

-- 2. RPC: reconcile_uncertain_password_reset
-- Action: Locks profiles 1st (FOR UPDATE), locks account_otps 2nd (FOR UPDATE), locks password_reset_requests 3rd (FOR UPDATE).
--         Validates System Owner caller via auth.uid(), checks UNCERTAIN status and PASSWORD_RESET type,
--         and applies COMPLETE (OTP -> COMPLETED, Request -> COMPLETED) or REJECT (OTP -> SUPERSEDED, Request -> REJECTED).
CREATE OR REPLACE FUNCTION public.reconcile_uncertain_password_reset(
    p_otp_id UUID,
    p_action TEXT
)
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_reviewer_id UUID;
    v_clean_action TEXT;
    v_snapshot RECORD;
    v_profile RECORD;
    v_record RECORD;
    v_request RECORD;
    v_req_count INT;
BEGIN
    v_clean_action := UPPER(TRIM(COALESCE(p_action, '')));
    IF v_clean_action NOT IN ('COMPLETE', 'REJECT') THEN
        RAISE EXCEPTION 'Validation failed: Action must be COMPLETE or REJECT.';
    END IF;

    IF p_otp_id IS NULL THEN
        RAISE EXCEPTION 'Validation failed: p_otp_id is required.';
    END IF;

    -- Authenticate caller session via auth.uid() and verify System Owner role
    v_reviewer_id := auth.uid();
    IF v_reviewer_id IS NULL THEN
        RAISE EXCEPTION 'Unauthorized: Session JWT is missing or unauthenticated.';
    END IF;

    IF NOT public.is_system_owner() THEN
        RAISE EXCEPTION 'Forbidden: System Owner access required for password reset reconciliation.';
    END IF;

    -- Unlocked snapshot lookup to resolve user_id and reset_request_id
    SELECT user_id, reset_request_id INTO v_snapshot
    FROM public.account_otps
    WHERE id = p_otp_id;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Validation failed: Account OTP record not found.';
    END IF;

    IF v_snapshot.reset_request_id IS NULL THEN
        RAISE EXCEPTION 'Validation failed: OTP record is not associated with a password reset request.';
    END IF;

    -- LOCK STEP 1: Lock canonical public.profiles row FIRST (FOR UPDATE)
    SELECT * INTO v_profile
    FROM public.profiles
    WHERE id = v_snapshot.user_id
    FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Validation failed: Profile record not found for account.';
    END IF;

    -- LOCK STEP 2: Lock matching public.account_otps row SECOND (FOR UPDATE)
    SELECT * INTO v_record
    FROM public.account_otps
    WHERE id = p_otp_id
    FOR UPDATE;

    IF NOT FOUND OR v_record.user_id <> v_profile.id THEN
        RAISE EXCEPTION 'Validation failed: Account OTP record mismatch during row locking.';
    END IF;

    -- Verify OTP status is UNCERTAIN and code_type is PASSWORD_RESET
    IF v_record.code_type <> 'PASSWORD_RESET' OR v_record.status <> 'UNCERTAIN' THEN
        RAISE EXCEPTION 'Validation failed: OTP record is not an UNCERTAIN PASSWORD_RESET record (status: %, type: %).', v_record.status, v_record.code_type;
    END IF;

    -- LOCK STEP 3: Lock matching public.password_reset_requests row THIRD (FOR UPDATE)
    SELECT * INTO v_request
    FROM public.password_reset_requests
    WHERE id = v_record.reset_request_id
    FOR UPDATE;

    IF NOT FOUND OR v_request.user_id <> v_profile.id THEN
        RAISE EXCEPTION 'Validation failed: Password reset request record mismatch during row locking.';
    END IF;

    -- Verify reset request is still APPROVED
    IF v_request.status <> 'APPROVED' THEN
        RAISE EXCEPTION 'Validation failed: Password reset request is no longer APPROVED (current status: %).', v_request.status;
    END IF;

    -- Execute action
    IF v_clean_action = 'COMPLETE' THEN
        UPDATE public.account_otps
        SET status = 'COMPLETED',
            completed_at = pg_catalog.now()
        WHERE id = v_record.id;

        UPDATE public.password_reset_requests
        SET status = 'COMPLETED'
        WHERE id = v_request.id
          AND status = 'APPROVED';

        GET DIAGNOSTICS v_req_count = ROW_COUNT;
        IF v_req_count <> 1 THEN
            RAISE EXCEPTION 'State Machine Violation: Expected exactly one APPROVED password reset request to be completed, updated %.', v_req_count;
        END IF;
    ELSE
        UPDATE public.account_otps
        SET status = 'SUPERSEDED',
            updated_at = pg_catalog.now()
        WHERE id = v_record.id;

        UPDATE public.password_reset_requests
        SET status = 'REJECTED'
        WHERE id = v_request.id
          AND status = 'APPROVED';

        GET DIAGNOSTICS v_req_count = ROW_COUNT;
        IF v_req_count <> 1 THEN
            RAISE EXCEPTION 'State Machine Violation: Expected exactly one APPROVED password reset request to be rejected, updated %.', v_req_count;
        END IF;
    END IF;

    -- Record audit log entry
    INSERT INTO public.audit_logs (actor_id, action, table_name, record_id, details)
    VALUES (v_reviewer_id, 'RECONCILE_UNCERTAIN_PASSWORD_RESET', 'account_otps', p_otp_id, jsonb_build_object('action', v_clean_action, 'reset_request_id', v_request.id, 'user_id', v_profile.id));

    RETURN TRUE;
END;
$$;

REVOKE EXECUTE ON FUNCTION public.reconcile_uncertain_password_reset(UUID, TEXT) FROM PUBLIC, anon, service_role;
GRANT EXECUTE ON FUNCTION public.reconcile_uncertain_password_reset(UUID, TEXT) TO authenticated;
