package com.example.hakot.ui.screens

import android.widget.Toast
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hakot.DumpTruckLogo
import com.example.hakot.data.HakotRepository
import com.example.hakot.data.SupabaseClient
import com.example.hakot.model.AnonymousReport
import com.example.hakot.model.BarangayItem
import com.example.hakot.model.Dumpsite
import com.example.hakot.model.Dumptrack
import com.example.hakot.model.ReportStatus
import com.example.hakot.model.ScheduleItem
import com.example.hakot.model.UserRole
import com.example.hakot.model.WasteCategory
import com.example.hakot.ui.components.DarkMapView
import com.example.hakot.ui.theme.DarkBackground
import com.example.hakot.ui.theme.DarkBorder
import com.example.hakot.ui.theme.DarkSurface
import com.example.hakot.ui.theme.DarkSurfaceCard
import com.example.hakot.ui.theme.DarkSurfaceVariant
import com.example.hakot.ui.theme.EcoGreenContainer
import com.example.hakot.ui.theme.EcoGreenLight
import com.example.hakot.ui.theme.EcoGreenPrimary
import com.example.hakot.ui.theme.HazardousRed
import com.example.hakot.ui.theme.RecycleYellow
import com.example.hakot.ui.theme.TextOnDarkMuted
import com.example.hakot.ui.theme.TextOnDarkPrimary
import com.example.hakot.ui.theme.TextOnDarkSecondary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
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
    var selectedRoleMode by remember { mutableStateOf<UserRole?>(null) }
    var showRedeemModal by remember { mutableStateOf(false) }
    var showCreateScheduleModal by remember { mutableStateOf(false) }

    // Phase 1: Map Route Drawing & Review State
    var showRouteSketchModal by remember { mutableStateOf(false) }
    var showRouteReviewModal by remember { mutableStateOf(false) }
    var isDrawingRouteMode by remember { mutableStateOf(false) }
    var capturedSketchPoints by remember { mutableStateOf<List<GeoPoint>>(emptyList()) }

    val isAuthenticated = HakotRepository.isAuthenticated
    val userRole = HakotRepository.currentAdminRole

    // Periodically verify Supabase Connection Status
    LaunchedEffect(Unit) {
        SupabaseClient.checkConnection()
    }

    if (selectedRoleMode == null && !isAuthenticated) {
        RoleSelectionScreen(
            onSelectRole = { role -> selectedRoleMode = role },
            onExitPortal = onExitAdminMode
        )
    } else if (!isAuthenticated) {
        when (selectedRoleMode) {
            UserRole.DISTRICT_ADMIN -> AdminLoginScreen(
                targetRole = UserRole.DISTRICT_ADMIN,
                onBackToRoleSelection = { selectedRoleMode = null },
                onOpenRedeemModal = { showRedeemModal = true },
                onExitAdminPortal = onExitAdminMode
            )
            UserRole.DRIVER -> AdminLoginScreen(
                targetRole = UserRole.DRIVER,
                onBackToRoleSelection = { selectedRoleMode = null },
                onOpenRedeemModal = { showRedeemModal = true },
                onExitAdminPortal = onExitAdminMode
            )
            else -> RoleSelectionScreen(
                onSelectRole = { role -> selectedRoleMode = role },
                onExitPortal = onExitAdminMode
            )
        }
    } else {
        if (userRole == UserRole.DRIVER) {
            DriverDashboardView(onSignOut = {
                HakotRepository.logout()
                selectedRoleMode = null
            })
        } else {
            val adminTabs = listOf("District Overview", "Collection Routes", "Schedules", "Fleet Trucks", "Citizen Reports", "Audit History")

            Column(
                modifier = modifier
                    .fillMaxSize()
                    .background(DarkBackground)
            ) {
                // Top Admin Banner with Sign Out
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
                                contentDescription = "District Admin",
                                tint = EcoGreenPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "DISTRICT ADMIN PORTAL",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextOnDarkPrimary
                                )
                                Text(
                                    text = HakotRepository.currentDistrictAdmin.districtName,
                                    fontSize = 11.sp,
                                    color = EcoGreenLight
                                )
                            }
                        }

                        Button(
                            onClick = {
                                HakotRepository.logout()
                                selectedRoleMode = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceCard),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Logout, contentDescription = "Logout", tint = HazardousRed, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Sign Out", fontSize = 11.sp, color = TextOnDarkPrimary)
                        }
                    }
                }

                // Live Supabase Cloud Connection Status Card
                Surface(
                    color = DarkSurfaceCard,
                    border = BorderStroke(1.dp, DarkBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "⚡ SUPABASE CLOUD: ${SupabaseClient.connectionState}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = EcoGreenLight
                            )
                            Text(
                                text = "Project: vyegsnkerrbdqacwegff • ${SupabaseClient.lastSyncTimestamp}",
                                fontSize = 10.sp,
                                color = TextOnDarkSecondary
                            )
                        }

                        val scope = rememberCoroutineScope()
                        val context = LocalContext.current
                        Button(
                            onClick = {
                                scope.launch {
                                    val res = SupabaseClient.testLiveReadWrite()
                                    Toast.makeText(context, res.second, Toast.LENGTH_LONG).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = EcoGreenPrimary),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("Test Supabase DB", fontSize = 10.sp, color = Color.Black, fontWeight = FontWeight.Bold)
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
                                    fontSize = 12.sp,
                                    fontWeight = if (selectedAdminTab == index) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        )
                    }
                }

                // Admin Tab Content Views
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    when (selectedAdminTab) {
                        0 -> DistrictOverviewView(
                            onCreateSchedule = { showCreateScheduleModal = true },
                            onOpenSketchRoute = {
                                isDrawingRouteMode = true
                                showRouteSketchModal = true
                            },
                            onRedeemCode = { showRedeemModal = true }
                        )
                        1 -> AdminRoutesView(
                            onOpenSketchRoute = {
                                isDrawingRouteMode = true
                                showRouteSketchModal = true
                            }
                        )
                        2 -> AdminSchedulesView(onCreateSchedule = { showCreateScheduleModal = true })
                        3 -> AdminTrucksView()
                        4 -> AdminReportsView()
                        5 -> AdminAuditLogsView()
                    }
                }
            }
        }
    }

    if (showRedeemModal) {
        RedeemInvitationModal(
            onDismiss = { showRedeemModal = false },
            onRedeemed = { showRedeemModal = false }
        )
    }

    if (showCreateScheduleModal) {
        CreateScheduleWizardModal(
            onDismiss = { showCreateScheduleModal = false },
            onPublished = { showCreateScheduleModal = false }
        )
    }

    // Phase 1 Modal: Full-Screen Route Drawing Canvas
    if (showRouteSketchModal) {
        AdminRouteSketchingModal(
            isDrawingRouteMode = isDrawingRouteMode,
            sketchedPoints = capturedSketchPoints,
            onSketchPointsUpdated = { points ->
                capturedSketchPoints = points
            },
            onUndoSketch = {
                if (capturedSketchPoints.isNotEmpty()) {
                    val removeCount = minOf(10, capturedSketchPoints.size)
                    capturedSketchPoints = capturedSketchPoints.dropLast(removeCount)
                }
            },
            onClearSketch = {
                capturedSketchPoints = emptyList()
            },
            onFinishSketch = {
                isDrawingRouteMode = false
                showRouteSketchModal = false
                showRouteReviewModal = true // Opens Review Screen with captured points!
            },
            onCancel = {
                isDrawingRouteMode = false
                showRouteSketchModal = false
            }
        )
    }

    // Phase 1 Modal: Post-Finish Route Sketch Review Screen
    if (showRouteReviewModal) {
        AdminRouteReviewModal(
            capturedPoints = capturedSketchPoints,
            onEditSketch = {
                showRouteReviewModal = false
                isDrawingRouteMode = true
                showRouteSketchModal = true
            },
            onCancel = {
                capturedSketchPoints = emptyList()
                showRouteReviewModal = false
            }
        )
    }
}

