package com.example.hakot.model

import java.time.LocalDate
import java.time.LocalTime

enum class WasteCategory(val displayName: String, val colorHex: Long) {
    GENERAL_WASTE("General Waste (All Trash Accepted)", 0xFF10B981),
    RECYCLABLE("Recyclables & Plastic", 0xFFF59E0B),
    HAZARDOUS("Hazardous & E-Waste", 0xFFEF4444),
    BULKY_RESIDUAL("Bulky Household & Furniture", 0xFF3B82F6)
}

enum class ReportType(val title: String, val description: String, val targetOffice: String) {
    UNCOLLECTED_GARBAGE("Uncollected Garbage", "Accumulated trash awaiting regular truck pass", "Waste Collection Office"),
    NO_ROUTE_IN_AREA("No Collection Route in My Area", "Inform admin that your street/area has no assigned waste collection route", "Collection Operations"),
    MISSED_COLLECTION("Missed Collection Schedule", "Collection truck did not arrive during scheduled collection window", "Collection Operations"),
    ILLEGAL_DUMPING("Illegal Dumping Spot", "Unauthorized garbage disposal spotted on street", "Environmental Enforcement Office"),
    OVERFLOWING_BINS("Overflowing Garbage Bins", "Public bins overflowing or damaged", "Sanitation Office"),
    IMPROPER_DISPOSAL("Improper Waste Disposal", "Non-segregated or hazardous waste disposed improperly", "Sanitation Office"),
    BLOCKED_DRAINAGE("Blocked Drainage Caused by Waste", "Waste blocking street drainage or waterway", "Municipal Maintenance Office"),
    OTHER("Other Collection Concern", "General sanitation or collection issue", "Municipal Waste Office")
}

enum class ReportStatus(val label: String) {
    SUBMITTED("Submitted"),
    RECEIVED("Received"),
    UNDER_REVIEW("Under Review"),
    ASSIGNED("Assigned"),
    IN_PROGRESS("In Progress"),
    AWAITING_INFO("Awaiting Additional Info"),
    RESOLVED("Resolved"),
    REJECTED("Rejected"),
    CLOSED("Closed")
}

enum class TruckStatus(val label: String) {
    ACTIVE_ON_ROUTE("En Route - Collecting"),
    UNLOADING_AT_DUMPSITE("Unloading at MRF/Landfill"),
    IN_MAINTENANCE("In Depot / Service"),
    STANDBY("Standby")
}

enum class UserRole(val label: String) {
    SYSTEM_OWNER("Platform System Owner"),
    MUNICIPAL_ADMIN("Municipal Administrator"),
    DISTRICT_ADMIN("District Administrator"),
    MUNICIPAL_STAFF("Municipal Staff / Inspector"),
    DRIVER("Truck Operator / Driver"),
    RESIDENT("Resident (Anonymous)")
}

enum class AdminStatus(val label: String) {
    ACTIVE("Active"),
    PENDING_INVITATION("Pending Invitation"),
    SUSPENDED("Suspended"),
    REVOKED("Revoked")
}

enum class SegmentProgressStatus(val label: String, val colorHex: Long) {
    NOT_STARTED("Not Started", 0xFF3B82F6),
    IN_PROGRESS("In Progress", 0xFFF59E0B),
    COMPLETED("Completed", 0xFF10B981),
    SKIPPED("Skipped", 0xFF6B7280),
    BLOCKED("Road Blocked", 0xFFEF4444)
}

enum class OrganizationType(val label: String) {
    MUNICIPALITY("Municipality / LGU"),
    DISTRICT("District / City Zone"),
    BARANGAY("Barangay LGU"),
    PRIVATE_COMPANY("Private Waste Collection Company")
}

data class OrganizationConfig(
    val organizationKey: String = "org_nasugbu_lgu",
    val municipalityId: String = "cb394eb7-a8b7-45f2-a314-3926baeab31f",
    val name: String,
    val type: OrganizationType,
    val province: String,
    val region: String,
    val assignedMunicipality: String,
    val coveredBarangays: List<String>,
    val adminContactEmail: String,
    val adminContactPhone: String
) {
    val id: String get() = organizationKey
}

data class BarangayItem(
    val id: String,
    val name: String
)

data class AdminUserProfile(
    val userId: String,
    val fullName: String,
    val email: String,
    val municipalityOrgId: String,
    val municipalityName: String,
    val districtId: String,
    val districtName: String,
    val role: UserRole = UserRole.MUNICIPAL_ADMIN,
    var status: AdminStatus = AdminStatus.ACTIVE,
    val registrationDate: String,
    val lastActivity: String = "Just now",
    val positionTitle: String = "Sanitation Official",
    val officeDepartment: String = "Municipal Waste Management"
)

