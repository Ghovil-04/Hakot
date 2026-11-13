-- ============================================================================
-- HAKOT VERSION 2.4 — ORIGINAL DATABASE BASELINE SCHEMA (PRE-V2.4 CORE)
-- Migration File: 20250222_00_base_schema.sql
-- Target Database: PostgreSQL / Supabase Project (Staging: jczbkpyktncwirlymkom)
-- Description: Establishes the authoritative pre-V2.4 baseline schema with corrected
--              production relationship mappings (streets, profiles, assignments,
--              truck_gps_locations, schedules, audit_logs, and notifications).
-- ============================================================================

BEGIN;

CREATE EXTENSION IF NOT EXISTS "pgcrypto" WITH SCHEMA extensions;

-- ============================================================================
-- SECTION 1: GEOGRAPHIC & ADMINISTRATIVE REFERENCE TABLES
-- ============================================================================

CREATE TABLE IF NOT EXISTS public.regions (
    id UUID PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    name TEXT NOT NULL,
    code TEXT UNIQUE NOT NULL
);

CREATE TABLE IF NOT EXISTS public.provinces (
    id UUID PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    region_id UUID REFERENCES public.regions(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    code TEXT UNIQUE NOT NULL
);

CREATE TABLE IF NOT EXISTS public.municipalities (
    id UUID PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    province_id UUID REFERENCES public.provinces(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    code TEXT UNIQUE NOT NULL,
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION
);

CREATE TABLE IF NOT EXISTS public.districts (
    id UUID PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    municipality_id UUID REFERENCES public.municipalities(id) ON DELETE CASCADE,
    name TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS public.barangays (
    id UUID PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    municipality_id UUID REFERENCES public.municipalities(id) ON DELETE CASCADE,
    district_id UUID REFERENCES public.districts(id) ON DELETE SET NULL,
    name TEXT NOT NULL,
    code TEXT
);

CREATE TABLE IF NOT EXISTS public.streets (
    id UUID PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    name TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS public.street_segments (
    id UUID PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    street_id UUID REFERENCES public.streets(id) ON DELETE CASCADE,
    barangay_id UUID REFERENCES public.barangays(id) ON DELETE SET NULL,
    municipality_id UUID REFERENCES public.municipalities(id) ON DELETE SET NULL,
    segment_name TEXT
);

CREATE TABLE IF NOT EXISTS public.intersections (
    id UUID PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    municipality_id UUID REFERENCES public.municipalities(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION
);

CREATE TABLE IF NOT EXISTS public.dumpsites (
    id UUID PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    municipality_id UUID REFERENCES public.municipalities(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    capacity_tons DOUBLE PRECISION
);

-- ============================================================================
-- SECTION 2: PROFILES, USERS & ASSIGNMENTS
-- ============================================================================

CREATE TABLE IF NOT EXISTS public.profiles (
    id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    full_name TEXT NOT NULL,
    role TEXT NOT NULL CHECK (role IN ('system_owner', 'district_admin', 'municipal_admin', 'municipal_staff', 'driver', 'citizen')),
    account_status TEXT NOT NULL DEFAULT 'active' CHECK (account_status IN ('active', 'pending', 'suspended', 'deactivated')),
    municipality_id UUID REFERENCES public.municipalities(id) ON DELETE SET NULL,
    district_id UUID REFERENCES public.districts(id) ON DELETE SET NULL,
    contact_number TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT pg_catalog.now()
);

CREATE TABLE IF NOT EXISTS public.administrator_assignments (
    id UUID PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    profile_id UUID REFERENCES public.profiles(id) ON DELETE CASCADE,
    district_id UUID REFERENCES public.districts(id) ON DELETE CASCADE,
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT pg_catalog.now()
);

CREATE TABLE IF NOT EXISTS public.driver_profiles (
    id UUID PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    profile_id UUID REFERENCES public.profiles(id) ON DELETE CASCADE,
    license_number TEXT NOT NULL,
    assigned_truck_id UUID,
    status TEXT NOT NULL DEFAULT 'off_duty'
);

CREATE TABLE IF NOT EXISTS public.invitation_codes (
    id UUID PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    code TEXT UNIQUE NOT NULL,
    role TEXT NOT NULL,
    municipality_id UUID REFERENCES public.municipalities(id) ON DELETE CASCADE,
    created_by UUID REFERENCES public.profiles(id) ON DELETE SET NULL,
    is_redeemed BOOLEAN NOT NULL DEFAULT FALSE,
    redeemed_by UUID REFERENCES public.profiles(id) ON DELETE SET NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT pg_catalog.now()
);

-- ============================================================================
-- SECTION 3: FLEET, ROUTES & SCHEDULES
-- ============================================================================

CREATE TABLE IF NOT EXISTS public.trucks (
    id UUID PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    municipality_id UUID REFERENCES public.municipalities(id) ON DELETE CASCADE,
    plate_number TEXT UNIQUE NOT NULL,
    model TEXT,
    capacity_tons DOUBLE PRECISION,
    status TEXT NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'maintenance', 'inactive'))
);

ALTER TABLE public.driver_profiles
    ADD CONSTRAINT fk_driver_profiles_truck
    FOREIGN KEY (assigned_truck_id) REFERENCES public.trucks(id) ON DELETE SET NULL;

CREATE TABLE IF NOT EXISTS public.routes (
    id UUID PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    municipality_id UUID REFERENCES public.municipalities(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT pg_catalog.now()
);

CREATE TABLE IF NOT EXISTS public.route_versions (
    id UUID PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    route_id UUID REFERENCES public.routes(id) ON DELETE CASCADE,
    version_number INT NOT NULL DEFAULT 1,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT pg_catalog.now()
);

CREATE TABLE IF NOT EXISTS public.route_segments (
    id UUID PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    route_version_id UUID REFERENCES public.route_versions(id) ON DELETE CASCADE,
    street_segment_id UUID REFERENCES public.street_segments(id) ON DELETE CASCADE,
    sequence_order INT NOT NULL
);

CREATE TABLE IF NOT EXISTS public.schedules (
    id UUID PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    municipality_id UUID REFERENCES public.municipalities(id) ON DELETE CASCADE,
    barangay_id UUID REFERENCES public.barangays(id) ON DELETE SET NULL,
    district_id UUID REFERENCES public.districts(id) ON DELETE SET NULL,
    driver_id UUID REFERENCES public.driver_profiles(id) ON DELETE SET NULL,
    dumpsite_id UUID REFERENCES public.dumpsites(id) ON DELETE SET NULL,
    route_version_id UUID REFERENCES public.route_versions(id) ON DELETE SET NULL,
    truck_id UUID REFERENCES public.trucks(id) ON DELETE SET NULL,
    collection_day TEXT NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME,
    status TEXT NOT NULL DEFAULT 'active'
);

CREATE TABLE IF NOT EXISTS public.schedule_segments (
    id UUID PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    schedule_id UUID REFERENCES public.schedules(id) ON DELETE CASCADE,
    street_segment_id UUID REFERENCES public.street_segments(id) ON DELETE CASCADE,
    status TEXT NOT NULL DEFAULT 'scheduled'
);

CREATE TABLE IF NOT EXISTS public.truck_gps_locations (
    id UUID PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    truck_id UUID REFERENCES public.trucks(id) ON DELETE CASCADE,
    driver_id UUID REFERENCES public.driver_profiles(id) ON DELETE SET NULL,
    schedule_id UUID REFERENCES public.schedules(id) ON DELETE SET NULL,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    speed DOUBLE PRECISION,
    heading DOUBLE PRECISION,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT pg_catalog.now()
);

CREATE TABLE IF NOT EXISTS public.collection_events (
    id UUID PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    schedule_segment_id UUID REFERENCES public.schedule_segments(id) ON DELETE CASCADE,
    driver_id UUID REFERENCES public.driver_profiles(id) ON DELETE SET NULL,
    collected_at TIMESTAMPTZ NOT NULL DEFAULT pg_catalog.now(),
    weight_collected_tons DOUBLE PRECISION,
    notes TEXT
);

-- ============================================================================
-- SECTION 4: CITIZEN REPORTS, NOTIFICATIONS & AUDIT LOGS
-- ============================================================================

CREATE TABLE IF NOT EXISTS public.account_applications (
    id UUID PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    full_name TEXT NOT NULL,
    email TEXT NOT NULL,
    contact_number TEXT NOT NULL,
    municipality_id UUID REFERENCES public.municipalities(id) ON DELETE RESTRICT,
    office_department TEXT NOT NULL,
    official_position TEXT NOT NULL,
    requested_role TEXT NOT NULL,
    company_name TEXT,
    status TEXT NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT pg_catalog.now(),
    reviewed_at TIMESTAMPTZ,
    reviewed_by UUID REFERENCES auth.users(id) ON DELETE SET NULL,
    review_notes TEXT
);

CREATE TABLE IF NOT EXISTS public.waste_reports (
    id UUID PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    municipality_id UUID REFERENCES public.municipalities(id) ON DELETE CASCADE,
    citizen_id UUID REFERENCES auth.users(id) ON DELETE SET NULL,
    reference_code TEXT UNIQUE NOT NULL,
    category TEXT NOT NULL,
    description TEXT,
    location TEXT NOT NULL,
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    status TEXT NOT NULL DEFAULT 'submitted' CHECK (status IN ('submitted', 'assigned', 'collected', 'resolved', 'rejected')),
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT pg_catalog.now()
);

CREATE TABLE IF NOT EXISTS public.report_attachments (
    id UUID PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    report_id UUID REFERENCES public.waste_reports(id) ON DELETE CASCADE,
    file_url TEXT NOT NULL,
    file_type TEXT
);

CREATE TABLE IF NOT EXISTS public.report_assignments (
    id UUID PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    report_id UUID REFERENCES public.waste_reports(id) ON DELETE CASCADE,
    assigned_profile_id UUID REFERENCES public.profiles(id) ON DELETE SET NULL,
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT pg_catalog.now()
);

CREATE TABLE IF NOT EXISTS public.report_messages (
    id UUID PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    report_id UUID REFERENCES public.waste_reports(id) ON DELETE CASCADE,
    sender_profile_id UUID REFERENCES public.profiles(id) ON DELETE SET NULL,
    sender_type TEXT NOT NULL,
    sender_name TEXT NOT NULL,
    message_text TEXT NOT NULL,
    sent_at TIMESTAMPTZ NOT NULL DEFAULT pg_catalog.now()
);

CREATE TABLE IF NOT EXISTS public.report_access_tokens (
    id UUID PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    report_id UUID REFERENCES public.waste_reports(id) ON DELETE CASCADE,
    token TEXT UNIQUE NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS public.report_status_history (
    id UUID PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    report_id UUID REFERENCES public.waste_reports(id) ON DELETE CASCADE,
    old_status TEXT,
    new_status TEXT NOT NULL,
    changed_by UUID REFERENCES public.profiles(id) ON DELETE SET NULL,
    changed_at TIMESTAMPTZ NOT NULL DEFAULT pg_catalog.now()
);

CREATE TABLE IF NOT EXISTS public.notifications (
    id UUID PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    profile_id UUID REFERENCES public.profiles(id) ON DELETE CASCADE,
    title TEXT NOT NULL,
    message TEXT NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT pg_catalog.now()
);

CREATE TABLE IF NOT EXISTS public.audit_logs (
    id UUID PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    actor_id UUID REFERENCES public.profiles(id) ON DELETE SET NULL,
    action TEXT NOT NULL,
    table_name TEXT,
    record_id UUID,
    details JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT pg_catalog.now()
);

-- ============================================================================
-- SECTION 5: STAGING / IMPORT TABLES
-- ============================================================================

CREATE TABLE IF NOT EXISTS public.psgc_import_staging (
    id UUID PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    payload JSONB,
    imported_at TIMESTAMPTZ NOT NULL DEFAULT pg_catalog.now()
);

CREATE TABLE IF NOT EXISTS public.osm_nasugbu_staging (
    id UUID PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    osm_id TEXT,
    feature_type TEXT,
    geom_json JSONB,
    properties JSONB
);

COMMIT;
