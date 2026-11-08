package com.example.hakot.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.hakot.model.AdminStatus
import com.example.hakot.model.AdminUserProfile
import com.example.hakot.model.Announcement
import com.example.hakot.model.AnonymousReport
import com.example.hakot.model.AuditLogEntry
import com.example.hakot.model.CollectionRoute
import com.example.hakot.model.DistrictAdminInvitation
import com.example.hakot.model.Dumpsite
import com.example.hakot.model.GarbageTruck
import com.example.hakot.model.OrganizationConfig
import com.example.hakot.model.OrganizationType
import com.example.hakot.model.ReportMessage
import com.example.hakot.model.ReportStatus
import com.example.hakot.model.ReportType
import com.example.hakot.model.ScheduleItem
import com.example.hakot.model.TruckStatus
import com.example.hakot.model.UserRole
import com.example.hakot.model.WasteCategory
import java.security.SecureRandom
import java.time.LocalDate
import java.time.LocalTime

object HakotRepository {
    val today: LocalDate get() = LocalDate.now()

    var selectedMapDate: LocalDate by mutableStateOf(LocalDate.now())

    var currentAdminRole: UserRole by mutableStateOf(UserRole.DISTRICT_ADMIN)

    var isAuthenticated: Boolean by mutableStateOf(false)
    var authenticatedUser: AdminUserProfile? by mutableStateOf(null)

    var activeOrganization = OrganizationConfig(
        id = "org_nasugbu_lgu",
        name = "Nasugbu Municipal Waste Management Office",
        type = OrganizationType.MUNICIPALITY,
        province = "Batangas",
        region = "CALABARZON (Region IV-A)",
        assignedMunicipality = "Nasugbu",
        coveredBarangays = listOf("Poblacion 1", "Poblacion 2", "Poblacion 3", "Wawa", "Bucana", "Looc", "Kaylaway"),
        adminContactEmail = "waste.management@nasugbu.gov.ph",
        adminContactPhone = "(043) 723-HAKOT"
    )

    var currentDistrictAdmin = AdminUserProfile(
        userId = "usr_dist_101",
        fullName = "Maria Clara Lopez",
        email = "m.lopez@nasugbu.gov.ph",
        municipalityOrgId = "org_nasugbu_lgu",
        municipalityName = "Nasugbu, Batangas",
        districtId = "dist_1",
        districtName = "District 1 - Poblacion & Coastal Corridor",
        role = UserRole.DISTRICT_ADMIN,
        status = AdminStatus.ACTIVE,
        registrationDate = "Feb 14, 2026",
        lastActivity = "Active Now"
    )

    var userLat: Double = 14.0700
    var userLng: Double = 120.6300
    var detectedLocationName: String = "Nasugbu, Batangas"
    var detectedCity: String = "Nasugbu"
    var detectedProvince: String = "Batangas"

    val adminProfiles = mutableStateListOf(
        AdminUserProfile(
            userId = "usr_dist_101",
            fullName = "Maria Clara Lopez",
            email = "m.lopez@nasugbu.gov.ph",
            municipalityOrgId = "org_nasugbu_lgu",
            municipalityName = "Nasugbu, Batangas",
            districtId = "dist_1",
            districtName = "District 1 - Poblacion & Coastal Corridor",
            role = UserRole.DISTRICT_ADMIN,
            status = AdminStatus.ACTIVE,
            registrationDate = "Feb 14, 2026",
            lastActivity = "Active Now"
        ),
        AdminUserProfile(
            userId = "usr_dist_102",
            fullName = "Capt. Juanito Cruz",
            email = "j.cruz@nasugbu.gov.ph",
            municipalityOrgId = "org_nasugbu_lgu",
            municipalityName = "Nasugbu, Batangas",
            districtId = "dist_2",
            districtName = "District 2 - Bypass & Highway Corridor",
            role = UserRole.DISTRICT_ADMIN,
            status = AdminStatus.ACTIVE,
            registrationDate = "Mar 01, 2026",
            lastActivity = "Yesterday"
        )
    )