data class AccountInvitation(
    val invitationId: String,
    val codeHash: String,
    val intendedEmail: String,
    val intendedName: String,
    val contactNumber: String,
    val municipalityId: String,
    val municipalityName: String,
    val assignedRole: UserRole = UserRole.MUNICIPAL_ADMIN,
    val positionTitle: String = "Municipal Official",
    val officeDepartment: String = "Sanitation Department",
    val createdByAuthorityOffice: String = "Nasugbu Municipal Sanitation Office",
    val createdAtMillis: Long,
    val expiresAtMillis: Long,
    var redeemedAtMillis: Long? = null,
    var status: AdminStatus = AdminStatus.PENDING_INVITATION
) {
    val codeHashDisplay: String get() = codeHash
    val rawCodeDisplay: String get() = "INV-${invitationId.takeLast(6).uppercase()}"
    val intendedAdminName: String get() = intendedName
    val municipalityOrgId: String get() = municipalityId
    val assignedDistrictId: String get() = "dist_nasugbu"
    val assignedDistrictName: String get() = "Nasugbu Scope"
}
typealias DistrictAdminInvitation = AccountInvitation

data class AuditLogEntry(
    val id: String,
    val actingUserEmail: String,
    val action: String,
    val targetRecord: String,
    val timestampMillis: Long
)

data class StreetSegment(
    val segmentId: String,
    val streetName: String,
    val barangayId: String,
    val barangayName: String,
    val municipalityOrgId: String,
    val roadType: String = "PRIMARY_STREET",
    val geoPoints: List<Pair<Double, Double>>,
    val connectedSegmentIds: List<String> = emptyList(),
    val dataSource: String = "PostGIS EPSG:4326 WGS84"
)

data class RouteSegmentJunction(
    val routeId: String,
    val segmentId: String,
    val sequenceOrder: Int,
    val traversalDirection: String = "FORWARD"
)

data class CollectionProgressRecord(
    val scheduleId: String,
    val segmentId: String,
    var status: SegmentProgressStatus = SegmentProgressStatus.NOT_STARTED,
    var confirmedByUserId: String = "m.lopez@nasugbu.gov.ph",
    var confirmedAtMillis: Long = System.currentTimeMillis()
)

data class TruckGpsTrailPoint(
    val id: String,
    val truckId: String,
    val scheduleId: String,
    val latitude: Double,
    val longitude: Double,
    val timestampMillis: Long,
    val accuracyMeters: Float = 3.5f,
    val speedKmH: Int = 22
)

data class Announcement(
    val id: String,
    val title: String,
    val message: String,
    val timestampMillis: Long,
    val category: String = "General",
    val isPinned: Boolean = false
)

data class Dumpsite(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    var status: Boolean = true,
    val id: String = "ds_1",
    val type: String = "Materials Recovery Facility",
    val address: String = "J.P. Laurel St, Nasugbu, Batangas",
    val capacityPercent: Int = 65,
    val operatingHours: String = "6:00 AM - 6:00 PM",
    val acceptedWasteTypes: String = "General Household, Compostables & Recyclables",
    val isOpenNow: Boolean = true,
    val contactNumber: String = "(043) 723-HAKOT"
) {
    init {
        require(name.isNotBlank()) { "Dumpsite name must not be blank" }
        require(latitude in -90.0..90.0) { "Invalid latitude: $latitude" }
        require(longitude in -180.0..180.0) { "Invalid longitude: $longitude" }
    }
}