@Composable
fun RoleSelectionScreen(
    onSelectRole: (UserRole) -> Unit,
    onExitPortal: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            DumpTruckLogo()

            Spacer(modifier = Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("HAK", fontSize = 28.sp, fontWeight = FontWeight.Black, color = Color.White)
                Icon(imageVector = Icons.Default.LocationOn, contentDescription = "Pin", tint = EcoGreenPrimary, modifier = Modifier.size(24.dp))
                Text("T", fontSize = 28.sp, fontWeight = FontWeight.Black, color = Color.White)
            }
            Text("PROTECTED ACCESS PORTAL", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, color = EcoGreenLight)

            Spacer(modifier = Modifier.height(24.dp))

            Text("CHOOSE YOUR ACCOUNT TYPE FIRST:", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = TextOnDarkMuted)

            Spacer(modifier = Modifier.height(14.dp))

            // Option 1: ADMINISTRATOR
            Card(
                onClick = { onSelectRole(UserRole.DISTRICT_ADMIN) },
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.5.dp, EcoGreenPrimary, RoundedCornerShape(18.dp))
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(EcoGreenContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = Icons.Default.Shield, contentDescription = "Admin", tint = EcoGreenPrimary, modifier = Modifier.size(26.dp))
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("ADMINISTRATOR", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextOnDarkPrimary)
                        Text("Manage district operations, schedules, trucks, reports, and municipal functions.", fontSize = 11.sp, color = TextOnDarkSecondary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Option 2: HAKOT TRUCKER
            Card(
                onClick = { onSelectRole(UserRole.DRIVER) },
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.5.dp, RecycleYellow, RoundedCornerShape(18.dp))
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(RecycleYellow.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = Icons.Default.DirectionsBus, contentDescription = "Trucker", tint = RecycleYellow, modifier = Modifier.size(26.dp))
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("HAKOT TRUCKER", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextOnDarkPrimary)
                        Text("Access assigned collection schedules, routes, vehicle info, and collection operations.", fontSize = 11.sp, color = TextOnDarkSecondary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            TextButton(onClick = onExitPortal) {
                Text("Return to Public Citizen Mode", fontSize = 12.sp, color = TextOnDarkMuted)
            }
        }
    }
}

