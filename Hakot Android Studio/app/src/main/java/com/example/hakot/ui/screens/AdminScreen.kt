package com.example.hakot.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hakot.data.HakotRepository
import com.example.hakot.model.CollectionRoute
import com.example.hakot.model.GarbageTruck
import com.example.hakot.model.ReportStatus
import com.example.hakot.model.ScheduleItem
import com.example.hakot.model.TruckStatus
import com.example.hakot.model.WasteCategory
import com.example.hakot.ui.theme.DarkBackground
import com.example.hakot.ui.theme.DarkBorder
import com.example.hakot.ui.theme.DarkSurface
import com.example.hakot.ui.theme.DarkSurfaceCard
import com.example.hakot.ui.theme.DarkSurfaceVariant
import com.example.hakot.ui.theme.EcoGreenContainer
import com.example.hakot.ui.theme.EcoGreenLight
import com.example.hakot.ui.theme.EcoGreenPrimary
import com.example.hakot.ui.theme.HazardousRed
import com.example.hakot.ui.theme.HazardousRedContainer
import com.example.hakot.ui.theme.RecycleYellow
import com.example.hakot.ui.theme.TextOnDarkMuted
import com.example.hakot.ui.theme.TextOnDarkPrimary
import com.example.hakot.ui.theme.TextOnDarkSecondary
import java.time.LocalDate
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(
    searchQuery: String = "",
    onExitAdminMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedAdminTab by remember { mutableIntStateOf(0) }
    var showAddModal by remember { mutableStateOf(false) }

    val adminTabs = listOf("Routes", "Schedules", "Truck Fleet", "Citizen Reports")

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // Top Admin Banner
        Surface(
            color = EcoGreenContainer,
            border = BorderStroke(1.dp, EcoGreenPrimary)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AdminPanelSettings,
                        contentDescription = "Admin",
                        tint = EcoGreenPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "HAKOT ADMIN PORTAL",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextOnDarkPrimary
                        )
                        Text(
                            text = "Set routes, schedules, trucks & dispatch",
                            fontSize = 11.sp,
                            color = EcoGreenLight
                        )
                    }
                }

                Button(
                    onClick = onExitAdminMode,
                    colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceCard),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("Exit Admin", fontSize = 11.sp, color = TextOnDarkPrimary)
                }
            }
        }

        // Admin Tab Selector Row
        ScrollableTabRow(
            selectedTabIndex = selectedAdminTab,
            containerColor = DarkSurface,
            contentColor = EcoGreenPrimary,
            edgePadding = 16.dp
        ) {
            adminTabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedAdminTab == index,
                    onClick = { selectedAdminTab = index },
                    text = {
                        Text(
                            text = title,
                            fontSize = 13.sp,
                            fontWeight = if (selectedAdminTab == index) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                )
            }
        }

        // Tab Content
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(16.dp)
        ) {
            when (selectedAdminTab) {
                0 -> AdminRoutesTab(searchQuery) { showAddModal = true }
                1 -> AdminSchedulesTab(searchQuery) { showAddModal = true }
                2 -> AdminTrucksTab(searchQuery) { showAddModal = true }
                3 -> AdminReportsTab(searchQuery)
            }
        }

        // Add Modal Dialog
        if (showAddModal) {
            when (selectedAdminTab) {
                0 -> AddRouteModal(onDismiss = { showAddModal = false })
                1 -> AddScheduleModal(onDismiss = { showAddModal = false })
                2 -> AddTruckModal(onDismiss = { showAddModal = false })
            }
        }
    }
}

// 1. Admin Routes Tab
@Composable
fun AdminRoutesTab(
    searchQuery: String,
    onAddClick: () -> Unit
) {
    val filteredRoutes = HakotRepository.routes.filter {
        searchQuery.isEmpty() || it.routeName.contains(searchQuery, true) || it.barangaysCovered.contains(searchQuery, true)
    }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Collection Routes (${filteredRoutes.size})",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = TextOnDarkPrimary
            )
            Button(
                onClick = onAddClick,
                colors = ButtonDefaults.buttonColors(containerColor = EcoGreenPrimary),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add", tint = Color.Black, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add Route", fontSize = 12.sp, color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(filteredRoutes) { route ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(Color(route.colorHex))
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(route.routeName, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextOnDarkPrimary)
                                Text(route.barangaysCovered, fontSize = 11.sp, color = TextOnDarkSecondary)
                            }
                        }
                        IconButton(onClick = { HakotRepository.removeRoute(route.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = HazardousRed)
                        }
                    }
                }
            }
        }
    }
}

// 2. Admin Schedules Tab
@Composable
fun AdminSchedulesTab(
    searchQuery: String,
    onAddClick: () -> Unit
) {
    val filteredSchedules = HakotRepository.schedules.filter {
        searchQuery.isEmpty() || it.barangay.contains(searchQuery, true) || it.routeName.contains(searchQuery, true)
    }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Collection Schedules (${filteredSchedules.size})",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = TextOnDarkPrimary
            )
            Button(
                onClick = onAddClick,
                colors = ButtonDefaults.buttonColors(containerColor = EcoGreenPrimary),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add", tint = Color.Black, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add Schedule", fontSize = 12.sp, color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(filteredSchedules) { sch ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("${sch.date} • ${sch.startTime} - ${sch.endTime}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextOnDarkPrimary)
                            Text("${sch.barangay} (${sch.routeName})", fontSize = 11.sp, color = EcoGreenLight)
                            Text("Waste: ${sch.wasteCategory.displayName} • ${sch.truckAssigned}", fontSize = 10.sp, color = TextOnDarkSecondary)
                        }
                        IconButton(onClick = { HakotRepository.removeSchedule(sch.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = HazardousRed)
                        }
                    }
                }
            }
        }
    }
}

