-- ============================================================================
-- HAKOT Version 2.4 — Approval Claims Architecture Migration (Final Corrections)
-- Local migration artifact (Do NOT execute live without verification)
-- ============================================================================

-- 1. Create approval_claims table
CREATE TABLE IF NOT EXISTS public.approval_claims (
    application_id UUID PRIMARY KEY REFERENCES public.account_applications(id) ON DELETE CASCADE,
    owner_token UUID NOT NULL DEFAULT pg_catalog.gen_random_uuid(),
    status TEXT NOT NULL CHECK (status IN ('CLAIMED', 'PROVISIONED', 'FAILED')),
    claimed_by UUID NOT NULL, -- references auth.users(id)
    claimed_at TIMESTAMPTZ NOT NULL DEFAULT pg_catalog.now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT pg_catalog.now(),
    error_message TEXT
);

-- 2. Security: Enable RLS and repeatable policy creation
ALTER TABLE public.approval_claims ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Deny direct access to approval_claims" ON public.approval_claims;
CREATE POLICY "Deny direct access to approval_claims"
ON public.approval_claims
FOR ALL
USING (false)
WITH CHECK (false);

REVOKE ALL ON public.approval_claims FROM PUBLIC, anon, authenticated;
GRANT ALL ON public.approval_claims TO service_role;

-- Drop old 3-parameter claim_approval_lock overload if it exists
DROP FUNCTION IF EXISTS public.claim_approval_lock(UUID, UUID, BOOLEAN);

-- 3. RPC: claim_approval_lock (Uniform Lock Order: 1. account_applications, 2. approval_claims)
-- Rejects existing CLAIMED records unconditionally (no automatic takeover/resume).
CREATE OR REPLACE FUNCTION public.claim_approval_lock(
    p_app_id UUID,
    p_admin_id UUID
)
RETURNS TABLE(success BOOLEAN, owner_token UUID, error_message TEXT)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_app_status TEXT;
    v_existing_claim_status TEXT;
    v_new_token UUID := pg_catalog.gen_random_uuid();
BEGIN
    -- Lock application row FIRST to establish consistent lock ordering
    SELECT status INTO v_app_status
    FROM public.account_applications
    WHERE id = p_app_id
    FOR UPDATE;

    IF NOT FOUND THEN
        RETURN QUERY SELECT FALSE, NULL::UUID, 'Application not found.'::TEXT;
        RETURN;
    END IF;

    IF v_app_status <> 'PENDING' THEN
        RETURN QUERY SELECT FALSE, NULL::UUID, ('Application is no longer pending (status: ' || v_app_status || ')')::TEXT;
        RETURN;
    END IF;

    -- Lock approval_claims row SECOND to establish consistent lock ordering
    SELECT status INTO v_existing_claim_status
    FROM public.approval_claims
    WHERE application_id = p_app_id
    FOR UPDATE;

    IF FOUND THEN
        IF v_existing_claim_status = 'PROVISIONED' THEN
            RETURN QUERY SELECT FALSE, NULL::UUID, 'Application has already been provisioned.'::TEXT;
            RETURN;
        ELSIF v_existing_claim_status = 'CLAIMED' THEN
            -- CLAIMED records must always be rejected; never replace owner token or take over claim
            RETURN QUERY SELECT FALSE, NULL::UUID, 'Application is currently locked by an active approval request. Takeover is prohibited.'::TEXT;
            RETURN;
        END IF;

        -- If status was FAILED, allow acquiring again with a newly generated owner token
        UPDATE public.approval_claims
        SET owner_token = v_new_token,
            status = 'CLAIMED',
            claimed_by = p_admin_id,
            claimed_at = pg_catalog.now(),
            updated_at = pg_catalog.now(),
            error_message = NULL
        WHERE application_id = p_app_id;
    ELSE
        INSERT INTO public.approval_claims (application_id, owner_token, status, claimed_by, claimed_at, updated_at)
        VALUES (p_app_id, v_new_token, 'CLAIMED', p_admin_id, pg_catalog.now(), pg_catalog.now());
    END IF;

    RETURN QUERY SELECT TRUE, v_new_token, NULL::TEXT;
END;
$$;

REVOKE EXECUTE ON FUNCTION public.claim_approval_lock(UUID, UUID) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.claim_approval_lock(UUID, UUID) TO service_role;

