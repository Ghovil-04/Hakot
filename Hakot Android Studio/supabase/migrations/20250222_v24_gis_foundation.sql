-- ============================================================================
-- HAKOT VERSION 2.4 — GIS FOUNDATION MIGRATION (POSTGIS 3.3.7)
-- Migration File: 20250222_v24_gis_foundation.sql
-- Target Database: PostgreSQL 17.11 / Supabase Project Staging
-- Description: Establishes PostGIS extension in 'extensions' schema safely,
--              adds spatial boundary columns to municipalities and barangays,
--              updates baseline tables with production GIS fields, replaces baseline
--              ON DELETE CASCADE/SET NULL foreign keys with production ON DELETE NO ACTION,
--              adds strict NOT NULL preconditions, and creates production GiST and B-tree indexes.
-- ============================================================================

BEGIN;

-- 1. Ensure PostGIS extension is installed in the 'extensions' schema safely
CREATE EXTENSION IF NOT EXISTS "postgis" WITH SCHEMA extensions;

-- 2. Add spatial boundary columns to baseline municipalities and barangays
ALTER TABLE public.municipalities
    ADD COLUMN IF NOT EXISTS boundary extensions.geometry(MULTIPOLYGON, 4326);

ALTER TABLE public.barangays
    ADD COLUMN IF NOT EXISTS boundary extensions.geometry(MULTIPOLYGON, 4326);

-- 3. Update / Alter baseline streets table with production fields & explicit ON DELETE NO ACTION FK
ALTER TABLE public.streets
    ADD COLUMN IF NOT EXISTS municipality_id UUID,
    ADD COLUMN IF NOT EXISTS source TEXT,
    ADD COLUMN IF NOT EXISTS verification_status TEXT NOT NULL DEFAULT 'unverified' CHECK (verification_status IN ('unverified', 'verified', 'rejected')),
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ DEFAULT pg_catalog.now();

-- Precondition check before enforcing NOT NULL
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM public.streets WHERE municipality_id IS NULL) THEN
        RAISE EXCEPTION 'Migration precondition failed: public.streets contains rows with NULL municipality_id. Backfill required before applying NOT NULL.';
    END IF;
END $$;

ALTER TABLE public.streets
    ALTER COLUMN municipality_id SET NOT NULL;

-- Drop and replace streets municipality foreign key with production ON DELETE NO ACTION
ALTER TABLE public.streets DROP CONSTRAINT IF EXISTS streets_municipality_id_fkey;
ALTER TABLE public.streets DROP CONSTRAINT IF EXISTS fk_streets_municipality;
ALTER TABLE public.streets
    ADD CONSTRAINT streets_municipality_id_fkey
    FOREIGN KEY (municipality_id) REFERENCES public.municipalities(id) ON DELETE NO ACTION;

-- 4. Update / Alter baseline street_segments table with PostGIS GEOMETRY(LINESTRING, 4326) and explicit ON DELETE NO ACTION FKs
ALTER TABLE public.street_segments
    ADD COLUMN IF NOT EXISTS geometry extensions.geometry(LINESTRING, 4326),
    ADD COLUMN IF NOT EXISTS road_type TEXT,
    ADD COLUMN IF NOT EXISTS source TEXT,
    ADD COLUMN IF NOT EXISTS verification_status TEXT NOT NULL DEFAULT 'unverified' CHECK (verification_status IN ('unverified', 'verified', 'rejected')),
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ DEFAULT pg_catalog.now(),
    ADD COLUMN IF NOT EXISTS osm_way_id BIGINT;

-- Precondition check before enforcing NOT NULL
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM public.street_segments WHERE geometry IS NULL) THEN
        RAISE EXCEPTION 'Migration precondition failed: public.street_segments contains rows with NULL geometry. Backfill required before applying NOT NULL.';
    END IF;
END $$;

ALTER TABLE public.street_segments
    ALTER COLUMN geometry SET NOT NULL;

-- Drop and replace street_segments foreign keys with production ON DELETE NO ACTION
ALTER TABLE public.street_segments DROP CONSTRAINT IF EXISTS street_segments_street_id_fkey;
ALTER TABLE public.street_segments DROP CONSTRAINT IF EXISTS fk_street_segments_street;
ALTER TABLE public.street_segments
    ADD CONSTRAINT street_segments_street_id_fkey
    FOREIGN KEY (street_id) REFERENCES public.streets(id) ON DELETE NO ACTION;

