-- ============================================================================
-- HAKOT VERSION 2.4 — APPLICATION IMMUTABILITY & RESET FENCING MIGRATION
-- Migration File: 20250303_v24_immutability_and_reset_fencing.sql
-- Target Database: PostgreSQL 17.11 / Supabase Project Staging
-- Description: Establishes database-level immutability triggers for approved organization
--              applications, state-machine validation and unique partial indexing for
--              password reset requests, least-privilege table grants, and the System Owner
--              password-reset review RPC with profile-first row locking.
-- ============================================================================

BEGIN;

-- ============================================================================
-- SECTION 1: APPLICATION IMMUTABILITY TRIGGER
-- ============================================================================

-- 1.1 Trigger Function: Prevent modification of approved applications
CREATE OR REPLACE FUNCTION public.v24_fn_prevent_approved_app_tampering()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
BEGIN
    IF OLD.status = 'APPROVED' THEN
        IF NEW.status IS DISTINCT FROM OLD.status THEN
            RAISE EXCEPTION 'Immutability Violation: Approved application status cannot be changed (current status: APPROVED).';
        END IF;

        IF NEW.auth_user_id IS DISTINCT FROM OLD.auth_user_id THEN
            RAISE EXCEPTION 'Immutability Violation: Approved application auth_user_id cannot be reassigned.';
        END IF;

        IF NEW.organization_id IS DISTINCT FROM OLD.organization_id THEN
            RAISE EXCEPTION 'Immutability Violation: Approved application organization_id cannot be reassigned.';
        END IF;
    END IF;

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS v24_trg_prevent_approved_app_tampering ON public.account_applications;
CREATE TRIGGER v24_trg_prevent_approved_app_tampering
    BEFORE UPDATE ON public.account_applications
    FOR EACH ROW
    EXECUTE FUNCTION public.v24_fn_prevent_approved_app_tampering();

-- ============================================================================
-- SECTION 2: PASSWORD RESET REQUEST FENCING & STATE MACHINE
-- ============================================================================

-- 2.1 Preflight check for duplicate outstanding reset requests
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM public.password_reset_requests
        WHERE status IN ('PENDING', 'APPROVED')
        GROUP BY user_id
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'Preflight Failure: Duplicate outstanding (PENDING or APPROVED) password reset requests exist in public.password_reset_requests. Manual administrative resolution required before index creation.';
    END IF;
END $$;

-- 2.2 Unique Partial Index: One outstanding (PENDING or APPROVED) reset request per user
CREATE UNIQUE INDEX IF NOT EXISTS idx_unique_outstanding_reset_request
ON public.password_reset_requests (user_id)
WHERE status IN ('PENDING', 'APPROVED');

-- 2.3 Trigger Function: Enforce immutable identity, metadata protection, and allowed state transitions
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

        -- 5. Enforce State Machine Transitions
        IF OLD.status = 'PENDING' AND NEW.status NOT IN ('PENDING', 'APPROVED', 'REJECTED') THEN
            RAISE EXCEPTION 'Invalid Status Transition: PENDING password reset request can only transition to APPROVED or REJECTED.';
        ELSIF OLD.status = 'APPROVED' AND NEW.status NOT IN ('APPROVED', 'COMPLETED') THEN
            RAISE EXCEPTION 'Invalid Status Transition: APPROVED password reset request can only transition to COMPLETED.';
        ELSIF OLD.status IN ('REJECTED', 'COMPLETED') AND NEW.status IS DISTINCT FROM OLD.status THEN
            RAISE EXCEPTION 'Invalid Status Transition: Terminal password reset request status (%) cannot be changed.', OLD.status;
        END IF;
    END IF;

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS v24_trg_validate_password_reset_request ON public.password_reset_requests;
CREATE TRIGGER v24_trg_validate_password_reset_request
    BEFORE UPDATE ON public.password_reset_requests
    FOR EACH ROW
    EXECUTE FUNCTION public.v24_fn_validate_password_reset_request();

-- ============================================================================
-- SECTION 3: STRICT LEAST-PRIVILEGE PERMISSIONS FOR PASSWORD RESET REQUESTS
-- ============================================================================

-- Explicitly revoke all privileges from public, anon, authenticated, and service_role
REVOKE ALL ON public.password_reset_requests FROM PUBLIC, anon, authenticated, service_role;

