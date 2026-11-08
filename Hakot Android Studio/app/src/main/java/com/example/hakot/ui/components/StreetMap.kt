package com.example.hakot.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.File

@Composable
fun StreetMap(
    modifier: Modifier = Modifier,
    initialLat: Double = 13.9419, // Lipa City, Batangas, Philippines
    initialLng: Double = 121.1644,
    initialZoom: Double = 15.5
) {
    val context = LocalContext.current

    // Synchronously configure osmdroid before MapView instantiation
    remember(context) {
        val appContext = context.applicationContext
        val config = Configuration.getInstance()
        config.load(appContext, appContext.getSharedPreferences("osmdroid_pref", 0))
        config.userAgentValue = "com.example.hakot/1.0 (Android; Lipa City Waste Management App)"
        config.osmdroidBasePath = File(appContext.cacheDir, "osmdroid")
        config.osmdroidTileCache = File(appContext.cacheDir, "osmdroid/tiles")
        config.tileDownloadThreads = 4
        true
    }

    var mapViewInstance by remember { mutableStateOf<MapView?>(null) }
    val lipaCityCenter = remember { GeoPoint(initialLat, initialLng) }

    Box(modifier = modifier.fillMaxSize().clip(RoundedCornerShape(16.dp))) {
        // OpenStreetMap Interactive View
        AndroidView(
            factory = { ctx ->
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setUseDataConnection(true)
                    setMultiTouchControls(true)
                    isTilesScaledToDpi = false
                    minZoomLevel = 4.0
                    maxZoomLevel = 18.5
                    controller.setZoom(initialZoom)
                    controller.setCenter(lipaCityCenter)

                    // Lipa City Center Marker
                    val lipaMarker = Marker(this).apply {
                        position = lipaCityCenter
                        title = "Lipa City, Batangas"
                        snippet = "Hakot Waste Collection Center Zone"
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    }
                    overlays.add(lipaMarker)

                    mapViewInstance = this
                    onResume()
                }
            },
            update = { mapView ->
                mapViewInstance = mapView
            },
            modifier = Modifier.fillMaxSize()
        )

        // Lifecycle Handling
        DisposableEffect(Unit) {
            mapViewInstance?.onResume()
            onDispose {
                mapViewInstance?.onPause()
                mapViewInstance?.onDetach()
            }
        }

        // Floating Target Re-Center Button
        Surface(
            onClick = {
                mapViewInstance?.controller?.animateTo(lipaCityCenter)
                mapViewInstance?.controller?.setZoom(initialZoom)
            },
            shape = CircleShape,
            color = Color.White,
            shadowElevation = 6.dp,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 16.dp)
                .size(44.dp)
                .border(1.dp, Color(0xFFE0E0E0), CircleShape)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = "Center Lipa City Location",
                    tint = Color(0xFF10B981),
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // Floating Zoom In / Zoom Out Controls
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MapControlButton(icon = Icons.Default.Add, contentDesc = "Zoom In") {
                mapViewInstance?.controller?.zoomIn()
            }
            MapControlButton(icon = Icons.Default.Remove, contentDesc = "Zoom Out") {
                mapViewInstance?.controller?.zoomOut()
            }
        }
    }
}

@Composable
fun MapControlButton(
    icon: ImageVector,
    contentDesc: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color.White,
        shadowElevation = 4.dp,
        modifier = Modifier
            .size(38.dp)
            .border(1.dp, Color(0xFFE0E0E0), CircleShape)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDesc,
                tint = Color(0xFF424242),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
