package com.example.hakot.ui.components

import android.annotation.SuppressLint
import android.view.MotionEvent
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.hakot.data.HakotRepository
import com.example.hakot.data.OsrmRoutingHelper
import com.example.hakot.ui.theme.DarkSurfaceCard
import com.example.hakot.ui.theme.TextOnDarkPrimary
import com.example.hakot.ui.theme.TextOnDarkSecondary
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.osmdroid.util.GeoPoint
import java.time.LocalDate

data class SelectedMarkerInfo(
    val title: String,
    val subtitle: String,
    val type: String,
    val color: Color,
    val details: List<Pair<String, String>>
)

@SuppressLint("ClickableViewAccessibility")
@Composable
fun DarkMapView(
    modifier: Modifier = Modifier,
    searchQuery: String = "",
    onReportClick: () -> Unit = {},
    onMapInteractionStateChange: (isInteracting: Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var idleJob by remember { mutableStateOf<Job?>(null) }

    // Initialize MapLibre Native SDK with application context
    remember(context) {
        MapLibre.getInstance(context.applicationContext)
        true
    }

    var selectedMarker by remember { mutableStateOf<SelectedMarkerInfo?>(null) }
    var mapLibreMapInstance by remember { mutableStateOf<MapLibreMap?>(null) }
    var fetchedRoutePoints by remember { mutableStateOf<List<GeoPoint>>(emptyList()) }

    // Center coordinates for Nasugbu, Batangas, Philippines (14.0700° N, 120.6300° E)
    val nasugbuCenter = remember { LatLng(14.0700, 120.6300) }

    val mapView = remember { MapView(context) }

    // Fetch Date-Specific OSRM Road-Snapped Route asynchronously when selectedMapDate changes
    LaunchedEffect(HakotRepository.selectedMapDate) {
        if (HakotRepository.schedules.isEmpty() && !HakotRepository.isSchedulesLoading) {
            HakotRepository.fetchSchedules()
        }
        val selectedDate = HakotRepository.selectedMapDate
        val selectedDayName = selectedDate.dayOfWeek.name.lowercase()

        val scheduleForDate = HakotRepository.schedules.find { sch ->
            val schDayName = sch.collectionDay.trim().lowercase()
            if (sch.date != null) {
                sch.date == selectedDate
            } else if (schDayName.isNotBlank()) {
                schDayName == selectedDayName || schDayName.startsWith(selectedDayName)
            } else {
                false
            }
        }

        if (scheduleForDate != null && scheduleForDate.routeVersionId.isNotBlank()) {
            val rawGeoPoints = HakotRepository.fetchRouteGeometry(scheduleForDate.routeVersionId)
            if (rawGeoPoints.isNotEmpty()) {
                val points = OsrmRoutingHelper.fetchRoadRoute(rawGeoPoints)
                fetchedRoutePoints = points
            } else {
                fetchedRoutePoints = emptyList()
            }
        } else {
            fetchedRoutePoints = emptyList()
        }

        mapLibreMapInstance?.let { maplibreMap ->
            setupMapLibreRouteOverlay(maplibreMap, fetchedRoutePoints)
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color(0xFFF4F3EE))) {
        // MapLibre Native Android View with OpenFreeMap Public Style (Zero 403 Errors, Zero API Keys, Vector Quality)
        AndroidView(
            factory = {
                mapView.apply {
                    getMapAsync { maplibreMap ->
                        mapLibreMapInstance = maplibreMap

                        // Enable 3D Tilt, Rotation, Pinch Zoom & Panning
                        maplibreMap.uiSettings.isZoomGesturesEnabled = true
                        maplibreMap.uiSettings.isScrollGesturesEnabled = true
                        maplibreMap.uiSettings.isRotateGesturesEnabled = true
                        maplibreMap.uiSettings.isTiltGesturesEnabled = true
                        maplibreMap.uiSettings.isDoubleTapGesturesEnabled = true

                        // Set OpenFreeMap Public Style
                        maplibreMap.setStyle("https://tiles.openfreemap.org/styles/bright") { _ ->
                            maplibreMap.cameraPosition = CameraPosition.Builder()
                                .target(nasugbuCenter)
                                .zoom(15.0)
                                .tilt(20.0)
                                .build()

                            setupMapLibreRouteOverlay(maplibreMap, fetchedRoutePoints)
                        }

                        // Touch listener to disallow parent intercept and hide search bar during map gestures
                        setOnTouchListener { v, event ->
                            v.parent?.requestDisallowInterceptTouchEvent(true)
                            when (event.actionMasked) {
                                MotionEvent.ACTION_DOWN,
                                MotionEvent.ACTION_MOVE,
                                MotionEvent.ACTION_POINTER_DOWN -> {
                                    idleJob?.cancel()
                                    onMapInteractionStateChange(true)
                                }
                                MotionEvent.ACTION_UP,
                                MotionEvent.ACTION_CANCEL,
                                MotionEvent.ACTION_POINTER_UP -> {
                                    v.performClick()
                                    idleJob?.cancel()
                                    idleJob = scope.launch {
                                        delay(1500)
                                        onMapInteractionStateChange(false)
                                    }
                                }
                            }
                            v.onTouchEvent(event)
                        }
                    }
                }
            },
            update = {
                mapLibreMapInstance?.let { maplibreMap ->
                    setupMapLibreRouteOverlay(maplibreMap, fetchedRoutePoints)
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

        // Selected Route Details Overlay Card
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

private fun setupMapLibreRouteOverlay(maplibreMap: MapLibreMap, points: List<GeoPoint>) {
    val style = maplibreMap.style ?: return

    val sourceId = "today-route-source"
    val layerId = "today-route-layer"
    val arrowLayerId = "today-route-arrows-layer"

    try {
        if (style.getLayer(arrowLayerId) != null) style.removeLayer(arrowLayerId)
        if (style.getLayer(layerId) != null) style.removeLayer(layerId)
        if (style.getSource(sourceId) != null) style.removeSource(sourceId)

        if (points.isNotEmpty()) {
            val geoJsonPoints = points.map { Point.fromLngLat(it.longitude, it.latitude) }
            val lineString = LineString.fromLngLats(geoJsonPoints)
            val featureCollection = FeatureCollection.fromFeature(Feature.fromGeometry(lineString))

            style.addSource(GeoJsonSource(sourceId, featureCollection))

            // 1. Royal Blue Route Line Layer
            val lineLayer = LineLayer(layerId, sourceId).apply {
                setProperties(
                    PropertyFactory.lineColor(android.graphics.Color.parseColor("#3B82F6")),
                    PropertyFactory.lineWidth(7f),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
                )
            }
            style.addLayer(lineLayer)

            // 2. Repeating Directional Arrowheads along the route line
            val arrowLayer = SymbolLayer(arrowLayerId, sourceId).apply {
                setProperties(
                    PropertyFactory.symbolPlacement(Property.SYMBOL_PLACEMENT_LINE),
                    PropertyFactory.symbolSpacing(50f),
                    PropertyFactory.textField("▶"),
                    PropertyFactory.textSize(14f),
                    PropertyFactory.textColor(android.graphics.Color.WHITE),
                    PropertyFactory.textHaloColor(android.graphics.Color.parseColor("#3B82F6")),
                    PropertyFactory.textHaloWidth(2f),
                    PropertyFactory.textRotationAlignment(Property.TEXT_ROTATION_ALIGNMENT_MAP),
                    PropertyFactory.textPitchAlignment(Property.TEXT_PITCH_ALIGNMENT_MAP)
                )
            }
            style.addLayer(arrowLayer)
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
