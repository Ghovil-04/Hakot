-- ============================================================================
-- HAKOT VERSION 2.4 — STALE CLAIM RECOVERY MIGRATION
-- Migration File: 20250227_v24_stale_claim_recovery_rpc.sql
-- Target Database: PostgreSQL / Supabase Project vyegsnkerrbdqacwegff
-- Description: Updates claim_approval_lock to allow safe recovery/takeover of
--              stale CLAIMED approval locks (older than 10 minutes) caused by
--              process crashes or unhandled timeouts, preventing permanent lockouts.
-- ============================================================================

BEGIN;

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
    v_claimed_at TIMESTAMPTZ;
    v_claimed_by UUID;
    v_new_token UUID := pg_catalog.gen_random_uuid();
BEGIN
    -- 1. Lock application row FIRST to establish consistent lock ordering
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

    -- 2. Lock approval_claims row SECOND to establish consistent lock ordering
    SELECT status, claimed_at, claimed_by INTO v_existing_claim_status, v_claimed_at, v_claimed_by
    FROM public.approval_claims
    WHERE application_id = p_app_id
    FOR UPDATE;

    IF FOUND THEN
        IF v_existing_claim_status = 'PROVISIONED' THEN
            RETURN QUERY SELECT FALSE, NULL::UUID, 'Application has already been provisioned.'::TEXT;
            RETURN;
        ELSIF v_existing_claim_status = 'CLAIMED' THEN
            -- Check if claim is active and NOT stale (less than 10 minutes old) unless same admin retrying
            IF v_claimed_at > (pg_catalog.now() - INTERVAL '10 minutes') AND v_claimed_by <> p_admin_id THEN
                RETURN QUERY SELECT FALSE, NULL::UUID, 'Application is currently locked by an active approval request. Takeover is prohibited.'::TEXT;
                RETURN;
            END IF;
        END IF;

        -- If status was FAILED, or CLAIMED is stale (> 10 mins old), or same admin retrying, acquire with new token
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

COMMIT;