@Composable
fun AdminLoginScreen(
    targetRole: UserRole,
    onBackToRoleSelection: () -> Unit,
    onOpenRedeemModal: () -> Unit,
    onExitAdminPortal: () -> Unit
) {
    val context = LocalContext.current
    var emailInput by remember { mutableStateOf(if (targetRole == UserRole.DRIVER) "driver@hakot.gov.ph" else "m.lopez@nasugbu.gov.ph") }
    var passwordInput by remember { mutableStateOf("admin123") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .border(1.dp, DarkBorder, RoundedCornerShape(22.dp))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start
                ) {
                    TextButton(onClick = onBackToRoleSelection) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = EcoGreenLight, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("← Back to Role Selection", fontSize = 11.sp, color = EcoGreenLight)
                    }
                }

                DumpTruckLogo()

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = if (targetRole == UserRole.DRIVER) "HAKOT TRUCKER LOGIN" else "DISTRICT ADMIN LOGIN",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (targetRole == UserRole.DRIVER) RecycleYellow else EcoGreenLight
                )
                Text(
                    text = "Enter your credentials to access ${targetRole.label} functions",
                    fontSize = 11.sp,
                    color = TextOnDarkSecondary
                )

                Spacer(modifier = Modifier.height(18.dp))

                OutlinedTextField(
                    value = emailInput,
                    onValueChange = {
                        emailInput = it
                        errorMessage = null
                    },
                    leadingIcon = { Icon(imageVector = Icons.Default.Mail, contentDescription = "Email", tint = TextOnDarkSecondary) },
                    placeholder = { Text("user@hakot.gov.ph", color = TextOnDarkMuted) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EcoGreenPrimary,
                        unfocusedBorderColor = DarkBorder,
                        focusedTextColor = TextOnDarkPrimary,
                        unfocusedTextColor = TextOnDarkPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = passwordInput,
                    onValueChange = {
                        passwordInput = it
                        errorMessage = null
                    },
                    leadingIcon = { Icon(imageVector = Icons.Default.Lock, contentDescription = "Password", tint = TextOnDarkSecondary) },
                    trailingIcon = {
                        IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                            Icon(
                                imageVector = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = "Toggle Password",
                                tint = TextOnDarkSecondary
                            )
                        }
                    },
                    visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    placeholder = { Text("••••••••", color = TextOnDarkMuted) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EcoGreenPrimary,
                        unfocusedBorderColor = DarkBorder,
                        focusedTextColor = TextOnDarkPrimary,
                        unfocusedTextColor = TextOnDarkPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                errorMessage?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(it, color = HazardousRed, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(18.dp))

                Button(
                    onClick = {
                        if (emailInput.isNotBlank() && passwordInput.isNotBlank()) {
                            val success = HakotRepository.login(emailInput, passwordInput)
                            if (success) {
                                val actualRole = HakotRepository.currentAdminRole
                                if (actualRole != targetRole && targetRole == UserRole.DISTRICT_ADMIN) {
                                    HakotRepository.logout()
                                    errorMessage = "Access Denied: Account is registered as a Trucker. Please select HAKOT TRUCKER mode."
                                } else if (actualRole != targetRole && targetRole == UserRole.DRIVER) {
                                    HakotRepository.logout()
                                    errorMessage = "Access Denied: Account is registered as a District Admin. Please select ADMINISTRATOR mode."
                                } else {
                                    Toast.makeText(context, "Sign In Successful!", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                errorMessage = "Invalid credentials or inactive account."
                            }
                        } else {
                            errorMessage = "Please enter email and password."
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = if (targetRole == UserRole.DRIVER) RecycleYellow else EcoGreenPrimary),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text("Sign In as ${if (targetRole == UserRole.DRIVER) "Trucker" else "Admin"}", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (targetRole == UserRole.DISTRICT_ADMIN) {
                    TextButton(onClick = onOpenRedeemModal) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Key, contentDescription = "Code", tint = EcoGreenPrimary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Redeem District Invitation Code", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = EcoGreenPrimary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DriverDashboardView(onSignOut: () -> Unit) {
    val driver = HakotRepository.authenticatedUser
    val truck = HakotRepository.trucks.firstOrNull()

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(16.dp),
        modifier = Modifier.fillMaxSize().background(DarkBackground)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = EcoGreenContainer),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth().border(1.dp, EcoGreenPrimary, RoundedCornerShape(18.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("TRUCK DRIVER OPERATOR PORTAL", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = EcoGreenLight)
                            Text(driver?.fullName ?: "Mario Santos", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextOnDarkPrimary)
                        }

                        Button(
                            onClick = onSignOut,
                            colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceCard),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Sign Out", fontSize = 11.sp, color = TextOnDarkPrimary)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text("🚚 Assigned Vehicle: ${truck?.truckCode} (${truck?.plateNumber})", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextOnDarkPrimary)
                    Text("📍 Route: ${truck?.assignedRoute}", fontSize = 12.sp, color = EcoGreenLight)
                    Text("🏁 Destination: ${truck?.destinationDumpsite}", fontSize = 12.sp, color = TextOnDarkSecondary)
                }
            }
        }

        item {
            Text("TODAY'S DRIVER COLLECTION SCHEDULE WINDOW", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = TextOnDarkMuted)
        }

        items(HakotRepository.schedules) { sch ->
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().border(1.dp, DarkBorder, RoundedCornerShape(14.dp))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(sch.routeName, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextOnDarkPrimary)
                    Text("📍 ${sch.barangay}", fontSize = 12.sp, color = EcoGreenLight)
                    Text("Time Window: 8:00 AM - 11:30 AM • Status: ${sch.status}", fontSize = 11.sp, color = TextOnDarkSecondary)
                }
            }
        }
    }
}

@Composable
fun MetricCard(
    title: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, DarkBorder),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(title, fontSize = 10.sp, color = TextOnDarkMuted)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
fun DistrictOverviewView(
    onCreateSchedule: () -> Unit,
    onOpenSketchRoute: () -> Unit,
    onRedeemCode: () -> Unit
) {
    val admin = HakotRepository.currentDistrictAdmin
    val org = HakotRepository.activeOrganization

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 20.dp)
    ) {
        // PROMINENT + CREATE NEW SCHEDULE PRIMARY ACTION CARD
        item {
            Card(
                onClick = onCreateSchedule,
                colors = CardDefaults.cardColors(containerColor = EcoGreenContainer),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(2.dp, EcoGreenPrimary, RoundedCornerShape(18.dp))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(EcoGreenPrimary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Create", tint = Color.Black, modifier = Modifier.size(28.dp))
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("+ CREATE NEW SCHEDULE", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = EcoGreenLight)
                        Text("6-Step Guided Wizard: Date, Barangay, Street Segments, Vehicle & Dumpsite", fontSize = 11.sp, color = TextOnDarkPrimary)
                    }
                }
            }
        }

        // PHASE 1 ACTION CARD: SKETCH NEW ROUTE ON MAP
        item {
            Card(
                onClick = onOpenSketchRoute,
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.5.dp, EcoGreenPrimary, RoundedCornerShape(18.dp))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(EcoGreenContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = Icons.Default.Create, contentDescription = "Sketch Route", tint = EcoGreenPrimary, modifier = Modifier.size(24.dp))
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("✏️ SKETCH NEW ROUTE ON MAP", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextOnDarkPrimary)
                        Text("Draw a freehand route directly over the Nasugbu GIS map", fontSize = 11.sp, color = TextOnDarkSecondary)
                    }
                }
            }
        }

        // District Profile Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, EcoGreenPrimary.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = admin.fullName,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextOnDarkPrimary
                            )
                            Text(
                                text = admin.email,
                                fontSize = 12.sp,
                                color = TextOnDarkSecondary
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(EcoGreenContainer)
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(admin.status.label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = EcoGreenLight)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "🏛️ Assigned Municipality: ${org.name}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextOnDarkPrimary
                    )
                    Text(
                        text = "📍 Scope: ${admin.districtName}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = EcoGreenLight
                    )
                }
            }
        }

        // Summary Metric Cards
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricCard(title = "Barangays", value = "${org.coveredBarangays.size} Covered", color = EcoGreenPrimary, modifier = Modifier.weight(1f))
                MetricCard(title = "Assigned Trucks", value = "${HakotRepository.trucks.size} Trucks", color = RecycleYellow, modifier = Modifier.weight(1f))
                MetricCard(title = "Active Routes", value = "${HakotRepository.routes.size} Corridors", color = EcoGreenLight, modifier = Modifier.weight(1f))
            }
        }

        // Action: Redeem District Invitation Code Button
        item {
            Button(
                onClick = onRedeemCode,
                colors = ButtonDefaults.buttonColors(containerColor = EcoGreenPrimary),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Key,
                    contentDescription = "Key",
                    tint = Color.Black,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Redeem District Invitation Code", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }

        // Authorized Corridors List
        item {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "AUTHORIZED DISTRICT COLLECTION CORRIDORS",
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextOnDarkMuted
            )
        }

        items(HakotRepository.routes) { route ->
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().border(1.dp, DarkBorder, RoundedCornerShape(14.dp))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(route.routeName, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextOnDarkPrimary)
                    Text("📍 ${route.barangaysCovered}", fontSize = 11.sp, color = TextOnDarkSecondary)
                }
            }
        }
    }
}