    val invitations = mutableStateListOf(
        DistrictAdminInvitation(
            invitationId = "inv_801",
            codeHash = "HAKOT-D7K9-M2QP",
            rawCodeDisplay = "HAKOT-D7K9-M2QP-X8RN",
            intendedEmail = "sanitation.head@nasugbu.gov.ph",
            intendedAdminName = "Engr. Marco Valenzuela",
            municipalityOrgId = "org_nasugbu_lgu",
            assignedDistrictId = "dist_3",
            assignedDistrictName = "District 3 - Outer Municipal Zone",
            assignedRole = UserRole.DISTRICT_ADMIN,
            createdByAuthorityOffice = "Nasugbu Sanitation Authority",
            createdAtMillis = System.currentTimeMillis() - 86400000 * 2,
            expiresAtMillis = System.currentTimeMillis() + 86400000 * 5,
            status = AdminStatus.PENDING_INVITATION
        )
    )

    val auditLogs = mutableStateListOf(
        AuditLogEntry(
            id = "aud_101",
            actingUserEmail = "m.lopez@nasugbu.gov.ph",
            action = "Updated District 1 Waste Collection Schedule",
            targetRecord = "C. Alvarez to F. Alix Street Corridor",
            timestampMillis = System.currentTimeMillis() - 3600000 * 2
        ),
        AuditLogEntry(
            id = "aud_102",
            actingUserEmail = "m.lopez@nasugbu.gov.ph",
            action = "Redeemed District Invitation Code",
            targetRecord = "District 1 Administrator Account Activated",
            timestampMillis = System.currentTimeMillis() - 86400000 * 10
        )
    )

    val reportMessages = mutableStateListOf(
        ReportMessage(
            id = "msg_1",
            reportId = "rep_100",
            senderType = "HANDLER",
            senderName = "Nasugbu Waste Collection Office",
            text = "Thank you for reporting this unassigned section. Our team is inspecting P. Burgos Street Extension.",
            timestampMillis = System.currentTimeMillis() - 3600000 * 1
        ),
        ReportMessage(
            id = "msg_2",
            reportId = "rep_100",
            senderType = "RESIDENT",
            senderName = "Anonymous Resident",
            text = "Thank you! We have 8 households along this corner needing collection.",
            timestampMillis = System.currentTimeMillis() - 1800000
        )
    )

    val announcements = mutableStateListOf(
        Announcement(
            id = "ann_1",
            title = "📢 LGU Holiday Waste Collection Notice",
            message = "All regular street routes in Nasugbu (C. Alvarez St, J.P. Laurel St & F. Alix St) will proceed as scheduled today.",
            timestampMillis = System.currentTimeMillis() - 3600000 * 3,
            category = "Schedule Update",
            isPinned = true
        ),
        Announcement(
            id = "ann_2",
            title = "🚛 Extra Truck Dispatched to Roxas Street",
            message = "Hakot Truck #2 dispatched for afternoon collection along Roxas Street corridor.",
            timestampMillis = System.currentTimeMillis() - 3600000 * 8,
            category = "Dispatch Alert",
            isPinned = false
        )
    )

    val dumpsites = mutableStateListOf(
        Dumpsite(
            id = "ds_1",
            name = "Nasugbu MRF (Recovery Facility)",
            type = "Materials Recovery Facility",
            address = "J.P. Laurel St, Nasugbu, Batangas",
            latitude = 14.0755,
            longitude = 120.6350,
            capacityPercent = 65,
            operatingHours = "6:00 AM - 6:00 PM",
            acceptedWasteTypes = "General Household Waste, Compostables & Recyclables",
            isOpenNow = true,
            contactNumber = "(043) 723-HAKOT"
        ),
        Dumpsite(
            id = "ds_2",
            name = "Central Municipal Landfill",
            type = "Sanitary Landfill",
            address = "Nasugbu Outer Bypass Road",
            latitude = 14.0820,
            longitude = 120.6420,
            capacityPercent = 42,
            operatingHours = "5:00 AM - 8:00 PM",
            acceptedWasteTypes = "All Municipal & Residual Waste",
            isOpenNow = true,
            contactNumber = "(043) 723-9900"
        )
    )

