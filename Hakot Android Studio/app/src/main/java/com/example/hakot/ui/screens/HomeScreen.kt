package com.example.hakot.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AddAlert
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hakot.data.HakotRepository
import com.example.hakot.model.ReportType
import com.example.hakot.model.WasteCategory
import com.example.hakot.ui.components.DarkMapView
import com.example.hakot.ui.components.TopSearchBar
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
import com.example.hakot.ui.theme.RecycleYellowContainer
import com.example.hakot.ui.theme.ResidualCyan
import com.example.hakot.ui.theme.ResidualCyanContainer
import com.example.hakot.ui.theme.TextOnDarkMuted
import com.example.hakot.ui.theme.TextOnDarkPrimary
import com.example.hakot.ui.theme.TextOnDarkSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    searchQuery: String = "",
    onQueryChange: (String) -> Unit = {},
    onMenuClick: () -> Unit = {},
    isAdminMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    var isSheetExpanded by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }
    var selectedReportType by remember { mutableStateOf(ReportType.MISSED_COLLECTION) }
    var isMapInteracting by remember { mutableStateOf(false) }

    // Animated sheet height based on drag state
    val sheetMaxHeight = 560.dp
    val sheetMinHeight = 175.dp
    val sheetHeight by animateDpAsState(
        targetValue = if (isSheetExpanded) sheetMaxHeight else sheetMinHeight,
        label = "SheetHeightAnimation"
    )

    val scrollState = rememberScrollState()

    Box(modifier = modifier.fillMaxSize()) {
        // Fullscreen Vector/OSM Map Background with interaction listener
        DarkMapView(
            searchQuery = searchQuery,
            onReportClick = { showReportDialog = true },
            onMapInteractionStateChange = { interacting ->
                isMapInteracting = interacting
            }
        )

        // Floating Top Search Bar Island over the Map - Hides smoothly during map interaction
        AnimatedVisibility(
            visible = !isMapInteracting,
            enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            TopSearchBar(
                query = searchQuery,
                onQueryChange = onQueryChange,
                onMenuClick = onMenuClick,
                isAdminMode = isAdminMode
            )
        }

        // Minimalist Slim Floating Status & Report Pill right above Bottom Sheet
        Surface(
            onClick = { showReportDialog = true },
            color = DarkSurface.copy(alpha = 0.92f),
            shape = RoundedCornerShape(24.dp),
            shadowElevation = 8.dp,
            border = BorderStroke(1.dp, DarkBorder),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 185.dp)
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DirectionsBus,
                    contentDescription = "Fleet",
                    tint = EcoGreenPrimary,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = "${HakotRepository.trucks.size} Trucks Active",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextOnDarkPrimary
                )
            }
        }

        // Draggable Upwards Home Bottom Sheet
        Surface(
            color = DarkSurface,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            shadowElevation = 16.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(sheetHeight)
                .align(Alignment.BottomCenter)
                .draggable(
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState { delta ->
                        if (delta < -15) isSheetExpanded = true
                        else if (delta > 15) isSheetExpanded = false
                    }
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // Drag Handle Bar Top Indicator
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isSheetExpanded = !isSheetExpanded }
                        .padding(vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .width(42.dp)
                                .height(5.dp)
                                .clip(CircleShape)
                                .background(TextOnDarkMuted.copy(alpha = 0.5f))
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Icon(
                            imageVector = if (isSheetExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                            contentDescription = "Expand or Collapse Sheet",
                            tint = TextOnDarkSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Collapsed View: Next Hakot Schedule Widget & Live Summary
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isSheetExpanded = !isSheetExpanded }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.AccessTime,
                                    contentDescription = "Countdown",
                                    tint = EcoGreenPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Next Hakot Schedule",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextOnDarkPrimary
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "📍 ${HakotRepository.detectedLocationName} • J.P. Laurel St Route",
                                fontSize = 12.sp,
                                color = TextOnDarkSecondary
                            )
                            Text(
                                text = "⏳ Collection window active now",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = EcoGreenLight
                            )
                        }

                        // On Track Badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(EcoGreenContainer)
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "On Track",
                                color = EcoGreenLight,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                if (!isSheetExpanded) {
                    Text(
                        text = "↑ Drag upward for LGU announcements, guidelines & reports",
                        fontSize = 11.sp,
                        color = TextOnDarkMuted,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(top = 8.dp)
                    )
                }

                // Expanded View Content (Scrollable when expanded)
                if (isSheetExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 8.dp)
                            .verticalScroll(scrollState)
                    ) {
                        // Official LGU Announcements Section
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Campaign,
                                contentDescription = "Announcements",
                                tint = RecycleYellow,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Official LGU Announcements",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextOnDarkPrimary
                            )
                        }

                        HakotRepository.announcements.forEach { ann ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = ann.title,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextOnDarkPrimary
                                        )
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(DarkSurfaceVariant)
                                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text(ann.category, fontSize = 10.sp, color = RecycleYellow)
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = ann.message,
                                        fontSize = 11.sp,
                                        color = TextOnDarkSecondary
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Section 1: Anonymous Reporting System
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = "Anonymous",
                                tint = EcoGreenPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Anonymous Citizen Incident Reporting",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextOnDarkPrimary
                            )
                        }

                        // Big Anonymous Report Action Card
                        Card(
                            colors = CardDefaults.cardColors(containerColor = HazardousRedContainer),
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showReportDialog = true }
                                .border(1.dp, HazardousRed.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.ReportProblem,
                                        contentDescription = "Report Issue",
                                        tint = HazardousRed,
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "Report Waste Issue Anonymously",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextOnDarkPrimary
                                        )
                                        Text(
                                            text = "No collection route, missed schedule, pile-up",
                                            fontSize = 11.sp,
                                            color = TextOnDarkSecondary
                                        )
                                    }
                                }
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = "Go",
                                    tint = TextOnDarkSecondary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Quick Issue Action Chips
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            QuickReportChip(
                                label = "No Route in Area",
                                icon = Icons.Default.ReportProblem,
                                modifier = Modifier.weight(1f)
                            ) {
                                selectedReportType = ReportType.NO_ROUTE_IN_AREA
                                showReportDialog = true
                            }
                            QuickReportChip(
                                label = "Missed Hakot",
                                icon = Icons.Default.AddAlert,
                                modifier = Modifier.weight(1f)
                            ) {
                                selectedReportType = ReportType.MISSED_COLLECTION
                                showReportDialog = true
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Section 2: Waste Management Guidelines & Rules
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Guidelines",
                                tint = RecycleYellow,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Waste Segregation Guidelines & Features",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextOnDarkPrimary
                            )
                        }

                        WasteCategoryGrid()

                        Spacer(modifier = Modifier.height(12.dp))

                        // Local Sanitation Helpline Card
                        Card(
                            colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PhoneInTalk,
                                    contentDescription = "Helpline",
                                    tint = EcoGreenPrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "LGU Waste Sanitation Hotline",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextOnDarkPrimary
                                    )
                                    Text(
                                        text = "(043) 723-HAKOT • 24/7 Anonymous Dispatch",
                                        fontSize = 11.sp,
                                        color = TextOnDarkSecondary
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                    }
                }
            }
        }

        // Anonymous Report Sheet / Dialog Modal
        if (showReportDialog) {
            AnonymousReportModal(
                initialType = selectedReportType,
                onDismiss = { showReportDialog = false },
                onSubmit = { type, loc, desc ->
                    HakotRepository.addAnonymousReport(
                        type = type,
                        locationName = loc,
                        description = desc
                    )
                    showReportDialog = false
                    isSheetExpanded = false
                }
            )
        }
    }
}

