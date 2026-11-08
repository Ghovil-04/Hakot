package com.example.hakot.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBus
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hakot.data.HakotRepository
import com.example.hakot.ui.components.DarkMapView
import com.example.hakot.ui.components.TopSearchBar
import com.example.hakot.ui.theme.DarkBorder
import com.example.hakot.ui.theme.DarkSurface
import com.example.hakot.ui.theme.EcoGreenPrimary
import com.example.hakot.ui.theme.TextOnDarkPrimary

@Composable
fun MapScreen(
    searchQuery: String = "",
    onQueryChange: (String) -> Unit = {},
    onMenuClick: () -> Unit = {},
    isAdminMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    var isMapInteracting by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        // Map taking full screen edge-to-edge
        DarkMapView(
            searchQuery = searchQuery,
            onMapInteractionStateChange = { interacting ->
                isMapInteracting = interacting
            }
        )

        // Top Search Bar floating as an island over the map - Hides during map interaction
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

        // Active Trucks Indicator (Report Button removed)
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp)
        ) {
            Surface(
                color = DarkSurface.copy(alpha = 0.92f),
                shape = RoundedCornerShape(24.dp),
                shadowElevation = 8.dp,
                border = BorderStroke(1.dp, DarkBorder)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DirectionsBus,
                        contentDescription = "Active Trucks",
                        tint = EcoGreenPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = "${HakotRepository.trucks.size} Trucks Active",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextOnDarkPrimary
                    )
                }
            }
        }
    }
}
