package com.example.hakot.data

import android.content.Context
import android.os.Build
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.SecureRandom
import java.time.LocalDate
import java.time.LocalTime

object HakotRepository {
    val today: LocalDate get() = LocalDate.now()

    var selectedMapDate: LocalDate by mutableStateOf(LocalDate.now())

    var currentAdminRole: UserRole by mutableStateOf(UserRole.DISTRICT_ADMIN)

    var isAuthenticated: Boolean by mutableStateOf(false)
    var authenticatedUser: AdminUserProfile? by mutableStateOf(null)

    private var currentAccessToken: String? = null
    private var currentRefreshToken: String? = null
    private var tokenExpiresAt: Long = 0L
    private var currentUserId: String? = null
    private val refreshMutex = Mutex()

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

    val invitations = mutableStateListOf<DistrictAdminInvitation>()
    val auditLogs = mutableStateListOf<AuditLogEntry>()
    val reportMessages = mutableStateListOf<ReportMessage>()
    val announcements = mutableStateListOf<Announcement>()
    val dumpsites = mutableStateListOf<Dumpsite>(
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
        )
    )

    val trucks = mutableStateListOf<GarbageTruck>(
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
        )
    )

    val routes = mutableStateListOf<CollectionRoute>(
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
                Pair(14.0712, 120.6322)
            ),
            assignedTruckPlate = "ABK-8891",
            scheduledTimeWindow = "8:00 AM - 11:30 AM",
            scheduledDate = "Today",
            destinationDumpsite = "Nasugbu MRF Facility"
        )
    )

    val reports = mutableStateListOf<AnonymousReport>()
    val schedules = mutableStateListOf<ScheduleItem>()

    init {
        generateDefaultSchedules()
    }

    private fun generateDefaultSchedules() {
        val currDate = LocalDate.now()
        schedules.clear()
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
    }

    suspend fun getValidAccessToken(context: Context): String? = refreshMutex.withLock {
        if (currentAccessToken.isNullOrBlank() || currentUserId.isNullOrBlank()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val sessionManager = SessionManager(context)
                val session = sessionManager.loadSession() ?: return@withLock null
                if (!SessionManager.isValidUuid(session.userId)) {
                    sessionManager.clearSession()
                    return@withLock null
                }
                currentAccessToken = session.accessToken
                currentRefreshToken = session.refreshToken
                tokenExpiresAt = session.expiresAt
                currentUserId = session.userId
            } else {
                return@withLock null
            }
        }

        // Check if token expires within 2 minutes
        if (System.currentTimeMillis() >= tokenExpiresAt - 120_000) {
            val tokenToRefresh = currentRefreshToken ?: return@withLock null
            val resp = SupabaseClient.refreshSession(tokenToRefresh)
            if (resp != null && resp.userId == currentUserId && SessionManager.isValidUuid(resp.userId)) {
                // Persist new token pair FIRST before updating in-memory state
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val sessionManager = SessionManager(context)
                    val session = sessionManager.loadSession()
                    val userEmail = session?.email ?: ""
                    if (userEmail.isBlank()) {
                        sessionManager.clearSession()
                        currentAccessToken = null
                        currentRefreshToken = null
                        tokenExpiresAt = 0L
                        currentUserId = null
                        isAuthenticated = false
                        authenticatedUser = null
                        return@withLock null
                    }
                    val saved = sessionManager.saveSession(UserSession(resp.accessToken, resp.refreshToken, resp.expiresAt, resp.userId, userEmail))
                    if (!saved) {
                        sessionManager.clearSession()
                        currentAccessToken = null
                        currentRefreshToken = null
                        tokenExpiresAt = 0L
                        currentUserId = null
                        isAuthenticated = false
                        authenticatedUser = null
                        return@withLock null
                    }
                }
                currentAccessToken = resp.accessToken
                currentRefreshToken = resp.refreshToken
                tokenExpiresAt = resp.expiresAt
            } else {
                // Refresh failed or user ID mismatch: clear session safely
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    SessionManager(context).clearSession()
                }
                currentAccessToken = null
                currentRefreshToken = null
                tokenExpiresAt = 0L
                currentUserId = null
                isAuthenticated = false
                authenticatedUser = null
                return@withLock null
            }
        }
        return@withLock currentAccessToken
    }

    suspend fun login(context: Context, emailInput: String, passwordInput: String): Boolean {
        // Ensure failed login cannot leave previous user authenticated state active
        logout(context)

        val cleanEmail = emailInput.trim()
        val authResp = SupabaseClient.signIn(cleanEmail, passwordInput) ?: return false

        val accessToken = authResp.accessToken
        val userId = authResp.userId

        if (!SessionManager.isValidUuid(userId)) return false

        val profileJson = SupabaseClient.fetchProfile(userId, accessToken) ?: return false

        val profileId = profileJson.optString("id")
        if (profileId != userId || !SessionManager.isValidUuid(profileId)) {
            return false
        }

        val roleStr = profileJson.optString("role").lowercase()
        val statusStr = profileJson.optString("account_status").lowercase()

        if (statusStr != "active") return false
        val role = when (roleStr) {
            "district_admin" -> UserRole.DISTRICT_ADMIN
            "driver" -> UserRole.DRIVER
            else -> return false
        }

        val fullName = profileJson.optString("full_name")
        if (fullName.isBlank()) return false

        // Role-aware validation: district_admin requires municipality_id and district_id; driver may have nullable district_id
        val municipalityId = profileJson.optString("municipality_id")
        val districtId = profileJson.optString("district_id")

        if (role == UserRole.DISTRICT_ADMIN && (municipalityId.isBlank() || districtId.isBlank())) {
            return false
        }

        val profile = AdminUserProfile(
            userId = userId,
            fullName = fullName,
            email = cleanEmail,
            municipalityOrgId = municipalityId.ifBlank { activeOrganization.id },
            municipalityName = activeOrganization.name,
            districtId = districtId.ifBlank { "dist_1" },
            districtName = "District Assigned Scope",
            role = role,
            status = AdminStatus.ACTIVE,
            registrationDate = profileJson.optString("created_at", "Today"),
            lastActivity = "Active Now"
        )

        // Save encrypted session FIRST before establishing authenticated state
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val sessionManager = SessionManager(context)
            val saved = sessionManager.saveSession(UserSession(accessToken, authResp.refreshToken, authResp.expiresAt, userId, cleanEmail))
            if (!saved) {
                sessionManager.clearSession()
                return false
            }
        }

        currentAccessToken = accessToken
        currentRefreshToken = authResp.refreshToken
        tokenExpiresAt = authResp.expiresAt
        currentUserId = userId

        isAuthenticated = true
        authenticatedUser = profile
        currentAdminRole = role
        if (role == UserRole.DISTRICT_ADMIN) {
            currentDistrictAdmin = profile
        }

        auditLogs.add(0, AuditLogEntry(
            id = "aud_${System.currentTimeMillis() % 10000}",
            actingUserEmail = profile.email,
            action = "Supabase Auth Login Success (${role.label})",
            targetRecord = profile.districtName,
            timestampMillis = System.currentTimeMillis()
        ))

        return true
    }

    suspend fun restoreSession(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
        val sessionManager = SessionManager(context)
        val session = sessionManager.loadSession() ?: return false

        if (!SessionManager.isValidUuid(session.userId) || session.email.isBlank()) {
            sessionManager.clearSession()
            return false
        }

        val token = getValidAccessToken(context) ?: return false
        val profileJson = SupabaseClient.fetchProfile(session.userId, token) ?: return false

        val profileId = profileJson.optString("id")
        if (profileId != session.userId || !SessionManager.isValidUuid(profileId)) {
            sessionManager.clearSession()
            return false
        }

        val roleStr = profileJson.optString("role").lowercase()
        val statusStr = profileJson.optString("account_status").lowercase()

        if (statusStr != "active") {
            sessionManager.clearSession()
            return false
        }

        val role = when (roleStr) {
            "district_admin" -> UserRole.DISTRICT_ADMIN
            "driver" -> UserRole.DRIVER
            else -> {
                sessionManager.clearSession()
                return false
            }
        }

        val fullName = profileJson.optString("full_name")
        if (fullName.isBlank()) {
            sessionManager.clearSession()
            return false
        }

        val municipalityId = profileJson.optString("municipality_id")
        val districtId = profileJson.optString("district_id")

        if (role == UserRole.DISTRICT_ADMIN && (municipalityId.isBlank() || districtId.isBlank())) {
            sessionManager.clearSession()
            return false
        }

        val profile = AdminUserProfile(
            userId = session.userId,
            fullName = fullName,
            email = session.email,
            municipalityOrgId = municipalityId.ifBlank { activeOrganization.id },
            municipalityName = activeOrganization.name,
            districtId = districtId.ifBlank { "dist_1" },
            districtName = "District Assigned Scope",
            role = role,
            status = AdminStatus.ACTIVE,
            registrationDate = profileJson.optString("created_at", "Restored"),
            lastActivity = "Active Now"
        )

        currentUserId = session.userId
        isAuthenticated = true
        authenticatedUser = profile
        currentAdminRole = role
        if (role == UserRole.DISTRICT_ADMIN) {
            currentDistrictAdmin = profile
        }

        return true
    }

    suspend fun logout(context: Context) {
        val token = currentAccessToken
        if (!token.isNullOrBlank()) {
            try {
                SupabaseClient.signOutRemote(token)
            } catch (_: Exception) {
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            SessionManager(context).clearSession()
        }

        currentAccessToken = null
        currentRefreshToken = null
        tokenExpiresAt = 0L
        currentUserId = null

        isAuthenticated = false
        authenticatedUser = null
    }

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
        return newInv
    }

    // Invitation redemption updated: NEVER establishes isAuthenticated = true or creates an authenticated user by generating a fake user ID.
    // Invitation redemption is strictly an administrative placeholder / backend RPC task and does not bypass Supabase Auth.
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