@Composable
fun QuickReportChip(
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = DarkSurfaceCard,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, DarkBorder),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = HazardousRed,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = TextOnDarkPrimary
            )
        }
    }
}

@Composable
fun WasteCategoryGrid() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        WasteCategoryItem(
            name = "General Waste (All Trash Accepted)",
            desc = "All household waste, food scraps, plastics, paper & general garbage collected together",
            color = EcoGreenPrimary,
            bgColor = EcoGreenContainer
        )
        WasteCategoryItem(
            name = "Recyclables & Dry Trash",
            desc = "Plastics, cardboard, bottles & dry packaging accepted during regular pass",
            color = RecycleYellow,
            bgColor = RecycleYellowContainer
        )
        WasteCategoryItem(
            name = "Bulky & Large Household Trash",
            desc = "Furniture, worn items & large household waste accepted on scheduled routes",
            color = ResidualCyan,
            bgColor = ResidualCyanContainer
        )
        WasteCategoryItem(
            name = "Hazardous & E-Waste",
            desc = "Batteries, electronics, bulbs & chemical containers",
            color = HazardousRed,
            bgColor = HazardousRedContainer
        )
    }
}

@Composable
fun WasteCategoryItem(
    name: String,
    desc: String,
    color: Color,
    bgColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor.copy(alpha = 0.4f))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = name,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = desc,
                fontSize = 11.sp,
                color = TextOnDarkSecondary
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnonymousReportModal(
    initialType: ReportType,
    onDismiss: () -> Unit,
    onSubmit: (ReportType, String, String) -> Unit
) {
    var selectedType by remember { mutableStateOf(initialType) }
    var locationInput by remember { mutableStateOf("J.P. Laurel Street, Nasugbu") }
    var descriptionInput by remember { mutableStateOf("") }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DarkSurface,
        scrimColor = Color.Black.copy(alpha = 0.6f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Report Waste Issue Anonymously",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextOnDarkPrimary
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(EcoGreenContainer)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("100% Anonymous", fontSize = 10.sp, color = EcoGreenLight)
                }
            }

            Text(
                text = "No personal details or profile required. Your report alerts the sanitation team.",
                fontSize = 12.sp,
                color = TextOnDarkSecondary,
                modifier = Modifier.padding(vertical = 6.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Issue Type Selection Chips
            Text("Issue Category:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextOnDarkPrimary)
            Spacer(modifier = Modifier.height(6.dp))

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ReportType.entries.forEach { type ->
                    val isSelected = selectedType == type
                    Surface(
                        onClick = { selectedType = type },
                        color = if (isSelected) HazardousRedContainer else DarkSurfaceCard,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = 1.dp,
                                color = if (isSelected) HazardousRed else DarkBorder,
                                shape = RoundedCornerShape(12.dp)
                            )
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.ReportProblem,
                                contentDescription = type.title,
                                tint = if (isSelected) HazardousRed else TextOnDarkSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = type.title,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextOnDarkPrimary
                                )
                                Text(
                                    text = type.description,
                                    fontSize = 10.sp,
                                    color = TextOnDarkSecondary
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Location Input Field
            Text("Location / Street / Barangay:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextOnDarkPrimary)
            OutlinedTextField(
                value = locationInput,
                onValueChange = { locationInput = it },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = EcoGreenPrimary,
                    unfocusedBorderColor = DarkBorder,
                    focusedTextColor = TextOnDarkPrimary,
                    unfocusedTextColor = TextOnDarkPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Description Input Field
            Text("Details / Notes (Optional):", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextOnDarkPrimary)
            OutlinedTextField(
                value = descriptionInput,
                onValueChange = { descriptionInput = it },
                placeholder = { Text("e.g. No collection route assigned to our street", color = TextOnDarkMuted, fontSize = 12.sp) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = EcoGreenPrimary,
                    unfocusedBorderColor = DarkBorder,
                    focusedTextColor = TextOnDarkPrimary,
                    unfocusedTextColor = TextOnDarkPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Submit Button
            Button(
                onClick = {
                    if (locationInput.isNotBlank()) {
                        onSubmit(selectedType, locationInput, descriptionInput)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = HazardousRed),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text(
                    text = "Submit Anonymous Report",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
