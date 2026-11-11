package com.example.hakot.data

import android.content.Context
import android.util.Log
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.hakot.model.AccountApplication
import com.example.hakot.model.AccountInvitation
import com.example.hakot.model.AdminStatus
import com.example.hakot.model.AdminUserProfile
import com.example.hakot.model.Announcement
import com.example.hakot.model.AnonymousReport
import com.example.hakot.model.ApplicationStatus
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
import org.json.JSONObject
import java.security.SecureRandom
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

object HakotRepository {
    val today: LocalDate get() = LocalDate.now()

    var selectedMapDate: LocalDate by mutableStateOf(LocalDate.now())

    var currentAdminRole: UserRole by mutableStateOf(UserRole.MUNICIPAL_ADMIN)

    var isAuthenticated: Boolean by mutableStateOf(false)
    var authenticatedUser: AdminUserProfile? by mutableStateOf(null)
    var lastLoginError: String? by mutableStateOf(null)

    private var currentAccessToken: String? = null
    private var currentRefreshToken: String? = null
    private var tokenExpiresAt: Long = 0L
    private var currentUserId: String? = null
    private val refreshMutex = Mutex()

    var activeOrganization = OrganizationConfig(
        organizationKey = "org_nasugbu_lgu",
        municipalityId = "cb394eb7-a8b7-45f2-a314-3926baeab31f",
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
        municipalityOrgId = "cb394eb7-a8b7-45f2-a314-3926baeab31f",
        municipalityName = "Nasugbu, Batangas",
        districtId = "dist_1",
        districtName = "Nasugbu Municipal Scope",
        role = UserRole.MUNICIPAL_ADMIN,
        status = AdminStatus.ACTIVE,
        registrationDate = "Feb 14, 2026",
        lastActivity = "Active Now",
        positionTitle = "Municipal Sanitation Head",
        officeDepartment = "Solid Waste Management Office"
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
            municipalityOrgId = "cb394eb7-a8b7-45f2-a314-3926baeab31f",
            municipalityName = "Nasugbu, Batangas",
            districtId = "dist_1",
            districtName = "Nasugbu Scope",
            role = UserRole.MUNICIPAL_ADMIN,
            status = AdminStatus.ACTIVE,
            registrationDate = "Feb 14, 2026",
            lastActivity = "Active Now",
            positionTitle = "Municipal Sanitation Head",
            officeDepartment = "Solid Waste Management Office"
        ),
        AdminUserProfile(
            userId = "usr_staff_102",
            fullName = "Engr. Marco Valenzuela",
            email = "m.valenzuela@nasugbu.gov.ph",
            municipalityOrgId = "cb394eb7-a8b7-45f2-a314-3926baeab31f",
            municipalityName = "Nasugbu, Batangas",
            districtId = "dist_1",
            districtName = "Poblacion Sector",
            role = UserRole.MUNICIPAL_STAFF,
            status = AdminStatus.ACTIVE,
            registrationDate = "Mar 01, 2026",
            lastActivity = "10 mins ago",
            positionTitle = "Field Operations Inspector",
            officeDepartment = "Sanitation Enforcement Unit"
        )
    )

    val accountInvitations = mutableStateListOf<AccountInvitation>(
        AccountInvitation(
            invitationId = "inv_801",
            codeHash = "HAKOT-D7K9-M2QP-X8RN",
            intendedEmail = "sanitation.head@nasugbu.gov.ph",
            intendedName = "Engr. Marco Valenzuela",
            contactNumber = "(043) 723-9911",
            municipalityId = "cb394eb7-a8b7-45f2-a314-3926baeab31f",
            municipalityName = "Nasugbu, Batangas",
            assignedRole = UserRole.MUNICIPAL_ADMIN,
            positionTitle = "Municipal Sanitation Head",
            officeDepartment = "Solid Waste Management Office",
            createdAtMillis = System.currentTimeMillis() - 86400000 * 2,
            expiresAtMillis = System.currentTimeMillis() + 86400000 * 5,
            status = AdminStatus.PENDING_INVITATION
        )
    )

    val invitations: List<AccountInvitation> get() = accountInvitations

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
            driverName = "Unassigned Driver",
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
            val refreshResult = SupabaseClient.refreshSession(tokenToRefresh)
            val resp = refreshResult.tokenResponse
            if (resp != null) {
                if (resp.userId != currentUserId || !SessionManager.isValidUuid(resp.userId)) {
                    Log.e("HAKOT_AUTH", "Token refresh user ID mismatch. Identity integrity failure.")
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
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val sessionManager = SessionManager(context)
                    val session = sessionManager.loadSession()
                    val userEmail = session?.email ?: ""
                    if (userEmail.isNotBlank()) {
                        val saved = sessionManager.saveSession(UserSession(resp.accessToken, resp.refreshToken, resp.expiresAt, resp.userId, userEmail))
                        if (!saved) {
                            Log.e("HAKOT_AUTH", "Failed to save refreshed session locally. Aborting in-memory update.")
                            return@withLock if (System.currentTimeMillis() < tokenExpiresAt) currentAccessToken else null
                        }
                    } else {
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
                if (refreshResult.isPermanentFailure) {
                    // Refresh failed permanently (HTTP 400/401/403): clear session safely
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
                } else {
                    // Temporary transport / network failure: preserve encrypted saved session, do not erase refresh token
                    return@withLock if (System.currentTimeMillis() < tokenExpiresAt) currentAccessToken else null
                }
            }
        }
        return@withLock currentAccessToken
    }

    suspend fun login(
        context: Context,
        emailInput: String,
        passwordInput: String,
        targetRole: UserRole
    ): Boolean {
        lastLoginError = null

        val cleanEmail = emailInput.trim()
        val authResp = SupabaseClient.signIn(cleanEmail, passwordInput)
        if (authResp == null) {
            lastLoginError = "Authentication failed: Invalid email or password."
            return false
        }

        val accessToken = authResp.accessToken
        val userId = authResp.userId

        if (!SessionManager.isValidUuid(userId)) {
            lastLoginError = "Authentication failed: Invalid user identifier."
            return false
        }

        val profileJson = SupabaseClient.fetchProfile(userId, accessToken)
        if (profileJson == null) {
            lastLoginError = "Profile retrieval failed: Unable to load profile from database."
            return false
        }

        val profileId = profileJson.optString("id")
        if (profileId != userId || !SessionManager.isValidUuid(profileId)) {
            lastLoginError = "Profile validation failed: User ID mismatch."
            return false
        }

        val roleStr = profileJson.optString("role").lowercase()
        val statusStr = profileJson.optString("account_status").lowercase()

        if (statusStr != "active") {
            lastLoginError = "Login failed: Account is inactive or pending ($statusStr)."
            return false
        }
        val role = when (roleStr) {
            "system_owner" -> UserRole.SYSTEM_OWNER
            "municipal_admin" -> UserRole.MUNICIPAL_ADMIN
            "district_admin" -> UserRole.DISTRICT_ADMIN
            "company_admin" -> UserRole.COMPANY_ADMIN
            "municipal_staff" -> UserRole.MUNICIPAL_STAFF
            "driver" -> UserRole.DRIVER
            else -> {
                lastLoginError = "Login failed: Unrecognized role ($roleStr)."
                return false
            }
        }

        // Validate portal authorization before saving session or authenticating
        if (targetRole == UserRole.DRIVER && role != UserRole.DRIVER) {
            lastLoginError = "Access denied: Account is registered as an Administrator. Please use the Admin Portal."
            return false
        }
        if (targetRole != UserRole.DRIVER && role == UserRole.DRIVER) {
            lastLoginError = "Access denied: Account is registered as a Trucker. Please use the Trucker Portal."
            return false
        }

        val fullName = profileJson.optString("full_name")
        if (fullName.isBlank()) {
            lastLoginError = "Profile validation failed: Full name is missing."
            return false
        }

        val municipalityId = if (profileJson.isNull("municipality_id")) "" else profileJson.optString("municipality_id", "").let { if (it.equals("null", ignoreCase = true)) "" else it }
        val districtId = if (profileJson.isNull("district_id")) "" else profileJson.optString("district_id", "").let { if (it.equals("null", ignoreCase = true)) "" else it }
        val companyId = if (profileJson.isNull("company_id")) "" else profileJson.optString("company_id", "").let { if (it.equals("null", ignoreCase = true)) "" else it }

        when (role) {
            UserRole.SYSTEM_OWNER,
            UserRole.MUNICIPAL_STAFF,
            UserRole.DRIVER -> {
                // Supported roles without additional scope constraints
            }
            UserRole.MUNICIPAL_ADMIN -> {
                if (!SessionManager.isValidUuid(municipalityId)) {
                    lastLoginError = "Access denied: Municipal Admin account requires a valid municipality UUID."
                    return false
                }
            }
            UserRole.DISTRICT_ADMIN -> {
                if (!SessionManager.isValidUuid(municipalityId) || !SessionManager.isValidUuid(districtId)) {
                    lastLoginError = "Access denied: District Admin account requires valid municipality and district UUIDs."
                    return false
                }
            }
            UserRole.COMPANY_ADMIN -> {
                if (!SessionManager.isValidUuid(municipalityId) || !SessionManager.isValidUuid(companyId)) {
                    lastLoginError = "Access denied: Company Admin account requires valid municipality and company UUIDs."
                    return false
                }
            }
            else -> {
                lastLoginError = "Access denied: Role is not an authenticated account."
                return false
            }
        }

        val profile = AdminUserProfile(
            userId = userId,
            fullName = fullName,
            email = cleanEmail,
            municipalityOrgId = municipalityId,
            municipalityName = if (municipalityId == activeOrganization.municipalityId) activeOrganization.name else "",
            districtId = districtId,
            districtName = "",
            companyId = companyId,
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
                lastLoginError = "Session storage failed: Unable to save secure session locally."
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
        if (role == UserRole.MUNICIPAL_ADMIN || role == UserRole.SYSTEM_OWNER) {
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

        currentAccessToken = session.accessToken
        currentRefreshToken = session.refreshToken
        tokenExpiresAt = session.expiresAt
        currentUserId = session.userId

        val token = getValidAccessToken(context)
        if (token.isNullOrBlank()) {
            currentAccessToken = null
            currentRefreshToken = null
            tokenExpiresAt = 0L
            currentUserId = null
            isAuthenticated = false
            authenticatedUser = null
            return false
        }
        val profileRes = SupabaseClient.fetchProfileResult(session.userId, token)
        val profileJson = profileRes.profile
        if (profileJson == null) {
            if (profileRes.isPermanentFailure) {
                sessionManager.clearSession()
            }
            currentAccessToken = null
            currentRefreshToken = null
            tokenExpiresAt = 0L
            currentUserId = null
            isAuthenticated = false
            authenticatedUser = null
            return false
        }

        val profileId = profileJson.optString("id")
        if (profileId != session.userId || !SessionManager.isValidUuid(profileId)) {
            sessionManager.clearSession()
            currentAccessToken = null
            currentRefreshToken = null
            tokenExpiresAt = 0L
            currentUserId = null
            isAuthenticated = false
            authenticatedUser = null
            return false
        }

        val roleStr = profileJson.optString("role").lowercase()
        val statusStr = profileJson.optString("account_status").lowercase()

        if (statusStr != "active") {
            sessionManager.clearSession()
            currentAccessToken = null
            currentRefreshToken = null
            tokenExpiresAt = 0L
            currentUserId = null
            isAuthenticated = false
            authenticatedUser = null
            return false
        }

        val role = when (roleStr) {
            "system_owner" -> UserRole.SYSTEM_OWNER
            "municipal_admin" -> UserRole.MUNICIPAL_ADMIN
            "district_admin" -> UserRole.DISTRICT_ADMIN
            "company_admin" -> UserRole.COMPANY_ADMIN
            "municipal_staff" -> UserRole.MUNICIPAL_STAFF
            "driver" -> UserRole.DRIVER
            else -> {
                sessionManager.clearSession()
                currentAccessToken = null
                currentRefreshToken = null
                tokenExpiresAt = 0L
                currentUserId = null
                isAuthenticated = false
                authenticatedUser = null
                return false
            }
        }

        val fullName = profileJson.optString("full_name")
        if (fullName.isBlank()) {
            sessionManager.clearSession()
            currentAccessToken = null
            currentRefreshToken = null
            tokenExpiresAt = 0L
            currentUserId = null
            isAuthenticated = false
            authenticatedUser = null
            return false
        }

        val municipalityId = if (profileJson.isNull("municipality_id")) "" else profileJson.optString("municipality_id", "").let { if (it.equals("null", ignoreCase = true)) "" else it }
        val districtId = if (profileJson.isNull("district_id")) "" else profileJson.optString("district_id", "").let { if (it.equals("null", ignoreCase = true)) "" else it }
        val companyId = if (profileJson.isNull("company_id")) "" else profileJson.optString("company_id", "").let { if (it.equals("null", ignoreCase = true)) "" else it }

        when (role) {
            UserRole.SYSTEM_OWNER,
            UserRole.MUNICIPAL_STAFF,
            UserRole.DRIVER -> {
                // Supported roles without additional scope constraints
            }
            UserRole.MUNICIPAL_ADMIN -> {
                if (!SessionManager.isValidUuid(municipalityId)) {
                    sessionManager.clearSession()
                    currentAccessToken = null
                    currentRefreshToken = null
                    tokenExpiresAt = 0L
                    currentUserId = null
                    isAuthenticated = false
                    authenticatedUser = null
                    return false
                }
            }
            UserRole.DISTRICT_ADMIN -> {
                if (!SessionManager.isValidUuid(municipalityId) || !SessionManager.isValidUuid(districtId)) {
                    sessionManager.clearSession()
                    currentAccessToken = null
                    currentRefreshToken = null
                    tokenExpiresAt = 0L
                    currentUserId = null
                    isAuthenticated = false
                    authenticatedUser = null
                    return false
                }
            }
            UserRole.COMPANY_ADMIN -> {
                if (!SessionManager.isValidUuid(municipalityId) || !SessionManager.isValidUuid(companyId)) {
                    sessionManager.clearSession()
                    currentAccessToken = null
                    currentRefreshToken = null
                    tokenExpiresAt = 0L
                    currentUserId = null
                    isAuthenticated = false
                    authenticatedUser = null
                    return false
                }
            }
            else -> {
                sessionManager.clearSession()
                currentAccessToken = null
                currentRefreshToken = null
                tokenExpiresAt = 0L
                currentUserId = null
                isAuthenticated = false
                authenticatedUser = null
                return false
            }
        }

        val profile = AdminUserProfile(
            userId = session.userId,
            fullName = fullName,
            email = session.email,
            municipalityOrgId = municipalityId,
            municipalityName = if (municipalityId == activeOrganization.municipalityId) activeOrganization.name else "",
            districtId = districtId,
            districtName = "",
            companyId = companyId,
            role = role,
            status = AdminStatus.ACTIVE,
            registrationDate = profileJson.optString("created_at", "Restored"),
            lastActivity = "Active Now"
        )

        currentUserId = session.userId
        isAuthenticated = true
        authenticatedUser = profile
        currentAdminRole = role
        if (role == UserRole.MUNICIPAL_ADMIN || role == UserRole.SYSTEM_OWNER) {
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

    fun createAccountInvitation(
        intendedName: String,
        intendedEmail: String,
        contactNumber: String,
        municipalityId: String,
        municipalityName: String,
        assignedRole: UserRole,
        positionTitle: String,
        officeDepartment: String
    ): AccountInvitation {
        val invId = "inv_${System.currentTimeMillis() % 10000}"
        val dummyHash = "hash_${System.currentTimeMillis() % 10000}"
        val newInv = AccountInvitation(
            invitationId = invId,
            codeHash = dummyHash,
            intendedEmail = intendedEmail.trim(),
            intendedName = intendedName.trim(),
            contactNumber = contactNumber.trim(),
            municipalityId = municipalityId,
            municipalityName = municipalityName,
            assignedRole = assignedRole,
            positionTitle = positionTitle.trim(),
            officeDepartment = officeDepartment.trim(),
            createdAtMillis = System.currentTimeMillis(),
            expiresAtMillis = System.currentTimeMillis() + 86400000 * 7,
            status = AdminStatus.PENDING_INVITATION
        )
        accountInvitations.add(0, newInv)
        return newInv
    }

    fun revokeAccountInvitation(invitationId: String): Boolean {
        val inv = accountInvitations.find { it.invitationId == invitationId }
        if (inv != null) {
            inv.status = AdminStatus.REVOKED
            return true
        }
        return false
    }

    fun resendAccountInvitation(invitationId: String): Pair<Boolean, String> {
        val inv = accountInvitations.find { it.invitationId == invitationId }
        if (inv != null && inv.status == AdminStatus.PENDING_INVITATION) {
            return Pair(true, "Invitation email queued for resend to ${inv.intendedEmail} (Backend Contract Pending)")
        }
        return Pair(false, "Invitation cannot be resent")
    }

    fun suspendAccount(userId: String): Boolean {
        val prof = adminProfiles.find { it.userId == userId }
        if (prof != null) {
            prof.status = AdminStatus.SUSPENDED
            return true
        }
        return false
    }

    fun reactivateAccount(userId: String): Boolean {
        val prof = adminProfiles.find { it.userId == userId }
        if (prof != null) {
            prof.status = AdminStatus.ACTIVE
            return true
        }
        return false
    }

    fun revokeAccount(userId: String): Boolean {
        val prof = adminProfiles.find { it.userId == userId }
        if (prof != null) {
            prof.status = AdminStatus.REVOKED
            return true
        }
        return false
    }

    fun generateDistrictInvitation(
        districtName: String,
        intendedAdminName: String,
        intendedEmail: String
    ): DistrictAdminInvitation {
        return createAccountInvitation(
            intendedName = intendedAdminName,
            intendedEmail = intendedEmail,
            contactNumber = "(043) 723-HAKOT",
            municipalityId = activeOrganization.municipalityId,
            municipalityName = activeOrganization.name,
            assignedRole = UserRole.MUNICIPAL_ADMIN,
            positionTitle = "Municipal Sanitation Administrator",
            officeDepartment = "Solid Waste Management Office"
        )
    }

    fun redeemInvitation(code: String, email: String): Boolean {
        val cleanedCode = code.trim().uppercase()
        val matchedInv = accountInvitations.find {
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

    suspend fun submitAccountApplication(
        fullName: String,
        email: String,
        contactNumber: String,
        municipalityId: String,
        officeDepartment: String,
        officialPosition: String,
        requestedRole: UserRole,
        companyName: String = ""
    ): Pair<Boolean, String> {
        Log.d("HAKOT_SUBMIT_REPOSITORY", "submitAccountApplication called for requested role: ${requestedRole.name}")
        // Enforce role safety: System Owner cannot be requested via self-registration (Explicit Validation Failure)
        if (requestedRole == UserRole.SYSTEM_OWNER) {
            Log.w("HAKOT_SUBMIT_REPOSITORY", "Registration rejected: System Owner requested.")
            return Pair(false, "Registration for Platform System Owner is restricted and cannot be requested.")
        }

        val payload = JSONObject().apply {
            put("full_name", fullName)
            put("email", email)
            put("contact_number", contactNumber)
            put("municipality_id", municipalityId.ifBlank { activeOrganization.municipalityId })
            put("office_department", officeDepartment)
            put("official_position", officialPosition)
            put("requested_role", requestedRole.name)
            if (requestedRole == UserRole.COMPANY_ADMIN && companyName.isNotBlank()) {
                put("company_name", companyName.trim())
            }
            put("status", "PENDING")
        }

        Log.d("HAKOT_SUBMIT_REPOSITORY", "Calling SupabaseClient.insertPublicTableRecord for account_applications")
        val result = SupabaseClient.insertPublicTableRecord("account_applications", payload)
        Log.d("HAKOT_SUBMIT_REPOSITORY", "SupabaseClient.insertPublicTableRecord returned: isSuccess=${result.isSuccess}, statusCode=${result.statusCode}, errorMessage=${result.errorMessage}")
        return if (result.isSuccess) {
            Pair(true, "Account application submitted successfully! Pending System Owner review.")
        } else {
            Log.e("HAKOT_APPLICATION_SUBMISSION", "Failed to submit account application. Status code: ${result.statusCode}, Error: ${result.errorMessage}")
            // Return safe user-facing error message without exposing raw backend details
            Pair(false, "Unable to submit your application at this time. Please check your connection and try again.")
        }
    }

    val accountApplications = mutableStateListOf<AccountApplication>()

    suspend fun fetchAccountApplications(): Pair<Boolean, String> {
        if (!isAuthenticated || currentAdminRole != UserRole.SYSTEM_OWNER) {
            return Pair(false, "Access Denied: Only verified System Owners can review applications.")
        }
        val token = currentAccessToken ?: return Pair(false, "Authentication token missing or expired.")

        val fetchResult = SupabaseClient.invokeManagementEdgeFunction("LIST_PENDING", null, token)
        if (fetchResult.isSuccess && fetchResult.data != null) {
            val jsonArray = fetchResult.data
            val list = mutableListOf<AccountApplication>()
            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(i)
                val id = item.optString("id", "")
                val fullName = item.optString("full_name", "")
                val email = item.optString("email", "")
                val contactNumber = item.optString("contact_number", "")
                val municipality = item.optString("municipality_id", "Nasugbu, Batangas")
                val office = item.optString("office_department", "")
                val position = item.optString("official_position", "")
                val roleStr = item.optString("requested_role", "").lowercase().trim()
                val statusStr = item.optString("status", "").uppercase().trim()

                // Map only the 5 permitted application roles: municipal_admin, municipal_staff, company_admin, district_admin, driver
                // Never accept system_owner as an application role. Do not silently convert unknown roles into MUNICIPAL_STAFF.
                val role = when (roleStr) {
                    "municipal_admin" -> UserRole.MUNICIPAL_ADMIN
                    "municipal_staff" -> UserRole.MUNICIPAL_STAFF
                    "company_admin" -> UserRole.COMPANY_ADMIN
                    "district_admin" -> UserRole.DISTRICT_ADMIN
                    "driver" -> UserRole.DRIVER
                    else -> {
                        Log.w("HAKOT_APPLICATIONS", "Skipping application ID $id: Unrecognized or prohibited requested_role '$roleStr'")
                        continue
                    }
                }

                // Do not silently convert unknown statuses into PENDING.
                val status = when (statusStr) {
                    "PENDING" -> ApplicationStatus.PENDING
                    "APPROVED" -> ApplicationStatus.APPROVED
                    "REJECTED" -> ApplicationStatus.REJECTED
                    else -> {
                        Log.w("HAKOT_APPLICATIONS", "Skipping application ID $id: Unrecognized status '$statusStr'")
                        continue
                    }
                }

                val submittedAtStr = item.optString("submitted_at", "")
                val createdAtMillis = if (submittedAtStr.isNotBlank()) {
                    try {
                        Instant.parse(submittedAtStr).toEpochMilli()
                    } catch (e: Exception) {
                        Log.e("HAKOT_APPLICATIONS", "Failed to parse submitted_at timestamp '$submittedAtStr': ${e.message}")
                        continue // Skip malformed record rather than silently substituting current time
                    }
                } else {
                    Log.e("HAKOT_APPLICATIONS", "Missing submitted_at timestamp for application ID $id")
                    continue
                }

                list.add(
                    AccountApplication(
                        applicationId = id,
                        fullName = fullName,
                        email = email,
                        contactNumber = contactNumber,
                        municipality = municipality,
                        officeDepartment = office,
                        officialPosition = position,
                        requestedRole = role,
                        companyName = "",
                        status = status,
                        createdAtMillis = createdAtMillis
                    )
                )
            }
            // Do not clear the currently displayed application list until the new response has been successfully parsed.
            accountApplications.clear()
            accountApplications.addAll(list)
            return Pair(true, "Fetched ${list.size} pending account applications successfully.")
        } else {
            val err = fetchResult.errorMessage ?: "Network or authorization error"
            Log.e("HAKOT_APPLICATIONS", "Failed to fetch applications: $err (Code: ${fetchResult.statusCode})")
            return Pair(false, "Failed to fetch applications: $err")
        }
    }

    suspend fun updateAccountApplicationStatus(
        applicationId: String,
        newStatus: ApplicationStatus
    ): Pair<Boolean, String> {
        if (!isAuthenticated || currentAdminRole != UserRole.SYSTEM_OWNER) {
            return Pair(false, "Access Denied: Only verified System Owners can update application status.")
        }
        if (newStatus != ApplicationStatus.APPROVED && newStatus != ApplicationStatus.REJECTED) {
            return Pair(false, "Invalid status transition. Only APPROVED or REJECTED are permitted.")
        }
        val token = currentAccessToken ?: return Pair(false, "Authentication token missing or expired.")

        val action = if (newStatus == ApplicationStatus.APPROVED) "APPROVE" else "REJECT"
        val result = SupabaseClient.invokeManagementEdgeFunction(action, applicationId, token)

        return if (result.isSuccess) {
            accountApplications.removeIf { it.applicationId == applicationId }
            Pair(true, "Application updated to ${newStatus.name} successfully.")
        } else {
            Pair(false, "Failed to update application status: ${result.errorMessage ?: "Server-side processing error"}")
        }
    }

    suspend fun addAnonymousReport(
        type: ReportType,
        locationName: String,
        description: String,
        lat: Double? = null,
        lng: Double? = null
    ): Pair<Boolean, String> {
        val uniqueSuffix = UUID.randomUUID().toString().substring(0, 8).uppercase()
        val refCode = "HK-$uniqueSuffix"

        val fullDescription = if (locationName.isNotBlank() && description.isNotBlank()) {
            "Location: $locationName - $description"
        } else if (locationName.isNotBlank()) {
            "Location: $locationName"
        } else {
            description
        }

        val payload = JSONObject().apply {
            put("reference_code", refCode)
            put("category", type.title)
            put("description", fullDescription)
            if (lat != null && lng != null) {
                put("location", "SRID=4326;POINT($lng $lat)")
            }
            put("status", "submitted")
        }

        val result = SupabaseClient.insertPublicTableRecord("waste_reports", payload)
        if (result.isSuccess) {
            val newReport = AnonymousReport(
                id = "rep_${System.currentTimeMillis() % 10000}",
                referenceNumber = "#$refCode",
                reportType = type,
                locationName = locationName,
                latitude = lat,
                longitude = lng,
                description = description,
                timestampMillis = System.currentTimeMillis(),
                status = ReportStatus.SUBMITTED
            )
            reports.add(0, newReport)
            return Pair(true, "Report submitted successfully! Reference Code: #$refCode")
        } else {
            return Pair(false, "Failed to submit report to database: ${result.errorMessage}")
        }
    }

    suspend fun fetchAnonymousReportsFromSupabase(): Boolean {
        val fetchResult = SupabaseClient.fetchPublicTableRecords("waste_reports")
        if (fetchResult.isSuccess && fetchResult.data != null) {
            val jsonArray = fetchResult.data
            val fetchedList = mutableListOf<AnonymousReport>()
            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(i)
                val refCode = item.optString("reference_code", "HK-0000")
                val categoryStr = item.optString("category", "General")
                val desc = item.optString("description", "")
                val statusStr = item.optString("status", "submitted")

                val matchedType = ReportType.entries.find { it.title.equals(categoryStr, ignoreCase = true) }
                    ?: ReportType.OTHER

                val matchedStatus = ReportStatus.entries.find { it.label.equals(statusStr, ignoreCase = true) || it.name.equals(statusStr, ignoreCase = true) }
                    ?: ReportStatus.SUBMITTED

                val locName = if (desc.contains("Location: ")) {
                    desc.substringAfter("Location: ").substringBefore(" - ")
                } else if (desc.contains(" - ")) {
                    desc.substringBefore(" - ")
                } else {
                    "Nasugbu Scope"
                }

                val notesText = if (desc.contains(" - ")) desc.substringAfter(" - ") else desc

                fetchedList.add(
                    AnonymousReport(
                        id = item.optString("id", "rep_$i"),
                        referenceNumber = if (refCode.startsWith("#")) refCode else "#$refCode",
                        reportType = matchedType,
                        locationName = locName,
                        latitude = null,
                        longitude = null,
                        description = notesText,
                        timestampMillis = System.currentTimeMillis(),
                        status = matchedStatus
                    )
                )
            }
            if (fetchedList.isNotEmpty()) {
                reports.clear()
                reports.addAll(fetchedList)
            }
            return true
        }
        return false
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