    val trucks = mutableStateListOf(
        GarbageTruck(
            id = "tr_1",
            truckCode = "Hakot Truck #1",
            plateNumber = "ABK-8891",
            driverName = "Mario Santos",
            assignedRoute = "C. Alvarez to F. Alix Street Corridor",
            assignedBarangays = "C. Alvarez St → J.P. Laurel St → F. Alix St",
            destinationDumpsite = "Nasugbu MRF Facility",
            latitude = 14.0715,
            longitude = 120.6255,
            status = TruckStatus.ACTIVE_ON_ROUTE,
            speedKmH = 22,
            lastGpsUpdate = "Live - 1 min ago"
        ),
        GarbageTruck(
            id = "tr_2",
            truckCode = "Hakot Truck #2",
            plateNumber = "NWD-4512",
            driverName = "Juan Dela Cruz",
            assignedRoute = "Roxas Street Corridor",
            assignedBarangays = "Spans Barangays Wawa & Bucana",
            destinationDumpsite = "Central Municipal Landfill",
            latitude = 14.0718,
            longitude = 120.6330,
            status = TruckStatus.ACTIVE_ON_ROUTE,
            speedKmH = 18,
            lastGpsUpdate = "Live - 2 mins ago"
        ),
        GarbageTruck(
            id = "tr_3",
            truckCode = "Hakot Truck #3",
            plateNumber = "RST-9904",
            driverName = "Pedro Penduko",
            assignedRoute = "P. Burgos Street Pass",
            assignedBarangays = "Spans Barangays Looc & Kaylaway",
            destinationDumpsite = "Nasugbu MRF Facility",
            latitude = 14.0740,
            longitude = 120.6325,
            status = TruckStatus.UNLOADING_AT_DUMPSITE,
            speedKmH = 0,
            lastGpsUpdate = "Live - Just now"
        )
    )

    val routes = mutableStateListOf(
        CollectionRoute(
            id = "rt_1",
            routeName = "C. Alvarez to F. Alix Street Corridor",
            barangaysCovered = "C. Alvarez St → J.P. Laurel St → F. Alix St",
            colorHex = 0xFF3B82F6,
            waypoints = listOf(
                Pair(300f, 400f),
                Pair(450f, 480f),
                Pair(550f, 500f)
            ),
            geoPoints = listOf(
                Pair(14.0712, 120.6220),
                Pair(14.0712, 120.6270),
                Pair(14.0712, 120.6322),
                Pair(14.0722, 120.6322),
                Pair(14.0722, 120.6355)
            ),
            assignedTruckPlate = "ABK-8891",
            scheduledTimeWindow = "8:00 AM - 11:30 AM",
            scheduledDate = "Today",
            destinationDumpsite = "Nasugbu MRF Facility"
        ),
        CollectionRoute(
            id = "rt_2",
            routeName = "Roxas Street Corridor",
            barangaysCovered = "Roxas St (Spans Brgy Wawa & Bucana)",
            colorHex = 0xFFF59E0B,
            waypoints = listOf(
                Pair(200f, 250f),
                Pair(500f, 260f),
                Pair(800f, 280f)
            ),
            geoPoints = listOf(
                Pair(14.0705, 120.6255),
                Pair(14.0712, 120.6290),
                Pair(14.0718, 120.6330),
                Pair(14.0725, 120.6370)
            ),
            assignedTruckPlate = "NWD-4512",
            scheduledTimeWindow = "1:00 PM - 4:30 PM",
            scheduledDate = "Today",
            destinationDumpsite = "Central Municipal Landfill"
        ),
        CollectionRoute(
            id = "rt_3",
            routeName = "P. Burgos Street Pass",
            barangaysCovered = "P. Burgos St (Spans Brgy Looc & Kaylaway)",
            colorHex = 0xFF10B981,
            waypoints = listOf(
                Pair(600f, 300f),
                Pair(750f, 320f),
                Pair(850f, 450f)
            ),
            geoPoints = listOf(
                Pair(14.0680, 120.6275),
                Pair(14.0710, 120.6295),
                Pair(14.0740, 120.6325)
            ),
            assignedTruckPlate = "RST-9904",
            scheduledTimeWindow = "5:00 PM - 8:00 PM",
            scheduledDate = "Today",
            destinationDumpsite = "Nasugbu MRF Facility"
        )
    )

