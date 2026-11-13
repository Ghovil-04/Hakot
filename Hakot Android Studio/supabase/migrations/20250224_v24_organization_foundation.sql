-- ============================================================================
-- HAKOT VERSION 2.4 — ORGANIZATION FOUNDATION & RLS SECURITY MIGRATION
-- Migration File: 20250224_v24_organization_foundation.sql
-- Target Database: PostgreSQL / Supabase Project vyegsnkerrbdqacwegff
-- ============================================================================

BEGIN;

-- ============================================================================
-- SECTION 1: PREFLIGHT CONFLICT CHECKS
-- ============================================================================

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'profiles' AND column_name = 'organization_id'
          AND data_type <> 'uuid'
    ) THEN
        RAISE EXCEPTION 'Preflight Failure: public.profiles.organization_id exists with an incompatible data type.';
    END IF;
END $$;

CREATE EXTENSION IF NOT EXISTS "pgcrypto" WITH SCHEMA extensions;

-- ============================================================================
-- SECTION 2: CANONICAL ORGANIZATION TABLES
-- ============================================================================

-- 2.1 Master Registry for LGU and Provider Organizations
CREATE TABLE IF NOT EXISTS public.organizations (
    id UUID PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    name TEXT NOT NULL,
    type TEXT NOT NULL CHECK (type IN ('LGU', 'PROVIDER')),
    municipality_id UUID REFERENCES public.municipalities(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT pg_catalog.now()
);

ALTER TABLE public.organizations ENABLE ROW LEVEL SECURITY;

-- 2.2 Relational Provider Operating Coverage Junction Table
CREATE TABLE IF NOT EXISTS public.organization_municipalities (
    organization_id UUID NOT NULL REFERENCES public.organizations(id) ON DELETE CASCADE,
    municipality_id UUID NOT NULL REFERENCES public.municipalities(id) ON DELETE CASCADE,
    PRIMARY KEY (organization_id, municipality_id)
);

ALTER TABLE public.organization_municipalities ENABLE ROW LEVEL SECURITY;

-- 2.3 Relational Requested Provider Coverage Junction Table for Applications
CREATE TABLE IF NOT EXISTS public.account_application_municipalities (
    application_id UUID NOT NULL REFERENCES public.account_applications(id) ON DELETE CASCADE,
    municipality_id UUID NOT NULL REFERENCES public.municipalities(id) ON DELETE CASCADE,
    PRIMARY KEY (application_id, municipality_id)
);

ALTER TABLE public.account_application_municipalities ENABLE ROW LEVEL SECURITY;

-- Explicitly define minimum necessary table privileges for canonical organization tables
REVOKE ALL ON public.organizations FROM PUBLIC, anon, authenticated;
REVOKE ALL ON public.organization_municipalities FROM PUBLIC, anon, authenticated;
REVOKE ALL ON public.account_application_municipalities FROM PUBLIC, anon, authenticated;

GRANT SELECT ON public.organizations TO authenticated;
GRANT ALL ON public.organizations TO service_role;

GRANT SELECT ON public.organization_municipalities TO authenticated;
GRANT ALL ON public.organization_municipalities TO service_role;

GRANT SELECT ON public.account_application_municipalities TO authenticated;
GRANT ALL ON public.account_application_municipalities TO service_role;

-- ============================================================================
-- SECTION 3: ALTER EXISTING TABLES FOR ORGANIZATION SCOPE
-- ============================================================================

-- 3.1 Safely update account_applications for organization columns
ALTER TABLE public.account_applications
    ADD COLUMN IF NOT EXISTS organization_type TEXT DEFAULT 'LGU' CHECK (organization_type IN ('LGU', 'PROVIDER')),
    ADD COLUMN IF NOT EXISTS organization_name TEXT,
    ADD COLUMN IF NOT EXISTS rep_full_name TEXT,
    ADD COLUMN IF NOT EXISTS rep_position TEXT,
    ADD COLUMN IF NOT EXISTS rep_contact_number TEXT,
    ADD COLUMN IF NOT EXISTS cert_document_url TEXT;

UPDATE public.account_applications
SET organization_type = 'LGU'
WHERE organization_type IS NULL;

ALTER TABLE public.account_applications
    ALTER COLUMN organization_type SET NOT NULL;

-- 3.2 Add organization_id FK to profiles
ALTER TABLE public.profiles
    ADD COLUMN IF NOT EXISTS organization_id UUID REFERENCES public.organizations(id) ON DELETE SET NULL;

-- 3.3 Add organization_id FK to routes
ALTER TABLE public.routes
    ADD COLUMN IF NOT EXISTS organization_id UUID REFERENCES public.organizations(id) ON DELETE SET NULL;

-- 3.4 Add organization_id FK to schedules
ALTER TABLE public.schedules
    ADD COLUMN IF NOT EXISTS organization_id UUID REFERENCES public.organizations(id) ON DELETE SET NULL;

-- 3.5 Add organization_id FK to trucks
ALTER TABLE public.trucks
    ADD COLUMN IF NOT EXISTS organization_id UUID REFERENCES public.organizations(id) ON DELETE SET NULL;

-- 3.6 Add assigned_organization_id FK to waste_reports
ALTER TABLE public.waste_reports
    ADD COLUMN IF NOT EXISTS assigned_organization_id UUID REFERENCES public.organizations(id) ON DELETE SET NULL;

-- ============================================================================
-- SECTION 4: ATOMIC TRANSACTIONAL RPC: submit_organization_application
-- ============================================================================

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
BEGIN
    IF p_organization_type NOT IN ('LGU', 'PROVIDER') THEN
        RAISE EXCEPTION 'Validation failed: organization_type must be LGU or PROVIDER.';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM public.municipalities WHERE id = p_municipality_id) THEN
        RAISE EXCEPTION 'Validation failed: Primary municipality ID does not exist.';
    END IF;

    v_derived_role := CASE WHEN p_organization_type = 'PROVIDER' THEN 'company_admin' ELSE 'municipal_admin' END;

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
        p_organization_type,
        p_organization_name,
        p_rep_full_name,
        p_email,
        p_contact_number,
        p_municipality_id,
        COALESCE(p_office_department, CASE WHEN p_organization_type = 'LGU' THEN 'Municipal Sanitation Office' ELSE 'Operations Department' END),
        p_rep_position,
        v_derived_role,
        CASE WHEN p_organization_type = 'PROVIDER' THEN p_organization_name ELSE '' END,
        p_cert_document_url,
        'PENDING'
    )
    RETURNING id INTO v_app_id;

    INSERT INTO public.account_application_municipalities (application_id, municipality_id)
    VALUES (v_app_id, p_municipality_id)
    ON CONFLICT DO NOTHING;

    IF p_requested_municipalities IS NOT NULL AND array_length(p_requested_municipalities, 1) > 0 THEN
        FOREACH v_muni_id IN ARRAY p_requested_municipalities
        LOOP
            IF v_muni_id IS NOT NULL AND v_muni_id <> p_municipality_id THEN
                IF NOT EXISTS (SELECT 1 FROM public.municipalities WHERE id = v_muni_id) THEN
                    RAISE EXCEPTION 'Validation failed: Requested operating municipality ID (%) does not exist.', v_muni_id;
                END IF;

                INSERT INTO public.account_application_municipalities (application_id, municipality_id)
                VALUES (v_app_id, v_muni_id)
                ON CONFLICT DO NOTHING;
            END IF;
        END LOOP;
    END IF;

    RETURN v_app_id;
