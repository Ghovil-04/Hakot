package com.example.hakot.model

import java.time.LocalDate
import java.time.LocalTime

enum class WasteCategory(val displayName: String, val colorHex: Long) {
    GENERAL_WASTE("General Waste (All Trash Accepted)", 0xFF10B981),
    RECYCLABLE("Recyclables & Plastic", 0xFFF59E0B),
    HAZARDOUS("Hazardous & E-Waste", 0xFFEF4444),
    BULKY_RESIDUAL("Bulky Household & Furniture", 0xFF3B82F6)
}

enum class ReportType(val title: String, val description: String) {
    MISSED_COLLECTION("Missed Schedule", "Collection truck did not arrive on scheduled time"),
    WASTE_PILE_UP("Waste Pile-Up", "Overflowing waste accumulated on street/corner"),
    ILLEGAL_DUMPING("Illegal Dumping", "Unauthorized garbage disposal spotted"),
    DAMAGED_BIN("Damaged Public Bin", "Public trash bin broken or destroyed"),
    OTHER("Other Concern", "General sanitation issue")
}

enum class ReportStatus(val label: String) {
    SUBMITTED("Report Received"),
    IN_PROGRESS("Dispatching Truck"),
    RESOLVED("Cleaned & Cleared")
}

enum class TruckStatus(val label: String) {
    ACTIVE_ON_ROUTE("En Route - Collecting"),
    UNLOADING_AT_DUMPSITE("Unloading at MRF/Landfill"),
    IN_MAINTENANCE("In Depot / Service"),
    STANDBY("Standby")
}

enum class UserRole(val label: String) {
    RESIDENT("Resident (Anonymous)"),
    DRIVER("Truck Operator / Driver"),
    ADMIN("Sanitation Admin")
}

data class Announcement(
    val id: String,
    val title: String,
    val message: String,
    val timestampMillis: Long,
    val category: String = "General",
    val isPinned: Boolean = false
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
    val status: String
)

data class GarbageTruck(
    val id: String,
    val truckCode: String,
    val plateNumber: String,
    val driverName: String,
    val assignedRoute: String,
    val assignedBarangays: String = "Barangay Poblacion, Sabang",
    val destinationDumpsite: String = "Lipa City MRF Facility",
    var latitude: Double,
    var longitude: Double,
    val status: TruckStatus,
    val speedKmH: Int,
    val lastGpsUpdate: String = "Live - 1 min ago"
)

data class Dumpsite(
    val id: String,
    val name: String,
    val type: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val capacityPercent: Int,
    val operatingHours: String = "6:00 AM - 6:00 PM",
    val acceptedWasteTypes: String = "General Household, Recyclables & Compost",
    val isOpenNow: Boolean = true,
    val contactNumber: String = "(046) 888-HAKOT"
)

data class CollectionRoute(
    val id: String,
    val routeName: String,
    val barangaysCovered: String,
    val colorHex: Long,
    val waypoints: List<Pair<Float, Float>>
)

data class AnonymousReport(
    val id: String,
    val reportType: ReportType,
    val locationName: String,
    val latitude: Double,
    val longitude: Double,
    val description: String,
    val timestampMillis: Long,
    val status: ReportStatus = ReportStatus.SUBMITTED
)