data class Dumptrack(
    val plateNum: String,
    var isCollecting: Boolean = true,
    var route: String = "J.P. Laurel Street Route",
    var Destination: String = "Nasugbu MRF Facility",
    var isOnline: Boolean = true,
    var driverName: String = "Mario Santos",
    var speed: Int = 22,
    var latitude: Double = 14.0725,
    var longitude: Double = 120.6335,
    val id: String = "tr_1",
    val truckCode: String = "Hakot Truck #1",
    val assignedBarangays: String = "Spans Barangays Poblacion 1, 2 & 3",
    val status: TruckStatus = TruckStatus.ACTIVE_ON_ROUTE,
    val lastGpsUpdate: String = "Live - 1 min ago"
) {
    val plateNumber: String get() = plateNum
    val assignedRoute: String get() = route
    val destinationDumpsite: String get() = Destination
    val speedKmH: Int get() = speed

    constructor(
        id: String = "tr_1",
        truckCode: String = "Hakot Truck #1",
        plateNumber: String,
        driverName: String,
        assignedRoute: String,
        assignedBarangays: String = "Spans Barangays Poblacion 1, 2 & 3",
        destinationDumpsite: String = "Nasugbu MRF Facility",
        latitude: Double,
        longitude: Double,
        status: TruckStatus = TruckStatus.ACTIVE_ON_ROUTE,
        speedKmH: Int = 22,
        lastGpsUpdate: String = "Live - 1 min ago",
        isCollecting: Boolean = true,
        isOnline: Boolean = true
    ) : this(
        plateNum = plateNumber,
        isCollecting = isCollecting,
        route = assignedRoute,
        Destination = destinationDumpsite,
        isOnline = isOnline,
        driverName = driverName,
        speed = speedKmH,
        latitude = latitude,
        longitude = longitude,
        id = id,
        truckCode = truckCode,
        assignedBarangays = assignedBarangays,
        status = status,
        lastGpsUpdate = lastGpsUpdate
    )

    init {
        require(plateNum.isNotBlank()) { "Plate number must not be blank" }
    }

    fun collecting(routeName: String, streets: List<String>, destination: String) {
        this.isCollecting = true
        this.route = if (streets.isNotEmpty()) "$routeName (${streets.joinToString(", ")})" else routeName
        this.Destination = destination
    }

    fun status(driverName: String, lat: Double, lng: Double, speed: Int, isOnline: Boolean) {
        this.driverName = driverName
        this.latitude = lat
        this.longitude = lng
        this.speed = speed
        this.isOnline = isOnline
    }
}
typealias GarbageTruck = Dumptrack

data class Route(
    var routeName: String,
    var street: List<String> = listOf("J.P. Laurel St"),
    val id: String = "rt_1",
    val barangaysCovered: String = "J.P. Laurel St (Spans Brgy 1, 2 & 3)",
    val colorHex: Long = 0xFF3B82F6,
    val waypoints: List<Pair<Float, Float>> = emptyList(),
    val geoPoints: List<Pair<Double, Double>> = emptyList(),
    val assignedTruckPlate: String = "ABK-8891",
    val scheduledTimeWindow: String = "8:00 AM - 11:30 AM",
    val scheduledDate: String = "Today",
    val destinationDumpsite: String = "Nasugbu MRF Facility"
) {
    val routeNameProperty: String get() = routeName
    val streetProperty: List<String> get() = street

    fun setRoute(barangay: String, streets: List<String>) {
        if (barangay.isNotBlank()) {
            this.routeName = "$routeName - $barangay"
        }
        this.street = streets
    }
}
typealias CollectionRoute = Route

data class Schedules(
    val dumptrack: Dumptrack,
    val dumpsite: Dumpsite,
    val route: Route,
    val eta: String = "8:00 AM",
    val collectionDate: LocalDate = LocalDate.now(),
    val scheduledTime: LocalTime = LocalTime.of(8, 0)
)

data class ScheduleItem(
    val id: String,
    val barangay: String,
    val routeName: String,
    val date: LocalDate,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val wasteCategory: WasteCategory,
    val truckAssigned: String,
    val status: String,
    val barangayId: String = "brgy_041019001"
)

data class ReportMessage(
    val id: String,
    val reportId: String,
    val senderType: String,
    val senderName: String,
    val text: String,
    val timestampMillis: Long,
    val isRead: Boolean = false
)

data class AnonymousReport(
    val id: String,
    val referenceNumber: String = "#HK-${(System.currentTimeMillis() % 10000)}",
    val accessToken: String = "AUTH-${(System.currentTimeMillis() % 10000)}",
    val reportType: ReportType,
    val locationName: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val description: String,
    val timestampMillis: Long,
    var status: ReportStatus = ReportStatus.SUBMITTED,
    val assignedOffice: String = reportType.targetOffice,
    var assignedHandlerName: String = "Unassigned Handler",
    var resolutionDetails: String = ""
)

enum class ApplicationStatus(val label: String) {
    PENDING("Pending System Owner Review"),
    APPROVED("Approved"),
    REJECTED("Rejected")
}

data class AccountApplication(
    val applicationId: String,
    val fullName: String,
    val email: String,
    val contactNumber: String,
    val municipality: String,
    val officeDepartment: String,
    val officialPosition: String,
    val requestedRole: UserRole, // MUNICIPAL_ADMIN, MUNICIPAL_STAFF, DRIVER (Never SYSTEM_OWNER)
    var status: ApplicationStatus = ApplicationStatus.PENDING,
    val createdAtMillis: Long = System.currentTimeMillis()
)
