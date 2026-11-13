-- ============================================================================
-- HAKOT VERSION 2.4 — DURABLE INVITATION STATE MIGRATION WITH OWNER-TOKEN FENCING
-- Migration File: 20250230_v24_durable_invitation_state.sql
-- Target Database: PostgreSQL / Supabase Project vyegsnkerrbdqacwegff
-- Description: Adds durable invitation state tracking columns to account_applications
--              and provides an atomic, owner-token fenced state-transition RPC function
--              with controlled application error codes and a strict allowlist.
-- ============================================================================

BEGIN;

-- 1. Add durable invitation tracking columns to account_applications
ALTER TABLE public.account_applications
    ADD COLUMN IF NOT EXISTS invitation_state TEXT NOT NULL DEFAULT 'NOT_ATTEMPTED' CHECK (invitation_state IN ('NOT_ATTEMPTED', 'ATTEMPT_IN_PROGRESS', 'INVITATION_CONFIRMED', 'OUTCOME_UNKNOWN', 'DEFINITIVELY_FAILED')),
    ADD COLUMN IF NOT EXISTS invitation_attempted_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS invitation_confirmed_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS invitation_error TEXT;

-- Drop old function overloads safely
DROP FUNCTION IF EXISTS public.transition_invitation_state(UUID, TEXT, TEXT, TEXT);
DROP FUNCTION IF EXISTS public.transition_invitation_state(UUID, UUID, TEXT, TEXT, TEXT);

-- 2. Atomic owner-token fenced state-transition function for invitations
CREATE OR REPLACE FUNCTION public.transition_invitation_state(
    p_app_id UUID,
    p_owner_token UUID,
    p_from_state TEXT,
    p_to_state TEXT,
    p_error_code TEXT DEFAULT NULL
)
RETURNS TABLE(success BOOLEAN, current_state TEXT, error_message TEXT)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_app_status TEXT;
    v_current_state TEXT;
    v_claim_status TEXT;
    v_claim_owner_token UUID;
    v_controlled_error TEXT;
    v_valid_states TEXT[] := ARRAY['NOT_ATTEMPTED', 'ATTEMPT_IN_PROGRESS', 'INVITATION_CONFIRMED', 'OUTCOME_UNKNOWN', 'DEFINITIVELY_FAILED'];
    v_valid_codes TEXT[] := ARRAY['AUTH_INVITE_TIMEOUT', 'AUTH_INVITE_REJECTED', 'NETWORK_FAILURE', 'UNKNOWN_ERROR'];
