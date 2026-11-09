-- ============================================================================
-- HAKOT MUNICIPAL WASTE MANAGEMENT SYSTEM — SUPABASE POSTGIS CENTRAL SCHEMA
-- Project Reference: vyegsnkerrbdqacwegff
-- URL: https://vyegsnkerrbdqacwegff.supabase.co
-- ============================================================================

-- 1. Enable PostGIS & UUID Extensions
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 2. Municipalities & Organizations
CREATE TABLE IF NOT EXISTS public.municipalities (
    id TEXT PRIMARY KEY DEFAULT ('org_' || uuid_generate_v4()),
    name TEXT NOT NULL,
    type TEXT NOT NULL DEFAULT 'MUNICIPALITY',
    province TEXT NOT NULL DEFAULT 'Batangas',
    region TEXT NOT NULL DEFAULT 'CALABARZON (Region IV-A)',
    admin_contact_email TEXT NOT NULL,
    admin_contact_phone TEXT NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 3. Districts
CREATE TABLE IF NOT EXISTS public.districts (
    id TEXT PRIMARY KEY DEFAULT ('dist_' || uuid_generate_v4()),
    municipality_id TEXT REFERENCES public.municipalities(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    code TEXT NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 4. Barangays
CREATE TABLE IF NOT EXISTS public.barangays (
    id TEXT PRIMARY KEY DEFAULT ('brgy_' || uuid_generate_v4()),
    district_id TEXT REFERENCES public.districts(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    boundary_polygon GEOMETRY(POLYGON, 4326),
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 5. User Profiles & Roles
CREATE TABLE IF NOT EXISTS public.profiles (
    user_id TEXT PRIMARY KEY,
    full_name TEXT NOT NULL,
    email TEXT UNIQUE NOT NULL,
    municipality_id TEXT REFERENCES public.municipalities(id),
    municipality_name TEXT NOT NULL,
    district_id TEXT REFERENCES public.districts(id),
    district_name TEXT NOT NULL,
    role TEXT NOT NULL CHECK (role IN ('DISTRICT_ADMIN', 'DRIVER', 'RESIDENT')),
    status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'PENDING_INVITATION', 'SUSPENDED', 'REVOKED')),
    registration_date TEXT DEFAULT 'Today',
    last_activity TEXT DEFAULT 'Just now',
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 6. District Admin Invitation Codes
CREATE TABLE IF NOT EXISTS public.invitation_codes (
    id TEXT PRIMARY KEY DEFAULT ('inv_' || uuid_generate_v4()),
    code_hash TEXT NOT NULL,
    raw_code_display TEXT UNIQUE NOT NULL,
    intended_email TEXT NOT NULL,
    intended_admin_name TEXT NOT NULL,
    municipality_id TEXT REFERENCES public.municipalities(id),
    assigned_district_id TEXT REFERENCES public.districts(id),
    assigned_district_name TEXT NOT NULL,
    assigned_role TEXT NOT NULL DEFAULT 'DISTRICT_ADMIN',
    created_by_authority TEXT NOT NULL DEFAULT 'Nasugbu Sanitation Authority',
    expires_at_millis BIGINT NOT NULL,
    redeemed_at_millis BIGINT,
    status TEXT NOT NULL DEFAULT 'PENDING_INVITATION',
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 7. Dumpsites & Facilities
CREATE TABLE IF NOT EXISTS public.dumpsites (
    id TEXT PRIMARY KEY DEFAULT ('ds_' || uuid_generate_v4()),
    municipality_id TEXT REFERENCES public.municipalities(id),
    name TEXT NOT NULL,
    type TEXT NOT NULL,
    address TEXT NOT NULL,
    location GEOMETRY(POINT, 4326) NOT NULL,
    capacity_percent INT NOT NULL DEFAULT 65,
    operating_hours TEXT NOT NULL,
    accepted_waste_types TEXT NOT NULL,
    is_open_now BOOLEAN DEFAULT TRUE,
    contact_number TEXT NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 8. Trucks & Vehicles
CREATE TABLE IF NOT EXISTS public.trucks (
    id TEXT PRIMARY KEY DEFAULT ('tr_' || uuid_generate_v4()),
    district_id TEXT REFERENCES public.districts(id),
    truck_code TEXT NOT NULL,
    plate_number TEXT UNIQUE NOT NULL,
    driver_name TEXT NOT NULL,
    assigned_route TEXT NOT NULL,
    assigned_barangays TEXT NOT NULL,
    destination_dumpsite TEXT NOT NULL,
    current_location GEOMETRY(POINT, 4326) NOT NULL,
    status TEXT NOT NULL DEFAULT 'ACTIVE_ON_ROUTE',
    speed_kmh INT DEFAULT 22,
    last_gps_update TEXT DEFAULT 'Live - Just now',
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 9. PostGIS Street Segments Table (EPSG:4326 WGS84)
CREATE TABLE IF NOT EXISTS public.street_segments (
    id TEXT PRIMARY KEY DEFAULT ('seg_' || uuid_generate_v4()),
    municipality_id TEXT REFERENCES public.municipalities(id),
    district_id TEXT REFERENCES public.districts(id),
    barangay_id TEXT REFERENCES public.barangays(id),
    street_name TEXT NOT NULL,
    road_type TEXT NOT NULL DEFAULT 'PRIMARY_STREET',
    segment_geom GEOMETRY(LINESTRING, 4326) NOT NULL,
    connected_segment_ids TEXT[] DEFAULT '{}',
    data_source TEXT DEFAULT 'PostGIS EPSG:4326 WGS84',
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 10. Collection Routes
CREATE TABLE IF NOT EXISTS public.routes (
    id TEXT PRIMARY KEY DEFAULT ('rt_' || uuid_generate_v4()),
    district_id TEXT REFERENCES public.districts(id),
    route_name TEXT NOT NULL,
    barangays_covered TEXT NOT NULL,
    color_hex BIGINT DEFAULT 4282080006,
    assigned_truck_plate TEXT NOT NULL,
    scheduled_time_window TEXT NOT NULL,
    destination_dumpsite TEXT NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 11. Route Segment Sequence Junction Table
CREATE TABLE IF NOT EXISTS public.route_segments (
    id TEXT PRIMARY KEY DEFAULT ('jnc_' || uuid_generate_v4()),
    route_id TEXT REFERENCES public.routes(id) ON DELETE CASCADE,
    segment_id TEXT REFERENCES public.street_segments(id) ON DELETE CASCADE,
    sequence_order INT NOT NULL,
    traversal_direction TEXT DEFAULT 'FORWARD',
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 12. Collection Schedules
CREATE TABLE IF NOT EXISTS public.schedules (
    id TEXT PRIMARY KEY DEFAULT ('sch_' || uuid_generate_v4()),
    district_id TEXT REFERENCES public.districts(id),
    route_id TEXT REFERENCES public.routes(id),
    barangay TEXT NOT NULL,
    route_name TEXT NOT NULL,
    schedule_date DATE NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    waste_category TEXT NOT NULL,
    truck_assigned TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'On Track',
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 13. Schedule Segment Progress Tracking
CREATE TABLE IF NOT EXISTS public.schedule_segments (
    id TEXT PRIMARY KEY DEFAULT ('prog_' || uuid_generate_v4()),
    schedule_id TEXT REFERENCES public.schedules(id) ON DELETE CASCADE,
    segment_id TEXT REFERENCES public.street_segments(id) ON DELETE CASCADE,
    status TEXT NOT NULL DEFAULT 'NOT_STARTED',
    confirmed_by_user TEXT NOT NULL,
    confirmed_at_millis BIGINT NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 14. Live & Historical Truck GPS Locations
CREATE TABLE IF NOT EXISTS public.truck_gps_locations (
    id TEXT PRIMARY KEY DEFAULT ('gps_' || uuid_generate_v4()),
    truck_id TEXT REFERENCES public.trucks(id) ON DELETE CASCADE,
    schedule_id TEXT REFERENCES public.schedules(id),
    location GEOMETRY(POINT, 4326) NOT NULL,
    accuracy_meters FLOAT DEFAULT 3.5,
    speed_kmh INT DEFAULT 22,
    gps_timestamp TIMESTAMPTZ DEFAULT NOW()
);

-- 15. Anonymous Waste Incident Reports
CREATE TABLE IF NOT EXISTS public.waste_reports (
    id TEXT PRIMARY KEY DEFAULT ('rep_' || uuid_generate_v4()),
    reference_number TEXT UNIQUE NOT NULL,
    access_token TEXT NOT NULL,
    report_type TEXT NOT NULL,
    location_name TEXT NOT NULL,
    location GEOMETRY(POINT, 4326) NOT NULL,
    description TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'SUBMITTED',
    assigned_office TEXT NOT NULL,
    assigned_handler_name TEXT DEFAULT 'Unassigned Handler',
    resolution_details TEXT DEFAULT '',
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 16. Two-Way Anonymous Messages
CREATE TABLE IF NOT EXISTS public.report_messages (
    id TEXT PRIMARY KEY DEFAULT ('msg_' || uuid_generate_v4()),
    report_id TEXT REFERENCES public.waste_reports(id) ON DELETE CASCADE,
    sender_type TEXT NOT NULL CHECK (sender_type IN ('RESIDENT', 'HANDLER')),
    sender_name TEXT NOT NULL,
    text TEXT NOT NULL,
    is_read BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 17. System Audit Logs
CREATE TABLE IF NOT EXISTS public.audit_logs (
    id TEXT PRIMARY KEY DEFAULT ('aud_' || uuid_generate_v4()),
    acting_user_email TEXT NOT NULL,
    action TEXT NOT NULL,
    target_record TEXT NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- ============================================================================
-- 18. SPATIAL INDEXES FOR FAST GEOGRAPHIC GIS QUERIES
-- ============================================================================
CREATE INDEX IF NOT EXISTS idx_barangays_geom ON public.barangays USING GIST(boundary_polygon);
CREATE INDEX IF NOT EXISTS idx_dumpsites_geom ON public.dumpsites USING GIST(location);
CREATE INDEX IF NOT EXISTS idx_trucks_geom ON public.trucks USING GIST(current_location);
CREATE INDEX IF NOT EXISTS idx_segments_geom ON public.street_segments USING GIST(segment_geom);
CREATE INDEX IF NOT EXISTS idx_gps_trails_geom ON public.truck_gps_locations USING GIST(location);
CREATE INDEX IF NOT EXISTS idx_reports_geom ON public.waste_reports USING GIST(location);

-- ============================================================================
-- 19. ENABLE ROW LEVEL SECURITY (RLS) & PUBLIC POLICIES
-- ============================================================================
ALTER TABLE public.municipalities ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.districts ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.barangays ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.invitation_codes ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.dumpsites ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.trucks ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.street_segments ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.routes ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.schedules ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.waste_reports ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.report_messages ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Public Read Municipalities" ON public.municipalities FOR SELECT USING (true);
CREATE POLICY "Public Read Districts" ON public.districts FOR SELECT USING (true);
CREATE POLICY "Public Read Barangays" ON public.barangays FOR SELECT USING (true);
CREATE POLICY "Public Read Profiles" ON public.profiles FOR SELECT USING (true);
CREATE POLICY "Public Read Dumpsites" ON public.dumpsites FOR SELECT USING (true);
CREATE POLICY "Public Read Trucks" ON public.trucks FOR SELECT USING (true);
CREATE POLICY "Public Read Street Segments" ON public.street_segments FOR SELECT USING (true);
CREATE POLICY "Public Read Routes" ON public.routes FOR SELECT USING (true);
CREATE POLICY "Public Read Schedules" ON public.schedules FOR SELECT USING (true);
CREATE POLICY "Public Insert Waste Reports" ON public.waste_reports FOR INSERT WITH CHECK (true);
CREATE POLICY "Public Select Waste Reports" ON public.waste_reports FOR SELECT USING (true);
CREATE POLICY "Public Read/Write Messages" ON public.report_messages FOR ALL USING (true);
CREATE POLICY "Admin All Access Profiles" ON public.profiles FOR ALL USING (true);

-- ============================================================================
-- 20. SEED INITIAL DATA FOR NASUGBU, BATANGAS (DISTRICT 1)
-- ============================================================================
INSERT INTO public.municipalities (id, name, type, province, region, admin_contact_email, admin_contact_phone)
VALUES ('org_nasugbu_lgu', 'Nasugbu Municipal Waste Management Office', 'MUNICIPALITY', 'Batangas', 'CALABARZON (Region IV-A)', 'waste.management@nasugbu.gov.ph', '(043) 723-HAKOT')
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.districts (id, municipality_id, name, code)
VALUES ('dist_1', 'org_nasugbu_lgu', 'District 1 - Poblacion & Coastal Corridor', 'DST-NAS-01')
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.profiles (user_id, full_name, email, municipality_id, municipality_name, district_id, district_name, role, status)
VALUES ('usr_dist_101', 'Maria Clara Lopez', 'm.lopez@nasugbu.gov.ph', 'org_nasugbu_lgu', 'Nasugbu, Batangas', 'dist_1', 'District 1 - Poblacion & Coastal Corridor', 'DISTRICT_ADMIN', 'ACTIVE')
ON CONFLICT (user_id) DO NOTHING;

INSERT INTO public.invitation_codes (id, code_hash, raw_code_display, intended_email, intended_admin_name, municipality_id, assigned_district_id, assigned_district_name, status, expires_at_millis)
VALUES ('inv_801', 'HAKOT-D7K9-M2QP', 'HAKOT-D7K9-M2QP-X8RN', 'sanitation.head@nasugbu.gov.ph', 'Engr. Marco Valenzuela', 'org_nasugbu_lgu', 'dist_1', 'District 1 - Poblacion & Coastal Corridor', 'PENDING_INVITATION', 1780000000000)
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.dumpsites (id, municipality_id, name, type, address, location, capacity_percent, operating_hours, accepted_waste_types, contact_number)
VALUES ('ds_1', 'org_nasugbu_lgu', 'Nasugbu MRF (Recovery Facility)', 'Materials Recovery Facility', 'J.P. Laurel St, Nasugbu, Batangas', ST_SetSRID(ST_MakePoint(120.6350, 14.0755), 4326), 65, '6:00 AM - 6:00 PM', 'General Household, Recyclables & Compost', '(043) 723-HAKOT')
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.trucks (id, district_id, truck_code, plate_number, driver_name, assigned_route, assigned_barangays, destination_dumpsite, current_location, status, speed_kmh)
VALUES ('tr_1', 'dist_1', 'Hakot Truck #1', 'ABK-8891', 'Mario Santos', 'C. Alvarez to F. Alix Street Corridor', 'C. Alvarez St → J.P. Laurel St → F. Alix St', 'Nasugbu MRF Facility', ST_SetSRID(ST_MakePoint(120.6255, 14.0715), 4326), 'ACTIVE_ON_ROUTE', 22)
ON CONFLICT (id) DO NOTHING;