    val reports = mutableStateListOf(
        AnonymousReport(
            id = "rep_100",
            referenceNumber = "#HK-8924",
            accessToken = "AUTH-8A2F-9D4C-3B1E",
            reportType = ReportType.NO_ROUTE_IN_AREA,
            locationName = "P. Burgos Street Extension, Nasugbu",
            latitude = 14.0680,
            longitude = 120.6275,
            description = "No waste collection route assigned for our section of P. Burgos Street",
            timestampMillis = System.currentTimeMillis() - 3600000 * 1,
            status = ReportStatus.IN_PROGRESS,
            assignedOffice = ReportType.NO_ROUTE_IN_AREA.targetOffice,
            assignedHandlerName = "Engr. Marco Valenzuela"
        ),
        AnonymousReport(
            id = "rep_101",
            referenceNumber = "#HK-8925",
            accessToken = "AUTH-4C7B-1E8D-9F0A",
            reportType = ReportType.MISSED_COLLECTION,
            locationName = "C. Alvarez Street corner Zone 2",
            latitude = 14.0712,
            longitude = 120.6270,
            description = "Truck skipped morning collection for 3 houses",
            timestampMillis = System.currentTimeMillis() - 3600000 * 2,
            status = ReportStatus.SUBMITTED,
            assignedOffice = ReportType.MISSED_COLLECTION.targetOffice,
            assignedHandlerName = "Unassigned Handler"
        )
    )

    val schedules = mutableStateListOf<ScheduleItem>()

    init {
        generateDefaultSchedules()
    }

    private fun generateDefaultSchedules() {
        val currDate = LocalDate.now()
        schedules.clear()
        
        // Today's working schedules based on Street Names spanning multiple barangays
        schedules.add(
            ScheduleItem(
                id = "sch_1",
                barangay = "C. Alvarez St → J.P. Laurel St → F. Alix St",
                routeName = "C. Alvarez to F. Alix Street Corridor",
                date = currDate,
                startTime = LocalTime.of(8, 0),
                endTime = LocalTime.of(11, 30),
                wasteCategory = WasteCategory.GENERAL_WASTE,
                truckAssigned = "Hakot Truck #1 (ABK-8891)",
                status = "On Track"
            )
        )
        schedules.add(
            ScheduleItem(
                id = "sch_2",
                barangay = "Roxas Street (Spans Brgy Wawa & Bucana)",
                routeName = "Roxas Street Corridor",
                date = currDate,
                startTime = LocalTime.of(13, 0),
                endTime = LocalTime.of(16, 30),
                wasteCategory = WasteCategory.GENERAL_WASTE,
                truckAssigned = "Hakot Truck #2 (NWD-4512)",
                status = "Scheduled"
            )
        )
        schedules.add(
            ScheduleItem(
                id = "sch_3",
                barangay = "P. Burgos Street (Spans Brgy Looc & Kaylaway)",
                routeName = "P. Burgos Street Pass",
                date = currDate,
                startTime = LocalTime.of(17, 0),
                endTime = LocalTime.of(20, 0),
                wasteCategory = WasteCategory.GENERAL_WASTE,
                truckAssigned = "Hakot Truck #3 (RST-9904)",
                status = "Scheduled"
            )
        )

        // Tomorrow's schedules
        schedules.add(
            ScheduleItem(
                id = "sch_4",
                barangay = "Nasugbu Highway (Spans Multiple Barangays)",
                routeName = "Roxas Street Corridor",
                date = currDate.plusDays(1),
                startTime = LocalTime.of(7, 30),
                endTime = LocalTime.of(11, 0),
                wasteCategory = WasteCategory.GENERAL_WASTE,
                truckAssigned = "Hakot Truck #1 (ABK-8891)",
                status = "Scheduled"
            )
        )
        schedules.add(
            ScheduleItem(
                id = "sch_5",
                barangay = "C. Alvarez St → J.P. Laurel St → F. Alix St",
                routeName = "C. Alvarez to F. Alix Street Corridor",
                date = currDate.plusDays(1),
                startTime = LocalTime.of(14, 0),
                endTime = LocalTime.of(17, 0),
                wasteCategory = WasteCategory.GENERAL_WASTE,
                truckAssigned = "Hakot Truck #2 (NWD-4512)",
                status = "Scheduled"
            )
        )
    }

