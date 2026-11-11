package com.example.hakot.ui.screens

import android.util.Log
import android.util.Patterns
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.style.TextAlign
import com.example.hakot.ui.theme.RecycleYellowContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import com.example.hakot.model.AccountApplication
import com.example.hakot.model.AccountInvitation
import com.example.hakot.model.AdminStatus
import com.example.hakot.model.AdminUserProfile
import com.example.hakot.model.AnonymousReport
import com.example.hakot.model.ApplicationStatus
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
import kotlinx.coroutines.launch

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
    var showCreateAccountModal by remember { mutableStateOf(false) }
    var showRegistrationScreen by remember { mutableStateOf(false) }

    val isAuthenticated = HakotRepository.isAuthenticated
    val userRole = HakotRepository.currentAdminRole

    if (showRegistrationScreen) {
        AccountRegistrationScreen(
            onBackToLogin = { showRegistrationScreen = false }
        )
    } else if (selectedRoleMode == null && !isAuthenticated) {
        RoleSelectionScreen(
            onSelectRole = { role -> selectedRoleMode = role },
            onExitPortal = onExitAdminMode
        )
    } else if (!isAuthenticated) {
        when (selectedRoleMode) {
            UserRole.SYSTEM_OWNER -> AdminLoginScreen(
                targetRole = UserRole.SYSTEM_OWNER,
                onBackToRoleSelection = { selectedRoleMode = null },
                onOpenRedeemModal = { showRedeemModal = true },
                onOpenRegistration = { showRegistrationScreen = true },
                onExitAdminPortal = onExitAdminMode
            )
            UserRole.DISTRICT_ADMIN, UserRole.MUNICIPAL_ADMIN -> AdminLoginScreen(
                targetRole = UserRole.MUNICIPAL_ADMIN,
                onBackToRoleSelection = { selectedRoleMode = null },
                onOpenRedeemModal = { showRedeemModal = true },
                onOpenRegistration = { showRegistrationScreen = true },
                onExitAdminPortal = onExitAdminMode
            )
            UserRole.DRIVER -> AdminLoginScreen(
                targetRole = UserRole.DRIVER,
                onBackToRoleSelection = { selectedRoleMode = null },
                onOpenRedeemModal = { showRedeemModal = true },
                onOpenRegistration = { showRegistrationScreen = true },
                onExitAdminPortal = onExitAdminMode
            )
            else -> RoleSelectionScreen(
                onSelectRole = { role -> selectedRoleMode = role },
                onExitPortal = onExitAdminMode
            )
        }
    } else {
        val scope = rememberCoroutineScope()
        val context = LocalContext.current
        if (userRole == UserRole.DRIVER) {
            DriverDashboardView(onSignOut = {
                scope.launch {
                    HakotRepository.logout(context)
                    selectedRoleMode = null
                }
            })
        } else {
            val adminTabs = if (userRole == UserRole.SYSTEM_OWNER) {
                listOf(
                    "District Overview",
                    "Account Management",
                    "Account Applications",
                    "Collection Routes",
                    "Schedules",
                    "Fleet Trucks",
                    "Citizen Reports",
                    "Audit History"
                )
            } else {
                listOf(
                    "District Overview",
                    "Account Management",
                    "Collection Routes",
                    "Schedules",
                    "Fleet Trucks",
                    "Citizen Reports",
                    "Audit History"
                )
            }

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
                                    text = "MUNICIPAL SANITATION PORTAL",
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
                                scope.launch {
                                    HakotRepository.logout(context)
                                    selectedRoleMode = null
                                }
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
                        1 -> AdminAccountManagementView(
                            onOpenCreateModal = { showCreateAccountModal = true }
                        )
                        2 -> if (userRole == UserRole.SYSTEM_OWNER) {
                            SystemOwnerApplicationsReviewView()
                        } else {
                            AdminRoutesView()
                        }
                        3 -> if (userRole == UserRole.SYSTEM_OWNER) {
                            AdminRoutesView()
                        } else {
                            AdminSchedulesView()
                        }
                        4 -> if (userRole == UserRole.SYSTEM_OWNER) {
                            AdminSchedulesView()
                        } else {
                            AdminTrucksView()
                        }
                        5 -> if (userRole == UserRole.SYSTEM_OWNER) {
                            AdminTrucksView()
                        } else {
                            AdminReportsView()
                        }
                        6 -> if (userRole == UserRole.SYSTEM_OWNER) {
                            AdminReportsView()
                        } else {
                            AdminAuditLogsView()
                        }
                        7 -> if (userRole == UserRole.SYSTEM_OWNER) {
                            AdminAuditLogsView()
                        }
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

    if (showCreateAccountModal) {
        CreateAccountModal(
            onDismiss = { showCreateAccountModal = false },
            onCreated = { showCreateAccountModal = false }
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

            Card(
                onClick = { onSelectRole(UserRole.SYSTEM_OWNER) },
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
                            .background(RecycleYellowContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = Icons.Default.Key, contentDescription = "System Owner", tint = RecycleYellow, modifier = Modifier.size(28.dp))
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text("Platform System Owner", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextOnDarkPrimary)
                        Text("Full system control & pending account approvals", fontSize = 11.sp, color = TextOnDarkSecondary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Card(
                onClick = { onSelectRole(UserRole.MUNICIPAL_ADMIN) },
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
                        Icon(imageVector = Icons.Default.AdminPanelSettings, contentDescription = "Admin", tint = EcoGreenPrimary, modifier = Modifier.size(28.dp))
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text("Municipal / District Admin", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextOnDarkPrimary)
                        Text("Manage routes, trucks, schedules, & citizen reports", fontSize = 11.sp, color = TextOnDarkSecondary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Card(
                onClick = { onSelectRole(UserRole.DRIVER) },
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.5.dp, DarkBorder, RoundedCornerShape(18.dp))
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(DarkSurfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = Icons.Default.DirectionsBus, contentDescription = "Trucker", tint = RecycleYellow, modifier = Modifier.size(28.dp))
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text("Truck Operator / Driver", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextOnDarkPrimary)
                        Text("View assigned collection routes & send live GPS", fontSize = 11.sp, color = TextOnDarkSecondary)
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
fun SystemOwnerApplicationsReviewView() {
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    var isProcessingDecision by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    var selectedApplication by remember { mutableStateOf<AccountApplication?>(null) }
    var showApproveConfirm by remember { mutableStateOf(false) }
    var showRejectConfirm by remember { mutableStateOf(false) }

    val applications = HakotRepository.accountApplications

    fun refreshApplications() {
        scope.launch {
            isLoading = true
            errorMessage = null
            successMessage = null
            val res = HakotRepository.fetchAccountApplications()
            isLoading = false
            if (!res.first) {
                errorMessage = res.second
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshApplications()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "ACCOUNT APPLICATIONS REVIEW",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = EcoGreenLight
                )
                Text(
                    text = "Review and approve/reject pending municipal self-registration requests. (System Owner Only)",
                    fontSize = 11.sp,
                    color = TextOnDarkSecondary
                )
            }
            IconButton(
                enabled = !isLoading && !isProcessingDecision,
                onClick = { refreshApplications() }
            ) {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh", tint = EcoGreenLight)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        errorMessage?.let {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().border(1.dp, HazardousRed, RoundedCornerShape(10.dp))
            ) {
                Text(it, color = HazardousRed, fontSize = 12.sp, modifier = Modifier.padding(12.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        successMessage?.let {
            Card(
                colors = CardDefaults.cardColors(containerColor = EcoGreenContainer),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().border(1.dp, EcoGreenPrimary, RoundedCornerShape(10.dp))
            ) {
                Text(it, color = EcoGreenLight, fontSize = 12.sp, modifier = Modifier.padding(12.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = EcoGreenPrimary)
            }
        } else if (applications.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(imageVector = Icons.Default.Mail, contentDescription = "Empty", tint = TextOnDarkSecondary, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("No pending account applications found.", fontSize = 13.sp, color = TextOnDarkSecondary)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(applications) { app ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, DarkBorder, RoundedCornerShape(14.dp))
                            .clickable(enabled = !isProcessingDecision) { selectedApplication = app }
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(app.fullName, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextOnDarkPrimary)
                                Surface(
                                    color = when (app.status) {
                                        ApplicationStatus.PENDING -> RecycleYellowContainer
                                        ApplicationStatus.APPROVED -> EcoGreenContainer
                                        ApplicationStatus.REJECTED -> HazardousRed.copy(alpha = 0.2f)
                                    },
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = app.status.label,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = when (app.status) {
                                            ApplicationStatus.PENDING -> RecycleYellow
                                            ApplicationStatus.APPROVED -> EcoGreenLight
                                            ApplicationStatus.REJECTED -> HazardousRed
                                        },
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Email: ${app.email} • Contact: ${app.contactNumber}", fontSize = 11.sp, color = TextOnDarkSecondary)
                            Text("Office: ${app.officeDepartment} (${app.officialPosition})", fontSize = 11.sp, color = TextOnDarkSecondary)
                            Text("Requested Role: ${app.requestedRole.label}", fontSize = 11.sp, color = EcoGreenLight)

                            if (app.status == ApplicationStatus.PENDING) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    Button(
                                        enabled = !isProcessingDecision,
                                        onClick = {
                                            selectedApplication = app
                                            showRejectConfirm = true
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = HazardousRed.copy(alpha = 0.2f)),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text("Reject", color = HazardousRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(
                                        enabled = !isProcessingDecision,
                                        onClick = {
                                            selectedApplication = app
                                            showApproveConfirm = true
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = EcoGreenPrimary),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text("Approve", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showApproveConfirm && selectedApplication != null) {
        AlertDialog(
            onDismissRequest = {
                if (!isProcessingDecision) {
                    showApproveConfirm = false
                    selectedApplication = null
                }
            },
            title = { Text("Approve Application?") },
            text = { Text("Are you sure you want to approve ${selectedApplication?.fullName} for ${selectedApplication?.requestedRole?.label}?") },
            confirmButton = {
                Button(
                    enabled = !isProcessingDecision,
                    onClick = {
                        val app = selectedApplication ?: return@Button
                        scope.launch {
                            isProcessingDecision = true
                            errorMessage = null
                            successMessage = null
                            val res = HakotRepository.updateAccountApplicationStatus(app.applicationId, ApplicationStatus.APPROVED)
                            if (res.first) {
                                HakotRepository.fetchAccountApplications()
                                successMessage = "Application approved successfully."
                                showApproveConfirm = false
                                selectedApplication = null
                            } else {
                                errorMessage = res.second
                            }
                            isProcessingDecision = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EcoGreenPrimary)
                ) {
                    if (isProcessingDecision) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.Black,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Approve", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isProcessingDecision,
                    onClick = {
                        showApproveConfirm = false
                        selectedApplication = null
                    }
                ) {
                    Text("Cancel", color = TextOnDarkSecondary)
                }
            },
            containerColor = DarkSurfaceCard
        )
    }

    if (showRejectConfirm && selectedApplication != null) {
        AlertDialog(
            onDismissRequest = {
                if (!isProcessingDecision) {
                    showRejectConfirm = false
                    selectedApplication = null
                }
            },
            title = { Text("Reject Application?") },
            text = { Text("Are you sure you want to reject ${selectedApplication?.fullName}'s application?") },
            confirmButton = {
                Button(
                    enabled = !isProcessingDecision,
                    onClick = {
                        val app = selectedApplication ?: return@Button
                        scope.launch {
                            isProcessingDecision = true
                            errorMessage = null
                            successMessage = null
                            val res = HakotRepository.updateAccountApplicationStatus(app.applicationId, ApplicationStatus.REJECTED)
                            if (res.first) {
                                HakotRepository.fetchAccountApplications()
                                successMessage = "Application rejected successfully."
                                showRejectConfirm = false
                                selectedApplication = null
                            } else {
                                errorMessage = res.second
                            }
                            isProcessingDecision = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = HazardousRed)
                ) {
                    if (isProcessingDecision) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Reject", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isProcessingDecision,
                    onClick = {
                        showRejectConfirm = false
                        selectedApplication = null
                    }
                ) {
                    Text("Cancel", color = TextOnDarkSecondary)
                }
            },
            containerColor = DarkSurfaceCard
        )
    }
}

@Composable
fun AdminLoginScreen(
    targetRole: UserRole,
    onBackToRoleSelection: () -> Unit,
    onOpenRedeemModal: () -> Unit,
    onOpenRegistration: () -> Unit,
    onExitAdminPortal: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var emailInput by remember { mutableStateOf(if (targetRole == UserRole.DRIVER) "driver@hakot.gov.ph" else "m.lopez@nasugbu.gov.ph") }
    var passwordInput by remember { mutableStateOf("admin123") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
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
                    text = when (targetRole) {
                        UserRole.SYSTEM_OWNER -> "SYSTEM OWNER LOGIN"
                        UserRole.DRIVER -> "HAKOT TRUCKER LOGIN"
                        else -> "MUNICIPAL ADMIN LOGIN"
                    },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = when (targetRole) {
                        UserRole.SYSTEM_OWNER -> RecycleYellow
                        UserRole.DRIVER -> RecycleYellow
                        else -> EcoGreenLight
                    }
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
                            scope.launch {
                                isLoading = true
                                errorMessage = null
                                val success = HakotRepository.login(context, emailInput, passwordInput)
                                isLoading = false
                                if (success) {
                                    val actualRole = HakotRepository.currentAdminRole
                                    if (targetRole == UserRole.SYSTEM_OWNER && actualRole != UserRole.SYSTEM_OWNER) {
                                        HakotRepository.logout(context)
                                        errorMessage = "Access Denied: Portal restricted to Platform System Owners."
                                    } else if (targetRole == UserRole.MUNICIPAL_ADMIN && actualRole == UserRole.SYSTEM_OWNER) {
                                        HakotRepository.logout(context)
                                        errorMessage = "Access Denied: System Owners must sign in via the System Owner portal."
                                    } else if ((actualRole != UserRole.MUNICIPAL_ADMIN && actualRole != UserRole.DISTRICT_ADMIN && actualRole != UserRole.SYSTEM_OWNER) && (targetRole == UserRole.MUNICIPAL_ADMIN || targetRole == UserRole.DISTRICT_ADMIN)) {
                                        HakotRepository.logout(context)
                                        errorMessage = "Access Denied: Account is registered as a Trucker."
                                    } else if (actualRole != targetRole && targetRole == UserRole.DRIVER) {
                                        HakotRepository.logout(context)
                                        errorMessage = "Access Denied: Account is registered as an Administrator."
                                    } else {
                                        Toast.makeText(context, "Sign In Successful!", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    errorMessage = HakotRepository.lastLoginError ?: "Invalid credentials, inactive account, or network error."
                                }
                            }
                        } else {
                            errorMessage = "Please enter email and password."
                        }
                    },
                    enabled = !isLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = if (targetRole == UserRole.DRIVER || targetRole == UserRole.SYSTEM_OWNER) RecycleYellow else EcoGreenPrimary),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.Black, strokeWidth = 2.dp)
                    } else {
                        Text(
                            text = when (targetRole) {
                                UserRole.SYSTEM_OWNER -> "Sign In as System Owner"
                                UserRole.DRIVER -> "Sign In as Trucker"
                                else -> "Sign In as Admin"
                            },
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (targetRole == UserRole.MUNICIPAL_ADMIN || targetRole == UserRole.DISTRICT_ADMIN) {
                    TextButton(onClick = onOpenRedeemModal) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Key, contentDescription = "Code", tint = EcoGreenPrimary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Redeem Invitation Code", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = EcoGreenPrimary)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                TextButton(onClick = onOpenRegistration) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.PersonAdd, contentDescription = "Register", tint = EcoGreenLight, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("New Applicant? Register for Access", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = EcoGreenLight)
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
                Text("Redeem Invitation Code", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }

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



@Composable
fun AdminAccountManagementView(
    onOpenCreateModal: () -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedRoleFilter by remember { mutableStateOf<UserRole?>(null) }
    var selectedStatusFilter by remember { mutableStateOf<AdminStatus?>(null) }
    var selectedSubTab by remember { mutableIntStateOf(0) }

    var selectedAccountForDetails by remember { mutableStateOf<AdminUserProfile?>(null) }
    var selectedInvitationForDetails by remember { mutableStateOf<AccountInvitation?>(null) }

    val totalAdmins = HakotRepository.adminProfiles.count {
        it.role == UserRole.MUNICIPAL_ADMIN || it.role == UserRole.DISTRICT_ADMIN || it.role == UserRole.SYSTEM_OWNER
    }
    val activeCount = HakotRepository.adminProfiles.count { it.status == AdminStatus.ACTIVE }
    val pendingCount = HakotRepository.accountInvitations.count { it.status == AdminStatus.PENDING_INVITATION }
    val suspendedCount = HakotRepository.adminProfiles.count { it.status == AdminStatus.SUSPENDED }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 20.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricCard(title = "Total Admins", value = "$totalAdmins", color = EcoGreenPrimary, modifier = Modifier.weight(1f))
                MetricCard(title = "Active Accounts", value = "$activeCount", color = EcoGreenLight, modifier = Modifier.weight(1f))
                MetricCard(title = "Pending Invites", value = "$pendingCount", color = RecycleYellow, modifier = Modifier.weight(1f))
                MetricCard(title = "Suspended", value = "$suspendedCount", color = HazardousRed, modifier = Modifier.weight(1f))
            }
        }

        item {
            Card(
                onClick = onOpenCreateModal,
                colors = CardDefaults.cardColors(containerColor = EcoGreenContainer),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, EcoGreenPrimary, RoundedCornerShape(14.dp))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.PersonAdd,
                        contentDescription = "Invite Account",
                        tint = EcoGreenPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "+ INVITE MUNICIPAL ACCOUNT VIA EMAIL",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = EcoGreenLight
                    )
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search accounts by name, email, or position...", color = TextOnDarkMuted, fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = TextOnDarkSecondary) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EcoGreenPrimary,
                        unfocusedBorderColor = DarkBorder,
                        focusedTextColor = TextOnDarkPrimary,
                        unfocusedTextColor = TextOnDarkPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { selectedSubTab = 0 },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedSubTab == 0) EcoGreenPrimary else DarkSurfaceCard
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "Active Accounts (${HakotRepository.adminProfiles.size})",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selectedSubTab == 0) Color.Black else TextOnDarkPrimary
                        )
                    }

                    Button(
                        onClick = { selectedSubTab = 1 },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedSubTab == 1) EcoGreenPrimary else DarkSurfaceCard
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "Pending Invites (${HakotRepository.accountInvitations.size})",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selectedSubTab == 1) Color.Black else TextOnDarkPrimary
                        )
                    }
                }
            }
        }

        if (selectedSubTab == 0) {
            val filteredProfiles = HakotRepository.adminProfiles.filter { prof ->
                val matchesSearch = searchQuery.isBlank() ||
                        prof.fullName.contains(searchQuery, ignoreCase = true) ||
                        prof.email.contains(searchQuery, ignoreCase = true) ||
                        prof.positionTitle.contains(searchQuery, ignoreCase = true) ||
                        prof.officeDepartment.contains(searchQuery, ignoreCase = true)

                val matchesRole = selectedRoleFilter == null || prof.role == selectedRoleFilter
                val matchesStatus = selectedStatusFilter == null || prof.status == selectedStatusFilter

                matchesSearch && matchesRole && matchesStatus
            }

            if (filteredProfiles.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No administrator accounts match your criteria.",
                            fontSize = 12.sp,
                            color = TextOnDarkMuted
                        )
                    }
                }
            } else {
                items(filteredProfiles) { prof ->
                    AccountCardItem(
                        profile = prof,
                        onClick = { selectedAccountForDetails = prof }
                    )
                }
            }
        } else {
            val filteredInvitations = HakotRepository.accountInvitations.filter { inv ->
                searchQuery.isBlank() ||
                        inv.intendedName.contains(searchQuery, ignoreCase = true) ||
                        inv.intendedEmail.contains(searchQuery, ignoreCase = true) ||
                        inv.positionTitle.contains(searchQuery, ignoreCase = true)
            }

            if (filteredInvitations.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No pending email invitations found.",
                            fontSize = 12.sp,
                            color = TextOnDarkMuted
                        )
                    }
                }
            } else {
                items(filteredInvitations) { inv ->
                    InvitationCardItem(
                        invitation = inv,
                        onClick = { selectedInvitationForDetails = inv }
                    )
                }
            }
        }
    }

    if (selectedAccountForDetails != null) {
        AccountDetailsModal(
            profile = selectedAccountForDetails!!,
            onDismiss = { selectedAccountForDetails = null }
        )
    }

    if (selectedInvitationForDetails != null) {
        InvitationDetailsModal(
            invitation = selectedInvitationForDetails!!,
            onDismiss = { selectedInvitationForDetails = null }
        )
    }
}

@Composable
fun AccountCardItem(
    profile: AdminUserProfile,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, DarkBorder, RoundedCornerShape(14.dp))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(EcoGreenContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AdminPanelSettings,
                    contentDescription = "User Avatar",
                    tint = EcoGreenPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = profile.fullName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextOnDarkPrimary
                    )

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                when (profile.status) {
                                    AdminStatus.ACTIVE -> EcoGreenContainer
                                    AdminStatus.SUSPENDED -> Color(0xFF450A0A)
                                    else -> DarkSurfaceVariant
                                }
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = profile.status.label,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = when (profile.status) {
                                AdminStatus.ACTIVE -> EcoGreenLight
                                AdminStatus.SUSPENDED -> HazardousRed
                                else -> TextOnDarkMuted
                            }
                        )
                    }
                }

                Text(
                    text = "${profile.role.label} • ${profile.positionTitle}",
                    fontSize = 11.sp,
                    color = EcoGreenLight
                )
                Text(
                    text = "${profile.email} • ${profile.municipalityName}",
                    fontSize = 10.sp,
                    color = TextOnDarkSecondary
                )
            }
        }
    }
}



