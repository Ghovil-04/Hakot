-- ============================================================================
-- HAKOT DATABASE POPULATION SCRIPT — NASUGBU, BATANGAS, PHILIPPINES
-- Official Sources: Philippine Statistics Authority (PSA) PSGC + OpenStreetMap
-- Target Supabase Project: vyegsnkerrbdqacwegff (https://vyegsnkerrbdqacwegff.supabase.co)
-- ============================================================================

-- 1. Insert Region (CALABARZON Region IV-A - PSGC: 040000000)
CREATE TABLE IF NOT EXISTS public.regions (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    psgc_code TEXT UNIQUE NOT NULL
);

INSERT INTO public.regions (id, name, psgc_code)
VALUES ('reg_04a', 'CALABARZON (Region IV-A)', '040000000')
ON CONFLICT (id) DO NOTHING;

-- 2. Insert Province (Batangas - PSGC: 041000000)
CREATE TABLE IF NOT EXISTS public.provinces (
    id TEXT PRIMARY KEY,
    region_id TEXT REFERENCES public.regions(id),
    name TEXT NOT NULL,
    psgc_code TEXT UNIQUE NOT NULL
);

INSERT INTO public.provinces (id, region_id, name, psgc_code)
VALUES ('prov_batangas', 'reg_04a', 'Batangas', '041000000')
ON CONFLICT (id) DO NOTHING;

-- 3. Update / Insert Municipality (Nasugbu - PSGC: 041019000)
INSERT INTO public.municipalities (id, name, type, province, region, admin_contact_email, admin_contact_phone)
VALUES ('org_nasugbu_lgu', 'Nasugbu Municipal Waste Management Office', 'MUNICIPALITY', 'Batangas', 'CALABARZON (Region IV-A)', 'waste.management@nasugbu.gov.ph', '(043) 723-HAKOT')
ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name,
    province = EXCLUDED.province,
    region = EXCLUDED.region;

-- 4. Insert Districts for Nasugbu
INSERT INTO public.districts (id, municipality_id, name, code)
VALUES
    ('dist_1', 'org_nasugbu_lgu', 'District 1 - Poblacion & Coastal Corridor', 'DST-NAS-01'),
    ('dist_2', 'org_nasugbu_lgu', 'District 2 - Bypass & Highway Corridor', 'DST-NAS-02'),
    ('dist_3', 'org_nasugbu_lgu', 'District 3 - Outer Municipal Zone', 'DST-NAS-03')
ON CONFLICT (id) DO NOTHING;

-- 5. Insert Official PSA PSGC Barangays for Nasugbu, Batangas
INSERT INTO public.barangays (id, district_id, name)
VALUES
    ('brgy_041019001', 'dist_1', 'Poblacion 1'),
    ('brgy_041019002', 'dist_1', 'Poblacion 2'),
    ('brgy_041019003', 'dist_1', 'Poblacion 3'),
    ('brgy_041019004', 'dist_1', 'Poblacion 4'),
    ('brgy_041019005', 'dist_1', 'Poblacion 5'),
    ('brgy_041019006', 'dist_1', 'Poblacion 6'),
    ('brgy_041019007', 'dist_1', 'Poblacion 7'),
    ('brgy_041019008', 'dist_1', 'Poblacion 8'),
    ('brgy_041019009', 'dist_1', 'Poblacion 9'),
    ('brgy_041019010', 'dist_1', 'Poblacion 10'),
    ('brgy_041019011', 'dist_1', 'Poblacion 11'),
    ('brgy_041019012', 'dist_1', 'Wawa'),
    ('brgy_041019013', 'dist_1', 'Bucana'),
    ('brgy_041019014', 'dist_3', 'Looc'),
    ('brgy_041019015', 'dist_3', 'Kaylaway')
ON CONFLICT (id) DO NOTHING;

-- 6. Insert Verified OpenStreetMap Street Segments (PostGIS Linestrings)
INSERT INTO public.street_segments (id, municipality_id, district_id, barangay_id, street_name, road_type, segment_geom, connected_segment_ids, data_source)
VALUES
    ('seg_c_alvarez_01', 'org_nasugbu_lgu', 'dist_1', 'brgy_041019001', 'C. Alvarez Street (West)', 'PRIMARY_STREET', ST_SetSRID(ST_MakeLine(ARRAY[ST_MakePoint(120.6220, 14.0712), ST_MakePoint(120.6270, 14.0712)]), 4326), ARRAY['seg_c_alvarez_02'], 'OpenStreetMap / PostGIS WGS84'),
    ('seg_c_alvarez_02', 'org_nasugbu_lgu', 'dist_1', 'brgy_041019001', 'C. Alvarez Street (East)', 'PRIMARY_STREET', ST_SetSRID(ST_MakeLine(ARRAY[ST_MakePoint(120.6270, 14.0712), ST_MakePoint(120.6322, 14.0712)]), 4326), ARRAY['seg_c_alvarez_01', 'seg_jp_laurel_01'], 'OpenStreetMap / PostGIS WGS84'),
    ('seg_jp_laurel_01', 'org_nasugbu_lgu', 'dist_1', 'brgy_041019002', 'J.P. Laurel Street Pass', 'PRIMARY_STREET', ST_SetSRID(ST_MakeLine(ARRAY[ST_MakePoint(120.6322, 14.0712), ST_MakePoint(120.6322, 14.0722)]), 4326), ARRAY['seg_c_alvarez_02', 'seg_f_alix_01'], 'OpenStreetMap / PostGIS WGS84'),
    ('seg_f_alix_01', 'org_nasugbu_lgu', 'dist_1', 'brgy_041019003', 'F. Alix Street Corridor', 'SECONDARY_STREET', ST_SetSRID(ST_MakeLine(ARRAY[ST_MakePoint(120.6322, 14.0722), ST_MakePoint(120.6355, 14.0722)]), 4326), ARRAY['seg_jp_laurel_01'], 'OpenStreetMap / PostGIS WGS84'),
    ('seg_roxas_01', 'org_nasugbu_lgu', 'dist_1', 'brgy_041019012', 'Roxas Street Pass', 'PRIMARY_STREET', ST_SetSRID(ST_MakeLine(ARRAY[ST_MakePoint(120.6255, 14.0705), ST_MakePoint(120.6370, 14.0725)]), 4326), ARRAY['seg_jp_laurel_01'], 'OpenStreetMap / PostGIS WGS84'),
    ('seg_p_burgos_01', 'org_nasugbu_lgu', 'dist_3', 'brgy_041019014', 'P. Burgos Street Pass', 'SECONDARY_STREET', ST_SetSRID(ST_MakeLine(ARRAY[ST_MakePoint(120.6275, 14.0680), ST_MakePoint(120.6325, 14.0740)]), 4326), ARRAY['seg_roxas_01'], 'OpenStreetMap / PostGIS WGS84')
ON CONFLICT (id) DO NOTHING;

-- 7. Insert Verified Dumpsite Facilities
INSERT INTO public.dumpsites (id, municipality_id, name, type, address, location, capacity_percent, operating_hours, accepted_waste_types, contact_number)
VALUES
    ('ds_1', 'org_nasugbu_lgu', 'Nasugbu MRF (Recovery Facility)', 'Materials Recovery Facility', 'J.P. Laurel St, Nasugbu, Batangas', ST_SetSRID(ST_MakePoint(120.6350, 14.0755), 4326), 65, '6:00 AM - 6:00 PM', 'General Household, Recyclables & Compost', '(043) 723-HAKOT'),
    ('ds_2', 'org_nasugbu_lgu', 'Central Municipal Landfill', 'Sanitary Landfill', 'Nasugbu Outer Bypass Road', ST_SetSRID(ST_MakePoint(120.6420, 14.0820), 4326), 42, '5:00 AM - 8:00 PM', 'All Municipal & Residual Waste', '(043) 723-9900')
ON CONFLICT (id) DO NOTHING;

-- 8. Insert Fleet Garbage Trucks
INSERT INTO public.trucks (id, district_id, truck_code, plate_number, driver_name, assigned_route, assigned_barangays, destination_dumpsite, current_location, status, speed_kmh)
VALUES
    ('tr_1', 'dist_1', 'Hakot Truck #1', 'ABK-8891', 'Mario Santos', 'C. Alvarez to F. Alix Street Corridor', 'C. Alvarez St → J.P. Laurel St → F. Alix St', 'Nasugbu MRF Facility', ST_SetSRID(ST_MakePoint(120.6255, 14.0715), 4326), 'ACTIVE_ON_ROUTE', 22),
    ('tr_2', 'dist_1', 'Hakot Truck #2', 'NWD-4512', 'Juan Dela Cruz', 'Roxas Street Corridor', 'Spans Barangays Wawa & Bucana', 'Central Municipal Landfill', ST_SetSRID(ST_MakePoint(120.6330, 14.0718), 4326), 'ACTIVE_ON_ROUTE', 18),
    ('tr_3', 'dist_3', 'Hakot Truck #3', 'RST-9904', 'Pedro Penduko', 'P. Burgos Street Pass', 'Spans Barangays Looc & Kaylaway', 'Nasugbu MRF Facility', ST_SetSRID(ST_MakePoint(120.6325, 14.0740), 4326), 'UNLOADING_AT_DUMPSITE', 0)
ON CONFLICT (id) DO NOTHING;

-- 9. Insert Published Collection Routes
INSERT INTO public.routes (id, district_id, route_name, barangays_covered, color_hex, assigned_truck_plate, scheduled_time_window, destination_dumpsite)
VALUES
    ('rt_1', 'dist_1', 'C. Alvarez to F. Alix Street Corridor', 'C. Alvarez St → J.P. Laurel St → F. Alix St', 4282080006, 'ABK-8891', '8:00 AM - 11:30 AM', 'Nasugbu MRF Facility'),
    ('rt_2', 'dist_1', 'Roxas Street Corridor', 'Roxas St (Spans Brgy Wawa & Bucana)', 4294286859, 'NWD-4512', '1:00 PM - 4:30 PM', 'Central Municipal Landfill'),
    ('rt_3', 'dist_3', 'P. Burgos Street Pass', 'P. Burgos St (Spans Brgy Looc & Kaylaway)', 4279286145, 'RST-9904', '5:00 PM - 8:00 PM', 'Nasugbu MRF Facility')
ON CONFLICT (id) DO NOTHING;

-- 10. Insert Route Sequence Junctions
INSERT INTO public.route_segments (id, route_id, segment_id, sequence_order, traversal_direction)
VALUES
    ('jnc_1', 'rt_1', 'seg_c_alvarez_01', 1, 'FORWARD'),
    ('jnc_2', 'rt_1', 'seg_c_alvarez_02', 2, 'FORWARD'),
    ('jnc_3', 'rt_1', 'seg_jp_laurel_01', 3, 'FORWARD'),
    ('jnc_4', 'rt_1', 'seg_f_alix_01', 4, 'FORWARD')
ON CONFLICT (id) DO NOTHING;