BEGIN
    -- Input validation: Reject NULL or empty required parameters
    IF p_app_id IS NULL OR p_owner_token IS NULL OR p_from_state IS NULL OR p_to_state IS NULL THEN
        RETURN QUERY SELECT FALSE, NULL::TEXT, 'Validation failed: Required parameters (app_id, owner_token, from_state, to_state) cannot be NULL.'::TEXT;
        RETURN;
    END IF;

    IF NOT (p_from_state = ANY(v_valid_states)) OR NOT (p_to_state = ANY(v_valid_states)) THEN
        RETURN QUERY SELECT FALSE, NULL::TEXT, 'Validation failed: Invalid state name provided.'::TEXT;
        RETURN;
    END IF;

    -- Controlled error mapping (never persisting arbitrary raw external error strings or credentials)
    IF p_to_state IN ('OUTCOME_UNKNOWN', 'DEFINITIVELY_FAILED') THEN
        IF p_error_code IS NULL OR NOT (p_error_code = ANY(v_valid_codes)) THEN
            v_controlled_error := 'UNKNOWN_ERROR: An unspecified error occurred during the invitation process.';
        ELSE
            v_controlled_error := CASE p_error_code
                WHEN 'AUTH_INVITE_TIMEOUT' THEN 'AUTH_INVITE_TIMEOUT: The invitation request timed out or received an ambiguous response.'
                WHEN 'AUTH_INVITE_REJECTED' THEN 'AUTH_INVITE_REJECTED: Supabase Auth rejected the invitation request.'
                WHEN 'NETWORK_FAILURE' THEN 'NETWORK_FAILURE: A network communication failure occurred during invitation.'
                ELSE 'UNKNOWN_ERROR: An unspecified error occurred during the invitation process.'
            END;
        END IF;
    ELSIF p_to_state = 'INVITATION_CONFIRMED' THEN
        v_controlled_error := NULL;
    END IF;

    -- 1. Lock application row FIRST to establish consistent lock ordering
    SELECT status, invitation_state INTO v_app_status, v_current_state
    FROM public.account_applications
    WHERE id = p_app_id
    FOR UPDATE;

    IF NOT FOUND THEN
        RETURN QUERY SELECT FALSE, NULL::TEXT, 'Application not found.'::TEXT;
        RETURN;
    END IF;

    IF v_app_status <> 'PENDING' THEN
        RETURN QUERY SELECT FALSE, NULL::TEXT, ('Application is no longer pending (status: ' || v_app_status || ')')::TEXT;
        RETURN;
    END IF;

    -- 2. Lock approval_claims row SECOND to establish consistent lock ordering & owner-token fencing
    SELECT status, owner_token INTO v_claim_status, v_claim_owner_token
    FROM public.approval_claims
    WHERE application_id = p_app_id
    FOR UPDATE;

    IF NOT FOUND THEN
        RETURN QUERY SELECT FALSE, v_current_state, 'Approval claim record not found for application.'::TEXT;
        RETURN;
    END IF;

    -- 3. Verify CLAIMED status and owner token matching
    IF v_claim_status IS DISTINCT FROM 'CLAIMED' THEN
        RETURN QUERY SELECT FALSE, v_current_state, ('Approval claim is not active (status: ' || v_claim_status || ')')::TEXT;
        RETURN;
    END IF;

    IF v_claim_owner_token IS DISTINCT FROM p_owner_token THEN
        RETURN QUERY SELECT FALSE, v_current_state, 'Owner token mismatch. Access denied.'::TEXT;
        RETURN;
    END IF;

    -- 4. Verify current state matches expected from_state
    IF v_current_state <> p_from_state THEN
        RETURN QUERY SELECT FALSE, v_current_state, ('State transition violation: Expected invitation state to be ' || p_from_state || ', but found ' || v_current_state)::TEXT;
        RETURN;
    END IF;

    -- 5. Explicitly restrict permitted transitions (Strict Allowlist)
    IF NOT (
        (p_from_state = 'NOT_ATTEMPTED' AND p_to_state = 'ATTEMPT_IN_PROGRESS') OR
        (p_from_state = 'ATTEMPT_IN_PROGRESS' AND p_to_state = 'INVITATION_CONFIRMED') OR
        (p_from_state = 'ATTEMPT_IN_PROGRESS' AND p_to_state = 'OUTCOME_UNKNOWN') OR
        (p_from_state = 'ATTEMPT_IN_PROGRESS' AND p_to_state = 'DEFINITIVELY_FAILED')
    ) THEN
        RETURN QUERY SELECT FALSE, v_current_state, ('Prohibited state transition: ' || p_from_state || ' -> ' || p_to_state)::TEXT;
        RETURN;
    END IF;

    -- 6. Perform atomic update
    UPDATE public.account_applications
    SET invitation_state = p_to_state,
        invitation_attempted_at = CASE WHEN p_to_state = 'ATTEMPT_IN_PROGRESS' THEN pg_catalog.now() ELSE invitation_attempted_at END,
        invitation_confirmed_at = CASE WHEN p_to_state = 'INVITATION_CONFIRMED' THEN pg_catalog.now() ELSE invitation_confirmed_at END,
        invitation_error = v_controlled_error
    WHERE id = p_app_id;

    RETURN QUERY SELECT TRUE, p_to_state, NULL::TEXT;
END;
$$;

REVOKE EXECUTE ON FUNCTION public.transition_invitation_state(UUID, UUID, TEXT, TEXT, TEXT) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.transition_invitation_state(UUID, UUID, TEXT, TEXT, TEXT) TO service_role;

COMMIT;
