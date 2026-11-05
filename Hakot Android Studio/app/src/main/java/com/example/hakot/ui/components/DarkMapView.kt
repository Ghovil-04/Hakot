package com.example.hakot.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.hakot.ui.theme.DarkSurfaceCard
import com.example.hakot.ui.theme.EcoGreenPrimary
import com.example.hakot.ui.theme.TextOnDarkPrimary
import com.example.hakot.ui.theme.TextOnDarkSecondary
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView

data class SelectedMarkerInfo(
    val title: String,
    val subtitle: String,
    val type: String,
    val color: Color,
    val details: List<Pair<String, String>>
)

@Composable
fun DarkMapView(
    modifier: Modifier = Modifier,
    searchQuery: String = "",
    onReportClick: () -> Unit = {}
) {
    val context = LocalContext.current

    // Initialize MapLibre Native SDK
    remember(context) {
        MapLibre.getInstance(context)
        true
    }

    var selectedMarker by remember { mutableStateOf<SelectedMarkerInfo?>(null) }

    // Center coordinates for Nasugbu, Batangas, Philippines (14.0700° N, 120.6300° E)
    val nasugbuCenter = remember { LatLng(14.0700, 120.6300) }

    val mapView = remember { MapView(context) }

    Box(modifier = modifier.fillMaxSize().background(Color(0xFFF4F3EE))) {
        // MapLibre Native Android View with OpenFreeMap Public Style (No API Key Required)
        AndroidView(
            factory = {
                mapView.apply {
                    getMapAsync { maplibreMap ->
                        // Set OpenFreeMap Public Style (Bright)
                        maplibreMap.setStyle("https://tiles.openfreemap.org/styles/bright") { _ ->
                            // Set camera position at Nasugbu, Batangas
                            maplibreMap.cameraPosition = CameraPosition.Builder()
                                .target(nasugbuCenter)
                                .zoom(14.5)
                                .build()

                            // Clear any overlays for clean map view
                            setupMapLibreOverlays(maplibreMap)
                        }

                        // Marker Selection Listener
                        maplibreMap.setOnMarkerClickListener { marker ->
                            selectedMarker = SelectedMarkerInfo(
                                title = marker.title ?: "Location Point",
                                subtitle = marker.snippet ?: "Nasugbu, Batangas Location",
                                type = "MARKER",
                                color = EcoGreenPrimary,
                                details = listOf(
                                    "Location" to "${marker.position.latitude}, ${marker.position.longitude}",
                                    "Municipality" to "Nasugbu, Batangas",
                                    "Status" to "Active Route Point"
                                )
                            )
                            true
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // MapLibre Lifecycle Management
        DisposableEffect(mapView) {
            mapView.onStart()
            mapView.onResume()
            onDispose {
                mapView.onPause()
                mapView.onStop()
                mapView.onDestroy()
            }
        }

        // Selected Marker Details Overlay Card
        AnimatedVisibility(
            visible = selectedMarker != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        ) {
            selectedMarker?.let { info ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, info.color.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(info.color)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = info.title,
                                    color = TextOnDarkPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            IconButton(
                                onClick = { selectedMarker = null },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = TextOnDarkSecondary
                                )
                            }
                        }

                        Text(
                            text = info.subtitle,
                            color = TextOnDarkSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(start = 18.dp, bottom = 8.dp)
                        )

                        info.details.forEach { (k, v) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(k, fontSize = 12.sp, color = TextOnDarkSecondary)
                                Text(v, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextOnDarkPrimary)
                            }
                        }
                    }
                }
            }
        }
    }
}

// Clean map view without extra pinpoints or lines
private fun setupMapLibreOverlays(maplibreMap: MapLibreMap) {
    @Suppress("DEPRECATION")
    maplibreMap.clear()
}