END;
$$;

REVOKE EXECUTE ON FUNCTION public.submit_organization_application(TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, UUID, UUID[], TEXT, TEXT) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.submit_organization_application(TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, UUID, UUID[], TEXT, TEXT) TO service_role;

-- ============================================================================
-- SECTION 5: CONSTRAINTS & TRIGGERS
-- ============================================================================

CREATE OR REPLACE FUNCTION public.v24_validate_organization_identity()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $$
BEGIN
    IF NEW.type = 'LGU' AND NEW.municipality_id IS NULL THEN
        RAISE EXCEPTION 'LGU organizations must have a non-null municipality_id.';
    END IF;

    IF TG_OP = 'UPDATE' AND OLD.type = 'PROVIDER' AND NEW.type = 'LGU' THEN
        IF EXISTS (
            SELECT 1 FROM public.organization_municipalities
            WHERE organization_id = OLD.id
        ) THEN
            RAISE EXCEPTION 'Cannot convert PROVIDER organization (%) to LGU while operating coverage records exist in organization_municipalities.', OLD.id;
        END IF;
    END IF;

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS v24_trg_validate_organization_identity ON public.organizations;
CREATE TRIGGER v24_trg_validate_organization_identity
    BEFORE INSERT OR UPDATE ON public.organizations
    FOR EACH ROW
    EXECUTE FUNCTION public.v24_validate_organization_identity();

CREATE OR REPLACE FUNCTION public.v24_validate_org_coverage_type()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $$
DECLARE
    v_type TEXT;
BEGIN
    SELECT type INTO v_type FROM public.organizations WHERE id = NEW.organization_id;
    if v_type IS NULL THEN
        RAISE EXCEPTION 'Referenced organization_id (%) does not exist.', NEW.organization_id;
    ELSIF v_type <> 'PROVIDER' THEN
        RAISE EXCEPTION 'Coverage records (organization_municipalities) can only be managed for PROVIDER organizations. LGU organizations manage their single assigned municipality.';
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS v24_trg_validate_org_coverage_type ON public.organization_municipalities;
CREATE TRIGGER v24_trg_validate_org_coverage_type
    BEFORE INSERT OR UPDATE ON public.organization_municipalities
    FOR EACH ROW
    EXECUTE FUNCTION public.v24_validate_org_coverage_type();