// Phase 1 Component: Full-Screen Map Drawing Canvas Overlay
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminRouteSketchingModal(
    isDrawingRouteMode: Boolean,
    sketchedPoints: List<GeoPoint>,
    onSketchPointsUpdated: (List<GeoPoint>) -> Unit,
    onUndoSketch: () -> Unit,
    onClearSketch: () -> Unit,
    onFinishSketch: () -> Unit,
    onCancel: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // Embeds DarkMapView with live drawing mode & point callbacks!
        DarkMapView(
            isDrawingRouteMode = isDrawingRouteMode,
            sketchedPoints = sketchedPoints,
            onSketchPointsUpdated = onSketchPointsUpdated,
            onUndoSketch = onUndoSketch,
            onClearSketch = onClearSketch,
            onFinishSketch = onFinishSketch,
            modifier = Modifier.fillMaxSize()
        )

        // Close / Cancel Overlay Button
        Surface(
            onClick = onCancel,
            color = DarkSurface.copy(alpha = 0.9f),
            shape = CircleShape,
            border = BorderStroke(1.dp, DarkBorder),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(imageVector = Icons.Default.Close, contentDescription = "Cancel Drawing", tint = TextOnDarkPrimary, modifier = Modifier.size(20.dp))
            }
        }
    }
}