-- Grant column-specific SELECT and INSERT to authenticated
GRANT SELECT ON public.password_reset_requests TO authenticated;
GRANT INSERT (user_id, organization_id, email, reason) ON public.password_reset_requests TO authenticated;

-- Grant SELECT only to service_role (UPDATE and DELETE remain completely REVOKED)
GRANT SELECT ON public.password_reset_requests TO service_role;

-- ============================================================================
-- SECTION 4: SYSTEM OWNER PASSWORD RESET REVIEW RPC
-- ============================================================================

-- RPC: review_password_reset_request
-- Action: Locks profiles 1st (FOR UPDATE), locks password_reset_requests 2nd (FOR UPDATE),
--         validates PENDING status, verifies auth.uid() System Owner role, applies APPROVED or REJECTED action,
--         sets reviewed_by & reviewed_at, and writes audit log.
CREATE OR REPLACE FUNCTION public.review_password_reset_request(
    p_request_id UUID,
    p_action TEXT
)
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_snapshot RECORD;
    v_profile RECORD;
    v_request RECORD;
    v_clean_action TEXT;
    v_reviewer_id UUID;
BEGIN
    v_clean_action := UPPER(TRIM(COALESCE(p_action, '')));
    IF v_clean_action NOT IN ('APPROVE', 'REJECT') THEN
        RAISE EXCEPTION 'Validation failed: Action must be APPROVE or REJECT.';
    END IF;

    IF p_request_id IS NULL THEN
        RAISE EXCEPTION 'Validation failed: p_request_id is required.';
    END IF;

    -- Authenticate caller session via auth.uid() and verify System Owner role
    v_reviewer_id := auth.uid();
    IF v_reviewer_id IS NULL THEN
        RAISE EXCEPTION 'Unauthorized: Session JWT is missing or unauthenticated.';
    END IF;

    IF NOT public.is_system_owner() THEN
        RAISE EXCEPTION 'Forbidden: System Owner access required for password reset request review.';
    END IF;

    -- Unlocked snapshot lookup to resolve user_id
    SELECT user_id INTO v_snapshot
    FROM public.password_reset_requests
    WHERE id = p_request_id;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Validation failed: Password reset request not found.';
    END IF;

    -- LOCK STEP 1: Lock canonical public.profiles row FIRST (FOR UPDATE)
    SELECT * INTO v_profile
    FROM public.profiles
    WHERE id = v_snapshot.user_id
    FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Validation failed: Profile record not found for account.';
    END IF;

    -- LOCK STEP 2: Lock target public.password_reset_requests row SECOND (FOR UPDATE)
    SELECT * INTO v_request
    FROM public.password_reset_requests
    WHERE id = p_request_id
    FOR UPDATE;

    IF NOT FOUND OR v_request.user_id <> v_profile.id THEN
        RAISE EXCEPTION 'Validation failed: Password reset request mismatch during row locking.';
    END IF;

    -- Revalidate current status (must be PENDING)
    IF v_request.status <> 'PENDING' THEN
        RAISE EXCEPTION 'Validation failed: Password reset request is no longer pending (current status: %).', v_request.status;
    END IF;

    -- Execute approved state transition (preserving original requester reason, populating reviewed_by & reviewed_at atomically)
    IF v_clean_action = 'APPROVE' THEN
        UPDATE public.password_reset_requests
        SET status = 'APPROVED',
            reviewed_at = pg_catalog.now(),
            reviewed_by = v_reviewer_id
        WHERE id = p_request_id;
    ELSE
        UPDATE public.password_reset_requests
        SET status = 'REJECTED',
            reviewed_at = pg_catalog.now(),
            reviewed_by = v_reviewer_id
        WHERE id = p_request_id;
    END IF;

    -- Write audit log entry
    INSERT INTO public.audit_logs (actor_id, action, table_name, record_id, details)
    VALUES (v_reviewer_id, 'REVIEW_PASSWORD_RESET_REQUEST', 'password_reset_requests', p_request_id, jsonb_build_object('action', v_clean_action, 'user_id', v_profile.id));

    RETURN TRUE;
END;
$$;

-- Restrict execution to authenticated users (RPC authenticates System Owner session via auth.uid())
REVOKE EXECUTE ON FUNCTION public.review_password_reset_request(UUID, TEXT) FROM PUBLIC, anon, service_role;
GRANT EXECUTE ON FUNCTION public.review_password_reset_request(UUID, TEXT) TO authenticated;

COMMIT;