    fun login(emailInput: String, passwordInput: String): Boolean {
        val trimmedEmail = emailInput.trim().lowercase()
        val profile = adminProfiles.find { it.email.lowercase() == trimmedEmail }

        if (profile != null && profile.status == AdminStatus.ACTIVE) {
            isAuthenticated = true
            authenticatedUser = profile
            currentDistrictAdmin = profile
            currentAdminRole = profile.role

            auditLogs.add(0, AuditLogEntry(
                id = "aud_${System.currentTimeMillis() % 10000}",
                actingUserEmail = profile.email,
                action = "Authenticated Login Success (${profile.role.label})",
                targetRecord = profile.districtName,
                timestampMillis = System.currentTimeMillis()
            ))

            return true
        }

        // Driver login shortcut or default valid email login
        if (trimmedEmail.contains("driver") || trimmedEmail.contains("truck") || emailInput == "driver@hakot.gov.ph") {
            val driverProfile = AdminUserProfile(
                userId = "usr_driver_1",
                fullName = "Mario Santos (Truck Driver)",
                email = "driver@hakot.gov.ph",
                municipalityOrgId = activeOrganization.id,
                municipalityName = activeOrganization.name,
                districtId = "dist_1",
                districtName = "District 1 - Truck #1 Route",
                role = UserRole.DRIVER,
                status = AdminStatus.ACTIVE,
                registrationDate = "Today",
                lastActivity = "Active Now"
            )
            isAuthenticated = true
            authenticatedUser = driverProfile
            currentAdminRole = UserRole.DRIVER
            return true
        }

        // Allow default admin email
        val defaultAdmin = currentDistrictAdmin
        if (trimmedEmail == defaultAdmin.email.lowercase() || trimmedEmail.contains("admin")) {
            isAuthenticated = true
            authenticatedUser = defaultAdmin
            currentAdminRole = defaultAdmin.role
            return true
        }

        return false
    }

    fun logout() {
        val userEmail = authenticatedUser?.email ?: "admin@hakot.gov.ph"
        auditLogs.add(0, AuditLogEntry(
            id = "aud_${System.currentTimeMillis() % 10000}",
            actingUserEmail = userEmail,
            action = "Authenticated User Signed Out",
            targetRecord = "Session Closed",
            timestampMillis = System.currentTimeMillis()
        ))

        isAuthenticated = false
        authenticatedUser = null
    }

    // Cryptographically secure random invitation code generator
    fun generateDistrictInvitation(
        districtName: String,
        intendedAdminName: String,
        intendedEmail: String
    ): DistrictAdminInvitation {
        val random = SecureRandom()
        val chars = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
        fun randomChunk(len: Int) = (1..len).map { chars[random.nextInt(chars.length)] }.joinToString("")
        val formattedCode = "HAKOT-${randomChunk(4)}-${randomChunk(4)}-${randomChunk(4)}"

        val newInv = DistrictAdminInvitation(
            invitationId = "inv_${System.currentTimeMillis() % 10000}",
            codeHash = formattedCode.substring(0, 14),
            rawCodeDisplay = formattedCode,
            intendedEmail = intendedEmail,
            intendedAdminName = intendedAdminName,
            municipalityOrgId = activeOrganization.id,
            assignedDistrictId = "dist_${System.currentTimeMillis() % 1000}",
            assignedDistrictName = districtName,
            assignedRole = UserRole.DISTRICT_ADMIN,
            createdByAuthorityOffice = "Nasugbu Municipal Sanitation Office",
            createdAtMillis = System.currentTimeMillis(),
            expiresAtMillis = System.currentTimeMillis() + 86400000 * 7,
            status = AdminStatus.PENDING_INVITATION
        )

        invitations.add(0, newInv)

        auditLogs.add(0, AuditLogEntry(
            id = "aud_${System.currentTimeMillis() % 10000}",
            actingUserEmail = currentDistrictAdmin.email,
            action = "Generated Invitation Code for $districtName",
            targetRecord = "$intendedEmail ($formattedCode)",
            timestampMillis = System.currentTimeMillis()
        ))

        return newInv
    }

