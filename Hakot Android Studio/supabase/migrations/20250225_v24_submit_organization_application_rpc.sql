-- ============================================================================
-- HAKOT VERSION 2.4 — FINAL CORRECTED RPC MIGRATION: submit_organization_application
-- Migration File: 20250225_v24_submit_organization_application_rpc.sql
-- Target Database: PostgreSQL / Supabase Project vyegsnkerrbdqacwegff
-- Description: Installs the final corrected atomic transactional PL/pgSQL RPC function
--              used by the submit-organization-application Edge Function to
--              securely insert organization applications and validated coverage.
-- ============================================================================

BEGIN;

-- CREATE OR REPLACE FUNCTION: public.submit_organization_application
-- Action: CREATES or REPLACES secure atomic transaction function for submitting organization applications
CREATE OR REPLACE FUNCTION public.submit_organization_application(
    p_organization_type TEXT,
    p_organization_name TEXT,
    p_email TEXT,
    p_rep_full_name TEXT,
    p_rep_position TEXT,
    p_contact_number TEXT,
    p_municipality_id UUID,
    p_requested_municipalities UUID[],
    p_cert_document_url TEXT,
    p_office_department TEXT
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $$
DECLARE
    v_app_id UUID;
    v_muni_id UUID;
    v_derived_role TEXT;
    v_clean_org_type TEXT;
    v_clean_org_name TEXT;
    v_clean_email TEXT;
    v_clean_rep_name TEXT;
    v_clean_rep_pos TEXT;
    v_clean_contact TEXT;
    v_clean_dept TEXT;
    v_unique_muni_ids UUID[];
BEGIN
    -- 1. Validate organization type
    v_clean_org_type := UPPER(TRIM(COALESCE(p_organization_type, '')));
    IF v_clean_org_type NOT IN ('LGU', 'PROVIDER') THEN
        RAISE EXCEPTION 'Validation failed: organization_type must be LGU or PROVIDER.';
    END IF;

    -- 2. Validate organization name (reject blank or > 150 chars)
    v_clean_org_name := TRIM(COALESCE(p_organization_name, ''));
    IF v_clean_org_name = '' OR LENGTH(v_clean_org_name) > 150 THEN
        RAISE EXCEPTION 'Validation failed: organization_name is required and must not exceed 150 characters.';
    END IF;

    -- 3. Validate email (reject blank, invalid format, or > 254 chars)
    v_clean_email := LOWER(TRIM(COALESCE(p_email, '')));
    IF v_clean_email = '' OR v_clean_email !~ '^[^\s@]+@[^\s@]+\.[^\s@]+$' OR LENGTH(v_clean_email) > 254 THEN
        RAISE EXCEPTION 'Validation failed: A valid email address is required.';
    END IF;

    -- 4. Validate representative full name (reject blank or > 100 chars)
    v_clean_rep_name := TRIM(COALESCE(p_rep_full_name, ''));
    IF v_clean_rep_name = '' OR LENGTH(v_clean_rep_name) > 100 THEN
        RAISE EXCEPTION 'Validation failed: Representative full name is required and must not exceed 100 characters.';
    END IF;

    -- 5. Validate representative position (reject if supplied blank, max length 100)
    IF p_rep_position IS NOT NULL AND TRIM(p_rep_position) = '' THEN
        RAISE EXCEPTION 'Validation failed: Representative position must not be blank when supplied.';
    END IF;
    v_clean_rep_pos := TRIM(COALESCE(p_rep_position, 'Organization Administrator'));
    IF LENGTH(v_clean_rep_pos) > 100 THEN
        RAISE EXCEPTION 'Validation failed: Representative position must not exceed 100 characters.';
    END IF;

    -- 6. Validate contact number (reject blank or > 50 chars)
    v_clean_contact := TRIM(COALESCE(p_contact_number, ''));
    IF v_clean_contact = '' OR LENGTH(v_clean_contact) > 50 THEN
        RAISE EXCEPTION 'Validation failed: Contact number is required and must not exceed 50 characters.';
    END IF;

    -- 7. Validate office department (reject if supplied blank, max length 100)
    IF p_office_department IS NOT NULL AND TRIM(p_office_department) = '' THEN
        RAISE EXCEPTION 'Validation failed: Office department must not be blank when supplied.';
    END IF;
    v_clean_dept := TRIM(COALESCE(p_office_department, CASE WHEN v_clean_org_type = 'LGU' THEN 'Municipal Sanitation Office' ELSE 'Operations Department' END));
    IF LENGTH(v_clean_dept) > 100 THEN
        RAISE EXCEPTION 'Validation failed: Office department must not exceed 100 characters.';
    END IF;

    -- 8. Validate primary municipality
    IF p_municipality_id IS NULL THEN
        RAISE EXCEPTION 'Validation failed: Primary municipality ID is required.';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM public.municipalities WHERE id = p_municipality_id) THEN
        RAISE EXCEPTION 'Validation failed: Primary municipality ID does not exist in public.municipalities.';
    END IF;

    -- 9. Enforce Organization Type Specific Rules & Coverage Rules
    IF v_clean_org_type = 'LGU' THEN
        v_derived_role := 'municipal_admin';
        -- LGU may only be associated with its primary municipality. Reject if requested_municipalities contains any other municipality.
        IF p_requested_municipalities IS NOT NULL AND array_length(p_requested_municipalities, 1) > 0 THEN
            FOREACH v_muni_id IN ARRAY p_requested_municipalities
            LOOP
                IF v_muni_id IS NULL OR v_muni_id <> p_municipality_id THEN
                    RAISE EXCEPTION 'Validation failed: LGU organizations can only be associated with their single primary municipality.';
                END IF;
            END LOOP;
        END IF;
        v_unique_muni_ids := ARRAY[p_municipality_id];

    ELSIF v_clean_org_type = 'PROVIDER' THEN
        v_derived_role := 'company_admin';
        v_unique_muni_ids := ARRAY[p_municipality_id];

        IF p_requested_municipalities IS NOT NULL AND array_length(p_requested_municipalities, 1) > 0 THEN
            FOREACH v_muni_id IN ARRAY p_requested_municipalities
            LOOP
                IF v_muni_id IS NULL THEN
                    RAISE EXCEPTION 'Validation failed: Requested municipalities array contains a NULL element.';
                END IF;

                IF NOT EXISTS (SELECT 1 FROM public.municipalities WHERE id = v_muni_id) THEN
                    RAISE EXCEPTION 'Validation failed: Requested operating municipality ID (%) does not exist.', v_muni_id;
                END IF;

                IF NOT (v_muni_id = ANY(v_unique_muni_ids)) THEN
                    v_unique_muni_ids := array_append(v_unique_muni_ids, v_muni_id);
                END IF;
            END LOOP;
        END IF;

        IF array_length(v_unique_muni_ids, 1) < 1 THEN
            RAISE EXCEPTION 'Validation failed: Provider application must include at least one valid operating municipality.';
        END IF;
    END IF;

    -- 10. Insert application record atomically
    INSERT INTO public.account_applications (
        organization_type,
        organization_name,
        full_name,
        email,
        contact_number,
        municipality_id,
        office_department,
        official_position,
        requested_role,
        company_name,
        cert_document_url,
        status
    ) VALUES (
        v_clean_org_type,
        v_clean_org_name,
        v_clean_rep_name,
        v_clean_email,
        v_clean_contact,
        p_municipality_id,
        v_clean_dept,
        v_clean_rep_pos,
        v_derived_role,
        CASE WHEN v_clean_org_type = 'PROVIDER' THEN v_clean_org_name ELSE '' END,
        COALESCE(p_cert_document_url, ''),
        'PENDING'
    )
    RETURNING id INTO v_app_id;

    -- 11. Insert verified municipal coverage into junction table atomically
    FOREACH v_muni_id IN ARRAY v_unique_muni_ids
    LOOP
        INSERT INTO public.account_application_municipalities (application_id, municipality_id)
        VALUES (v_app_id, v_muni_id)
        ON CONFLICT (application_id, municipality_id) DO NOTHING;
    END LOOP;

    RETURN v_app_id;
END;
$$;

-- Restrict execution to service_role; revoke from PUBLIC, anon, and authenticated
REVOKE EXECUTE ON FUNCTION public.submit_organization_application(TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, UUID, UUID[], TEXT, TEXT) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.submit_organization_application(TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, UUID, UUID[], TEXT, TEXT) TO service_role;

COMMIT;