CREATE UNIQUE INDEX IF NOT EXISTS v24_idx_unique_org_admin_profile
ON public.profiles (organization_id)
WHERE role IN ('municipal_admin', 'company_admin');

CREATE OR REPLACE FUNCTION public.v24_validate_profile_organization_role()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $$
DECLARE
    v_org_type TEXT;
    v_org_muni_id UUID;
BEGIN
    IF NEW.role IN ('municipal_admin', 'company_admin') THEN
        IF NEW.organization_id IS NULL THEN
            RAISE EXCEPTION 'Organization Admin profiles (municipal_admin / company_admin) must be linked to an organization_id.';
        END IF;

        SELECT type, municipality_id INTO v_org_type, v_org_muni_id
        FROM public.organizations
        WHERE id = NEW.organization_id;

        IF v_org_type IS NULL THEN
            RAISE EXCEPTION 'Referenced organization_id (%) does not exist.', NEW.organization_id;
        END IF;

        IF NEW.role = 'municipal_admin' THEN
            IF v_org_type <> 'LGU' THEN
                RAISE EXCEPTION 'Role municipal_admin can only be assigned to LGU organizations.';
            END IF;
            IF NEW.municipality_id IS NULL OR NEW.municipality_id <> v_org_muni_id THEN
                RAISE EXCEPTION 'Municipal Admin profile municipality_id (%) must match LGU organization municipality_id (%).', NEW.municipality_id, v_org_muni_id;
            END IF;
        ELSIF NEW.role = 'company_admin' AND v_org_type <> 'PROVIDER' THEN
            RAISE EXCEPTION 'Role company_admin can only be assigned to PROVIDER organizations.';
        END IF;
    ELSIF NEW.role = 'system_owner' AND NEW.organization_id IS NOT NULL THEN
        RAISE EXCEPTION 'System Owner profiles must not be linked to an organization_id.';
    END IF;

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS v24_trg_validate_profile_organization_role ON public.profiles;
CREATE TRIGGER v24_trg_validate_profile_organization_role
    BEFORE INSERT OR UPDATE ON public.profiles
    FOR EACH ROW
    EXECUTE FUNCTION public.v24_validate_profile_organization_role();

CREATE OR REPLACE FUNCTION public.v24_validate_schedule_assignment_consistency()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $$
DECLARE
    v_route_muni_id UUID;
    v_route_org_id UUID;
    v_truck_org_id UUID;
    v_effective_org_id UUID;
    v_org_type TEXT;
    v_coverage_exists BOOLEAN;
BEGIN
    IF NEW.route_version_id IS NOT NULL THEN
        SELECT r.municipality_id, r.organization_id
        INTO v_route_muni_id, v_route_org_id
        FROM public.route_versions rv
        JOIN public.routes r ON rv.route_id = r.id
        WHERE rv.id = NEW.route_version_id;

        IF v_route_muni_id IS NOT NULL AND v_route_muni_id <> NEW.municipality_id THEN
            RAISE EXCEPTION 'Schedule municipality_id (%) does not match route municipality_id (%).', NEW.municipality_id, v_route_muni_id;
        END IF;
    END IF;

    v_effective_org_id := NEW.organization_id;

    IF v_effective_org_id IS NULL THEN
        IF v_route_org_id IS NOT NULL THEN
            v_effective_org_id := v_route_org_id;
        ELSIF NEW.truck_id IS NOT NULL THEN
            SELECT organization_id INTO v_truck_org_id FROM public.trucks WHERE id = NEW.truck_id;
            IF v_truck_org_id IS NOT NULL THEN
                v_effective_org_id := v_truck_org_id;
            END IF;
        END IF;

        IF v_effective_org_id IS NOT NULL THEN
            NEW.organization_id := v_effective_org_id;
        END IF;
    END IF;

    IF v_route_org_id IS NOT NULL AND NEW.organization_id IS NOT NULL AND v_route_org_id <> NEW.organization_id THEN
        RAISE EXCEPTION 'Schedule organization_id (%) does not match assigned route organization_id (%).', NEW.organization_id, v_route_org_id;
    END IF;

    IF NEW.truck_id IS NOT NULL THEN
        SELECT organization_id INTO v_truck_org_id
        FROM public.trucks
        WHERE id = NEW.truck_id;

        IF NEW.organization_id IS NOT NULL AND v_truck_org_id IS NOT NULL AND v_truck_org_id <> NEW.organization_id THEN
            RAISE EXCEPTION 'Assigned truck organization_id (%) does not match schedule organization_id (%).', v_truck_org_id, NEW.organization_id;
        END IF;
    END IF;

    IF NEW.organization_id IS NOT NULL THEN
        SELECT type INTO v_org_type
        FROM public.organizations
        WHERE id = NEW.organization_id;

        IF v_org_type = 'PROVIDER' THEN
            SELECT EXISTS (
                SELECT 1 FROM public.organization_municipalities
                WHERE organization_id = NEW.organization_id AND municipality_id = NEW.municipality_id
            ) INTO v_coverage_exists;

            IF NOT v_coverage_exists THEN
                RAISE EXCEPTION 'Provider organization (%) is not approved to operate in municipality (%).', NEW.organization_id, NEW.municipality_id;
            END IF;
        ELSIF v_org_type = 'LGU' THEN
            SELECT EXISTS (
                SELECT 1 FROM public.organizations
                WHERE id = NEW.organization_id AND municipality_id = NEW.municipality_id
            ) INTO v_coverage_exists;

            IF NOT v_coverage_exists THEN
                RAISE EXCEPTION 'LGU organization (%) is not assigned to municipality (%).', NEW.organization_id, NEW.municipality_id;
            END IF;
        END IF;
    END IF;

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS v24_trg_validate_schedule_assignment_consistency ON public.schedules;
CREATE TRIGGER v24_trg_validate_schedule_assignment_consistency
    BEFORE INSERT OR UPDATE ON public.schedules
    FOR EACH ROW
    EXECUTE FUNCTION public.v24_validate_schedule_assignment_consistency();