// Phase 1 Component: Route Sketch Review Screen
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminRouteReviewModal(
    capturedPoints: List<GeoPoint>,
    onEditSketch: () -> Unit,
    onCancel: () -> Unit
) {
    var routeNameInput by remember { mutableStateOf("New Nasugbu Corridor") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onCancel,
        sheetState = sheetState,
        containerColor = DarkSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "✏️ Review Captured Route Sketch",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = EcoGreenLight
                    )
                    Text(
                        text = "Captured ${capturedPoints.size} WGS84 GeoPoints from Map Sketch",
                        fontSize = 11.sp,
                        color = TextOnDarkSecondary
                    )
                }

                IconButton(onClick = onCancel) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = TextOnDarkSecondary)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text("Route Name / Label:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextOnDarkPrimary)
            OutlinedTextField(
                value = routeNameInput,
                onValueChange = { routeNameInput = it },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = EcoGreenPrimary,
                    unfocusedBorderColor = DarkBorder,
                    focusedTextColor = TextOnDarkPrimary,
                    unfocusedTextColor = TextOnDarkPrimary
                ),
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Action: Edit Sketch on Map
            Button(
                onClick = onEditSketch,
                colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceCard),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, EcoGreenPrimary),
                modifier = Modifier.fillMaxWidth().height(46.dp)
            ) {
                Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit", tint = EcoGreenPrimary, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Edit Sketch on Map", fontSize = 13.sp, color = TextOnDarkPrimary, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action: Discard Sketch
            TextButton(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Discard Sketch & Return", fontSize = 12.sp, color = HazardousRed)
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateScheduleWizardModal(
    onDismiss: () -> Unit,
    onPublished: () -> Unit
) {
    val context = LocalContext.current
    var currentStep by remember { mutableIntStateOf(1) }
    var isLoadingFromSupabase by remember { mutableStateOf(true) }

    var selectedBarangayItem by remember { mutableStateOf(BarangayItem("brgy_041019001", "Poblacion 1")) }
    var selectedCategory by remember { mutableStateOf(WasteCategory.GENERAL_WASTE) }
    var selectedTruckPlate by remember { mutableStateOf("ABK-8891") }
    var selectedDumpsiteName by remember { mutableStateOf("Nasugbu MRF Facility") }
    var selectedStreetNames by remember { mutableStateOf(listOf("C. Alvarez St", "J.P. Laurel St", "F. Alix St")) }

    // Dynamic Supabase / GeoJSON Database Options
    var dbBarangayItems by remember { mutableStateOf<List<BarangayItem>>(emptyList()) }

    // Fetch Database Records Asynchronously from PostGIS GeoJSON & Supabase Rest Endpoint
    LaunchedEffect(Unit) {
        isLoadingFromSupabase = true
        try {
            if (!HakotRepository.isGisLoaded) {
                HakotRepository.loadNasugbuGisData()
            }

            val barangayGeoJson = HakotRepository.barangayGeoJson
            if (!barangayGeoJson.isNullOrBlank()) {
                val json = JSONObject(barangayGeoJson)
                val features = json.getJSONArray("features")
                val itemsList = mutableListOf<BarangayItem>()
                for (i in 0 until features.length()) {
                    val feat = features.getJSONObject(i)
                    val props = feat.getJSONObject("properties")
                    val id = if (props.has("id")) props.getString("id") else "brgy_$i"
                    val name = if (props.has("name")) props.getString("name") else "Barangay $i"
                    itemsList.add(BarangayItem(id = id, name = name))
                }
                itemsList.sortBy { it.name }
                if (itemsList.isNotEmpty()) {
                    dbBarangayItems = itemsList
                    selectedBarangayItem = itemsList.first()
                }
            } else {
                val records = SupabaseClient.fetchTableRecords("barangays")
                if (records.length() > 0) {
                    val list = mutableListOf<BarangayItem>()
                    for (i in 0 until records.length()) {
                        val obj = records.getJSONObject(i)
                        val id = if (obj.has("id")) obj.getString("id") else "brgy_$i"
                        val name = if (obj.has("name")) obj.getString("name") else "Barangay $i"
                        list.add(BarangayItem(id = id, name = name))
                    }
                    list.sortBy { it.name }
                    if (list.isNotEmpty()) {
                        dbBarangayItems = list
                        selectedBarangayItem = list.first()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            isLoadingFromSupabase = false
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DarkSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "📅 + CREATE NEW SCHEDULE (Step $currentStep/6)",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = EcoGreenLight
                    )
                    Text(
                        text = "Database-Driven • District 1 Scope",
                        fontSize = 11.sp,
                        color = TextOnDarkSecondary
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = TextOnDarkSecondary)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (isLoadingFromSupabase) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(color = EcoGreenPrimary, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Fetching Records from Supabase Database...", fontSize = 12.sp, color = EcoGreenLight)
                }
            }

            when (currentStep) {
                1 -> {
                    Text("Step 1 — Time & Waste Category", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextOnDarkPrimary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Time Window: 8:00 AM - 11:30 AM (Standard Shift)", fontSize = 12.sp, color = TextOnDarkSecondary)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Select Waste Category:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextOnDarkMuted)
                    WasteCategory.entries.forEach { cat ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selectedCategory == cat) EcoGreenContainer else DarkSurfaceCard)
                                .clickable { selectedCategory = cat }
                                .padding(10.dp)
                        ) {
                            Text(cat.displayName, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextOnDarkPrimary)
                        }
                    }
                }
                2 -> {
                    Text("Step 2 — Select Barangay Scope (${dbBarangayItems.size} Barangays)", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextOnDarkPrimary)
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(dbBarangayItems) { bgy ->
                            val isSelected = selectedBarangayItem.id == bgy.id
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) EcoGreenContainer else DarkSurfaceCard)
                                    .clickable { selectedBarangayItem = bgy }
                                    .padding(10.dp)
                            ) {
                                Text(
                                    text = "📍 ${bgy.name}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) EcoGreenLight else TextOnDarkPrimary
                                )
                            }
                        }
                    }
                }
                3 -> {
                    Text("Step 3 — Select Street Segments", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextOnDarkPrimary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Selected ${selectedStreetNames.size} connected street segments:", fontSize = 12.sp, color = EcoGreenLight)
                    selectedStreetNames.forEach { st ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(DarkSurfaceCard)
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("🛣️ $st", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextOnDarkPrimary)
                            Text("Connected Segment ✓", fontSize = 10.sp, color = EcoGreenPrimary)
                        }
                    }
                }
                4 -> {
                    Text("Step 4 — Assign Truck & Driver", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextOnDarkPrimary)
                    Spacer(modifier = Modifier.height(8.dp))
                    val trucksList = HakotRepository.trucks.ifEmpty {
                        listOf(Dumptrack(plateNumber = "ABK-8891", truckCode = "Hakot Truck #1", driverName = "Mario Santos", assignedRoute = "C. Alvarez to F. Alix Corridor", latitude = 14.0715, longitude = 120.6255))
                    }
                    trucksList.forEach { tr ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selectedTruckPlate == tr.plateNumber) EcoGreenContainer else DarkSurfaceCard)
                                .clickable { selectedTruckPlate = tr.plateNumber }
                                .padding(10.dp)
                        ) {
                            Text("🚚 ${tr.truckCode} (${tr.plateNumber}) - Driver: ${tr.driverName}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextOnDarkPrimary)
                        }
                    }
                }
                5 -> {
                    Text("Step 5 — Select Destination Dumpsite", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextOnDarkPrimary)
                    Spacer(modifier = Modifier.height(8.dp))
                    val dumpsitesList = HakotRepository.dumpsites.ifEmpty {
                        listOf(Dumpsite(name = "Nasugbu MRF Facility", latitude = 14.0755, longitude = 120.6350))
                    }
                    dumpsitesList.forEach { ds ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selectedDumpsiteName == ds.name) EcoGreenContainer else DarkSurfaceCard)
                                .clickable { selectedDumpsiteName = ds.name }
                                .padding(10.dp)
                        ) {
                            Text("🏭 ${ds.name} (${ds.type})", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextOnDarkPrimary)
                        }
                    }
                }
                6 -> {
                    Text("Step 6 — Summary & Database Publish", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextOnDarkPrimary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Date: Today (${LocalDate.now()})", fontSize = 12.sp, color = TextOnDarkPrimary)
                            Text("Barangay Scope: ${selectedBarangayItem.name} (UUID: ${selectedBarangayItem.id})", fontSize = 12.sp, color = TextOnDarkPrimary)
                            Text("Streets: ${selectedStreetNames.joinToString(" → ")}", fontSize = 12.sp, color = EcoGreenLight)
                            Text("Category: ${selectedCategory.displayName}", fontSize = 12.sp, color = TextOnDarkPrimary)
                            Text("Vehicle: $selectedTruckPlate", fontSize = 12.sp, color = TextOnDarkPrimary)
                            Text("Dumpsite: $selectedDumpsiteName", fontSize = 12.sp, color = TextOnDarkPrimary)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (currentStep > 1) {
                    Button(
                        onClick = { currentStep-- },
                        colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceCard)
                    ) {
                        Text("Previous", color = TextOnDarkPrimary)
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                if (currentStep < 6) {
                    Button(
                        onClick = { currentStep++ },
                        colors = ButtonDefaults.buttonColors(containerColor = EcoGreenPrimary)
                    ) {
                        Text("Next Step", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = {
                            val scheduleId = "sch_${System.currentTimeMillis() % 10000}"
                            val newItem = ScheduleItem(
                                id = scheduleId,
                                barangay = "${selectedBarangayItem.name} (${selectedStreetNames.joinToString(" → ")})",
                                routeName = "${selectedStreetNames.first()} to ${selectedStreetNames.last()} Corridor",
                                date = LocalDate.now(),
                                startTime = LocalTime.of(8, 0),
                                endTime = LocalTime.of(11, 30),
                                wasteCategory = selectedCategory,
                                truckAssigned = "Hakot Truck #1 ($selectedTruckPlate)",
                                status = "On Track",
                                barangayId = selectedBarangayItem.id
                            )
                            HakotRepository.addSchedule(newItem)

                            // Transactional Supabase Database Insert using verified UUID primary key
                            CoroutineScope(Dispatchers.IO).launch {
                                try {
                                    val jsonPayload = JSONObject().apply {
                                        put("id", scheduleId)
                                        put("district_id", "dist_1")
                                        put("barangay_id", selectedBarangayItem.id) // Actual UUID Primary Key
                                        put("barangay", selectedBarangayItem.name)
                                        put("route_name", newItem.routeName)
                                        put("schedule_date", LocalDate.now().toString())
                                        put("start_time", "08:00:00")
                                        put("end_time", "11:30:00")
                                        put("waste_category", selectedCategory.name)
                                        put("truck_assigned", newItem.truckAssigned)
                                        put("status", "On Track")
                                    }
                                    SupabaseClient.insertTableRecord("schedules", jsonPayload)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }

                            Toast.makeText(context, "Schedule Published & Saved to Supabase Database!", Toast.LENGTH_LONG).show()
                            onPublished()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = EcoGreenPrimary)
                    ) {
                        Text("Publish & Save to Supabase", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RedeemInvitationModal(
    onDismiss: () -> Unit,
    onRedeemed: () -> Unit
) {
    val context = LocalContext.current
    var codeInput by remember { mutableStateOf("HAKOT-D7K9-M2QP-X8RN") }
    var emailInput by remember { mutableStateOf("sanitation.head@nasugbu.gov.ph") }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DarkSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            Text(
                text = "Redeem District Admin Invitation Code",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextOnDarkPrimary
            )
            Text(
                text = "Enter the unique invitation code provided by your Municipal Sanitation Authority.",
                fontSize = 11.sp,
                color = TextOnDarkSecondary,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text("Administrator Email:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextOnDarkPrimary)
            OutlinedTextField(
                value = emailInput,
                onValueChange = { emailInput = it },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = EcoGreenPrimary,
                    unfocusedBorderColor = DarkBorder,
                    focusedTextColor = TextOnDarkPrimary,
                    unfocusedTextColor = TextOnDarkPrimary
                ),
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text("Invitation Code (HAKOT-XXXX-YYYY):", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextOnDarkPrimary)
            OutlinedTextField(
                value = codeInput,
                onValueChange = { codeInput = it },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = EcoGreenPrimary,
                    unfocusedBorderColor = DarkBorder,
                    focusedTextColor = TextOnDarkPrimary,
                    unfocusedTextColor = TextOnDarkPrimary
                ),
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    val success = HakotRepository.redeemInvitation(codeInput, emailInput)
                    if (success) {
                        Toast.makeText(context, "Code Redeemed! District Account Activated", Toast.LENGTH_LONG).show()
                        onRedeemed()
                    } else {
                        Toast.makeText(context, "Invalid or Expired Code", Toast.LENGTH_SHORT).show()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = EcoGreenPrimary),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text("Redeem & Activate District Scope", color = Color.Black, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
fun AdminRoutesView(
    onOpenSketchRoute: () -> Unit = {}
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 20.dp)
    ) {
        item {
            Card(
                onClick = onOpenSketchRoute,
                colors = CardDefaults.cardColors(containerColor = EcoGreenContainer),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().border(1.dp, EcoGreenPrimary, RoundedCornerShape(14.dp))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.Create, contentDescription = "Sketch Route", tint = EcoGreenPrimary, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("✏️ SKETCH NEW ROUTE ON MAP", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = EcoGreenLight)
                }
            }
        }

        items(HakotRepository.routes) { route ->
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = route.routeName,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextOnDarkPrimary
                    )
                    Text(
                        text = "📍 ${route.barangaysCovered}",
                        fontSize = 12.sp,
                        color = TextOnDarkSecondary
                    )
                }
            }
        }
    }
}

@Composable
fun AdminSchedulesView(onCreateSchedule: () -> Unit) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 20.dp)
    ) {
        item {
            Card(
                onClick = onCreateSchedule,
                colors = CardDefaults.cardColors(containerColor = EcoGreenContainer),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().border(1.dp, EcoGreenPrimary, RoundedCornerShape(14.dp))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.Event, contentDescription = "Schedule", tint = EcoGreenPrimary, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("+ CREATE NEW SCHEDULE", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = EcoGreenLight)
                }
            }
        }

        items(HakotRepository.schedules) { sch ->
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = sch.routeName,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextOnDarkPrimary
                    )
                    Text(
                        text = "Assigned Truck: ${sch.truckAssigned} • Status: ${sch.status}",
                        fontSize = 12.sp,
                        color = TextOnDarkSecondary
                    )
                }
            }
        }
    }
}

