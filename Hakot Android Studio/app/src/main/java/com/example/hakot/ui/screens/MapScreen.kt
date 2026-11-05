package com.example.hakot.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hakot.data.HakotRepository
import com.example.hakot.model.ReportType
import com.example.hakot.ui.components.DarkMapView
import com.example.hakot.ui.components.TopSearchBar
import com.example.hakot.ui.theme.DarkBorder
import com.example.hakot.ui.theme.DarkSurface
import com.example.hakot.ui.theme.EcoGreenPrimary
import com.example.hakot.ui.theme.HazardousRed
import com.example.hakot.ui.theme.TextOnDarkPrimary
import com.example.hakot.ui.theme.TextOnDarkSecondary

@Composable
fun MapScreen(
    searchQuery: String = "",
    onQueryChange: (String) -> Unit = {},
    onMenuClick: () -> Unit = {},
    isAdminMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    var showReportModal by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        // Dark Map taking full screen edge-to-edge
        DarkMapView(
            searchQuery = searchQuery,
            onReportClick = { showReportModal = true }
        )

        // Top Search Bar floating as an island over the map
        TopSearchBar(
            query = searchQuery,
            onQueryChange = onQueryChange,
            onMenuClick = onMenuClick,
            isAdminMode = isAdminMode,
            modifier = Modifier.align(Alignment.TopCenter)
        )

        // Minimalist Slim Floating Status & Report Pill
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp)
        ) {
            Surface(
                onClick = { showReportModal = true },
                color = DarkSurface.copy(alpha = 0.92f),
                shape = RoundedCornerShape(24.dp),
                shadowElevation = 8.dp,
                border = BorderStroke(1.dp, DarkBorder)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DirectionsBus,
                        contentDescription = "Fleet",
                        tint = EcoGreenPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "${HakotRepository.trucks.size} Trucks Active",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextOnDarkPrimary
                    )
                    Box(
                        modifier = Modifier
                            .size(3.dp)
                            .clip(CircleShape)
                            .background(TextOnDarkSecondary)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.ReportProblem,
                            contentDescription = "Report",
                            tint = HazardousRed,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Report Issue",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = HazardousRed
                        )
                    }
                }
            }
        }

        // Anonymous Report Sheet
        if (showReportModal) {
            AnonymousReportModal(
                initialType = ReportType.WASTE_PILE_UP,
                onDismiss = { showReportModal = false },
                onSubmit = { type, loc, desc ->
                    HakotRepository.addAnonymousReport(type, loc, desc)
                    showReportModal = false
                }
            )
        }
    }
}