CREATE OR REPLACE FUNCTION public.v24_prevent_route_invalidation()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $$
BEGIN
    IF (OLD.organization_id IS DISTINCT FROM NEW.organization_id OR OLD.municipality_id IS DISTINCT FROM NEW.municipality_id) THEN
        IF EXISTS (
            SELECT 1 FROM public.schedules s
            JOIN public.route_versions rv ON s.route_version_id = rv.id
            WHERE rv.route_id = OLD.id
        ) THEN
            RAISE EXCEPTION 'Cannot modify organization or municipality on route (%) while active schedules depend on it.', OLD.id;
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS v24_trg_prevent_route_invalidation ON public.routes;
CREATE TRIGGER v24_trg_prevent_route_invalidation
    BEFORE UPDATE ON public.routes
    FOR EACH ROW
    EXECUTE FUNCTION public.v24_prevent_route_invalidation();

CREATE OR REPLACE FUNCTION public.v24_prevent_route_version_invalidation()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $$
BEGIN
    IF (OLD.route_id IS DISTINCT FROM NEW.route_id) THEN
        IF EXISTS (
            SELECT 1 FROM public.schedules
            WHERE route_version_id = OLD.id
        ) THEN
            RAISE EXCEPTION 'Cannot reassign route_id on route_version (%) while active schedules depend on it.', OLD.id;
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS v24_trg_prevent_route_version_invalidation ON public.route_versions;
CREATE TRIGGER v24_trg_prevent_route_version_invalidation
    BEFORE UPDATE ON public.route_versions
    FOR EACH ROW
    EXECUTE FUNCTION public.v24_prevent_route_version_invalidation();

CREATE OR REPLACE FUNCTION public.v24_prevent_truck_invalidation()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $$
BEGIN
    IF (OLD.organization_id IS DISTINCT FROM NEW.organization_id) THEN
        IF EXISTS (
            SELECT 1 FROM public.schedules
            WHERE truck_id = OLD.id AND organization_id IS DISTINCT FROM NEW.organization_id
        ) THEN
            RAISE EXCEPTION 'Cannot modify organization_id on truck (%) while active schedules depend on it.', OLD.id;
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS v24_trg_prevent_truck_invalidation ON public.trucks;
CREATE TRIGGER v24_trg_prevent_truck_invalidation
    BEFORE UPDATE ON public.trucks
    FOR EACH ROW
    EXECUTE FUNCTION public.v24_prevent_truck_invalidation();

CREATE OR REPLACE FUNCTION public.v24_prevent_coverage_invalidation()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $$
BEGIN
    IF (TG_OP = 'DELETE') THEN
        IF EXISTS (
            SELECT 1 FROM public.schedules
            WHERE organization_id = OLD.organization_id AND municipality_id = OLD.municipality_id
        ) OR EXISTS (
            SELECT 1 FROM public.routes
            WHERE organization_id = OLD.organization_id AND municipality_id = OLD.municipality_id
        ) THEN
            RAISE EXCEPTION 'Cannot remove provider coverage for organization (%) in municipality (%) while active schedules or routes exist.', OLD.organization_id, OLD.municipality_id;
        END IF;
        RETURN OLD;
    ELSIF (TG_OP = 'UPDATE') THEN
        IF (OLD.organization_id IS DISTINCT FROM NEW.organization_id OR OLD.municipality_id IS DISTINCT FROM NEW.municipality_id) THEN
            IF EXISTS (
                SELECT 1 FROM public.schedules
                WHERE organization_id = OLD.organization_id AND municipality_id = OLD.municipality_id
            ) OR EXISTS (
                SELECT 1 FROM public.routes
                WHERE organization_id = OLD.organization_id AND municipality_id = OLD.municipality_id
            ) THEN
                RAISE EXCEPTION 'Cannot modify provider coverage for organization (%) in municipality (%) while active schedules or routes exist.', OLD.organization_id, OLD.municipality_id;
            END IF;
        END IF;
        RETURN NEW;
    END IF;
    RETURN NULL;
