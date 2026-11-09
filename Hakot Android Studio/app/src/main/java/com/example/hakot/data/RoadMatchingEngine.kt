package com.example.hakot.data

import com.example.hakot.model.StreetSegment
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.max
import kotlin.math.sqrt

data class MatchedSegment(
    val segmentId: String,
    val streetName: String,
    val sequenceOrder: Int,
    val traversalDirection: String, // "FORWARD" or "REVERSE"
    val entryProgress: Double, // 0.0 to 1.0 along LineString
    val exitProgress: Double,  // 0.0 to 1.0 along LineString
    val entryCoordinate: Pair<Double, Double>, // (lat, lng)
    val exitCoordinate: Pair<Double, Double>,  // (lat, lng)
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

data class DetailedIntersection(
    val intersectionCoordinate: Pair<Double, Double>,
    val segIndex1: Int,
    val segIndex2: Int,
    val t: Double,
    val u: Double,
    val progress1: Double,
    val progress2: Double
)

data class ProjectionPoint(
    val lat: Double,
    val lng: Double,
    val segIndex: Int,
    val t: Double,
    val distanceMeters: Double,
    val progress: Double
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

    private data class LineMetrics(
        val totalLengthMeters: Double,
        val cumulativeDistances: List<Double>,
        val segmentLengths: List<Double>
    )

    private fun computeLineMetrics(geoPoints: List<Pair<Double, Double>>): LineMetrics {
        val cumDistances = mutableListOf<Double>()
        val segLengths = mutableListOf<Double>()
        var total = 0.0
        cumDistances.add(0.0)

        for (i in 0 until geoPoints.size - 1) {
            val p1 = geoPoints[i]
            val p2 = geoPoints[i + 1]
            val len = distanceInMeters(p1.first, p1.second, p2.first, p2.second)
            segLengths.add(len)
            total += len
            cumDistances.add(total)
        }
        return LineMetrics(total, cumDistances, segLengths)
    }

    private fun interpolatePointAlongLine(
        geoPoints: List<Pair<Double, Double>>,
        metrics: LineMetrics,
        fraction: Double
    ): Pair<Double, Double> {
        if (geoPoints.size <= 1) return geoPoints.firstOrNull() ?: Pair(0.0, 0.0)
        if (fraction <= 0.0) return geoPoints.first()
        if (fraction >= 1.0) return geoPoints.last()

        val targetDist = fraction * metrics.totalLengthMeters
        var currentDist = 0.0

        for (i in 0 until metrics.segmentLengths.size) {
            val segLen = metrics.segmentLengths[i]
            if (currentDist + segLen >= targetDist) {
                val localFraction = if (segLen == 0.0) 0.0 else (targetDist - currentDist) / segLen
                val p1 = geoPoints[i]
                val p2 = geoPoints[i + 1]
                val lat = p1.first + localFraction * (p2.first - p1.first)
                val lng = p1.second + localFraction * (p2.second - p1.second)
                return Pair(lat, lng)
            }
            currentDist += segLen
        }
        return geoPoints.last()
    }

    private fun projectPointDetailed(
        sketchPt: GeoPoint,
        geoPoints: List<Pair<Double, Double>>
    ): ProjectionPoint {
        val metrics = computeLineMetrics(geoPoints)
        var minDistance = Double.MAX_VALUE
        var bestSegIndex = 0
        var bestT = 0.0
        var bestLat = geoPoints.first().first
        var bestLng = geoPoints.first().second
        var bestPosition = 0.0

        if (metrics.totalLengthMeters == 0.0) {
            val p = geoPoints.first()
            return ProjectionPoint(p.first, p.second, 0, 0.0, distanceInMeters(sketchPt.latitude, sketchPt.longitude, p.first, p.second), 0.0)
        }

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
                bestSegIndex = i
                bestT = proj.third
                bestLat = proj.first
                bestLng = proj.second
                val segStartDist = metrics.cumulativeDistances[i]
                val segLen = metrics.segmentLengths[i]
                val projOffsetFromSegStart = distanceInMeters(p1.first, p1.second, proj.first, proj.second)
                val absoluteDist = segStartDist + if (segLen > 0.0) (projOffsetFromSegStart / segLen) * segLen else 0.0
                bestPosition = (absoluteDist / metrics.totalLengthMeters).coerceIn(0.0, 1.0)
            }
        }
        return ProjectionPoint(bestLat, bestLng, bestSegIndex, bestT, minDistance, bestPosition)
    }

    // Phase 2.13.10: Return ALL actual geometric intersections & collinear overlaps with complete index and progress info
    fun findAllLineStringIntersections(
        line1: List<Pair<Double, Double>>,
        line2: List<Pair<Double, Double>>
    ): List<DetailedIntersection> {
        val results = mutableListOf<DetailedIntersection>()
        val metrics1 = computeLineMetrics(line1)
        val metrics2 = computeLineMetrics(line2)

        val start1 = line1.first(); val end1 = line1.last()
        val start2 = line2.first(); val end2 = line2.last()

        fun addEndpointMatch(pt: Pair<Double, Double>, idx1: Int, idx2: Int, t1: Double, t2: Double) {
            val absDist1 = metrics1.cumulativeDistances[idx1] + t1 * metrics1.segmentLengths[idx1]
            val absDist2 = metrics2.cumulativeDistances[idx2] + t2 * metrics2.segmentLengths[idx2]
            val p1 = if (metrics1.totalLengthMeters > 0.0) absDist1 / metrics1.totalLengthMeters else 0.0
            val p2 = if (metrics2.totalLengthMeters > 0.0) absDist2 / metrics2.totalLengthMeters else 0.0
            if (results.none { abs(it.progress1 - p1) < 0.001 && abs(it.progress2 - p2) < 0.001 }) {
                results.add(DetailedIntersection(pt, idx1, idx2, t1, t2, p1, p2))
            }
        }

        if (end1 == start2 || distanceInMeters(end1.first, end1.second, start2.first, start2.second) <= 0.001) {
            addEndpointMatch(start2, line1.size - 2, 0, 1.0, 0.0)
        }
        if (end1 == end2 || distanceInMeters(end1.first, end1.second, end2.first, end2.second) <= 0.001) {
            addEndpointMatch(end2, line1.size - 2, line2.size - 2, 1.0, 1.0)
        }
        if (start1 == start2 || distanceInMeters(start1.first, start1.second, start2.first, start2.second) <= 0.001) {
            addEndpointMatch(start2, 0, 0, 0.0, 0.0)
        }
        if (start1 == end2 || distanceInMeters(start1.first, start1.second, end2.first, end2.second) <= 0.001) {
            addEndpointMatch(end2, 0, line2.size - 2, 0.0, 1.0)
        }

        for (i in 0 until line1.size - 1) {
            val a = line1[i]
            val b = line1[i + 1]
            for (j in 0 until line2.size - 1) {
                val c = line2[j]
                val d = line2[j + 1]

                val ax = a.second; val ay = a.first
                val bx = b.second; val by = b.first
                val cx = c.second; val cy = c.first
                val dx = d.second; val dy = d.first

                val denom = (bx - ax) * (dy - cy) - (by - ay) * (dx - cx)
                if (denom != 0.0) {
                    val t = ((cx - ax) * (dy - cy) - (cy - ay) * (dx - cx)) / denom
                    val u = ((cx - ax) * (by - ay) - (cy - ay) * (bx - ax)) / denom
                    if (t in 0.0..1.0 && u in 0.0..1.0) {
                        val intersectLat = ay + t * (by - ay)
                        val intersectLng = ax + t * (bx - ax)
                        val coord = Pair(intersectLat, intersectLng)

                        val absDist1 = metrics1.cumulativeDistances[i] + t * metrics1.segmentLengths[i]
                        val absDist2 = metrics2.cumulativeDistances[j] + u * metrics2.segmentLengths[j]
                        val p1 = if (metrics1.totalLengthMeters > 0.0) absDist1 / metrics1.totalLengthMeters else 0.0
                        val p2 = if (metrics2.totalLengthMeters > 0.0) absDist2 / metrics2.totalLengthMeters else 0.0

                        if (results.none { abs(it.progress1 - p1) < 0.001 && abs(it.progress2 - p2) < 0.001 }) {
                            results.add(DetailedIntersection(coord, i, j, t, u, p1, p2))
                        }
                    }
                } else {
                    // Collinear segment handling: Check if segment AB and CD overlap
                    val projC = projectPointToLineSegment(cx, cy, ax, ay, bx, by)
                    if (projC.third in 0.0..1.0 && distanceInMeters(cy, cx, projC.first, projC.second) <= 5.0) {
                        addEndpointMatch(Pair(cy, cx), i, j, projC.third, 0.0)
                    }
                    val projD = projectPointToLineSegment(dx, dy, ax, ay, bx, by)
                    if (projD.third in 0.0..1.0 && distanceInMeters(dy, dx, projD.first, projD.second) <= 5.0) {
                        addEndpointMatch(Pair(dy, dx), i, j, projD.third, 1.0)
                    }
                }
            }
        }
        return results
    }

    fun matchSketchToRoads(
        sketchPoints: List<GeoPoint>,
        roadGeoJson: String?,
        fallbackSegments: List<StreetSegment>
    ): RoadMatchResult {
        val startTime = System.currentTimeMillis()
        val warnings = mutableListOf<String>()
        var hasUnmatchedPortions = false
        var hasDisconnectedGaps = false

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

        // Sample sketch points at ~15m increments
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

        // Phase 2.13.10: Intersection-Aware Beam Search preserving transition edges in BeamNode
        data class BeamNode(
            val cand: StreetSegment,
            val projection: ProjectionPoint,
            val resolvedIntersection: DetailedIntersection?,
            val cost: Double,
            val prevNode: BeamNode?
        )

        var activeBeam = mutableListOf<BeamNode>()

        for (sketchPt in sampledSketch) {
            val sortedCandidates = candidateSegments.map { cand ->
                val proj = projectPointDetailed(sketchPt, cand.geoPoints)
                Pair(cand, proj)
            }.filter { it.second.distanceMeters <= maxSnapDistanceMeters }.sortedBy { it.second.distanceMeters }

            if (sortedCandidates.isEmpty()) {
                hasUnmatchedPortions = true
                warnings.add("Off-road sketch point near ${sketchPt.latitude.toString().take(7)}, ${sketchPt.longitude.toString().take(7)} exceeds snap threshold.")
                activeBeam.clear()
                break
            }

            val newBeam = mutableListOf<BeamNode>()
            val beamWidth = 8

            for (candPair in sortedCandidates.take(beamWidth)) {
                val cand = candPair.first
                val proj = candPair.second

                if (activeBeam.isEmpty()) {
                    newBeam.add(BeamNode(cand, proj, null, proj.distanceMeters, null))
                } else {
                    for (prev in activeBeam) {
                        var transitionCost = proj.distanceMeters
                        var chosenInter: DetailedIntersection? = null

                        if (prev.cand.segmentId != cand.segmentId) {
                            val intersections = findAllLineStringIntersections(prev.cand.geoPoints, cand.geoPoints)
                            if (intersections.isEmpty()) {
                                continue
                            } else {
                                chosenInter = intersections.minByOrNull { abs(it.progress1 - prev.projection.progress) + abs(it.progress2 - proj.progress) }
                                if (chosenInter != null) {
                                    transitionCost += 5.0 + abs(chosenInter.progress1 - prev.projection.progress) * 10.0
                                }
                            }
                        } else {
                            if (proj.progress < prev.projection.progress - 0.25) {
                                transitionCost += 100.0 // Backtracking penalty
                            }
                        }
                        newBeam.add(BeamNode(cand, proj, chosenInter, prev.cost + transitionCost, prev))
                    }
                }
            }

            if (newBeam.isNotEmpty()) {
                activeBeam = newBeam.sortedBy { it.cost }.take(beamWidth).toMutableList()
            } else {
                activeBeam.clear()
                break
            }
        }

        if (activeBeam.isEmpty()) {
            return RoadMatchResult(
                isSuccess = false,
                matchedSegments = emptyList(),
                candidateRoadCount = candidateSegments.size,
                totalGisRoadCount = totalCount,
                executionTimeMs = System.currentTimeMillis() - startTime,
                warnings = warnings.ifEmpty { listOf("Could not establish a continuous valid road sequence along the sketch (unmatched portions or disconnected transitions rejected).") }
            )
        }

        // Backtrack best beam path
        val bestNode = activeBeam.first()
        val nodePath = mutableListOf<BeamNode>()
        var curr: BeamNode? = bestNode
        while (curr != null) {
            nodePath.add(0, curr)
            curr = curr.prevNode
        }

        data class OccRecord(
            val cand: StreetSegment,
            val projections: MutableList<ProjectionPoint>,
            var exitIntersection: DetailedIntersection? = null
        )

        val occurrences = mutableListOf<OccRecord>()
        for (node in nodePath) {
            if (occurrences.isNotEmpty() && occurrences.last().cand.segmentId == node.cand.segmentId) {
                occurrences.last().projections.add(node.projection)
            } else {
                if (occurrences.isNotEmpty() && node.resolvedIntersection != null) {
                    occurrences.last().exitIntersection = node.resolvedIntersection
                }
                occurrences.add(OccRecord(node.cand, mutableListOf(node.projection)))
            }
        }

        val finalMatchedList = mutableListOf<MatchedSegment>()

        for (m in occurrences.indices) {
            val occ = occurrences[m]
            val cand = occ.cand
            val projList = occ.projections

            val firstProj = projList.first()
            val lastProj = projList.last()

            var entryCoord = Pair(firstProj.lat, firstProj.lng)
            var exitCoord = Pair(lastProj.lat, lastProj.lng)

            if (m > 0 && occurrences[m - 1].exitIntersection != null) {
                entryCoord = occurrences[m - 1].exitIntersection!!.intersectionCoordinate
            }
            if (occ.exitIntersection != null) {
                exitCoord = occ.exitIntersection!!.intersectionCoordinate
            }

            val entryT = firstProj.progress
            val exitT = lastProj.progress
            val direction = if (entryT <= exitT) "FORWARD" else "REVERSE"

            val metrics = computeLineMetrics(cand.geoPoints)
            val trimmedGeoPoints = mutableListOf<Pair<Double, Double>>()
            val minT = min(entryT, exitT)
            val maxT = max(entryT, exitT)

            if (direction == "FORWARD") {
                for (i in cand.geoPoints.indices) {
                    val ptFrac = if (metrics.totalLengthMeters > 0.0) metrics.cumulativeDistances[i] / metrics.totalLengthMeters else 0.0
                    if (ptFrac >= minT && ptFrac <= maxT) {
                        val ptCoord = cand.geoPoints[i]
                        if (trimmedGeoPoints.isEmpty() || trimmedGeoPoints.last() != ptCoord) {
                            trimmedGeoPoints.add(ptCoord)
                        }
                    }
                }
            } else {
                for (i in cand.geoPoints.indices.reversed()) {
                    val ptFrac = if (metrics.totalLengthMeters > 0.0) metrics.cumulativeDistances[i] / metrics.totalLengthMeters else 0.0
                    if (ptFrac >= minT && ptFrac <= maxT) {
                        val ptCoord = cand.geoPoints[i]
                        if (trimmedGeoPoints.isEmpty() || trimmedGeoPoints.last() != ptCoord) {
                            trimmedGeoPoints.add(ptCoord)
                        }
                    }
                }
            }

            if (trimmedGeoPoints.isEmpty()) {
                trimmedGeoPoints.add(entryCoord)
                trimmedGeoPoints.add(exitCoord)
            } else {
                if (trimmedGeoPoints.first() != entryCoord) {
                    trimmedGeoPoints.add(0, entryCoord)
                }
                if (trimmedGeoPoints.last() != exitCoord) {
                    trimmedGeoPoints.add(exitCoord)
                }
            }

            finalMatchedList.add(
                MatchedSegment(
                    segmentId = cand.segmentId,
                    streetName = cand.streetName,
                    sequenceOrder = finalMatchedList.size + 1,
                    traversalDirection = direction,
                    entryProgress = entryT,
                    exitProgress = exitT,
                    entryCoordinate = entryCoord,
                    exitCoordinate = exitCoord,
                    geoPoints = trimmedGeoPoints
                )
            )
        }

        // Validate transitions using resolved intersection boundaries
        if (finalMatchedList.size >= 2) {
            for (m in 0 until finalMatchedList.size - 1) {
                val seg1 = finalMatchedList[m]
                val seg2 = finalMatchedList[m + 1]

                val line1 = candidateSegments.find { it.segmentId == seg1.segmentId }?.geoPoints ?: seg1.geoPoints
                val line2 = candidateSegments.find { it.segmentId == seg2.segmentId }?.geoPoints ?: seg2.geoPoints

                val intersections = findAllLineStringIntersections(line1, line2)
                val matchingIntersection = intersections.firstOrNull { inter ->
                    val distExit1 = distanceInMeters(seg1.exitCoordinate.first, seg1.exitCoordinate.second, inter.intersectionCoordinate.first, inter.intersectionCoordinate.second)
                    val distEntry2 = distanceInMeters(seg2.entryCoordinate.first, seg2.entryCoordinate.second, inter.intersectionCoordinate.first, inter.intersectionCoordinate.second)
                    distExit1 <= 75.0 && distEntry2 <= 75.0
                }

                if (intersections.isEmpty() || matchingIntersection == null) {
                    hasDisconnectedGaps = true
                    warnings.add("Transition validation failed between '${seg1.streetName}' and '${seg2.streetName}' (traversal exit/entry positions do not meet at a proven intersection within 75m).")
                }
            }
        }

        val isFinalSuccess = finalMatchedList.isNotEmpty() &&
                !hasUnmatchedPortions &&
                !hasDisconnectedGaps

        return RoadMatchResult(
            isSuccess = isFinalSuccess,
            matchedSegments = finalMatchedList,
            candidateRoadCount = candidateSegments.size,
            totalGisRoadCount = totalCount,
            executionTimeMs = System.currentTimeMillis() - startTime,
            warnings = warnings.distinct()
        )
    }
}