ALTER TABLE public.street_segments DROP CONSTRAINT IF EXISTS street_segments_barangay_id_fkey;
ALTER TABLE public.street_segments DROP CONSTRAINT IF EXISTS fk_street_segments_barangay;
ALTER TABLE public.street_segments
    ADD CONSTRAINT street_segments_barangay_id_fkey
    FOREIGN KEY (barangay_id) REFERENCES public.barangays(id) ON DELETE NO ACTION;

ALTER TABLE public.street_segments DROP CONSTRAINT IF EXISTS street_segments_municipality_id_fkey;
ALTER TABLE public.street_segments DROP CONSTRAINT IF EXISTS fk_street_segments_municipality;
ALTER TABLE public.street_segments
    ADD CONSTRAINT street_segments_municipality_id_fkey
    FOREIGN KEY (municipality_id) REFERENCES public.municipalities(id) ON DELETE NO ACTION;

-- 5. Update / Alter baseline intersections table with GEOMETRY(POINT, 4326) and created_at
-- Preserves existing scalar latitude/longitude columns without automatic conversion.
ALTER TABLE public.intersections
    ADD COLUMN IF NOT EXISTS location extensions.geometry(POINT, 4326),
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ DEFAULT pg_catalog.now();

-- Precondition check before enforcing NOT NULL on location
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM public.intersections WHERE location IS NULL) THEN
        RAISE EXCEPTION 'Migration precondition failed: public.intersections contains rows with NULL location. Backfill required before applying NOT NULL.';
    END IF;
END $$;

ALTER TABLE public.intersections
    ALTER COLUMN location SET NOT NULL;

-- 6. Create production B-tree and GiST spatial indexes (matching verified production index names)
CREATE INDEX IF NOT EXISTS streets_municipality_idx ON public.streets (municipality_id);
CREATE INDEX IF NOT EXISTS segments_barangay_idx ON public.street_segments (barangay_id);
CREATE INDEX IF NOT EXISTS street_segments_geometry_idx ON public.street_segments USING GIST (geometry);
CREATE INDEX IF NOT EXISTS intersections_location_idx ON public.intersections USING GIST (location);

-- 7. Create unique constraints (matching production unique index names without partial predicates)
CREATE UNIQUE INDEX IF NOT EXISTS streets_municipality_name_unique ON public.streets (municipality_id, name);
CREATE UNIQUE INDEX IF NOT EXISTS street_segments_osm_way_id_unique ON public.street_segments (osm_way_id);

-- ============================================================================
-- SECTION 8: COMPREHENSIVE READ-ONLY VALIDATION SQL SCRIPT
-- ============================================================================
-- Run the following statements after migration execution to verify compliance:
--
-- 1. Verify PostGIS Extension in 'extensions' schema:
-- SELECT extname, extversion, nspname AS schema_name FROM pg_extension e JOIN pg_namespace n ON e.extnamespace = n.oid WHERE extname = 'postgis';
--
-- 2. Verify Geometry Types and SRIDs via extensions.geometry_columns:
-- SELECT f_table_schema AS schema_name, f_table_name AS table_name, f_geometry_column AS geometry_column, type, srid FROM extensions.geometry_columns WHERE f_table_schema = 'public';
--
-- 3. Verify Exact ON DELETE NO ACTION Actions for Foreign Keys:
-- SELECT tc.table_name, kcu.column_name, rc.delete_rule FROM information_schema.table_constraints tc JOIN information_schema.key_column_usage kcu ON tc.constraint_name = kcu.constraint_name JOIN information_schema.referential_constraints rc ON tc.constraint_name = rc.constraint_name WHERE tc.constraint_type = 'FOREIGN KEY' AND tc.table_schema = 'public' AND tc.table_name IN ('streets', 'street_segments');
--
-- 4. Verify NOT NULL Constraints:
-- SELECT table_name, column_name, is_nullable FROM information_schema.columns WHERE table_schema = 'public' AND table_name IN ('streets', 'street_segments', 'intersections') AND column_name IN ('municipality_id', 'geometry', 'location');
--
-- 5. Verify Verification Status CHECK Constraints:
-- SELECT conrelid::regclass AS table_name, conname AS constraint_name, pg_get_constraintdef(oid) AS constraint_definition FROM pg_constraint WHERE conname LIKE '%verification_status%';
--
-- 6. Verify Expected Production GIS Indexes:
-- SELECT schemaname, tablename, indexname, indexdef FROM pg_indexes WHERE schemaname = 'public' AND indexname IN ('streets_municipality_idx', 'segments_barangay_idx', 'street_segments_geometry_idx', 'intersections_location_idx', 'streets_municipality_name_unique', 'street_segments_osm_way_id_unique');

COMMIT;
