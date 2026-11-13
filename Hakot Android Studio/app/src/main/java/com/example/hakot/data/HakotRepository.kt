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
import com.example.hakot.model.DriverProfile
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
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
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

    val trucks = mutableStateListOf<GarbageTruck>()
    val drivers = mutableStateListOf<DriverProfile>()
    var driversLoadError by mutableStateOf<String?>(null)
    var isDriversLoading by mutableStateOf(false)

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
    var schedulesLoadError by mutableStateOf<String?>(null)
    var isSchedulesLoading by mutableStateOf(false)
    var trucksLoadError by mutableStateOf<String?>(null)
    var isTrucksLoading by mutableStateOf(false)

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
        targetRole: UserRole? = null
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

        // Validate portal authorization if targetRole is specified
        if (targetRole != null) {
            if (targetRole == UserRole.DRIVER && role != UserRole.DRIVER) {
                lastLoginError = "Access denied: Account is registered as an Administrator. Please use the Admin Portal."
                return false
            }
            if (targetRole != UserRole.DRIVER && role == UserRole.DRIVER) {
                lastLoginError = "Access denied: Account is registered as a Trucker. Please use the Trucker Portal."
                return false
            }
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
        companyName: String = "",
        organizationType: String = "LGU",
        organizationName: String = "",
        repFullName: String = "",
        repPosition: String = "",
        repContactNumber: String = "",
        certDocumentUrl: String = ""
    ): Pair<Boolean, String> {
        Log.d("HAKOT_SUBMIT_REPOSITORY", "submitAccountApplication called for requested role: ${requestedRole.name}, orgType: $organizationType")
        // Enforce role safety: System Owner cannot be requested via self-registration
        if (requestedRole == UserRole.SYSTEM_OWNER) {
            Log.w("HAKOT_SUBMIT_REPOSITORY", "Registration rejected: System Owner requested.")
            return Pair(false, "Registration for Platform System Owner is restricted and cannot be requested.")
        }

        val cleanOrgType = if (organizationType.equals("PROVIDER", ignoreCase = true)) "PROVIDER" else "LGU"

        val payload = JSONObject().apply {
            put("organizationType", cleanOrgType)
            put("organizationName", organizationName.trim())
            put("email", email.trim())
            put("repFullName", (repFullName.ifBlank { fullName }).trim())
            put("repPosition", (repPosition.ifBlank { officialPosition }).trim())
            put("contactNumber", contactNumber.trim())
            put("municipalityId", municipalityId.ifBlank { activeOrganization.municipalityId })
            put("requestedMunicipalities", JSONArray())
            put("certDocumentUrl", certDocumentUrl.trim())
            put("officeDepartment", officeDepartment.trim())
            put("officialPosition", officialPosition.trim())
        }

        Log.d("HAKOT_SUBMIT_REPOSITORY", "Calling SupabaseClient.submitOrganizationApplicationEdgeFunction")
        val result = SupabaseClient.submitOrganizationApplicationEdgeFunction(payload)
        Log.d("HAKOT_SUBMIT_REPOSITORY", "Edge Function returned: isSuccess=${result.isSuccess}, statusCode=${result.statusCode}, errorMessage=${result.errorMessage}")
        return if (result.isSuccess) {
            Pair(true, "Organization account request submitted successfully! Pending System Owner review.")
        } else {
            Log.e("HAKOT_APPLICATION_SUBMISSION", "Failed to submit organization application. Status code: ${result.statusCode}, Error: ${result.errorMessage}")
            Pair(false, result.errorMessage ?: "Unable to submit your application at this time. Please check your connection and try again.")
        }
    }

    data class UncertainPasswordReset(
        val otpId: String,
        val resetRequestId: String,
        val userId: String,
        val email: String,
        val createdAt: String
    )

    val uncertainPasswordResets = mutableStateListOf<UncertainPasswordReset>()

    suspend fun fetchUncertainPasswordResets(): Pair<Boolean, String> {
        if (!isAuthenticated || currentAdminRole != UserRole.SYSTEM_OWNER) {
            return Pair(false, "Access Denied: Only verified System Owners can review password resets.")
        }
        val token = currentAccessToken ?: return Pair(false, "Authentication token missing or expired.")

        val res = SupabaseClient.fetchAuthenticatedTableRecords("account_otps?code_type=eq.PASSWORD_RESET&status=eq.UNCERTAIN&select=id,user_id,email,reset_request_id,created_at", token)
        if (res.isSuccess && res.data != null) {
            uncertainPasswordResets.clear()
            val jsonArray = res.data
            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(i)
                uncertainPasswordResets.add(
                    UncertainPasswordReset(
                        otpId = item.optString("id", ""),
                        resetRequestId = item.optString("reset_request_id", ""),
                        userId = item.optString("user_id", ""),
                        email = item.optString("email", ""),
                        createdAt = item.optString("created_at", "Recent")
                    )
                )
            }
            return Pair(true, "Fetched successfully")
        } else {
            return Pair(false, res.errorMessage ?: "Failed to fetch uncertain password resets.")
        }
    }

    suspend fun reconcileUncertainPasswordReset(otpId: String, action: String): Pair<Boolean, String> {
        if (!isAuthenticated || currentAdminRole != UserRole.SYSTEM_OWNER) {
            return Pair(false, "Access Denied: Only verified System Owners can reconcile password resets.")
        }
        val token = currentAccessToken ?: return Pair(false, "Authentication token missing or expired.")

        val res = SupabaseClient.reconcileUncertainPasswordReset(otpId, action, token)
        if (res.isSuccess) {
            fetchUncertainPasswordResets()
            return Pair(true, "Password reset reconciliation completed successfully.")
        } else {
            return Pair(false, res.errorMessage ?: "Reconciliation failed.")
        }
    }

    suspend fun fetchAuditLogs(): Pair<Boolean, String> {
        if (!isAuthenticated || currentAdminRole != UserRole.SYSTEM_OWNER) {
            return Pair(false, "Access Denied: Only verified System Owners can view audit logs.")
        }
        val token = currentAccessToken ?: return Pair(false, "Authentication token missing or expired.")

        val res = SupabaseClient.fetchAuthenticatedTableRecords("audit_logs?select=*&order=created_at.desc&limit=50", token)
        if (res.isSuccess && res.data != null) {
            auditLogs.clear()
            val jsonArray = res.data
            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(i)
                val id = item.optString("id", "")
                val actorId = item.optString("actor_id", "")
                val action = item.optString("action", "")
                val tableName = item.optString("table_name", "")
                val recordId = item.optString("record_id", "")
                val createdAt = item.optString("created_at", "")

                val timestamp = if (createdAt.isNotBlank()) {
                    try {
                        Instant.parse(createdAt).toEpochMilli()
                    } catch (_: Exception) {
                        -1L
                    }
                } else {
                    -1L
                }

                val actorDisplay = if (actorId.isNotBlank()) "Actor ID: ${actorId.takeLast(6)}" else "System Actor"

                auditLogs.add(
                    AuditLogEntry(
                        id = id.ifBlank { "aud_$i" },
                        actingUserEmail = actorDisplay,
                        action = action,
                        targetRecord = if (tableName.isNotBlank()) "$tableName ($recordId)" else recordId,
                        timestampMillis = timestamp
                    )
                )
            }
            return Pair(true, "Audit logs fetched successfully.")
        } else {
            return Pair(false, res.errorMessage ?: "Failed to fetch audit logs from Supabase.")
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
            val rootObj = result.data?.optJSONObject(0)
            val activationCode = rootObj?.optString("activationCode")
            val msg = if (!activationCode.isNullOrBlank()) {
                "Application approved! Activation Code: $activationCode (Please deliver this code manually to the applicant's Gmail)."
            } else {
                "Application updated to ${newStatus.name} successfully."
            }
            Pair(true, msg)
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

    private fun parseGeometryToGeoPoints(geomObj: Any?): List<Pair<Double, Double>> {
        val points = mutableListOf<Pair<Double, Double>>()
        if (geomObj == null) return points
        try {
            val textStr = when (geomObj) {
                is String -> geomObj.trim()
                else -> geomObj.toString().trim()
            }
            if (textStr.isBlank() || textStr.equals("null", ignoreCase = true)) {
                return points
            }

            val cleanStr = if (textStr.startsWith("\"") && textStr.endsWith("\"") && textStr.length >= 2) {
                try {
                    JSONTokener(textStr).nextValue() as? String ?: textStr
                } catch (_: Exception) {
                    textStr.removeSurrounding("\"")
                }
            } else {
                textStr
            }

            val coordsArray = if (cleanStr.startsWith("{")) {
                val json = JSONObject(cleanStr)
                val geom = if (json.has("geometry")) json.optJSONObject("geometry") else json
                geom?.optJSONArray("coordinates")
            } else if (cleanStr.startsWith("LINESTRING", ignoreCase = true)) {
                val content = cleanStr.substringAfter("(").substringBeforeLast(")")
                val pairs = content.split(",")
                val parsed = mutableListOf<Pair<Double, Double>>()
                for (pair in pairs) {
                    val parts = pair.trim().split(Regex("\\s+"))
                    if (parts.size >= 2) {
                        val lon = parts[0].toDoubleOrNull()
                        val lat = parts[1].toDoubleOrNull()
                        if (lon != null && lat != null && lon in -180.0..180.0 && lat in -90.0..90.0 && !(lon == 0.0 && lat == 0.0)) {
                            parsed.add(Pair(lat, lon))
                        }
                    }
                }
                return parsed.distinctBy { it.first to it.second }
            } else {
                null
            }

            if (coordsArray != null) {
                for (i in 0 until coordsArray.length()) {
                    val pt = coordsArray.optJSONArray(i)
                    if (pt != null && pt.length() >= 2) {
                        val lon = pt.optDouble(0, Double.NaN)
                        val lat = pt.optDouble(1, Double.NaN)
                        if (!lon.isNaN() && !lat.isNaN() && lon in -180.0..180.0 && lat in -90.0..90.0 && !(lon == 0.0 && lat == 0.0)) {
                            points.add(Pair(lat, lon))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("HAKOT_GEO", "Error parsing geometry: ${e.message}")
        }

        val deduped = mutableListOf<Pair<Double, Double>>()
        for (pt in points) {
            if (deduped.isEmpty() || deduped.last() != pt) {
                deduped.add(pt)
            }
        }
        return deduped
    }

    suspend fun fetchRouteGeometry(routeVersionId: String): List<Pair<Double, Double>> {
        if (routeVersionId.isBlank() || !isAuthenticated) return emptyList()
        val token = currentAccessToken ?: return emptyList()

        val res = SupabaseClient.fetchRouteGeometryGeoJson(routeVersionId, token)
        if (!res.isSuccess) {
            Log.e("HAKOT_ROUTE", "Failed to fetch route geometry for version $routeVersionId: HTTP ${res.statusCode} - ${res.errorMessage}")
            return emptyList()
        }

        val rawText = res.errorMessage?.trim() ?: return emptyList()
        if (rawText.isBlank() || rawText.equals("null", ignoreCase = true)) {
            return emptyList()
        }

        return parseGeometryToGeoPoints(rawText)
    }

    suspend fun fetchSchedules(): Pair<Boolean, String> {
        if (!isAuthenticated) {
            return Pair(false, "Access Denied: Authentication required.")
        }
        if (isSchedulesLoading) {
            return Pair(true, "Already loading schedules.")
        }
        val token = currentAccessToken ?: return Pair(false, "Authentication token missing or expired.")

        isSchedulesLoading = true
        schedulesLoadError = null

        val res = SupabaseClient.fetchAuthenticatedTableRecords(
            "schedules?select=id,collection_date,start_time,end_time,status,truck_id,route_version_id,route_versions(routes(name)),barangays(name),trucks(plate_number)",
            token
        )
        isSchedulesLoading = false

        if (res.isSuccess && res.data != null) {
            val newList = mutableListOf<ScheduleItem>()
            val jsonArray = res.data
            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(i)
                val id = item.optString("id", "")
                val collectionDateStr = item.optString("collection_date", "")
                val startTimeStr = item.optString("start_time", "08:00:00")
                val endTimeStr = item.optString("end_time", "")
                val status = item.optString("status", "active")
                val routeVersionId = item.optString("route_version_id", "")
                val truckId = if (item.isNull("truck_id")) null else item.optString("truck_id", "").trim()

                val date = try {
                    LocalDate.parse(collectionDateStr)
                } catch (_: Exception) {
                    null
                }
                val collectionDay = date?.dayOfWeek?.name?.lowercase()?.replaceFirstChar { it.uppercase() } ?: ""

                val routeVersionsObj = item.optJSONObject("route_versions")
                val routesObj = routeVersionsObj?.optJSONObject("routes")
                val routeName = routesObj?.optString("name") ?: (if (date != null) "Collection on $date" else "Collection Schedule")

                val barangaysObj = item.optJSONObject("barangays")
                val barangayName = barangaysObj?.optString("name") ?: "Barangay Unavailable"

                val trucksObj = item.optJSONObject("trucks")
                val plateNum = trucksObj?.optString("plate_number", "")?.trim() ?: ""

                val truckAssignedDisplay = when {
                    truckId == null || truckId.isBlank() -> "Unassigned"
                    plateNum.isNotBlank() -> plateNum
                    else -> "Assignment unavailable"
                }

                val startTime = try {
                    LocalTime.parse(startTimeStr.take(5))
                } catch (_: Exception) {
                    LocalTime.of(8, 0)
                }
                val endTime = try {
                    if (endTimeStr.isNotBlank()) {
                        LocalTime.parse(endTimeStr.take(5))
                    } else {
                        startTime.plusHours(3)
                    }
                } catch (_: Exception) {
                    startTime.plusHours(3)
                }

                newList.add(
                    ScheduleItem(
                        id = id.ifBlank { "sch_$i" },
                        barangay = barangayName.ifBlank { "Barangay Unavailable" },
                        routeName = routeName.ifBlank { "Route Unavailable" },
                        date = date,
                        collectionDay = collectionDay,
                        startTime = startTime,
                        endTime = endTime,
                        wasteCategory = WasteCategory.GENERAL_WASTE,
                        truckAssigned = truckAssignedDisplay,
                        status = status.replaceFirstChar { it.uppercase() },
                        routeVersionId = routeVersionId
                    )
                )
            }
            schedules.clear()
            schedules.addAll(newList)
            return Pair(true, "Schedules fetched successfully.")
        } else {
            val err = res.errorMessage ?: "Failed to fetch schedules from Supabase."
            schedulesLoadError = err
            return Pair(false, err)
        }
    }

    suspend fun fetchTrucks(): Pair<Boolean, String> {
        if (!isAuthenticated) {
            return Pair(false, "Access Denied: Authentication required.")
        }
        if (isTrucksLoading) {
            return Pair(true, "Already loading trucks.")
        }
        val token = currentAccessToken ?: return Pair(false, "Authentication token missing or expired.")

        isTrucksLoading = true
        trucksLoadError = null

        val res = SupabaseClient.fetchAuthenticatedTableRecords("trucks?select=id,plate_number,model,capacity_tons,status,driver_profiles(assigned_truck_id,profiles(full_name))", token)
        isTrucksLoading = false

        if (res.isSuccess && res.data != null) {
            val newList = mutableListOf<GarbageTruck>()
            val jsonArray = res.data
            var skippedCount = 0
            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(i)
                val id = item.optString("id", "").trim()
                val plateNumber = item.optString("plate_number", "").trim()

                if (id.isBlank() || plateNumber.isBlank()) {
                    skippedCount++
                    continue
                }

                val model = if (item.has("model") && !item.isNull("model")) item.optString("model", "").trim() else ""
                val capacityTons = item.optDouble("capacity_tons", 0.0)
                val rawStatus = if (item.has("status") && !item.isNull("status")) item.optString("status", "").trim().lowercase() else ""
                val statusStr = rawStatus.ifBlank { "unavailable" }

                val driverProfilesArr = if (item.has("driver_profiles") && !item.isNull("driver_profiles")) {
                    item.optJSONArray("driver_profiles")
                } else null

                val driverNameDisplay = if (driverProfilesArr != null) {
                    if (driverProfilesArr.length() > 0) {
                        val driverObj = driverProfilesArr.optJSONObject(0)
                        val profilesObj = driverObj?.optJSONObject("profiles")
                        val name = profilesObj?.optString("full_name", "")?.trim() ?: ""
                        if (name.isNotBlank()) name else "Assignment unavailable"
                    } else {
                        "Unassigned"
                    }
                } else {
                    val matchedDriver = drivers.find { it.assignedTruckId == id }
                    when {
                        matchedDriver != null -> matchedDriver.fullName.ifBlank { "Assignment unavailable" }
                        else -> "Unassigned"
                    }
                }

                newList.add(
                    GarbageTruck(
                        id = id,
                        plateNumber = plateNumber,
                        driverName = driverNameDisplay,
                        assignedRoute = "",
                        assignedBarangays = "",
                        destinationDumpsite = "",
                        latitude = 0.0,
                        longitude = 0.0,
                        status = TruckStatus.ACTIVE_ON_ROUTE,
                        speedKmH = 0,
                        lastGpsUpdate = "",
                        isCollecting = false,
                        isOnline = false,
                        modelName = model,
                        capacityTons = capacityTons,
                        dbStatus = statusStr
                    )
                )
            }
            trucks.clear()
            trucks.addAll(newList)
            val msg = if (skippedCount > 0) "Trucks fetched successfully ($skippedCount invalid records skipped)." else "Trucks fetched successfully."
            return Pair(true, msg)
        } else {
            val err = res.errorMessage ?: "Failed to fetch trucks from Supabase."
            trucksLoadError = err
            return Pair(false, err)
        }
    }

    suspend fun fetchDrivers(): Pair<Boolean, String> {
        if (!isAuthenticated) {
            return Pair(false, "Access Denied: Authentication required.")
        }
        if (isDriversLoading) {
            return Pair(true, "Already loading drivers.")
        }
        val token = currentAccessToken ?: return Pair(false, "Authentication token missing or expired.")

        isDriversLoading = true
        driversLoadError = null

        val res = SupabaseClient.fetchAuthenticatedTableRecords("driver_profiles?select=id,profile_id,license_number,status,assigned_truck_id,profiles(full_name,email),trucks(plate_number,model)", token)
        isDriversLoading = false

        if (res.isSuccess && res.data != null) {
            val newList = mutableListOf<DriverProfile>()
            val jsonArray = res.data
            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(i)
                val id = item.optString("id", "")
                val profileId = item.optString("profile_id", "")
                val licenseNumber = item.optString("license_number", "N/A")
                val status = item.optString("status", "off_duty")
                val assignedTruckId = if (item.isNull("assigned_truck_id")) null else item.optString("assigned_truck_id")

                val profilesObj = item.optJSONObject("profiles")
                val fullName = profilesObj?.optString("full_name") ?: "Unknown Driver"
                val email = profilesObj?.optString("email") ?: "N/A"

                val trucksObj = item.optJSONObject("trucks")
                val plateNumber = trucksObj?.optString("plate_number") ?: ""

                val assignedTruckDisplay = when {
                    assignedTruckId == null -> "Unassigned"
                    plateNumber.isNotBlank() -> plateNumber
                    else -> "Assignment unavailable"
                }

                newList.add(
                    DriverProfile(
                        id = id,
                        profileId = profileId,
                        licenseNumber = licenseNumber,
                        status = status,
                        assignedTruckId = assignedTruckId,
                        fullName = fullName,
                        email = email,
                        assignedTruckPlate = assignedTruckDisplay
                    )
                )
            }
            drivers.clear()
            drivers.addAll(newList)
            return Pair(true, "Drivers fetched successfully.")
        } else {
            val err = res.errorMessage ?: "Failed to fetch drivers from Supabase."
            driversLoadError = err
            return Pair(false, err)
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

    suspend fun activateAccount(email: String, code: String, newPassword: String): Pair<Boolean, String> {
        if (email.isBlank() || code.isBlank() || newPassword.isBlank()) {
            return Pair(false, "Email, activation code, and new password are required.")
        }
        if (newPassword.length < 6) {
            return Pair(false, "Password must be at least 6 characters long.")
        }

        val res = SupabaseClient.activateAccountEdgeFunction(email, code, newPassword)
        if (res.statusCode == -2) {
            return Pair(false, "Network request timed out. The operation outcome is uncertain — please do not retry immediately.")
        }
        if (!res.isSuccess) {
            return Pair(false, res.errorMessage ?: "Failed to activate account.")
        }

        val rootObj = res.data?.optJSONObject(0)
        val message = rootObj?.optString("message") ?: "Account activated successfully."
        return Pair(true, message)
    }

    suspend fun resetAccountPassword(email: String, code: String, newPassword: String): PasswordResetResult {
        if (email.isBlank() || code.isBlank() || newPassword.isBlank()) {
            return PasswordResetResult.Failed("Email, verification code, and new password are required.")
        }
        if (newPassword.length < 6) {
            return PasswordResetResult.Failed("Password must be at least 6 characters long.")
        }

        val res = SupabaseClient.resetAccountPasswordEdgeFunction(email, code, newPassword)
        if (res.statusCode == -2) {
            return PasswordResetResult.Uncertain("Network request timed out. The operation outcome is uncertain — please do not retry immediately to avoid duplicate attempts.")
        }
        if (!res.isSuccess) {
            return PasswordResetResult.Failed(res.errorMessage ?: "Failed to reset password.")
        }

        val rootObj = res.data?.optJSONObject(0)
        if (rootObj == null || rootObj.optBoolean("success", false) != true) {
            val err = rootObj?.optString("error", "Failed to reset password. Invalid server response.") ?: "Failed to reset password. Invalid server response."
            return PasswordResetResult.Failed(err)
        }

        val warning = rootObj.optString("warning")
        val message = rootObj.optString("message", "Password updated successfully.")

        if (!warning.isNullOrBlank()) {
            return PasswordResetResult.ReconciliationRequired(warning)
        }
        return PasswordResetResult.Success(message)
    }

    suspend fun validateInvitationToken(accessToken: String): Boolean {
        val res = SupabaseClient.getCurrentUser(accessToken)
        return res.isSuccess
    }

    suspend fun updatePasswordWithToken(accessToken: String, newPassword: String): Pair<Boolean, String> {
        val res = SupabaseClient.updateUserPassword(newPassword, accessToken)
        return if (res.isSuccess) {
            Pair(true, "Password updated successfully.")
        } else {
            Pair(false, res.errorMessage ?: "Failed to update password.")
        }
    }
}

sealed class PasswordResetResult {
    data class Success(val message: String) : PasswordResetResult()
    data class ReconciliationRequired(val warning: String) : PasswordResetResult()
    data class Failed(val error: String) : PasswordResetResult()
    data class Uncertain(val message: String) : PasswordResetResult()
}
