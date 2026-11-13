-- ============================================================================
-- MIGRATION: V2.4 Get Route Geometry GeoJSON RPC (Ordered ST_MakeLine)
-- Target Database: PostgreSQL / Supabase Project HAKOT-Staging (jczbkpyktncwirlymkom)
-- ============================================================================

CREATE OR REPLACE FUNCTION public.get_route_geometry_geojson(
    p_route_version_id UUID
)
RETURNS TEXT
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_geojson TEXT;
    v_reviewer_id UUID;
    v_authorized BOOLEAN := FALSE;
BEGIN
    IF p_route_version_id IS NULL THEN
        RETURN NULL;
    END IF;

    -- Authenticate caller session via auth.uid()
    v_reviewer_id := auth.uid();
    IF v_reviewer_id IS NULL THEN
        RAISE EXCEPTION 'Unauthorized: Session JWT is missing or unauthenticated.';
    END IF;

    -- Authorization check: System Owner, Municipal Admin for route municipality, or Provider Admin for organization
    IF public.is_system_owner() THEN
        v_authorized := TRUE;
    ELSE
        SELECT TRUE INTO v_authorized
        FROM public.route_versions rv
        JOIN public.routes r ON rv.route_id = r.id
        WHERE rv.id = p_route_version_id
          AND (
              public.is_municipal_admin(r.municipality_id)
              OR r.organization_id = public.get_auth_organization_id()
          );
    END IF;

    IF NOT COALESCE(v_authorized, FALSE) THEN
        RAISE EXCEPTION 'Forbidden: User is not authorized to access geometry for this route version.';
    END IF;

    -- Construct LineString explicitly ordered by sequence_order using ST_MakeLine aggregate
    SELECT json_build_object(
        'type', 'Feature',
        'geometry', extensions.ST_AsGeoJSON(
            extensions.ST_MakeLine(ss.geometry ORDER BY rs.sequence_order ASC)
        )::json
    )::text INTO v_geojson
    FROM public.route_segments rs
    JOIN public.street_segments ss ON rs.street_segment_id = ss.id
    WHERE rs.route_version_id = p_route_version_id;

    RETURN v_geojson;
END;
$$;

REVOKE EXECUTE ON FUNCTION public.get_route_geometry_geojson(UUID) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.get_route_geometry_geojson(UUID) TO authenticated, service_role;