@Composable
fun AdminTrucksView() {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 20.dp)
    ) {
        items(HakotRepository.trucks) { tr ->
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "${tr.truckCode} (${tr.plateNumber})",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextOnDarkPrimary
                    )
                    Text(
                        text = "Driver: ${tr.driverName} • Route: ${tr.assignedRoute}",
                        fontSize = 12.sp,
                        color = TextOnDarkSecondary
                    )
                }
            }
        }
    }
}

@Composable
fun AdminReportsView() {
    var selectedReportForChat by remember { mutableStateOf<AnonymousReport?>(null) }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 20.dp)
    ) {
        items(HakotRepository.reports) { rep ->
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { selectedReportForChat = rep }
                    .border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = rep.referenceNumber,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = EcoGreenLight
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = rep.reportType.title,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = HazardousRed
                            )
                        }

                        // Report Status Tag
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(EcoGreenContainer)
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(rep.status.label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = EcoGreenLight)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "📍 ${rep.locationName}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextOnDarkPrimary
                    )
                    Text(
                        text = rep.description,
                        fontSize = 11.sp,
                        color = TextOnDarkSecondary
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Office Routing Tag & Chat Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Assigned: ${rep.assignedOffice}",
                            fontSize = 10.sp,
                            color = TextOnDarkMuted
                        )

                        Button(
                            onClick = { selectedReportForChat = rep },
                            colors = ButtonDefaults.buttonColors(containerColor = EcoGreenPrimary),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.Chat, contentDescription = "Chat", tint = Color.Black, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Open Anonymous Chat", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // Modal Sheet: Handler-Resident Anonymous Two-Way Chat & Status Manager
    selectedReportForChat?.let { rep ->
        AdminReportChatModal(
            report = rep,
            onDismiss = { selectedReportForChat = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminReportChatModal(
    report: AnonymousReport,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var messageInput by remember { mutableStateOf("") }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val reportMessages = remember(report.id) {
        HakotRepository.reportMessages.filter { it.reportId == report.id }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DarkSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 8.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Anonymous Incident Chat ${report.referenceNumber}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextOnDarkPrimary
                    )
                    Text(
                        text = "Routing: ${report.assignedOffice} • Resident Token: ${report.accessToken}",
                        fontSize = 11.sp,
                        color = TextOnDarkSecondary
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = TextOnDarkSecondary)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Status Pipeline Transition Buttons
            Text("Update Incident Status:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextOnDarkMuted)
            Row(
                modifier = Modifier.padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Button(
                    onClick = {
                        HakotRepository.updateReportStatus(report.id, ReportStatus.IN_PROGRESS, "Truck dispatched to location.")
                        Toast.makeText(context, "Status set to In Progress", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EcoGreenContainer),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("In Progress", fontSize = 10.sp, color = EcoGreenLight)
                }

                Button(
                    onClick = {
                        HakotRepository.updateReportStatus(report.id, ReportStatus.RESOLVED, "Waste issue inspected and cleared.")
                        Toast.makeText(context, "Report Resolved!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EcoGreenPrimary),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Resolve Issue", fontSize = 10.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Two-Way Messages Stream
            Text("Two-Way Anonymous Conversation:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextOnDarkMuted)
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .background(DarkSurfaceCard, RoundedCornerShape(12.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(reportMessages) { msg ->
                    val isMe = msg.senderType == "HANDLER"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isMe) EcoGreenContainer else DarkSurfaceVariant)
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Column {
                                Text(
                                    text = msg.senderName,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isMe) EcoGreenLight else RecycleYellow
                                )
                                Text(
                                    text = msg.text,
                                    fontSize = 12.sp,
                                    color = TextOnDarkPrimary
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Message Input & Send Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = messageInput,
                    onValueChange = { messageInput = it },
                    placeholder = { Text("Type reply to anonymous resident...", fontSize = 11.sp, color = TextOnDarkMuted) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EcoGreenPrimary,
                        unfocusedBorderColor = DarkBorder,
                        focusedTextColor = TextOnDarkPrimary,
                        unfocusedTextColor = TextOnDarkPrimary
                    ),
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (messageInput.isNotBlank()) {
                            HakotRepository.addReportMessage(
                                reportId = report.id,
                                senderType = "HANDLER",
                                senderName = "Municipal Sanitation Office",
                                text = messageInput
                            )
                            messageInput = ""
                        }
                    }
                ) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = EcoGreenPrimary)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
fun AdminAuditLogsView() {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 20.dp)
    ) {
        items(HakotRepository.auditLogs) { log ->
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().border(1.dp, DarkBorder, RoundedCornerShape(14.dp))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(log.actingUserEmail, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = EcoGreenLight)
                        Text(log.targetRecord, fontSize = 11.sp, color = TextOnDarkMuted)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(log.action, fontSize = 12.sp, color = TextOnDarkPrimary)
                }
            }
        }
    }
}
