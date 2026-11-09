package com.example.hakot.data

import com.example.hakot.model.StreetSegment
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import kotlin.math.cos
import kotlin.math.sqrt

data class MatchedSegment(
    val segmentId: String,
    val streetName: String,
    val sequenceOrder: Int,
    val traversalDirection: String, // "FORWARD" or "REVERSE"
    val geoPoints: List<Pair<Double, Double>>
)

data class RoadMatchResult(
    val isSuccess: Boolean,
    val matchedSegments: List<MatchedSegment>,
    val candidateRoadCount: Int,
    val totalGisRoadCount: Int,
    val executionTimeMs: Long,
    val warnings: List<String>
)

object RoadMatchingEngine {

    // Converts LatLng distance difference to meters (WGS84 EPSG:4326 metric approximation at 14.07° N)
    fun distanceInMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val latMid = (lat1 + lat2) / 2.0 * Math.PI / 180.0
        val dLat = (lat2 - lat1) * 111139.0
        val dLon = (lon2 - lon1) * 111139.0 * cos(latMid)
        return sqrt(dLat * dLat + dLon * dLon)
    }

    // Projects point P(px, py) onto line segment AB and returns projection point (projLat, projLng) + parametric t in [0.0, 1.0]
    private fun projectPointToLineSegment(
        px: Double, py: Double,
        ax: Double, ay: Double,
        bx: Double, by: Double
    ): Triple<Double, Double, Double> {
        val abx = bx - ax
        val aby = by - ay
        val apx = px - ax
        val apy = py - ay

        val abLenSq = abx * abx + aby * aby
        if (abLenSq == 0.0) return Triple(ay, ax, 0.0)

        val t = ((apx * abx + apy * aby) / abLenSq).coerceIn(0.0, 1.0)
        val projLng = ax + t * abx
        val projLat = ay + t * aby

        return Triple(projLat, projLng, t)
    }

    // Minimum perpendicular distance from point P to LineString geometry
    private fun pointToLineStringDistanceMeters(
        sketchPt: GeoPoint,
        geoPoints: List<Pair<Double, Double>>
    ): Pair<Double, Double> { // Pair(minDistanceMeters, parametricPositionAlongLine)
        var minDistance = Double.MAX_VALUE
        var bestPosition = 0.0

        for (i in 0 until geoPoints.size - 1) {
            val p1 = geoPoints[i]
            val p2 = geoPoints[i + 1]

            val proj = projectPointToLineSegment(
                sketchPt.longitude, sketchPt.latitude,
                p1.second, p1.first,
                p2.second, p2.first
            )

            val dist = distanceInMeters(sketchPt.latitude, sketchPt.longitude, proj.first, proj.second)
            if (dist < minDistance) {
                minDistance = dist
                // Map local segment t to global LineString position [0.0..1.0]
                bestPosition = (i.toDouble() + proj.third) / (geoPoints.size - 1).toDouble()
            }
        }

        return Pair(minDistance, bestPosition)
    }

    fun matchSketchToRoads(
        sketchPoints: List<GeoPoint>,
        roadGeoJson: String?,
        fallbackSegments: List<StreetSegment>
    ): RoadMatchResult {
        val startTime = System.currentTimeMillis()
        val warnings = mutableListOf<String>()

        if (sketchPoints.size < 2) {
            return RoadMatchResult(
                isSuccess = false,
                matchedSegments = emptyList(),
                candidateRoadCount = 0,
                totalGisRoadCount = 0,
                executionTimeMs = System.currentTimeMillis() - startTime,
                warnings = listOf("Insufficient sketch points. Draw a longer path on the map.")
            )
        }

        // 1. Compute Bounding Box of Sketch + 0.005° (~550m) Buffer
        val minLat = sketchPoints.minOf { it.latitude } - 0.005
        val maxLat = sketchPoints.maxOf { it.latitude } + 0.005
        val minLng = sketchPoints.minOf { it.longitude } - 0.005
        val maxLng = sketchPoints.maxOf { it.longitude } + 0.005

        val candidateSegments = mutableListOf<StreetSegment>()
        var totalCount = 0

        if (!roadGeoJson.isNullOrBlank()) {
            try {
                val json = JSONObject(roadGeoJson)
                val features = json.getJSONArray("features")
                totalCount = features.length()

                for (i in 0 until features.length()) {
                    val feat = features.getJSONObject(i)
                    val props = feat.getJSONObject("properties")
                    val geom = feat.getJSONObject("geometry")
                    val coords = geom.getJSONArray("coordinates")

                    val id = if (props.has("id")) props.getString("id") else "seg_$i"
                    val name = if (props.has("segment_name")) props.getString("segment_name")
                    else if (props.has("street_name")) props.getString("street_name") else "Nasugbu Road $i"

                    val ptList = mutableListOf<Pair<Double, Double>>()
                    var segMinLat = 90.0
                    var segMaxLat = -90.0
                    var segMinLng = 180.0
                    var segMaxLng = -180.0

                    for (j in 0 until coords.length()) {
                        val pt = coords.getJSONArray(j)
                        val lng = pt.getDouble(0)
                        val lat = pt.getDouble(1)
                        ptList.add(Pair(lat, lng))

                        if (lat < segMinLat) segMinLat = lat
                        if (lat > segMaxLat) segMaxLat = lat
                        if (lng < segMinLng) segMinLng = lng
                        if (lng > segMaxLng) segMaxLng = lng
                    }

                    // Spatial Bounding Box Filter
                    if (segMaxLat >= minLat && segMinLat <= maxLat && segMaxLng >= minLng && segMinLng <= maxLng) {
                        candidateSegments.add(
                            StreetSegment(
                                segmentId = id,
                                streetName = name,
                                barangayId = "brgy_nasugbu",
                                barangayName = "Nasugbu Scope",
                                municipalityOrgId = "org_nasugbu_lgu",
                                geoPoints = ptList
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (candidateSegments.isEmpty() && fallbackSegments.isNotEmpty()) {
            candidateSegments.addAll(fallbackSegments)
            totalCount = fallbackSegments.size
        }

        if (candidateSegments.isEmpty()) {
            return RoadMatchResult(
                isSuccess = false,
                matchedSegments = emptyList(),
                candidateRoadCount = 0,
                totalGisRoadCount = totalCount,
                executionTimeMs = System.currentTimeMillis() - startTime,
                warnings = listOf("No GIS road segments found within the sketch area.")
            )
        }

        // 2. Sample Sketch Points at ~15m Increments
        val maxSnapDistanceMeters = 35.0
        val sampledSketch = mutableListOf<GeoPoint>()
        sampledSketch.add(sketchPoints.first())
        var lastPt = sketchPoints.first()
        for (pt in sketchPoints) {
            if (pt.distanceToAsDouble(lastPt) >= 15.0) {
                sampledSketch.add(pt)
                lastPt = pt
            }
        }
        if (sampledSketch.last() != sketchPoints.last()) {
            sampledSketch.add(sketchPoints.last())
        }

        // Track matched segments with entry and exit projection parameters
        val matchedOrderedList = mutableListOf<MatchedSegment>()

        for (idx in sampledSketch.indices) {
            val sketchPt = sampledSketch[idx]

            // Find top 2 nearest candidates
            val sortedCandidates = candidateSegments.map { cand ->
                val res = pointToLineStringDistanceMeters(sketchPt, cand.geoPoints)
                Triple(cand, res.first, res.second)
            }.sortedBy { it.second }

            if (sortedCandidates.isNotEmpty()) {
                val best = sortedCandidates[0]
                val bestCand = best.first
                val bestDist = best.second

                // Check Ambiguity against 2nd candidate
                if (sortedCandidates.size > 1) {
                    val secondBest = sortedCandidates[1]
                    val deltaDist = secondBest.second - bestDist
                    if (bestDist <= maxSnapDistanceMeters && deltaDist < 5.0 && secondBest.first.segmentId != bestCand.segmentId) {
                        warnings.add("Ambiguous match near ${bestCand.streetName} and ${secondBest.first.streetName} (delta ${deltaDist.toString().take(4)}m).")
                    }
                }

                if (bestDist <= maxSnapDistanceMeters) {
                    // Check if segment was recently visited (allow legitimate revisits if separated by >=2 other segments)
                    val lastTwoIds = matchedOrderedList.takeLast(2).map { it.segmentId }
                    if (!lastTwoIds.contains(bestCand.segmentId)) {

                        // 3. Accurate Projection-Based Traversal Direction Determination
                        // Compare sketch entry point projection t vs sketch exit point projection t
                        val nextSketchPt = if (idx < sampledSketch.size - 1) sampledSketch[idx + 1] else sketchPt
                        val entryProj = pointToLineStringDistanceMeters(sketchPt, bestCand.geoPoints).second
                        val exitProj = pointToLineStringDistanceMeters(nextSketchPt, bestCand.geoPoints).second

                        val direction = if (entryProj <= exitProj) "FORWARD" else "REVERSE"

                        matchedOrderedList.add(
                            MatchedSegment(
                                segmentId = bestCand.segmentId,
                                streetName = bestCand.streetName,
                                sequenceOrder = matchedOrderedList.size + 1,
                                traversalDirection = direction,
                                geoPoints = if (direction == "FORWARD") bestCand.geoPoints else bestCand.geoPoints.reversed()
                            )
                        )
                    }
                } else {
                    warnings.add("Sketch section near ${sketchPt.latitude.toString().take(7)}, ${sketchPt.longitude.toString().take(7)} is $bestDist meters off-road.")
                }
            }
        }

        // 4. Geometric Route Continuity & Gap Validation
        var hasDisconnectedGap = false
        if (matchedOrderedList.size >= 2) {
            for (m in 0 until matchedOrderedList.size - 1) {
                val seg1 = matchedOrderedList[m]
                val seg2 = matchedOrderedList[m + 1]

                // Check distance from seg1 end point to seg2 start point
                val endSeg1 = seg1.geoPoints.last()
                val startSeg2 = seg2.geoPoints.first()
                val gapDist = distanceInMeters(endSeg1.first, endSeg1.second, startSeg2.first, startSeg2.second)

                if (gapDist > 35.0) {
                    hasDisconnectedGap = true
                    warnings.add("Route gap detected between '${seg1.streetName}' and '${seg2.streetName}' (${gapDist.toInt()}m gap).")
                }
            }
        }

        val isFinalSuccess = matchedOrderedList.isNotEmpty() && !hasDisconnectedGap

        return RoadMatchResult(
            isSuccess = isFinalSuccess,
            matchedSegments = matchedOrderedList,
            candidateRoadCount = candidateSegments.size,
            totalGisRoadCount = totalCount,
            executionTimeMs = System.currentTimeMillis() - startTime,
            warnings = warnings.distinct()
        )
    }
}
