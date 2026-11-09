package com.example.hakot.ui.components

import android.annotation.SuppressLint
import android.graphics.PointF
import android.view.MotionEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Create
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import com.example.hakot.data.HakotRepository
import com.example.hakot.data.OsrmRoutingHelper
import com.example.hakot.ui.theme.DarkSurfaceCard
import com.example.hakot.ui.theme.EcoGreenLight
import com.example.hakot.ui.theme.EcoGreenPrimary
import com.example.hakot.ui.theme.HazardousRed
import com.example.hakot.ui.theme.TextOnDarkPrimary
import com.example.hakot.ui.theme.TextOnDarkSecondary
import kotlinx.coroutines.delay
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.style.layers.FillLayer
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
    isDrawingRouteMode: Boolean = false,
    sketchedPoints: List<GeoPoint> = emptyList(),
    matchedRoutePoints: List<GeoPoint> = emptyList(),
    onSketchPointsUpdated: (List<GeoPoint>) -> Unit = {},
    onUndoSketch: () -> Unit = {},
    onClearSketch: () -> Unit = {},
    onFinishSketch: () -> Unit = {},
    onReportClick: () -> Unit = {},
    onMapInteractionStateChange: (isInteracting: Boolean) -> Unit = {}
) {
    val context = LocalContext.current

    remember(context) {
        MapLibre.getInstance(context.applicationContext)
        true
    }

    var selectedMarker by remember { mutableStateOf<SelectedMarkerInfo?>(null) }
    var mapLibreMapInstance by remember { mutableStateOf<MapLibreMap?>(null) }
    var mapViewInstance by remember { mutableStateOf<MapView?>(null) }
    var fetchedRoutePoints by remember { mutableStateOf<List<GeoPoint>>(emptyList()) }

    // Internal list for live touch drawing points
    val internalSketchPoints = remember { mutableStateListOf<GeoPoint>() }

    // Synchronize internal sketch list with external state
    LaunchedEffect(sketchedPoints) {
        if (sketchedPoints != internalSketchPoints.toList()) {
            internalSketchPoints.clear()
            internalSketchPoints.addAll(sketchedPoints)
        }
        mapLibreMapInstance?.let { maplibreMap ->
            setupMapLibreSketchLayer(maplibreMap, internalSketchPoints)
        }
    }

    val nasugbuCenter = remember { LatLng(14.0700, 120.6300) }

    // ---------------------------------------------------------
    // LOAD NASUGBU GIS DATA FROM SUPABASE
    // ---------------------------------------------------------

    LaunchedEffect(Unit) {
        if (!HakotRepository.isGisLoaded) {
            HakotRepository.loadNasugbuGisData()
        }

        mapLibreMapInstance?.let { maplibreMap ->
            setupMapLibreDatabaseLayers(maplibreMap, if (matchedRoutePoints.isNotEmpty()) matchedRoutePoints else fetchedRoutePoints)
            setupMapLibreSketchLayer(maplibreMap, internalSketchPoints)
        }
    }

    // ---------------------------------------------------------
    // FETCH DATE-SPECIFIC COLLECTION ROUTE
    // ---------------------------------------------------------

    LaunchedEffect(HakotRepository.selectedMapDate, matchedRoutePoints) {
        if (matchedRoutePoints.isNotEmpty()) {
            fetchedRoutePoints = matchedRoutePoints
        } else {
            val selectedDate = HakotRepository.selectedMapDate
            val scheduleForDate = HakotRepository.schedules.find {
                it.date == selectedDate
            }

            if (scheduleForDate != null) {
                val matchedRoute =
                    HakotRepository.routes.find {
                        it.routeName == scheduleForDate.routeName
                    } ?: HakotRepository.routes.firstOrNull()

                if (matchedRoute != null && matchedRoute.geoPoints.isNotEmpty()) {
                    fetchedRoutePoints = matchedRoute.geoPoints.map {
                        GeoPoint(it.first, it.second)
                    }

                    val points = OsrmRoutingHelper.fetchRoadRoute(
                        matchedRoute.geoPoints
                    )

                    if (points.isNotEmpty()) {
                        fetchedRoutePoints = points
                    }
                } else {
                    fetchedRoutePoints = emptyList()
                }
            } else {
                fetchedRoutePoints = emptyList()
            }
        }

        mapLibreMapInstance?.let { maplibreMap ->
            setupMapLibreDatabaseLayers(maplibreMap, fetchedRoutePoints)
            setupMapLibreSketchLayer(maplibreMap, internalSketchPoints)
        }
    }

    // Dynamic Camera Gestures Control for Drawing Mode vs Normal Panning
    LaunchedEffect(isDrawingRouteMode, mapLibreMapInstance) {
        mapLibreMapInstance?.let { maplibreMap ->
            if (isDrawingRouteMode) {
                maplibreMap.uiSettings.isScrollGesturesEnabled = false
                maplibreMap.uiSettings.isZoomGesturesEnabled = false
                maplibreMap.uiSettings.isRotateGesturesEnabled = false
                maplibreMap.uiSettings.isTiltGesturesEnabled = false
            } else {
                maplibreMap.uiSettings.isScrollGesturesEnabled = true
                maplibreMap.uiSettings.isZoomGesturesEnabled = true
                maplibreMap.uiSettings.isRotateGesturesEnabled = true
                maplibreMap.uiSettings.isTiltGesturesEnabled = true
            }
        }
    }

    // ---------------------------------------------------------
    // MAP USER INTERFACE
    // ---------------------------------------------------------

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF4F3EE))
    ) {
        AndroidView(
            factory = { ctx ->
                MapView(ctx).apply {
                    mapViewInstance = this

                    try {
                        onCreate(null)
                        onStart()
                        onResume()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    getMapAsync { maplibreMap ->
                        mapLibreMapInstance = maplibreMap

                        maplibreMap.uiSettings.isZoomGesturesEnabled = !isDrawingRouteMode
                        maplibreMap.uiSettings.isScrollGesturesEnabled = !isDrawingRouteMode
                        maplibreMap.uiSettings.isRotateGesturesEnabled = !isDrawingRouteMode
                        maplibreMap.uiSettings.isTiltGesturesEnabled = !isDrawingRouteMode
                        maplibreMap.uiSettings.isDoubleTapGesturesEnabled = !isDrawingRouteMode

                        maplibreMap.setStyle(
                            "https://tiles.openfreemap.org/styles/bright"
                        ) { _ ->
                            try {
                                maplibreMap.cameraPosition =
                                    CameraPosition.Builder()
                                        .target(nasugbuCenter)
                                        .zoom(15.0)
                                        .tilt(20.0)
                                        .build()

                                setupMapLibreDatabaseLayers(
                                    maplibreMap,
                                    fetchedRoutePoints
                                )
                                setupMapLibreSketchLayer(
                                    maplibreMap,
                                    internalSketchPoints
                                )
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }

                        // Touch Listener: Handles Freehand Sketching when isDrawingRouteMode is TRUE
                        setOnTouchListener { v, event ->
                            v.parent?.requestDisallowInterceptTouchEvent(true)

                            if (isDrawingRouteMode) {
                                when (event.actionMasked) {
                                    MotionEvent.ACTION_DOWN,
                                    MotionEvent.ACTION_MOVE -> {
                                        val latLng = maplibreMap.projection.fromScreenLocation(PointF(event.x, event.y))
                                        val newPoint = GeoPoint(latLng.latitude, latLng.longitude)
                                        
                                        // Throttle nearby points to avoid excessive duplicate points
                                        if (internalSketchPoints.isEmpty() || internalSketchPoints.last().distanceToAsDouble(newPoint) > 3.0) {
                                            internalSketchPoints.add(newPoint)
                                            onSketchPointsUpdated(internalSketchPoints.toList())
                                            setupMapLibreSketchLayer(maplibreMap, internalSketchPoints)
                                        }
                                        true
                                    }
                                    MotionEvent.ACTION_UP,
                                    MotionEvent.ACTION_CANCEL -> {
                                        v.performClick()
                                        true
                                    }
                                    else -> false
                                }
                            } else {
                                when (event.actionMasked) {
                                    MotionEvent.ACTION_DOWN,
                                    MotionEvent.ACTION_MOVE,
                                    MotionEvent.ACTION_POINTER_DOWN -> {
                                        onMapInteractionStateChange(true)
                                    }

                                    MotionEvent.ACTION_UP,
                                    MotionEvent.ACTION_CANCEL,
                                    MotionEvent.ACTION_POINTER_UP -> {
                                        v.performClick()
                                        onMapInteractionStateChange(false)
                                    }
                                }
                                v.onTouchEvent(event)
                            }
                        }
                    }
                }
            },
            update = { mapView ->
                mapViewInstance = mapView

                mapLibreMapInstance?.let { maplibreMap ->
                    setupMapLibreDatabaseLayers(
                        maplibreMap,
                        fetchedRoutePoints
                    )
                    setupMapLibreSketchLayer(
                        maplibreMap,
                        internalSketchPoints
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // -----------------------------------------------------
        // MAPVIEW LIFECYCLE CLEANUP
        // -----------------------------------------------------

        DisposableEffect(Unit) {
            onDispose {
                try {
                    mapViewInstance?.onPause()
                    mapViewInstance?.onStop()
                    mapViewInstance?.onDestroy()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        // -----------------------------------------------------
        // FLOATING ROUTE SKETCHING CONTROLS TOOLBAR (PHASE A)
        // -----------------------------------------------------
        AnimatedVisibility(
            visible = isDrawingRouteMode || internalSketchPoints.isNotEmpty(),
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 70.dp)
        ) {
            Surface(
                color = DarkSurfaceCard,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, EcoGreenPrimary),
                shadowElevation = 8.dp,
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Create,
                        contentDescription = "Drawing Mode",
                        tint = EcoGreenPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = if (isDrawingRouteMode) "Drawing Route (${internalSketchPoints.size} pts)" else "Sketch Ready",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = EcoGreenLight
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    // Undo Button
                    IconButton(
                        onClick = {
                            if (internalSketchPoints.isNotEmpty()) {
                                // Remove last 10 sketch points for smooth undo stroke
                                val removeCount = minOf(10, internalSketchPoints.size)
                                repeat(removeCount) { internalSketchPoints.removeAt(internalSketchPoints.size - 1) }
                                onSketchPointsUpdated(internalSketchPoints.toList())
                                mapLibreMapInstance?.let { setupMapLibreSketchLayer(it, internalSketchPoints) }
                                onUndoSketch()
                            }
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo", tint = TextOnDarkPrimary)
                    }

                    // Clear Button
                    IconButton(
                        onClick = {
                            internalSketchPoints.clear()
                            onSketchPointsUpdated(emptyList())
                            mapLibreMapInstance?.let { setupMapLibreSketchLayer(it, emptyList()) }
                            onClearSketch()
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear", tint = HazardousRed)
                    }

                    // Finish Drawing Button
                    Button(
                        onClick = onFinishSketch,
                        colors = ButtonDefaults.buttonColors(containerColor = EcoGreenPrimary),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Check, contentDescription = "Done", tint = Color.Black, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Finish Sketch", fontSize = 11.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // -----------------------------------------------------
        // SELECTED MARKER DETAILS OVERLAY
        // -----------------------------------------------------

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
                    colors = CardDefaults.cardColors(
                        containerColor = DarkSurfaceCard
                    ),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            1.dp,
                            info.color.copy(alpha = 0.6f),
                            RoundedCornerShape(20.dp)
                        )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
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
                            modifier = Modifier.padding(
                                start = 18.dp,
                                bottom = 8.dp
                            )
                        )

                        info.details.forEach { (k, v) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    k,
                                    fontSize = 12.sp,
                                    color = TextOnDarkSecondary
                                )

                                Text(
                                    v,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextOnDarkPrimary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// PHASE A: TEMPORARY FREEHAND ROUTE SKETCH MAPLIBRE LAYER
// -------------------------------------------------------------

private fun setupMapLibreSketchLayer(
    maplibreMap: MapLibreMap,
    points: List<GeoPoint>
) {
    val style = maplibreMap.style ?: return

    val sketchSourceId = "sketch-route-source"
    val sketchLayerId = "sketch-route-layer"

    try {
        if (points.size >= 2) {
            val geoJsonPoints = points.map { Point.fromLngLat(it.longitude, it.latitude) }
            val lineString = LineString.fromLngLats(geoJsonPoints)
            val featureCollection = FeatureCollection.fromFeature(Feature.fromGeometry(lineString))

            val existingSource = style.getSourceAs<GeoJsonSource>(sketchSourceId)
            if (existingSource != null) {
                existingSource.setGeoJson(featureCollection)
            } else {
                style.addSource(GeoJsonSource(sketchSourceId, featureCollection))
            }

            if (style.getLayer(sketchLayerId) == null) {
                val sketchLayer = LineLayer(sketchLayerId, sketchSourceId).apply {
                    setProperties(
                        PropertyFactory.lineColor(android.graphics.Color.parseColor("#10B981")), // Emerald Green Sketch
                        PropertyFactory.lineWidth(6.5f),
                        PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                        PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
                    )
                }
                style.addLayer(sketchLayer)
            }
        } else {
            if (style.getLayer(sketchLayerId) != null) style.removeLayer(sketchLayerId)
            if (style.getSource(sketchSourceId) != null) style.removeSource(sketchSourceId)
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

// -------------------------------------------------------------
// NASUGBU GIS LAYERS
// -------------------------------------------------------------

private fun setupNasugbuGisLayers(
    maplibreMap: MapLibreMap
) {
    val style = maplibreMap.style ?: return

    val barangaySourceId = "nasugbu-barangay-source"
    val barangayFillLayerId = "nasugbu-barangay-fill-layer"
    val barangayOutlineLayerId = "nasugbu-barangay-outline-layer"

    val roadSourceId = "nasugbu-road-source"
    val roadLayerId = "nasugbu-road-layer"

    try {
        val barangayGeoJson = HakotRepository.barangayGeoJson

        if (!barangayGeoJson.isNullOrBlank()) {
            val existingBarangaySource =
                style.getSourceAs<GeoJsonSource>(barangaySourceId)

            if (existingBarangaySource != null) {
                existingBarangaySource.setGeoJson(barangayGeoJson)
            } else {
                style.addSource(
                    GeoJsonSource(
                        barangaySourceId,
                        barangayGeoJson
                    )
                )
            }

            if (style.getLayer(barangayFillLayerId) == null) {
                val barangayFillLayer = FillLayer(
                    barangayFillLayerId,
                    barangaySourceId
                ).apply {
                    setProperties(
                        PropertyFactory.fillColor(
                            android.graphics.Color.parseColor("#10B981")
                        ),
                        PropertyFactory.fillOpacity(0.12f)
                    )
                }

                style.addLayer(barangayFillLayer)
            }

            if (style.getLayer(barangayOutlineLayerId) == null) {
                val barangayOutlineLayer = LineLayer(
                    barangayOutlineLayerId,
                    barangaySourceId
                ).apply {
                    setProperties(
                        PropertyFactory.lineColor(
                            android.graphics.Color.parseColor("#059669")
                        ),
                        PropertyFactory.lineWidth(2f),
                        PropertyFactory.lineOpacity(0.85f),
                        PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                        PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
                    )
                }

                style.addLayer(barangayOutlineLayer)
            }
        }

        val roadGeoJson = HakotRepository.roadGeoJson

        if (!roadGeoJson.isNullOrBlank()) {
            val existingRoadSource =
                style.getSourceAs<GeoJsonSource>(roadSourceId)

            if (existingRoadSource != null) {
                existingRoadSource.setGeoJson(roadGeoJson)
            } else {
                style.addSource(
                    GeoJsonSource(
                        roadSourceId,
                        roadGeoJson
                    )
                )
            }

            if (style.getLayer(roadLayerId) == null) {
                val roadLayer = LineLayer(
                    roadLayerId,
                    roadSourceId
                ).apply {
                    setProperties(
                        PropertyFactory.lineColor(
                            android.graphics.Color.parseColor("#64748B")
                        ),
                        PropertyFactory.lineWidth(1.3f),
                        PropertyFactory.lineOpacity(0.85f),
                        PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                        PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
                    )
                }

                style.addLayer(roadLayer)
            }
        }

    } catch (e: Exception) {
        e.printStackTrace()
    }
}

// -------------------------------------------------------------
// ROUTE, GPS TRAIL, AND OTHER DATABASE MAP LAYERS
// -------------------------------------------------------------

private fun setupMapLibreDatabaseLayers(
    maplibreMap: MapLibreMap,
    points: List<GeoPoint>
) {
    val style = maplibreMap.style ?: return

    try {
        setupNasugbuGisLayers(maplibreMap)

        val sourceId = "today-route-source"
        val layerId = "today-route-layer"
        val arrowLayerId = "today-route-arrows-layer"

        val gpsTrailSourceId = "truck-gps-trail-source"
        val gpsTrailLayerId = "truck-gps-trail-layer"

        if (style.getLayer(gpsTrailLayerId) != null) {
            style.removeLayer(gpsTrailLayerId)
        }

        if (style.getSource(gpsTrailSourceId) != null) {
            style.removeSource(gpsTrailSourceId)
        }

        if (style.getLayer(arrowLayerId) != null) {
            style.removeLayer(arrowLayerId)
        }

        if (style.getLayer(layerId) != null) {
            style.removeLayer(layerId)
        }

        if (style.getSource(sourceId) != null) {
            style.removeSource(sourceId)
        }

        if (points.isNotEmpty()) {
            val geoJsonPoints = points.map {
                Point.fromLngLat(it.longitude, it.latitude)
            }

            val lineString = LineString.fromLngLats(geoJsonPoints)

            val featureCollection =
                FeatureCollection.fromFeature(
                    Feature.fromGeometry(lineString)
                )

            style.addSource(
                GeoJsonSource(
                    sourceId,
                    featureCollection
                )
            )

            val lineLayer = LineLayer(
                layerId,
                sourceId
            ).apply {
                setProperties(
                    PropertyFactory.lineColor(
                        android.graphics.Color.parseColor("#3B82F6")
                    ),
                    PropertyFactory.lineWidth(7f),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
                )
            }

            style.addLayer(lineLayer)

            val arrowLayer = SymbolLayer(
                arrowLayerId,
                sourceId
            ).apply {
                setProperties(
                    PropertyFactory.symbolPlacement(
                        Property.SYMBOL_PLACEMENT_LINE
                    ),
                    PropertyFactory.symbolSpacing(50f),
                    PropertyFactory.textField("▶"),
                    PropertyFactory.textSize(14f),
                    PropertyFactory.textColor(
                        android.graphics.Color.WHITE
                    ),
                    PropertyFactory.textHaloColor(
                        android.graphics.Color.parseColor("#3B82F6")
                    ),
                    PropertyFactory.textHaloWidth(2f),
                    PropertyFactory.textRotationAlignment(
                        Property.TEXT_ROTATION_ALIGNMENT_MAP
                    ),
                    PropertyFactory.textPitchAlignment(
                        Property.TEXT_PITCH_ALIGNMENT_MAP
                    )
                )
            }

            style.addLayer(arrowLayer)
        }

        val gpsPoints = HakotRepository.truckGpsTrails.map {
            Point.fromLngLat(it.longitude, it.latitude)
        }

        if (gpsPoints.size >= 2) {
            val gpsLineString = LineString.fromLngLats(gpsPoints)

            val gpsCollection =
                FeatureCollection.fromFeature(
                    Feature.fromGeometry(gpsLineString)
                )

            style.addSource(
                GeoJsonSource(
                    gpsTrailSourceId,
                    gpsCollection
                )
            )

            val gpsLayer = LineLayer(
                gpsTrailLayerId,
                gpsTrailSourceId
            ).apply {
                setProperties(
                    PropertyFactory.lineColor(
                        android.graphics.Color.parseColor("#06B6D4")
                    ),
                    PropertyFactory.lineWidth(4f),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
                )
            }

            style.addLayer(gpsLayer)
        }

    } catch (e: Exception) {
        e.printStackTrace()
    }
}