END;
$$;

DROP TRIGGER IF EXISTS v24_trg_prevent_coverage_invalidation ON public.organization_municipalities;
CREATE TRIGGER v24_trg_prevent_coverage_invalidation
    BEFORE UPDATE OR DELETE ON public.organization_municipalities
    FOR EACH ROW
    EXECUTE FUNCTION public.v24_prevent_coverage_invalidation();

-- ============================================================================
-- SECTION 6: HARDENED RLS SECURITY HELPER FUNCTIONS
-- ============================================================================

CREATE OR REPLACE FUNCTION public.is_system_owner()
RETURNS BOOLEAN
LANGUAGE sql
SECURITY DEFINER
SET search_path TO ''
AS $$
    SELECT EXISTS (
        SELECT 1 FROM public.profiles
        WHERE id = auth.uid()
          AND role = 'system_owner'
          AND account_status = 'active'
    );
$$;

CREATE OR REPLACE FUNCTION public.get_auth_organization_id()
RETURNS UUID
LANGUAGE sql
SECURITY DEFINER
SET search_path TO ''
AS $$
    SELECT organization_id FROM public.profiles
    WHERE id = auth.uid() AND account_status = 'active';
$$;

CREATE OR REPLACE FUNCTION public.is_municipal_admin(p_municipality_id UUID)
RETURNS BOOLEAN
LANGUAGE sql
SECURITY DEFINER
SET search_path TO ''
AS $$
    SELECT EXISTS (
        SELECT 1 FROM public.profiles
        WHERE id = auth.uid()
          AND role = 'municipal_admin'
          AND account_status = 'active'
          AND municipality_id = p_municipality_id
    );
$$;

CREATE OR REPLACE FUNCTION public.is_company_admin_for_organization(p_organization_id UUID)
RETURNS BOOLEAN
LANGUAGE sql
SECURITY DEFINER
SET search_path TO ''
AS $$
    SELECT EXISTS (
        SELECT 1 FROM public.profiles
        WHERE id = auth.uid()
          AND role = 'company_admin'
          AND account_status = 'active'
          AND organization_id = p_organization_id
    );
$$;

REVOKE EXECUTE ON FUNCTION public.is_system_owner() FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.is_system_owner() TO authenticated, service_role;

REVOKE EXECUTE ON FUNCTION public.get_auth_organization_id() FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.get_auth_organization_id() TO authenticated, service_role;

REVOKE EXECUTE ON FUNCTION public.is_municipal_admin(UUID) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.is_municipal_admin(UUID) TO authenticated, service_role;

REVOKE EXECUTE ON FUNCTION public.is_company_admin_for_organization(UUID) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.is_company_admin_for_organization(UUID) TO authenticated, service_role;

-- ============================================================================
-- SECTION 7: COLUMN-LEVEL PRIVILEGES & ROW LEVEL SECURITY POLICIES
-- ============================================================================

REVOKE ALL ON public.account_applications FROM PUBLIC, anon, authenticated;
GRANT SELECT ON public.account_applications TO authenticated;
GRANT INSERT (
    organization_type, organization_name, rep_full_name, rep_position,
    rep_contact_number, cert_document_url, municipality_id, full_name,
    email, contact_number, office_department, official_position, requested_role, status
) ON public.account_applications TO anon, authenticated;
GRANT UPDATE (review_notes) ON public.account_applications TO authenticated;

REVOKE ALL ON public.waste_reports FROM PUBLIC, anon, authenticated;
GRANT SELECT ON public.waste_reports TO authenticated;
GRANT INSERT (
    reference_code, category, description, location, status, municipality_id
) ON public.waste_reports TO anon, authenticated;
GRANT UPDATE (status, assigned_organization_id) ON public.waste_reports TO authenticated;

DROP POLICY IF EXISTS "Admins read routes in municipality" ON public.routes;
DROP POLICY IF EXISTS "Admins read schedules in municipality" ON public.schedules;
DROP POLICY IF EXISTS "Admins read trucks in municipality" ON public.trucks;
DROP POLICY IF EXISTS "Admins read municipal reports" ON public.waste_reports;
DROP POLICY IF EXISTS "Anonymous submit waste reports" ON public.waste_reports;
DROP POLICY IF EXISTS "Public insert anonymous reports" ON public.waste_reports;
DROP POLICY IF EXISTS "Anonymous users can submit applications" ON public.account_applications;