@Composable
fun InvitationCardItem(
    invitation: AccountInvitation,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, DarkBorder, RoundedCornerShape(14.dp))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(DarkSurfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Mail,
                    contentDescription = "Invite",
                    tint = RecycleYellow,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = invitation.intendedName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextOnDarkPrimary
                    )

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF3A2E00))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = invitation.status.label,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = RecycleYellow
                        )
                    }
                }

                Text(
                    text = "Role: ${invitation.assignedRole.label} • ${invitation.positionTitle}",
                    fontSize = 11.sp,
                    color = RecycleYellow
                )
                Text(
                    text = "Recipient: ${invitation.intendedEmail} • ${invitation.municipalityName}",
                    fontSize = 10.sp,
                    color = TextOnDarkSecondary
                )
            }
        }
    }
}



// ----------------------------------------------------------------------------
// CREATE ACCOUNT INVITATION FORM MODAL
// ----------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateAccountModal(
    onDismiss: () -> Unit,
    onCreated: () -> Unit
) {
    val context = LocalContext.current
    val currentUser = HakotRepository.authenticatedUser ?: HakotRepository.currentDistrictAdmin
    val callerRole = currentUser.role

    var fullNameInput by remember { mutableStateOf("") }
    var emailInput by remember { mutableStateOf("") }
    var phoneInput by remember { mutableStateOf("") }
    var positionInput by remember { mutableStateOf("") }
    var departmentInput by remember { mutableStateOf("") }
    var selectedRole by remember {
        mutableStateOf(
            if (callerRole == UserRole.SYSTEM_OWNER) UserRole.MUNICIPAL_ADMIN else UserRole.MUNICIPAL_STAFF
        )
    }
    var municipalityInput by remember { mutableStateOf(currentUser.municipalityName) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

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
                text = "Invite Municipal Account via Email",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextOnDarkPrimary
            )
            Text(
                text = "Send an email invitation. The recipient sets up their password upon account activation.",
                fontSize = 11.sp,
                color = TextOnDarkSecondary,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text("Select Role to Assign:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextOnDarkPrimary)
            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (callerRole == UserRole.SYSTEM_OWNER) {
                    FilterChip(
                        selected = selectedRole == UserRole.MUNICIPAL_ADMIN,
                        onClick = { selectedRole = UserRole.MUNICIPAL_ADMIN },
                        label = { Text("Municipal Admin", fontSize = 11.sp) }
                    )
                }

                if (callerRole == UserRole.SYSTEM_OWNER || callerRole == UserRole.MUNICIPAL_ADMIN || callerRole == UserRole.DISTRICT_ADMIN) {
                    FilterChip(
                        selected = selectedRole == UserRole.MUNICIPAL_STAFF,
                        onClick = { selectedRole = UserRole.MUNICIPAL_STAFF },
                        label = { Text("Municipal Staff", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = selectedRole == UserRole.DRIVER,
                        onClick = { selectedRole = UserRole.DRIVER },
                        label = { Text("Driver / Operator", fontSize = 11.sp) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = fullNameInput,
                onValueChange = { fullNameInput = it },
                label = { Text("Full Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(6.dp))

            OutlinedTextField(
                value = emailInput,
                onValueChange = { emailInput = it },
                label = { Text("Email Address") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(6.dp))

            OutlinedTextField(
                value = phoneInput,
                onValueChange = { phoneInput = it },
                label = { Text("Contact Number") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(6.dp))

            OutlinedTextField(
                value = municipalityInput,
                onValueChange = { if (callerRole == UserRole.SYSTEM_OWNER) municipalityInput = it },
                label = { Text("Municipality Scope") },
                enabled = callerRole == UserRole.SYSTEM_OWNER,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(6.dp))

            OutlinedTextField(
                value = positionInput,
                onValueChange = { positionInput = it },
                label = { Text("Official Position / Designation") },
                placeholder = { Text("e.g. Sanitation Inspector / Field Admin") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(6.dp))

            OutlinedTextField(
                value = departmentInput,
                onValueChange = { departmentInput = it },
                label = { Text("Office or Department") },
                placeholder = { Text("e.g. Solid Waste Management Office") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            errorMessage?.let {
                Spacer(modifier = Modifier.height(6.dp))
                Text(it, color = HazardousRed, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    if (fullNameInput.isBlank() || emailInput.isBlank() || positionInput.isBlank()) {
                        errorMessage = "Please complete full name, email, and official position."
                        return@Button
                    }

                    HakotRepository.createAccountInvitation(
                        intendedName = fullNameInput.trim(),
                        intendedEmail = emailInput.trim(),
                        contactNumber = phoneInput.trim(),
                        municipalityId = HakotRepository.activeOrganization.municipalityId,
                        municipalityName = municipalityInput.trim(),
                        assignedRole = selectedRole,
                        positionTitle = positionInput.trim(),
                        officeDepartment = departmentInput.trim()
                    )

                    Toast.makeText(context, "Email invitation sent to ${emailInput.trim()}! (Backend Contract Pending)", Toast.LENGTH_LONG).show()
                    onCreated()
                },
                colors = ButtonDefaults.buttonColors(containerColor = EcoGreenPrimary),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(imageVector = Icons.Default.Mail, contentDescription = "Send", tint = Color.Black, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("SEND EMAIL INVITATION", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

// ----------------------------------------------------------------------------
// ACCOUNT DETAILS MODAL SHEET
// ----------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountDetailsModal(
    profile: AdminUserProfile,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
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
                text = "Account Details & Permissions",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextOnDarkPrimary
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text("Full Name: ${profile.fullName}", fontSize = 14.sp, color = TextOnDarkPrimary, fontWeight = FontWeight.Bold)
            Text("Email: ${profile.email}", fontSize = 12.sp, color = TextOnDarkSecondary)
            Text("Role: ${profile.role.label}", fontSize = 12.sp, color = EcoGreenLight)
            Text("Municipality: ${profile.municipalityName}", fontSize = 12.sp, color = TextOnDarkSecondary)
            Text("Position: ${profile.positionTitle} (${profile.officeDepartment})", fontSize = 12.sp, color = TextOnDarkSecondary)
            Text("Status: ${profile.status.label}", fontSize = 12.sp, color = if (profile.status == AdminStatus.ACTIVE) EcoGreenLight else HazardousRed)

            Spacer(modifier = Modifier.height(20.dp))

            Text("Account Actions:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextOnDarkMuted)
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (profile.status == AdminStatus.ACTIVE) {
                    Button(
                        onClick = {
                            HakotRepository.suspendAccount(profile.userId)
                            Toast.makeText(context, "Account Suspended (Backend Pending)", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = HazardousRed),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Suspend", fontSize = 12.sp, color = Color.White)
                    }
                } else {
                    Button(
                        onClick = {
                            HakotRepository.reactivateAccount(profile.userId)
                            Toast.makeText(context, "Account Reactivated (Backend Pending)", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = EcoGreenPrimary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Reactivate", fontSize = 12.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }

                Button(
                    onClick = {
                        HakotRepository.revokeAccount(profile.userId)
                        Toast.makeText(context, "Account Revoked (Backend Pending)", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceCard),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Revoke", fontSize = 12.sp, color = HazardousRed)
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// ----------------------------------------------------------------------------
// INVITATION DETAILS MODAL SHEET
// ----------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvitationDetailsModal(
    invitation: AccountInvitation,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
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
                text = "Pending Email Invitation Details",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextOnDarkPrimary
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text("Intended Recipient: ${invitation.intendedName}", fontSize = 14.sp, color = TextOnDarkPrimary, fontWeight = FontWeight.Bold)
            Text("Email: ${invitation.intendedEmail}", fontSize = 12.sp, color = TextOnDarkSecondary)
            Text("Contact Phone: ${invitation.contactNumber}", fontSize = 12.sp, color = TextOnDarkSecondary)
            Text("Assigned Role: ${invitation.assignedRole.label}", fontSize = 12.sp, color = RecycleYellow)
            Text("Municipality: ${invitation.municipalityName}", fontSize = 12.sp, color = TextOnDarkSecondary)
            Text("Position: ${invitation.positionTitle} (${invitation.officeDepartment})", fontSize = 12.sp, color = TextOnDarkSecondary)
            Text("Invitation Code Display: ${invitation.rawCodeDisplay}", fontSize = 12.sp, color = EcoGreenLight, fontWeight = FontWeight.Bold)
            Text("Status: ${invitation.status.label}", fontSize = 12.sp, color = RecycleYellow)

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        val res = HakotRepository.resendAccountInvitation(invitation.invitationId)
                        Toast.makeText(context, res.second, Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EcoGreenPrimary),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Resend Email", fontSize = 12.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = {
                        HakotRepository.revokeAccountInvitation(invitation.invitationId)
                        Toast.makeText(context, "Invitation Revoked (Backend Pending)", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = HazardousRed),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Revoke Invite", fontSize = 12.sp, color = Color.White)
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun AdminRoutesView() {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 20.dp)
    ) {
        items(HakotRepository.routes) { rt ->
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(rt.routeName, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextOnDarkPrimary)
                    Text("📍 ${rt.barangaysCovered}", fontSize = 12.sp, color = EcoGreenLight)
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
                modifier = Modifier.fillMaxWidth().border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(sch.routeName, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextOnDarkPrimary)
                    Text("🗓️ Date: ${sch.date} • Time: ${sch.startTime} - ${sch.endTime}", fontSize = 12.sp, color = EcoGreenLight)
                    Text("Status: ${sch.status}", fontSize = 11.sp, color = TextOnDarkSecondary)
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
                modifier = Modifier.fillMaxWidth().border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
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

    LaunchedEffect(Unit) {
        HakotRepository.fetchAnonymousReportsFromSupabase()
    }

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
                }
            }
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



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RedeemInvitationModal(
    onDismiss: () -> Unit,
    onRedeemed: () -> Unit
) {
    val context = LocalContext.current
    var codeInput by remember { mutableStateOf("") }
    var emailInput by remember { mutableStateOf("") }
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
            Text("Redeem District Invitation Code", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextOnDarkPrimary)
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = codeInput,
                onValueChange = { codeInput = it },
                label = { Text("Invitation Code (e.g. HAKOT-XXXX)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = emailInput,
                onValueChange = { emailInput = it },
                label = { Text("Your Email Address") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    if (codeInput.isNotBlank() && emailInput.isNotBlank()) {
                        val success = HakotRepository.redeemInvitation(codeInput, emailInput)
                        if (success) {
                            Toast.makeText(context, "Invitation Redeemed Successfully!", Toast.LENGTH_SHORT).show()
                            onRedeemed()
                        } else {
                            Toast.makeText(context, "Invalid or expired invitation code.", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = EcoGreenPrimary),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text("Redeem Code", color = Color.Black, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun AccountRegistrationScreen(
    onBackToLogin: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var fullNameInput by remember { mutableStateOf("") }
    var emailInput by remember { mutableStateOf("") }
    var contactInput by remember { mutableStateOf("") }
    var municipalityInput by remember { mutableStateOf("Nasugbu, Batangas") }
    var officeInput by remember { mutableStateOf("Municipal Sanitation Office") }
    var positionInput by remember { mutableStateOf("Sanitation Inspector") }
    var requestedRole by remember { mutableStateOf(UserRole.MUNICIPAL_STAFF) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }
    var isSubmitted by remember { mutableStateOf(false) }

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
                .fillMaxHeight(0.92f)
                .border(1.dp, DarkBorder, RoundedCornerShape(22.dp))
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start
                ) {
                    TextButton(onClick = onBackToLogin) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = EcoGreenLight, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("← Back to Login", fontSize = 11.sp, color = EcoGreenLight)
                    }
                }

                Text(
                    text = "REQUEST MUNICIPAL ACCOUNT ACCESS",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = EcoGreenLight
                )
                Text(
                    text = "New account applications start as PENDING System Owner review.",
                    fontSize = 11.sp,
                    color = TextOnDarkSecondary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (isSubmitted) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = EcoGreenContainer),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().border(1.dp, EcoGreenPrimary, RoundedCornerShape(16.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(imageVector = Icons.Default.CheckCircle, contentDescription = "Success", tint = EcoGreenLight, modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Application Submitted Successfully!", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextOnDarkPrimary)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Your account application has been submitted and is currently PENDING System Owner review. You will receive access once approved.",
                                fontSize = 12.sp,
                                color = TextOnDarkSecondary,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = onBackToLogin,
                                colors = ButtonDefaults.buttonColors(containerColor = EcoGreenPrimary),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth().height(44.dp)
                            ) {
                                Text("Return to Login", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = fullNameInput,
                        onValueChange = { fullNameInput = it; errorMessage = null },
                        label = { Text("Full Name") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = EcoGreenPrimary, unfocusedBorderColor = DarkBorder, focusedTextColor = TextOnDarkPrimary, unfocusedTextColor = TextOnDarkPrimary),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = emailInput,
                        onValueChange = { emailInput = it; errorMessage = null },
                        label = { Text("Email Address") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = EcoGreenPrimary, unfocusedBorderColor = DarkBorder, focusedTextColor = TextOnDarkPrimary, unfocusedTextColor = TextOnDarkPrimary),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = contactInput,
                        onValueChange = { contactInput = it; errorMessage = null },
                        label = { Text("Contact Number") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = EcoGreenPrimary, unfocusedBorderColor = DarkBorder, focusedTextColor = TextOnDarkPrimary, unfocusedTextColor = TextOnDarkPrimary),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = municipalityInput,
                        onValueChange = { municipalityInput = it; errorMessage = null },
                        label = { Text("Municipality / LGU Scope") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = EcoGreenPrimary, unfocusedBorderColor = DarkBorder, focusedTextColor = TextOnDarkPrimary, unfocusedTextColor = TextOnDarkPrimary),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = officeInput,
                        onValueChange = { officeInput = it; errorMessage = null },
                        label = { Text("Office / Department") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = EcoGreenPrimary, unfocusedBorderColor = DarkBorder, focusedTextColor = TextOnDarkPrimary, unfocusedTextColor = TextOnDarkPrimary),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = positionInput,
                        onValueChange = { positionInput = it; errorMessage = null },
                        label = { Text("Official Position") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = EcoGreenPrimary, unfocusedBorderColor = DarkBorder, focusedTextColor = TextOnDarkPrimary, unfocusedTextColor = TextOnDarkPrimary),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Requested Account Type:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextOnDarkPrimary, modifier = Modifier.align(Alignment.Start))
                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = requestedRole == UserRole.MUNICIPAL_ADMIN,
                            onClick = { requestedRole = UserRole.MUNICIPAL_ADMIN },
                            label = { Text("Municipal Admin", fontSize = 10.sp) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = EcoGreenContainer, selectedLabelColor = EcoGreenLight)
                        )
                        FilterChip(
                            selected = requestedRole == UserRole.MUNICIPAL_STAFF,
                            onClick = { requestedRole = UserRole.MUNICIPAL_STAFF },
                            label = { Text("Municipal Staff", fontSize = 10.sp) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = EcoGreenContainer, selectedLabelColor = EcoGreenLight)
                        )
                        FilterChip(
                            selected = requestedRole == UserRole.DRIVER,
                            onClick = { requestedRole = UserRole.DRIVER },
                            label = { Text("Driver", fontSize = 10.sp) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = RecycleYellowContainer, selectedLabelColor = RecycleYellow)
                        )
                    }

                    errorMessage?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(it, color = HazardousRed, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            Log.d("HAKOT_SUBMIT_UI", "Submit account application button clicked.")
                            if (fullNameInput.isBlank() || emailInput.isBlank() || contactInput.isBlank() || municipalityInput.isBlank() || officeInput.isBlank() || positionInput.isBlank()) {
                                Log.d("HAKOT_SUBMIT_UI", "Validation failed: required fields are blank.")
                                errorMessage = "Please fill in all required registration fields."
                            } else if (!Patterns.EMAIL_ADDRESS.matcher(emailInput.trim()).matches()) {
                                Log.d("HAKOT_SUBMIT_UI", "Validation failed: invalid email format.")
                                errorMessage = "Please enter a valid email address."
                            } else {
                                isSubmitting = true
                                errorMessage = null
                                scope.launch {
                                    Log.d("HAKOT_SUBMIT_UI", "Preparing to submit account application for requested role: ${requestedRole.name}")
                                    val result = HakotRepository.submitAccountApplication(
                                        fullName = fullNameInput.trim(),
                                        email = emailInput.trim(),
                                        contactNumber = contactInput.trim(),
                                        municipalityId = HakotRepository.activeOrganization.municipalityId,
                                        officeDepartment = officeInput.trim(),
                                        officialPosition = positionInput.trim(),
                                        requestedRole = requestedRole
                                    )
                                    Log.d("HAKOT_SUBMIT_UI", "Received submission result: success=${result.first}, message=${result.second}")
                                    isSubmitting = false
                                    if (result.first) {
                                        isSubmitted = true
                                    } else {
                                        errorMessage = result.second
                                    }
                                }
                            }
                        },
                        enabled = !isSubmitting,
                        colors = ButtonDefaults.buttonColors(containerColor = EcoGreenPrimary),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.Black, strokeWidth = 2.5.dp)
                        } else {
                            Text("SUBMIT ACCOUNT APPLICATION", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}


