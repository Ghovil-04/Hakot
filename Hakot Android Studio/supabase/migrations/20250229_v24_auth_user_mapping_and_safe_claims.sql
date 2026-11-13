-- ============================================================================
-- HAKOT VERSION 2.4 — AUTH USER MAPPING & SAFE CLAIM LOCK MIGRATION
-- Migration File: 20250229_v24_auth_user_mapping_and_safe_claims.sql
-- Target Database: PostgreSQL / Supabase Project vyegsnkerrbdqacwegff
-- Description: Adds server-controlled auth_user_id mapping to account_applications
--              with unique indexing, and secures claim_approval_lock against
--              unsafe automatic takeovers while maintaining exact coverage rules.
-- ============================================================================

BEGIN;

-- 1. Add server-controlled auth_user_id mapping to account_applications
ALTER TABLE public.account_applications
    ADD COLUMN IF NOT EXISTS auth_user_id UUID REFERENCES auth.users(id) ON DELETE SET NULL;

CREATE UNIQUE INDEX IF NOT EXISTS v24_idx_unique_app_auth_user
ON public.account_applications (auth_user_id)
WHERE auth_user_id IS NOT NULL;

-- 2. Secure claim_approval_lock: Remove unsafe automatic takeover.
-- Only allow claims if PENDING and not actively CLAIMED (unless FAILED or released).
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
            -- Unsafe automatic takeover based on organization existence is removed.
            -- Active claims held by another request cannot be stolen.
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

-- 3. Corrected provision_organization_for_application with proper declaration of v_muni_id
CREATE OR REPLACE FUNCTION public.provision_organization_for_application(
    p_app_id UUID
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_app RECORD;
    v_org_id UUID;
    v_org RECORD;
    v_org_name TEXT;
    v_org_type TEXT;
    v_muni_id UUID;
    v_muni_row RECORD;
    v_primary_found BOOLEAN := FALSE;
    v_muni_count INT := 0;
    v_expected_muni_ids UUID[];
    v_actual_muni_ids UUID[];
BEGIN
    -- 1. Protect against concurrent provisioning by locking the application row with FOR UPDATE
    SELECT * INTO v_app
    FROM public.account_applications
    WHERE id = p_app_id
    FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Validation failed: Application not found.';
    END IF;

    -- 2. Validate organization type (allow only LGU and PROVIDER)
    v_org_type := UPPER(TRIM(COALESCE(v_app.organization_type, '')));
    IF v_org_type NOT IN ('LGU', 'PROVIDER') THEN
        RAISE EXCEPTION 'Validation failed: organization_type must be either LGU or PROVIDER.';
    END IF;

    IF v_app.municipality_id IS NULL THEN
        RAISE EXCEPTION 'Validation failed: Primary municipality ID is required.';
    END IF;

    -- 3. Gather and validate requested municipality coverage from account_application_municipalities
    v_expected_muni_ids := ARRAY[v_app.municipality_id];

    FOR v_muni_row IN
        SELECT municipality_id
        FROM public.account_application_municipalities
        WHERE application_id = p_app_id
    LOOP
        v_muni_id := v_muni_row.municipality_id;
        IF v_muni_id IS NULL THEN
            RAISE EXCEPTION 'Validation failed: Municipality coverage contains a NULL ID.';
        END IF;
        IF NOT EXISTS (SELECT 1 FROM public.municipalities WHERE id = v_muni_id) THEN
            RAISE EXCEPTION 'Validation failed: Municipality ID (%) in application coverage does not exist.', v_muni_id;
        END IF;
        IF v_muni_id = v_app.municipality_id THEN
            v_primary_found := TRUE;
        END IF;
        IF NOT (v_muni_id = ANY(v_expected_muni_ids)) THEN
            v_expected_muni_ids := array_append(v_expected_muni_ids, v_muni_id);
        END IF;
    END LOOP;

    IF NOT v_primary_found THEN
        RAISE EXCEPTION 'Validation failed: Primary municipality is not included in application coverage records.';
    END IF;

    -- Enforce exact organization coverage rules
    IF v_org_type = 'LGU' THEN
        -- LGU must have exactly one municipality (the primary municipality)
        IF array_length(v_expected_muni_ids, 1) > 1 OR v_expected_muni_ids[1] <> v_app.municipality_id THEN
            RAISE EXCEPTION 'Validation failed: LGU organizations can only be associated with their single primary municipality.';
        END IF;
    ELSIF v_org_type = 'PROVIDER' THEN
        -- Provider must have at least primary municipality plus requested coverage
        IF array_length(v_expected_muni_ids, 1) < 1 THEN
            RAISE EXCEPTION 'Validation failed: Provider application must include at least one valid operating municipality.';
        END IF;
    END IF;

    -- Determine expected organization name
    v_org_name := COALESCE(NULLIF(TRIM(v_app.organization_name), ''), NULLIF(TRIM(v_app.company_name), ''), TRIM(v_app.full_name) || ' Organization');

    -- 4. Check if application already has an organization_id associated (Idempotent Recovery)
    v_org_id := v_app.organization_id;

    IF v_org_id IS NOT NULL THEN
        -- Verify existing organization attributes and exact coverage match
        SELECT * INTO v_org
        FROM public.organizations
        WHERE id = v_org_id;

        IF NOT FOUND THEN
            RAISE EXCEPTION 'Validation failed: Linked organization ID does not exist in organizations table.';
        END IF;

        IF v_org.type <> v_org_type THEN
            RAISE EXCEPTION 'Validation failed: Linked organization type (%) does not match application organization type (%).', v_org.type, v_org_type;
        END IF;

        IF v_org.municipality_id <> v_app.municipality_id THEN
            RAISE EXCEPTION 'Validation failed: Linked organization primary municipality does not match application municipality.';
        END IF;

        IF v_org.name <> v_org_name THEN
            RAISE EXCEPTION 'Validation failed: Linked organization name (%) does not match application organization name (%).', v_org.name, v_org_name;
        END IF;

        -- Verify exact coverage match (no missing, no unexpected)
        SELECT array_agg(municipality_id ORDER BY municipality_id) INTO v_actual_muni_ids
        FROM public.organization_municipalities
        WHERE organization_id = v_org_id;

        -- Sort expected for comparison
        SELECT array_agg(m ORDER BY m) INTO v_expected_muni_ids
        FROM unnest(v_expected_muni_ids) m;

        IF v_actual_muni_ids IS DISTINCT FROM v_expected_muni_ids THEN
            RAISE EXCEPTION 'Validation failed: Linked organization municipality coverage does not match application requirements exactly.';
        END IF
;

        RETURN v_org_id;
    END IF;

    -- 5. Create new organization record atomically
    INSERT INTO public.organizations (
        name,
        type,
        municipality_id
    ) VALUES (
        v_org_name,
        v_org_type,
        v_app.municipality_id
    )
    RETURNING id INTO v_org_id;

    -- Link organization to account_applications
    UPDATE public.account_applications
    SET organization_id = v_org_id
    WHERE id = p_app_id;

    -- Insert all verified municipal coverage records into organization_municipalities
    FOREACH v_muni_id IN ARRAY v_expected_muni_ids
    LOOP
        INSERT INTO public.organization_municipalities (organization_id, municipality_id)
        VALUES (v_org_id, v_muni_id)
        ON CONFLICT (organization_id, municipality_id) DO NOTHING;
    END LOOP;

    RETURN v_org_id;
END;
$$;

REVOKE EXECUTE ON FUNCTION public.provision_organization_for_application(UUID) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.provision_organization_for_application(UUID) TO service_role;

COMMIT;
