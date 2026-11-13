-- ============================================================================
-- HAKOT VERSION 2.4 — PROVIDER MUNICIPALITY COVERAGE RLS HARDENING MIGRATION
-- Migration File: 20250304_v24_provider_coverage_rls_hardening.sql
-- Target Database: PostgreSQL 17.11 / Supabase Project Staging
-- Description: Hardens provider RLS policies across routes, route_versions,
--              route_segments, schedules, schedule_segments, and collection_events
--              to strictly enforce active municipal coverage via organization_municipalities
--              in both USING and WITH CHECK clauses, preventing unauthorized operational
--              access after municipality coverage is revoked or modified.
-- ============================================================================

BEGIN;

-- ============================================================================
-- SECTION 1: HARDEN ROUTES RLS POLICIES
-- ============================================================================

DROP POLICY IF EXISTS "Provider Admins manage assigned routes" ON public.routes;
CREATE POLICY "Provider Admins manage assigned routes" ON public.routes
    FOR ALL
    USING (
        organization_id IS NOT NULL
        AND organization_id = public.get_auth_organization_id()
        AND EXISTS (
            SELECT 1 FROM public.organization_municipalities om
            WHERE om.organization_id = routes.organization_id
              AND om.municipality_id = routes.municipality_id
        )
    )
    WITH CHECK (
        organization_id IS NOT NULL
        AND organization_id = public.get_auth_organization_id()
        AND EXISTS (
            SELECT 1 FROM public.organization_municipalities om
            WHERE om.organization_id = routes.organization_id
              AND om.municipality_id = routes.municipality_id
        )
    );

-- ============================================================================
-- SECTION 2: HARDEN ROUTE_VERSIONS RLS POLICIES
-- ============================================================================

DROP POLICY IF EXISTS "Provider Admins manage route_versions via assigned routes" ON public.route_versions;
CREATE POLICY "Provider Admins manage route_versions via assigned routes" ON public.route_versions
    FOR ALL
    USING (
        EXISTS (
            SELECT 1 FROM public.routes r
            WHERE r.id = route_versions.route_id
              AND r.organization_id = public.get_auth_organization_id()
              AND EXISTS (
                  SELECT 1 FROM public.organization_municipalities om
                  WHERE om.organization_id = r.organization_id
                    AND om.municipality_id = r.municipality_id
              )
        )
    )
    WITH CHECK (
        EXISTS (
            SELECT 1 FROM public.routes r
            WHERE r.id = route_versions.route_id
              AND r.organization_id = public.get_auth_organization_id()
              AND EXISTS (
                  SELECT 1 FROM public.organization_municipalities om
                  WHERE om.organization_id = r.organization_id
                    AND om.municipality_id = r.municipality_id
              )
        )
    );

-- ============================================================================
-- SECTION 3: HARDEN ROUTE_SEGMENTS RLS POLICIES
-- ============================================================================

DROP POLICY IF EXISTS "Provider Admins manage route_segments via assigned routes" ON public.route_segments;
CREATE POLICY "Provider Admins manage route_segments via assigned routes" ON public.route_segments
    FOR ALL
    USING (
        EXISTS (
            SELECT 1 FROM public.route_versions rv
            JOIN public.routes r ON rv.route_id = r.id
            WHERE rv.id = route_segments.route_version_id
              AND r.organization_id = public.get_auth_organization_id()
              AND EXISTS (
                  SELECT 1 FROM public.organization_municipalities om
                  WHERE om.organization_id = r.organization_id
                    AND om.municipality_id = r.municipality_id
              )
        )
    )
    WITH CHECK (
        EXISTS (
            SELECT 1 FROM public.route_versions rv
            JOIN public.routes r ON rv.route_id = r.id
            WHERE rv.id = route_segments.route_version_id
              AND r.organization_id = public.get_auth_organization_id()
              AND EXISTS (
                  SELECT 1 FROM public.organization_municipalities om
                  WHERE om.organization_id = r.organization_id
                    AND om.municipality_id = r.municipality_id
              )
        )
    );

-- ============================================================================
-- SECTION 4: HARDEN SCHEDULES RLS POLICIES
-- ============================================================================

DROP POLICY IF EXISTS "Provider Admins manage assigned schedules" ON public.schedules;
CREATE POLICY "Provider Admins manage assigned schedules" ON public.schedules
    FOR ALL
    USING (
        organization_id IS NOT NULL
        AND organization_id = public.get_auth_organization_id()
        AND EXISTS (
            SELECT 1 FROM public.organization_municipalities om
            WHERE om.organization_id = schedules.organization_id
              AND om.municipality_id = schedules.municipality_id
        )
    )
    WITH CHECK (
        organization_id IS NOT NULL
        AND organization_id = public.get_auth_organization_id()
        AND EXISTS (
            SELECT 1 FROM public.organization_municipalities om
            WHERE om.organization_id = schedules.organization_id
              AND om.municipality_id = schedules.municipality_id
        )
    );

-- ============================================================================
-- SECTION 5: HARDEN SCHEDULE_SEGMENTS RLS POLICIES
-- ============================================================================

DROP POLICY IF EXISTS "Provider Admins manage schedule_segments via assigned schedules" ON public.schedule_segments;
CREATE POLICY "Provider Admins manage schedule_segments via assigned schedules" ON public.schedule_segments
    FOR ALL
    USING (
        EXISTS (
            SELECT 1 FROM public.schedules s
            WHERE s.id = schedule_segments.schedule_id
              AND s.organization_id = public.get_auth_organization_id()
              AND EXISTS (
                  SELECT 1 FROM public.organization_municipalities om
                  WHERE om.organization_id = s.organization_id
                    AND om.municipality_id = s.municipality_id
              )
        )
    )
    WITH CHECK (
        EXISTS (
            SELECT 1 FROM public.schedules s
            WHERE s.id = schedule_segments.schedule_id
              AND s.organization_id = public.get_auth_organization_id()
              AND EXISTS (
                  SELECT 1 FROM public.organization_municipalities om
                  WHERE om.organization_id = s.organization_id
                    AND om.municipality_id = s.municipality_id
              )
        )
    );

-- ============================================================================
-- SECTION 6: HARDEN COLLECTION_EVENTS RLS POLICIES
-- ============================================================================

DROP POLICY IF EXISTS "Provider Admins read collection_events via assigned schedules" ON public.collection_events;
CREATE POLICY "Provider Admins read collection_events via assigned schedules" ON public.collection_events
    FOR SELECT
    USING (
        EXISTS (
            SELECT 1 FROM public.schedule_segments ss
            JOIN public.schedules s ON ss.schedule_id = s.id
            WHERE ss.id = collection_events.schedule_segment_id
              AND s.organization_id = public.get_auth_organization_id()
              AND EXISTS (
                  SELECT 1 FROM public.organization_municipalities om
                  WHERE om.organization_id = s.organization_id
                    AND om.municipality_id = s.municipality_id
              )
        )
    );

COMMIT;