-- Table: public.organizations
DROP POLICY IF EXISTS "System Owners read organizations" ON public.organizations;
CREATE POLICY "System Owners read organizations" ON public.organizations
    FOR SELECT USING (public.is_system_owner());

DROP POLICY IF EXISTS "Organization Admins read own organization" ON public.organizations;
CREATE POLICY "Organization Admins read own organization" ON public.organizations
    FOR SELECT USING (id = public.get_auth_organization_id());

-- Table: public.organization_municipalities
DROP POLICY IF EXISTS "System Owners read org_municipalities" ON public.organization_municipalities;
CREATE POLICY "System Owners read org_municipalities" ON public.organization_municipalities
    FOR SELECT USING (public.is_system_owner());

DROP POLICY IF EXISTS "LGU Admins read coverage matching municipality" ON public.organization_municipalities;
CREATE POLICY "LGU Admins read coverage matching municipality" ON public.organization_municipalities
    FOR SELECT USING (public.is_municipal_admin(municipality_id));

DROP POLICY IF EXISTS "Provider Admins read own coverage" ON public.organization_municipalities;
CREATE POLICY "Provider Admins read own coverage" ON public.organization_municipalities
    FOR SELECT USING (organization_id = public.get_auth_organization_id());

-- Table: public.account_applications
DROP POLICY IF EXISTS "Anonymous submit account_applications" ON public.account_applications;
CREATE POLICY "Anonymous submit account_applications" ON public.account_applications
    FOR INSERT TO anon, authenticated WITH CHECK (
        status = 'PENDING'
        AND (requested_role IS NULL OR LOWER(TRIM(requested_role)) NOT IN ('system_owner', 'platform_owner'))
    );

DROP POLICY IF EXISTS "System Owners read account_applications" ON public.account_applications;
CREATE POLICY "System Owners read account_applications" ON public.account_applications
    FOR SELECT USING (public.is_system_owner());

DROP POLICY IF EXISTS "System Owners update notes on account_applications" ON public.account_applications;
CREATE POLICY "System Owners update notes on account_applications" ON public.account_applications
    FOR UPDATE USING (public.is_system_owner()) WITH CHECK (public.is_system_owner());

-- Table: public.account_application_municipalities
DROP POLICY IF EXISTS "System Owners read app_municipalities" ON public.account_application_municipalities;
CREATE POLICY "System Owners read app_municipalities" ON public.account_application_municipalities
    FOR SELECT USING (public.is_system_owner());

-- Table: public.profiles
DROP POLICY IF EXISTS "System Owners read profiles" ON public.profiles;
CREATE POLICY "System Owners read profiles" ON public.profiles
    FOR SELECT USING (public.is_system_owner());

DROP POLICY IF EXISTS "LGU Admins read profiles in same municipality" ON public.profiles;
CREATE POLICY "LGU Admins read profiles in same municipality" ON public.profiles
    FOR SELECT USING (
        municipality_id IS NOT NULL AND public.is_municipal_admin(municipality_id)
    );

DROP POLICY IF EXISTS "Provider Admins read profiles in same organization" ON public.profiles;
CREATE POLICY "Provider Admins read profiles in same organization" ON public.profiles
    FOR SELECT USING (
        organization_id IS NOT NULL AND organization_id = public.get_auth_organization_id()
    );

-- Table: public.routes
DROP POLICY IF EXISTS "System Owners read routes" ON public.routes;
CREATE POLICY "System Owners read routes" ON public.routes
    FOR SELECT USING (public.is_system_owner());

DROP POLICY IF EXISTS "LGU Admins manage routes in municipality" ON public.routes;
CREATE POLICY "LGU Admins manage routes in municipality" ON public.routes
    FOR ALL USING (public.is_municipal_admin(municipality_id)) WITH CHECK (public.is_municipal_admin(municipality_id));

DROP POLICY IF EXISTS "Provider Admins manage assigned routes" ON public.routes;
CREATE POLICY "Provider Admins manage assigned routes" ON public.routes
    FOR ALL USING (organization_id IS NOT NULL AND organization_id = public.get_auth_organization_id())
    WITH CHECK (
        organization_id IS NOT NULL
        AND organization_id = public.get_auth_organization_id()
        AND EXISTS (
            SELECT 1 FROM public.organization_municipalities om
            WHERE om.organization_id = routes.organization_id AND om.municipality_id = routes.municipality_id
        )
    );

-- Table: public.route_versions
DROP POLICY IF EXISTS "System Owners read route_versions" ON public.route_versions;
CREATE POLICY "System Owners read route_versions" ON public.route_versions
    FOR SELECT USING (public.is_system_owner());