// 3. Admin Trucks Tab
@Composable
fun AdminTrucksTab(
    searchQuery: String,
    onAddClick: () -> Unit
) {
    val filteredTrucks = HakotRepository.trucks.filter {
        searchQuery.isEmpty() || it.truckCode.contains(searchQuery, true) || it.driverName.contains(searchQuery, true)
    }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Garbage Trucks Fleet (${filteredTrucks.size})",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = TextOnDarkPrimary
            )
            Button(
                onClick = onAddClick,
                colors = ButtonDefaults.buttonColors(containerColor = EcoGreenPrimary),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add", tint = Color.Black, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add Truck", fontSize = 12.sp, color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(filteredTrucks) { truck ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("${truck.truckCode} (${truck.plateNumber})", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextOnDarkPrimary)
                            Text("Driver: ${truck.driverName} • Route: ${truck.assignedRoute}", fontSize = 11.sp, color = TextOnDarkSecondary)
                            Text("Status: ${truck.status.label}", fontSize = 10.sp, color = EcoGreenLight)
                        }
                        IconButton(onClick = { HakotRepository.removeTruck(truck.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = HazardousRed)
                        }
                    }
                }
            }
        }
    }
}

// 4. Admin Reports Tab (Anonymous Citizen Reports Management)
@Composable
fun AdminReportsTab(searchQuery: String) {
    val reports = HakotRepository.reports.filter {
        searchQuery.isEmpty() || it.locationName.contains(searchQuery, true) || it.reportType.title.contains(searchQuery, true)
    }

    Column {
        Text(
            text = "Anonymous Citizen Reports (${reports.size})",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = TextOnDarkPrimary
        )

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(reports) { rep ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = rep.reportType.title,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = HazardousRed
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(DarkSurfaceVariant)
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(rep.status.label, fontSize = 10.sp, color = EcoGreenLight)
                            }
                        }
                        Text(rep.locationName, fontSize = 12.sp, color = TextOnDarkPrimary)
                        if (rep.description.isNotEmpty()) {
                            Text("Notes: ${rep.description}", fontSize = 11.sp, color = TextOnDarkSecondary)
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { HakotRepository.updateReportStatus(rep.id, ReportStatus.IN_PROGRESS) },
                                colors = ButtonDefaults.buttonColors(containerColor = RecycleYellow),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("Dispatch Truck", fontSize = 10.sp, color = Color.Black)
                            }
                            Button(
                                onClick = { HakotRepository.updateReportStatus(rep.id, ReportStatus.RESOLVED) },
                                colors = ButtonDefaults.buttonColors(containerColor = EcoGreenPrimary),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("Mark Resolved", fontSize = 10.sp, color = Color.Black)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRouteModal(onDismiss: () -> Unit) {
    var routeName by remember { mutableStateOf("") }
    var barangays by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("Add Collection Route", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextOnDarkPrimary)
            OutlinedTextField(
                value = routeName,
                onValueChange = { routeName = it },
                label = { Text("Route Name") },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
            OutlinedTextField(
                value = barangays,
                onValueChange = { barangays = it },
                label = { Text("Barangays Covered") },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    if (routeName.isNotBlank()) {
                        HakotRepository.addRoute(
                            CollectionRoute(
                                id = "rt_${System.currentTimeMillis() % 1000}",
                                routeName = routeName,
                                barangaysCovered = barangays,
                                colorHex = 0xFF4CAF50,
                                waypoints = emptyList()
                            )
                        )
                        onDismiss()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = EcoGreenPrimary),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Route", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddScheduleModal(onDismiss: () -> Unit) {
    var barangay by remember { mutableStateOf("") }
    var routeName by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("Add Collection Schedule", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextOnDarkPrimary)
            OutlinedTextField(
                value = barangay,
                onValueChange = { barangay = it },
                label = { Text("Barangays") },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
            OutlinedTextField(
                value = routeName,
                onValueChange = { routeName = it },
                label = { Text("Route Name") },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    if (barangay.isNotBlank()) {
                        HakotRepository.addSchedule(
                            ScheduleItem(
                                id = "sch_${System.currentTimeMillis() % 1000}",
                                barangay = barangay,
                                routeName = routeName.ifEmpty { "Barangays Route 1" },
                                date = LocalDate.now(),
                                startTime = LocalTime.of(8, 0),
                                endTime = LocalTime.of(11, 30),
                                wasteCategory = WasteCategory.GENERAL_WASTE,
                                truckAssigned = "Hakot Truck #1",
                                status = "Scheduled"
                            )
                        )
                        onDismiss()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = EcoGreenPrimary),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Schedule", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTruckModal(onDismiss: () -> Unit) {
    var truckCode by remember { mutableStateOf("") }
    var driverName by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("Add Garbage Truck", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextOnDarkPrimary)
            OutlinedTextField(
                value = truckCode,
                onValueChange = { truckCode = it },
                label = { Text("Truck Code (e.g. Hakot Truck #4)") },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
            OutlinedTextField(
                value = driverName,
                onValueChange = { driverName = it },
                label = { Text("Driver Name") },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    if (truckCode.isNotBlank()) {
                        HakotRepository.addTruck(
                            GarbageTruck(
                                id = "tr_${System.currentTimeMillis() % 1000}",
                                truckCode = truckCode,
                                plateNumber = "HKT-100",
                                driverName = driverName.ifEmpty { "Juan Santos" },
                                assignedRoute = "Barangays Route 1",
                                latitude = 14.1500,
                                longitude = 120.9840,
                                status = TruckStatus.ACTIVE_ON_ROUTE,
                                speedKmH = 20
                            )
                        )
                        onDismiss()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = EcoGreenPrimary),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Truck", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }
}