    fun redeemInvitation(code: String, email: String): Boolean {
        val cleanedCode = code.trim().uppercase()
        val matchedInv = invitations.find {
            (it.rawCodeDisplay == cleanedCode || it.codeHash == cleanedCode) &&
            it.status == AdminStatus.PENDING_INVITATION &&
            it.expiresAtMillis > System.currentTimeMillis()
        }

        if (matchedInv != null) {
            matchedInv.status = AdminStatus.ACTIVE
            matchedInv.redeemedAtMillis = System.currentTimeMillis()

            currentDistrictAdmin = AdminUserProfile(
                userId = "usr_dist_${System.currentTimeMillis() % 10000}",
                fullName = matchedInv.intendedAdminName,
                email = email,
                municipalityOrgId = matchedInv.municipalityOrgId,
                municipalityName = activeOrganization.name,
                districtId = matchedInv.assignedDistrictId,
                districtName = matchedInv.assignedDistrictName,
                role = UserRole.DISTRICT_ADMIN,
                status = AdminStatus.ACTIVE,
                registrationDate = "Today",
                lastActivity = "Active Now"
            )

            isAuthenticated = true
            authenticatedUser = currentDistrictAdmin
            currentAdminRole = UserRole.DISTRICT_ADMIN

            auditLogs.add(0, AuditLogEntry(
                id = "aud_${System.currentTimeMillis() % 10000}",
                actingUserEmail = email,
                action = "Redeemed Invitation Code & Bound Account to ${matchedInv.assignedDistrictName}",
                targetRecord = matchedInv.assignedDistrictName,
                timestampMillis = System.currentTimeMillis()
            ))

            return true
        }
        return false
    }

    fun addAnonymousReport(
        type: ReportType,
        locationName: String,
        description: String,
        lat: Double = 14.0700,
        lng: Double = 120.6300
    ): AnonymousReport {
        val randomNum = (1000..9999).random()
        val randomAuth = (1000..9999).random()
        val newReport = AnonymousReport(
            id = "rep_${System.currentTimeMillis() % 10000}",
            referenceNumber = "#HK-$randomNum",
            accessToken = "AUTH-$randomAuth-8A2F",
            reportType = type,
            locationName = locationName,
            latitude = lat,
            longitude = lng,
            description = description,
            timestampMillis = System.currentTimeMillis(),
            status = ReportStatus.SUBMITTED,
            assignedOffice = type.targetOffice
        )
        reports.add(0, newReport)
        return newReport
    }

    fun addReportMessage(reportId: String, senderType: String, senderName: String, text: String): ReportMessage {
        val newMsg = ReportMessage(
            id = "msg_${System.currentTimeMillis() % 10000}",
            reportId = reportId,
            senderType = senderType,
            senderName = senderName,
            text = text,
            timestampMillis = System.currentTimeMillis()
        )
        reportMessages.add(newMsg)
        return newMsg
    }

    fun updateReportStatus(reportId: String, newStatus: ReportStatus, resolutionText: String = "") {
        val index = reports.indexOfFirst { it.id == reportId }
        if (index != -1) {
            val item = reports[index]
            reports[index] = item.copy(
                status = newStatus,
                resolutionDetails = resolutionText.ifBlank { item.resolutionDetails }
            )
        }
    }

    fun addSchedule(item: ScheduleItem) {
        schedules.add(item)
    }

    fun removeSchedule(id: String) {
        schedules.removeIf { it.id == id }
    }

    fun addTruck(truck: GarbageTruck) {
        trucks.add(truck)
    }

    fun removeTruck(id: String) {
        trucks.removeIf { it.id == id }
    }

    fun addRoute(route: CollectionRoute) {
        routes.add(route)
    }

    fun removeRoute(id: String) {
        routes.removeIf { it.id == id }
    }
}