DROP POLICY IF EXISTS "LGU Admins manage route_versions via routes" ON public.route_versions;
CREATE POLICY "LGU Admins manage route_versions via routes" ON public.route_versions
    FOR ALL USING (
        EXISTS (SELECT 1 FROM public.routes r WHERE r.id = route_versions.route_id AND public.is_municipal_admin(r.municipality_id))
    )
    WITH CHECK (
        EXISTS (SELECT 1 FROM public.routes r WHERE r.id = route_versions.route_id AND public.is_municipal_admin(r.municipality_id))
    );

DROP POLICY IF EXISTS "Provider Admins manage route_versions via assigned routes" ON public.route_versions;
CREATE POLICY "Provider Admins manage route_versions via assigned routes" ON public.route_versions
    FOR ALL USING (
        EXISTS (SELECT 1 FROM public.routes r WHERE r.id = route_versions.route_id AND r.organization_id = public.get_auth_organization_id())
    )
    WITH CHECK (
        EXISTS (SELECT 1 FROM public.routes r WHERE r.id = route_versions.route_id AND r.organization_id = public.get_auth_organization_id())
    );

-- Table: public.route_segments
DROP POLICY IF EXISTS "System Owners read route_segments" ON public.route_segments;
CREATE POLICY "System Owners read route_segments" ON public.route_segments
    FOR SELECT USING (public.is_system_owner());

DROP POLICY IF EXISTS "LGU Admins manage route_segments via routes" ON public.route_segments;
CREATE POLICY "LGU Admins manage route_segments via routes" ON public.route_segments
    FOR ALL USING (
        EXISTS (
            SELECT 1 FROM public.route_versions rv
            JOIN public.routes r ON rv.route_id = r.id
            WHERE rv.id = route_segments.route_version_id AND public.is_municipal_admin(r.municipality_id)
        )
    )
    WITH CHECK (
        EXISTS (
            SELECT 1 FROM public.route_versions rv
            JOIN public.routes r ON rv.route_id = r.id
            WHERE rv.id = route_segments.route_version_id AND public.is_municipal_admin(r.municipality_id)
        )
    );

DROP POLICY IF EXISTS "Provider Admins manage route_segments via assigned routes" ON public.route_segments;
CREATE POLICY "Provider Admins manage route_segments via assigned routes" ON public.route_segments
    FOR ALL USING (
        EXISTS (
            SELECT 1 FROM public.route_versions rv
            JOIN public.routes r ON rv.route_id = r.id
            WHERE rv.id = route_segments.route_version_id AND r.organization_id = public.get_auth_organization_id()
        )
    )
    WITH CHECK (
        EXISTS (
            SELECT 1 FROM public.route_versions rv
            JOIN public.routes r ON rv.route_id = r.id
            WHERE rv.id = route_segments.route_version_id AND r.organization_id = public.get_auth_organization_id()
        )
    );

-- Table: public.schedules
DROP POLICY IF EXISTS "System Owners read schedules" ON public.schedules;
CREATE POLICY "System Owners read schedules" ON public.schedules
    FOR SELECT USING (public.is_system_owner());

DROP POLICY IF EXISTS "LGU Admins manage schedules in municipality" ON public.schedules;
CREATE POLICY "LGU Admins manage schedules in municipality" ON public.schedules
    FOR ALL USING (public.is_municipal_admin(municipality_id)) WITH CHECK (public.is_municipal_admin(municipality_id));

DROP POLICY IF EXISTS "Provider Admins manage assigned schedules" ON public.schedules;
CREATE POLICY "Provider Admins manage assigned schedules" ON public.schedules
    FOR ALL USING (organization_id IS NOT NULL AND organization_id = public.get_auth_organization_id())
    WITH CHECK (
        organization_id IS NOT NULL
        AND organization_id = public.get_auth_organization_id()
        AND EXISTS (
            SELECT 1 FROM public.organization_municipalities om
            WHERE om.organization_id = schedules.organization_id AND om.municipality_id = schedules.municipality_id
        )
    );

-- Table: public.schedule_segments
DROP POLICY IF EXISTS "System Owners read schedule_segments" ON public.schedule_segments;
CREATE POLICY "System Owners read schedule_segments" ON public.schedule_segments
    FOR SELECT USING (public.is_system_owner());

DROP POLICY IF EXISTS "LGU Admins manage schedule_segments via schedules" ON public.schedule_segments;
CREATE POLICY "LGU Admins manage schedule_segments via schedules" ON public.schedule_segments
    FOR ALL USING (
        EXISTS (SELECT 1 FROM public.schedules s WHERE s.id = schedule_segments.schedule_id AND public.is_municipal_admin(s.municipality_id))
    )
    WITH CHECK (
        EXISTS (SELECT 1 FROM public.schedules s WHERE s.id = schedule_segments.schedule_id AND public.is_municipal_admin(s.municipality_id))
    );

