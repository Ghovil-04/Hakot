package com.example.hakot.data

import androidx.compose.runtime.mutableStateListOf
import com.example.hakot.model.Announcement
import com.example.hakot.model.AnonymousReport
import com.example.hakot.model.CollectionRoute
import com.example.hakot.model.Dumpsite
import com.example.hakot.model.GarbageTruck
import com.example.hakot.model.ReportStatus
import com.example.hakot.model.ReportType
import com.example.hakot.model.ScheduleItem
import com.example.hakot.model.TruckStatus
import com.example.hakot.model.WasteCategory
import java.time.LocalDate
import java.time.LocalTime

object HakotRepository {
    val today: LocalDate get() = LocalDate.now()

    var userLat: Double = 14.0700
    var userLng: Double = 120.6300
    var detectedLocationName: String = "Nasugbu, Batangas"
    var detectedCity: String = "Nasugbu"
    var detectedProvince: String = "Batangas"

    val announcements = mutableStateListOf(
        Announcement(
            id = "ann_1",
            title = "📢 LGU Holiday Waste Collection Notice",
            message = "All regular street routes in Nasugbu (J.P. Laurel St, Roxas St & P. Burgos St) will proceed as scheduled today.",
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
            latitude = 14.0750,
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
            assignedRoute = "J.P. Laurel Street Route",
            assignedBarangays = "Spans Barangays Poblacion 1, 2 & 3",
            destinationDumpsite = "Nasugbu MRF Facility",
            latitude = 14.0720,
            longitude = 120.6280,
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
            latitude = 14.0740,
            longitude = 120.6320,
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
            latitude = 14.0750,
            longitude = 120.6350,
            status = TruckStatus.UNLOADING_AT_DUMPSITE,
            speedKmH = 0,
            lastGpsUpdate = "Live - Just now"
        )
    )

    val routes = mutableStateListOf(
        CollectionRoute(
            id = "rt_1",
            routeName = "J.P. Laurel Street Route",
            barangaysCovered = "J.P. Laurel St (Spans Brgy 1, 2 & 3)",
            colorHex = 0xFF4CAF50,
            waypoints = listOf(
                Pair(300f, 400f),
                Pair(450f, 480f),
                Pair(550f, 500f),
                Pair(600f, 550f)
            )
        ),
        CollectionRoute(
            id = "rt_2",
            routeName = "Roxas Street Corridor",
            barangaysCovered = "Roxas St (Spans Brgy Wawa & Bucana)",
            colorHex = 0xFFFFB300,
            waypoints = listOf(
                Pair(200f, 250f),
                Pair(500f, 260f),
                Pair(800f, 280f)
            )
        ),
        CollectionRoute(
            id = "rt_3",
            routeName = "P. Burgos Street Pass",
            barangaysCovered = "P. Burgos St (Spans Brgy Looc & Kaylaway)",
            colorHex = 0xFF0288D1,
            waypoints = listOf(
                Pair(600f, 300f),
                Pair(750f, 320f),
                Pair(850f, 450f)
            )
        )
    )

    val reports = mutableStateListOf(
        AnonymousReport(
            id = "rep_101",
            reportType = ReportType.MISSED_COLLECTION,
            locationName = "J.P. Laurel Street corner Zone 2",
            latitude = 14.0710,
            longitude = 120.6290,
            description = "Truck skipped morning collection for 3 houses",
            timestampMillis = System.currentTimeMillis() - 3600000 * 2,
            status = ReportStatus.IN_PROGRESS
        ),
        AnonymousReport(
            id = "rep_102",
            reportType = ReportType.WASTE_PILE_UP,
            locationName = "Near Roxas Street Public Market",
            latitude = 14.0735,
            longitude = 120.6315,
            description = "Overflowing plastic bags beside street pole",
            timestampMillis = System.currentTimeMillis() - 3600000 * 5,
            status = ReportStatus.SUBMITTED
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
                barangay = "J.P. Laurel St (Spans Brgy 1, 2 & 3)",
                routeName = "J.P. Laurel Street Route",
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
                routeName = "Highway Commercial Route",
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
                barangay = "J.P. Laurel St (Spans Brgy 1, 2 & 3)",
                routeName = "J.P. Laurel Street Route",
                date = currDate.plusDays(1),
                startTime = LocalTime.of(14, 0),
                endTime = LocalTime.of(17, 0),
                wasteCategory = WasteCategory.GENERAL_WASTE,
                truckAssigned = "Hakot Truck #2 (NWD-4512)",
                status = "Scheduled"
            )
        )

        // Yesterday's schedules
        schedules.add(
            ScheduleItem(
                id = "sch_0",
                barangay = "Roxas Street (Spans Brgy Wawa & Bucana)",
                routeName = "Roxas Street Corridor",
                date = currDate.minusDays(1),
                startTime = LocalTime.of(8, 0),
                endTime = LocalTime.of(12, 0),
                wasteCategory = WasteCategory.GENERAL_WASTE,
                truckAssigned = "Hakot Truck #3 (RST-9904)",
                status = "Completed"
            )
        )
    }

    fun addAnonymousReport(
        type: ReportType,
        locationName: String,
        description: String,
        lat: Double = 14.0700,
        lng: Double = 120.6300
    ): AnonymousReport {
        val newReport = AnonymousReport(
            id = "rep_${System.currentTimeMillis() % 10000}",
            reportType = type,
            locationName = locationName,
            latitude = lat,
            longitude = lng,
            description = description,
            timestampMillis = System.currentTimeMillis(),
            status = ReportStatus.SUBMITTED
        )
        reports.add(0, newReport)
        return newReport
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

    fun updateReportStatus(reportId: String, newStatus: ReportStatus) {
        val index = reports.indexOfFirst { it.id == reportId }
        if (index != -1) {
            val item = reports[index]
            reports[index] = item.copy(status = newStatus)
        }
    }
}