-- 4. RPC: release_or_fail_claim (Uniform Lock Order: 1. account_applications, 2. approval_claims)
CREATE OR REPLACE FUNCTION public.release_or_fail_claim(
    p_app_id UUID,
    p_owner_token UUID,
    p_error_message TEXT
)
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_app_status TEXT;
BEGIN
    -- Lock application row FIRST
    SELECT status INTO v_app_status
    FROM public.account_applications
    WHERE id = p_app_id
    FOR UPDATE;

    IF NOT FOUND THEN
        RETURN FALSE;
    END IF;

    -- Lock approval_claims row SECOND
    UPDATE public.approval_claims
    SET status = 'FAILED',
        error_message = p_error_message,
        updated_at = pg_catalog.now()
    WHERE application_id = p_app_id
      AND owner_token = p_owner_token
      AND status = 'CLAIMED';

    RETURN FOUND;
END;
$$;

REVOKE EXECUTE ON FUNCTION public.release_or_fail_claim(UUID, UUID, TEXT) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.release_or_fail_claim(UUID, UUID, TEXT) TO service_role;

-- 5. RPC: finalize_approval_claim (Uniform Lock Order: 1. account_applications, 2. approval_claims)
CREATE OR REPLACE FUNCTION public.finalize_approval_claim(
    p_app_id UUID,
    p_owner_token UUID,
    p_admin_id UUID
)
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_app_status TEXT;
    v_claim_status TEXT;
BEGIN
    -- Lock application row FIRST
    SELECT status INTO v_app_status
    FROM public.account_applications
    WHERE id = p_app_id
    FOR UPDATE;

    IF NOT FOUND OR v_app_status <> 'PENDING' THEN
        RETURN FALSE;
    END IF;

    -- Lock approval_claims row SECOND
    SELECT status INTO v_claim_status
    FROM public.approval_claims
    WHERE application_id = p_app_id AND owner_token = p_owner_token
    FOR UPDATE;

    IF NOT FOUND OR v_claim_status <> 'CLAIMED' THEN
        RETURN FALSE;
    END IF;

    UPDATE public.account_applications
    SET status = 'APPROVED',
        reviewed_at = pg_catalog.now(),
        reviewed_by = p_admin_id,
        review_notes = 'Approved and provisioned securely by System Owner.'
    WHERE id = p_app_id;

    UPDATE public.approval_claims
    SET status = 'PROVISIONED',
        updated_at = pg_catalog.now()
    WHERE application_id = p_app_id;

    RETURN TRUE;
END;
$$;

REVOKE EXECUTE ON FUNCTION public.finalize_approval_claim(UUID, UUID, UUID) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.finalize_approval_claim(UUID, UUID, UUID) TO service_role;

-- 6. RPC: reject_application_safely (Prevents concurrent APPROVE and REJECT race conditions)
CREATE OR REPLACE FUNCTION public.reject_application_safely(
    p_app_id UUID,
    p_admin_id UUID
)
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_app_status TEXT;
    v_claim_status TEXT;
BEGIN
    -- Lock application row FIRST
    SELECT status INTO v_app_status
    FROM public.account_applications
    WHERE id = p_app_id
    FOR UPDATE;

    IF NOT FOUND OR v_app_status <> 'PENDING' THEN
        RETURN FALSE;
    END IF;

    -- Lock approval_claims row SECOND to check if an active approval operation is claiming or provisioning it
    SELECT status INTO v_claim_status
    FROM public.approval_claims
    WHERE application_id = p_app_id
    FOR UPDATE;

    IF FOUND AND v_claim_status IN ('CLAIMED', 'PROVISIONED') THEN
        -- Cannot reject while an approval claim is active or provisioned
        RETURN FALSE;
    END IF;

    UPDATE public.account_applications
    SET status = 'REJECTED',
        reviewed_at = pg_catalog.now(),
        reviewed_by = p_admin_id,
        review_notes = 'Application rejected by System Owner.'
    WHERE id = p_app_id;

    -- If a failed claim existed, mark it failed/superseded
    IF FOUND AND v_claim_status = 'FAILED' THEN
        UPDATE public.approval_claims
        SET status = 'FAILED', error_message = 'Superceded by rejection.', updated_at = pg_catalog.now()
        WHERE application_id = p_app_id;
    END IF;

    RETURN TRUE;
END;
$$;

REVOKE EXECUTE ON FUNCTION public.reject_application_safely(UUID, UUID) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.reject_application_safely(UUID, UUID) TO service_role;
