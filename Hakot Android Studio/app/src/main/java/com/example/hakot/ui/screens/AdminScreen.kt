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
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hakot.DumpTruckLogo
import com.example.hakot.data.HakotRepository
import com.example.hakot.model.AnonymousReport
import com.example.hakot.model.ReportStatus
import com.example.hakot.model.UserRole
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

    val isAuthenticated = HakotRepository.isAuthenticated
    val userRole = HakotRepository.currentAdminRole

    // STEP 1: If no role selected, display Role Selection Screen
    if (selectedRoleMode == null && !isAuthenticated) {
        RoleSelectionScreen(
            onSelectRole = { role -> selectedRoleMode = role },
            onExitPortal = onExitAdminMode
        )
    }
    // STEP 2: Render Login Screen corresponding to chosen role
    else if (!isAuthenticated) {
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
    }
    // STEP 3: Render Authenticated Role-Based Dashboard
    else {
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
                            onRedeemCode = { showRedeemModal = true }
                        )
                        1 -> AdminRoutesView()
                        2 -> AdminSchedulesView()
                        3 -> AdminTrucksView()
                        4 -> AdminReportsView()
                        5 -> AdminAuditLogsView()
                    }
                }
            }
        }
    }

    // Modal Sheet: Redeem District Admin Invitation Code
    if (showRedeemModal) {
        RedeemInvitationModal(
            onDismiss = { showRedeemModal = false },
            onRedeemed = { showRedeemModal = false }
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
                // Back Button to Account Selection
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

                // Email Input
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

                // Password Input with Show/Hide Toggle
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

                // Sign In Button with Role Verification
                Button(
                    onClick = {
                        if (emailInput.isNotBlank() && passwordInput.isNotBlank()) {
                            val success = HakotRepository.login(emailInput, passwordInput)
                            if (success) {
                                val actualRole = HakotRepository.currentAdminRole
                                // Role verification check!
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
    onRedeemCode: () -> Unit
) {
    val admin = HakotRepository.currentDistrictAdmin
    val org = HakotRepository.activeOrganization

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 20.dp)
    ) {
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
fun AdminRoutesView() {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 20.dp)
    ) {
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
fun AdminSchedulesView() {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 20.dp)
    ) {
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
