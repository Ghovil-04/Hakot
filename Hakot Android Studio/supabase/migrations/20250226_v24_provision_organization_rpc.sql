-- ============================================================================
-- HAKOT VERSION 2.4 — ROBUST ATOMIC ORGANIZATION PROVISIONING RPC & SCHEMA FIX
-- Migration File: 20250226_v24_provision_organization_rpc.sql
-- Target Database: PostgreSQL / Supabase Project vyegsnkerrbdqacwegff
-- Description: Adds organization_id to account_applications and provides an atomic,
--              concurrency-safe, rigorous PL/pgSQL provisioning function with attribute
--              verification, row-level locking (SELECT FOR UPDATE), and strict name mismatch rejection.
--              Correctly bifurcates LGU (single municipality_id on organizations) and
--              PROVIDER (organization_municipalities junction table) provisioning paths.
-- ============================================================================

BEGIN;

-- 1. Add organization_id FK to account_applications for idempotency and retry safety
ALTER TABLE public.account_applications
    ADD COLUMN IF NOT EXISTS organization_id UUID REFERENCES public.organizations(id) ON DELETE SET NULL;

-- 2. Robust Atomic Provisioning RPC with Concurrency Locking & Attribute Verification
CREATE OR REPLACE FUNCTION public.provision_organization_for_application(
    p_app_id UUID
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $$
DECLARE
    v_app RECORD;
    v_org_id UUID;
    v_org RECORD;
    v_org_name TEXT;
    v_org_type TEXT;
    v_muni_row RECORD;
    v_primary_found BOOLEAN := FALSE;
    v_muni_count INT := 0;
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

    -- 3. Thoroughly validate municipality coverage from account_application_municipalities
    FOR v_muni_row IN
        SELECT municipality_id
        FROM public.account_application_municipalities
        WHERE application_id = p_app_id
    LOOP
        IF v_muni_row.municipality_id IS NULL THEN
            RAISE EXCEPTION 'Validation failed: Municipality coverage contains a NULL ID.';
        END IF;
        IF NOT EXISTS (SELECT 1 FROM public.municipalities WHERE id = v_muni_row.municipality_id) THEN
            RAISE EXCEPTION 'Validation failed: Municipality ID (%) in application coverage does not exist.', v_muni_row.municipality_id;
        END IF;
        v_muni_count := v_muni_count + 1;
        if v_muni_row.municipality_id = v_app.municipality_id THEN
            v_primary_found := TRUE;
        END IF;
    END LOOP;

    IF NOT v_primary_found THEN
        RAISE EXCEPTION 'Validation failed: Primary municipality is not included in application coverage records.';
    END IF;

    IF v_muni_count < 1 THEN
        RAISE EXCEPTION 'Validation failed: No municipal coverage records found for this application.';
    END IF;

    -- Determine expected organization name
    v_org_name := COALESCE(NULLIF(TRIM(v_app.organization_name), ''), NULLIF(TRIM(v_app.company_name), ''), TRIM(v_app.full_name) || ' Organization');

    -- 4. Check if application already has an organization_id associated (Idempotent Recovery)
    v_org_id := v_app.organization_id;

    IF v_org_id IS NOT NULL THEN
        -- Verify existing organization attributes and coverage
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

        -- Reject name mismatches explicitly instead of overwriting
        IF v_org.name <> v_org_name THEN
            RAISE EXCEPTION 'Validation failed: Linked organization name (%) does not match application organization name (%).', v_org.name, v_org_name;
        END IF;

        -- Verify and repair missing coverage records safely ONLY for PROVIDER organizations
        IF v_org_type = 'PROVIDER' THEN
            FOR v_muni_row IN
                SELECT DISTINCT municipality_id
                FROM public.account_application_municipalities
                WHERE application_id = p_app_id
            LOOP
                IF NOT EXISTS (
                    SELECT 1 FROM public.organization_municipalities
                    WHERE organization_id = v_org_id AND municipality_id = v_muni_row.municipality_id
                ) THEN
                    INSERT INTO public.organization_municipalities (organization_id, municipality_id)
                    VALUES (v_org_id, v_muni_row.municipality_id)
                    ON CONFLICT (organization_id, municipality_id) DO NOTHING;
                END IF;
            END LOOP;
        END IF;

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

    -- Insert all deduplicated municipal coverage records into organization_municipalities ONLY for PROVIDER organizations
    IF v_org_type = 'PROVIDER' THEN
        FOR v_muni_row IN
            SELECT DISTINCT municipality_id
            FROM public.account_application_municipalities
            WHERE application_id = p_app_id
        LOOP
            INSERT INTO public.organization_municipalities (organization_id, municipality_id)
            VALUES (v_org_id, v_muni_row.municipality_id)
            ON CONFLICT (organization_id, municipality_id) DO NOTHING;
        END LOOP;
    END IF;

    RETURN v_org_id;
END;
$$;

REVOKE EXECUTE ON FUNCTION public.provision_organization_for_application(UUID) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.provision_organization_for_application(UUID) TO service_role;

COMMIT;