DROP POLICY IF EXISTS "Provider Admins manage schedule_segments via assigned schedules" ON public.schedule_segments;
CREATE POLICY "Provider Admins manage schedule_segments via assigned schedules" ON public.schedule_segments
    FOR ALL USING (
        EXISTS (SELECT 1 FROM public.schedules s WHERE s.id = schedule_segments.schedule_id AND s.organization_id = public.get_auth_organization_id())
    )
    WITH CHECK (
        EXISTS (SELECT 1 FROM public.schedules s WHERE s.id = schedule_segments.schedule_id AND s.organization_id = public.get_auth_organization_id())
    );

-- Table: public.collection_events
DROP POLICY IF EXISTS "System Owners read collection_events" ON public.collection_events;
CREATE POLICY "System Owners read collection_events" ON public.collection_events
    FOR SELECT USING (public.is_system_owner());

DROP POLICY IF EXISTS "LGU Admins read collection_events via schedules" ON public.collection_events;
CREATE POLICY "LGU Admins read collection_events via schedules" ON public.collection_events
    FOR SELECT USING (
        EXISTS (
            SELECT 1 FROM public.schedule_segments ss
            JOIN public.schedules s ON ss.schedule_id = s.id
            WHERE ss.id = collection_events.schedule_segment_id AND public.is_municipal_admin(s.municipality_id)
        )
    );

DROP POLICY IF EXISTS "Provider Admins read collection_events via assigned schedules" ON public.collection_events;
CREATE POLICY "Provider Admins read collection_events via assigned schedules" ON public.collection_events
    FOR SELECT USING (
        EXISTS (
            SELECT 1 FROM public.schedule_segments ss
            JOIN public.schedules s ON ss.schedule_id = s.id
            WHERE ss.id = collection_events.schedule_segment_id AND s.organization_id = public.get_auth_organization_id()
        )
    );

-- Table: public.trucks
DROP POLICY IF EXISTS "System Owners read trucks" ON public.trucks;
CREATE POLICY "System Owners read trucks" ON public.trucks
    FOR SELECT USING (public.is_system_owner());

DROP POLICY IF EXISTS "LGU Admins manage trucks in municipality" ON public.trucks;
CREATE POLICY "LGU Admins manage trucks in municipality" ON public.trucks
    FOR ALL USING (public.is_municipal_admin(municipality_id)) WITH CHECK (public.is_municipal_admin(municipality_id));

DROP POLICY IF EXISTS "Provider Admins manage own trucks" ON public.trucks;
CREATE POLICY "Provider Admins manage own trucks" ON public.trucks
    FOR ALL USING (organization_id IS NOT NULL AND organization_id = public.get_auth_organization_id())
    WITH CHECK (organization_id IS NOT NULL AND organization_id = public.get_auth_organization_id());

-- Table: public.waste_reports
DROP POLICY IF EXISTS "Tightened anonymous submit waste reports" ON public.waste_reports;
CREATE POLICY "Tightened anonymous submit waste reports" ON public.waste_reports
    FOR INSERT TO anon, authenticated WITH CHECK (
        status = 'submitted'
        AND assigned_organization_id IS NULL
    );

DROP POLICY IF EXISTS "System Owners read waste_reports" ON public.waste_reports;
CREATE POLICY "System Owners read waste_reports" ON public.waste_reports
    FOR SELECT USING (public.is_system_owner());

DROP POLICY IF EXISTS "LGU Admins manage reports in municipality" ON public.waste_reports;
CREATE POLICY "LGU Admins manage reports in municipality" ON public.waste_reports
    FOR ALL USING (public.is_municipal_admin(municipality_id)) WITH CHECK (public.is_municipal_admin(municipality_id));

DROP POLICY IF EXISTS "Provider Admins manage assigned reports" ON public.waste_reports;
CREATE POLICY "Provider Admins manage assigned reports" ON public.waste_reports
    FOR ALL USING (assigned_organization_id IS NOT NULL AND assigned_organization_id = public.get_auth_organization_id())
    WITH CHECK (assigned_organization_id IS NOT NULL AND assigned_organization_id = public.get_auth_organization_id());

-- Table: public.audit_logs
DROP POLICY IF EXISTS "System Owners read audit_logs" ON public.audit_logs;
CREATE POLICY "System Owners read audit_logs" ON public.audit_logs
    FOR SELECT USING (public.is_system_owner());

DROP POLICY IF EXISTS "Organization Admins read own organization audit_logs" ON public.audit_logs;
CREATE POLICY "Organization Admins read own organization audit_logs" ON public.audit_logs
    FOR SELECT USING (
        details IS NOT NULL
        AND details ? 'organization_id'
        AND (details->>'organization_id') ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
        AND (details->>'organization_id')::UUID = public.get_auth_organization_id()
    );

COMMIT;
