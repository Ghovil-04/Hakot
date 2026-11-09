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
import com.example.hakot.model.CollectionProgressRecord
import com.example.hakot.model.CollectionRoute
import com.example.hakot.model.DistrictAdminInvitation
import com.example.hakot.model.Dumpsite
import com.example.hakot.model.GarbageTruck
import com.example.hakot.model.OrganizationConfig
import com.example.hakot.model.OrganizationType
import com.example.hakot.model.ReportMessage
import com.example.hakot.model.ReportStatus
import com.example.hakot.model.ReportType
import com.example.hakot.model.RouteSegmentJunction
import com.example.hakot.model.ScheduleItem
import com.example.hakot.model.StreetSegment
import com.example.hakot.model.TruckGpsTrailPoint
import com.example.hakot.model.UserRole
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.security.SecureRandom
import java.time.LocalDate

object HakotRepository {
    val today: LocalDate get() = LocalDate.now()

    var selectedMapDate: LocalDate by mutableStateOf(LocalDate.now())

    // NASUGBU GIS DATA FROM SUPABASE
    var barangayGeoJson: String? by mutableStateOf(null)
    var roadGeoJson: String? by mutableStateOf(null)

    var isGisLoading: Boolean by mutableStateOf(false)
    var gisLoadError: String by mutableStateOf("")

    var gisBarangayCount: Int by mutableStateOf(0)
    var gisRoadCount: Int by mutableStateOf(0)

    var isGisLoaded: Boolean by mutableStateOf(false)

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

    // Database tables - Starting clean for real administration entries
    val streetSegments = mutableStateListOf<StreetSegment>()
    val routeJunctions = mutableStateListOf<RouteSegmentJunction>()
    val collectionProgressRecords = mutableStateListOf<CollectionProgressRecord>()
    val truckGpsTrails = mutableStateListOf<TruckGpsTrailPoint>()

    val adminProfiles = mutableStateListOf<AdminUserProfile>()
    val invitations = mutableStateListOf<DistrictAdminInvitation>()
    val auditLogs = mutableStateListOf<AuditLogEntry>()
    val reportMessages = mutableStateListOf<ReportMessage>()
    val announcements = mutableStateListOf<Announcement>()
    val dumpsites = mutableStateListOf<Dumpsite>()
    val trucks = mutableStateListOf<GarbageTruck>()
    val routes = mutableStateListOf<CollectionRoute>()
    val reports = mutableStateListOf<AnonymousReport>()
    val schedules = mutableStateListOf<ScheduleItem>()

    init {
        // Ready for live database queries
    }

    // ---------------------------------------------------------
    // LOAD NASUGBU GIS DATA FROM SUPABASE
    // ---------------------------------------------------------

    suspend fun loadNasugbuGisData(): Boolean {
        if (isGisLoading) return false

        isGisLoading = true
        gisLoadError = ""

        try {
            val result = SupabaseClient.fetchNasugbuMapGeoJson()

            if (result == null) {
                gisLoadError = SupabaseClient.gisLoadError
                return false
            }

            val barangays = JSONObject(result.first)
            val roads = JSONObject(result.second)

            val barangayFeatures = barangays.getJSONArray("features")
            val roadFeatures = roads.getJSONArray("features")

            // Save the actual GeoJSON
            barangayGeoJson = result.first
            roadGeoJson = result.second

            // Save the actual feature counts
            gisBarangayCount = barangayFeatures.length()
            gisRoadCount = roadFeatures.length()

            isGisLoaded = true
            return true

        } catch (e: Exception) {
            gisLoadError = e.message ?: "Failed to process GIS data"
            return false

        } finally {
            isGisLoading = false
        }
    }

    // Validates whether selected street segment IDs connect consecutively
    fun validateRouteConnectivity(segmentIds: List<String>): Pair<Boolean, String> {
        if (segmentIds.size <= 1) return Pair(true, "Route geometry validated")

        for (i in 0 until segmentIds.size - 1) {
            val currSeg = streetSegments.find { it.segmentId == segmentIds[i] }
            val nextSeg = streetSegments.find { it.segmentId == segmentIds[i + 1] }

            if (currSeg != null && nextSeg != null) {
                val isConnected = currSeg.connectedSegmentIds.contains(nextSeg.segmentId) ||
                        nextSeg.connectedSegmentIds.contains(currSeg.segmentId)
                if (!isConnected) {
                    return Pair(false, "Disconnected segment warning: '${currSeg.streetName}' does not connect to '${nextSeg.streetName}'")
                }
            }
        }
        return Pair(true, "All consecutive street segments connected properly")
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

        // Asynchronously sync new report to Supabase PostgREST waste_reports table
        CoroutineScope(Dispatchers.IO).launch {
            val json = JSONObject().apply {
                put("id", newReport.id)
                put("reference_number", newReport.referenceNumber)
                put("access_token", newReport.accessToken)
                put("report_type", newReport.reportType.name)
                put("location_name", newReport.locationName)
                put("description", newReport.description)
                put("status", newReport.status.name)
                put("assigned_office", newReport.assignedOffice)
            }
            SupabaseClient.insertTableRecord("waste_reports", json)
        }

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
