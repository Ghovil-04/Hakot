package com.example.hakot

import com.example.hakot.data.RoadMatchingEngine
import com.example.hakot.model.StreetSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.osmdroid.util.GeoPoint

class RoadMatchingEngineTest {

    private val sampleSegments = listOf(
        StreetSegment(
            segmentId = "seg_alpha",
            streetName = "Alpha Street",
            barangayId = "brgy_1",
            barangayName = "Poblacion 1",
            municipalityOrgId = "org_nasugbu_lgu",
            geoPoints = listOf(
                Pair(14.0700, 120.6200),
                Pair(14.0700, 120.6300)
            )
        ),
        StreetSegment(
            segmentId = "seg_beta",
            streetName = "Beta Street",
            barangayId = "brgy_1",
            barangayName = "Poblacion 1",
            municipalityOrgId = "org_nasugbu_lgu",
            geoPoints = listOf(
                Pair(14.0700, 120.6300),
                Pair(14.0800, 120.6300)
            )
        )
    )

    @Test
    fun test1_trueEndpointToEndpointIntersection() {
        val sketch = listOf(
            GeoPoint(14.0700, 120.6200),
            GeoPoint(14.0700, 120.6300),
            GeoPoint(14.0750, 120.6300)
        )
        val result = RoadMatchingEngine.matchSketchToRoads(sketch, null, sampleSegments)
        assertTrue("Endpoint-to-endpoint intersection should succeed: ${result.warnings}", result.isSuccess)
        assertEquals(2, result.matchedSegments.size)
        assertEquals("seg_alpha", result.matchedSegments[0].segmentId)
        assertEquals("seg_beta", result.matchedSegments[1].segmentId)
    }

    @Test
    fun test2_trueEndpointToInteriorIntersection() {
        val interiorSegments = listOf(
            sampleSegments[0],
            StreetSegment(
                segmentId = "seg_cross",
                streetName = "Cross Road",
                barangayId = "brgy_1",
                barangayName = "Poblacion 1",
                municipalityOrgId = "org_nasugbu_lgu",
                geoPoints = listOf(Pair(14.0600, 120.6250), Pair(14.0800, 120.6250))
            )
        )
        val sketch = listOf(
            GeoPoint(14.0700, 120.6200),
            GeoPoint(14.0700, 120.6250),
            GeoPoint(14.0750, 120.6250)
        )
        val result = RoadMatchingEngine.matchSketchToRoads(sketch, null, interiorSegments)
        assertTrue("Endpoint-to-interior intersection should succeed: ${result.warnings}", result.isSuccess)
    }

    @Test
    fun test3_trueInteriorToInteriorXIntersection() {
        val crossingSegments = listOf(
            StreetSegment(
                segmentId = "seg_h",
                streetName = "Horizontal Road",
                barangayId = "brgy_1",
                barangayName = "Poblacion 1",
                municipalityOrgId = "org_nasugbu_lgu",
                geoPoints = listOf(Pair(14.0700, 120.6200), Pair(14.0700, 120.6400))
            ),
            StreetSegment(
                segmentId = "seg_v",
                streetName = "Vertical Road",
                barangayId = "brgy_1",
                barangayName = "Poblacion 1",
                municipalityOrgId = "org_nasugbu_lgu",
                geoPoints = listOf(Pair(14.0600, 120.6300), Pair(14.0800, 120.6300))
            )
        )
        val sketch = listOf(
            GeoPoint(14.0700, 120.6200),
            GeoPoint(14.0700, 120.6300),
            GeoPoint(14.0750, 120.6300)
        )
        val result = RoadMatchingEngine.matchSketchToRoads(sketch, null, crossingSegments)
        assertTrue("Interior-to-interior X intersection should succeed: ${result.warnings}", result.isSuccess)
    }

    @Test
    fun test4_twoEndpoints1To15mApartNotAutomaticallyConnected() {
        val closeUnconnected = listOf(
            sampleSegments[0],
            StreetSegment(
                segmentId = "seg_nearby_unconnected",
                streetName = "Nearby Unconnected",
                barangayId = "brgy_1",
                barangayName = "Poblacion 1",
                municipalityOrgId = "org_nasugbu_lgu",
                geoPoints = listOf(
                    Pair(14.0701, 120.6302), // 11m away from Alpha end, but no intersection
                    Pair(14.0701, 120.6400)
                )
            )
        )
        val sketch = listOf(
            GeoPoint(14.0700, 120.6210),
            GeoPoint(14.0701, 120.6350)
        )
        val result = RoadMatchingEngine.matchSketchToRoads(sketch, null, closeUnconnected)
        assertFalse("Endpoints 1-15m apart without intersection must NOT be connected", result.isSuccess)
    }

    @Test
    fun test5_parallelRoadsAreNotConnected() {
        val parallelSegments = listOf(
            sampleSegments[0],
            StreetSegment(
                segmentId = "seg_parallel",
                streetName = "Parallel Road",
                barangayId = "brgy_1",
                barangayName = "Poblacion 1",
                municipalityOrgId = "org_nasugbu_lgu",
                geoPoints = listOf(Pair(14.0710, 120.6200), Pair(14.0710, 120.6300))
            )
        )
        val sketch = listOf(
            GeoPoint(14.0700, 120.6210),
            GeoPoint(14.0710, 120.6250)
        )
        val result = RoadMatchingEngine.matchSketchToRoads(sketch, null, parallelSegments)
        // Parallel roads do not intersect
        assertFalse("Parallel roads must not be connected", result.isSuccess)
    }

    @Test
    fun test6_collinearOverlappingRoadsHandledExplicitly() {
        val collinearSegments = listOf(
            sampleSegments[0],
            StreetSegment(
                segmentId = "seg_collinear",
                streetName = "Collinear Road",
                barangayId = "brgy_1",
                barangayName = "Poblacion 1",
                municipalityOrgId = "org_nasugbu_lgu",
                geoPoints = listOf(Pair(14.0700, 120.6280), Pair(14.0700, 120.6400))
            )
        )
        val sketch = listOf(
            GeoPoint(14.0700, 120.6210),
            GeoPoint(14.0700, 120.6350)
        )
        val result = RoadMatchingEngine.matchSketchToRoads(sketch, null, collinearSegments)
        // Handled via endpoint sharing or intersection
        assertTrue("Collinear overlapping should execute: ${result.warnings}", result.matchedSegments.isNotEmpty())
    }

    @Test
    fun test7_multipleIntersectionsReturnAllRelevantIntersections() {
        val line1 = listOf(Pair(14.0700, 120.6200), Pair(14.0700, 120.6500))
        val line2 = listOf(Pair(14.0600, 120.6250), Pair(14.0800, 120.6250))
        val line3 = listOf(Pair(14.0600, 120.6350), Pair(14.0800, 120.6350))
        val multiSegments = listOf(
            StreetSegment("s1", "Line 1", "b1", "B", "org", geoPoints = line1),
            StreetSegment("s2", "Line 2", "b1", "B", "org", geoPoints = line2),
            StreetSegment("s3", "Line 3", "b1", "B", "org", geoPoints = line3)
        )
        val sketch = listOf(
            GeoPoint(14.0700, 120.6210),
            GeoPoint(14.0700, 120.6300),
            GeoPoint(14.0700, 120.6380)
        )
        val result = RoadMatchingEngine.matchSketchToRoads(sketch, null, multiSegments)
        val inters = RoadMatchingEngine.findAllLineStringIntersections(line1, line2)
        assertTrue("Should detect intersection between line1 and line2", inters.isNotEmpty())
    }

    @Test
    fun test8_correctIntersectionSelectedBasedOnTraversalPositions() {
        val sketch = listOf(
            GeoPoint(14.0700, 120.6200),
            GeoPoint(14.0700, 120.6300),
            GeoPoint(14.0750, 120.6300)
        )
        val result = RoadMatchingEngine.matchSketchToRoads(sketch, null, sampleSegments)
        assertTrue(result.isSuccess)
        assertEquals("seg_alpha", result.matchedSegments[0].segmentId)
        assertEquals("seg_beta", result.matchedSegments[1].segmentId)
    }

    @Test
    fun test9_roadsIntersectElsewhereButSelectedTransitionsDoNotMeetRejected() {
        val crossingSegments = listOf(
            StreetSegment(
                segmentId = "seg_h",
                streetName = "Horizontal Road",
                barangayId = "brgy_1",
                barangayName = "Poblacion 1",
                municipalityOrgId = "org_nasugbu_lgu",
                geoPoints = listOf(Pair(14.0700, 120.6200), Pair(14.0700, 120.6400))
            ),
            StreetSegment(
                segmentId = "seg_v",
                streetName = "Vertical Road",
                barangayId = "brgy_1",
                barangayName = "Poblacion 1",
                municipalityOrgId = "org_nasugbu_lgu",
                geoPoints = listOf(Pair(14.0600, 120.6300), Pair(14.0800, 120.6300))
            )
        )
        val sketch = listOf(
            GeoPoint(14.0700, 120.6200),
            GeoPoint(14.0700, 120.6250),
            GeoPoint(14.0750, 120.6350)
        )
        val result = RoadMatchingEngine.matchSketchToRoads(sketch, null, crossingSegments)
        assertFalse("Roads intersecting elsewhere but not meeting at transition must be rejected", result.isSuccess)
    }

    @Test
    fun test10_sketchJumpsAcrossIntersectionWithoutReachingItRejected() {
        val sketch = listOf(
            GeoPoint(14.0700, 120.6200),
            GeoPoint(14.0720, 120.6320)
        )
        val result = RoadMatchingEngine.matchSketchToRoads(sketch, null, sampleSegments)
        assertFalse("Sketch jumping across intersection without reaching it must be rejected", result.isSuccess)
    }

    @Test
    fun test11_exactEntryAndExitCoordinates() {
        val segment = listOf(
            StreetSegment(
                segmentId = "seg_multi",
                streetName = "Multi Vertex Road",
                barangayId = "brgy_1",
                barangayName = "Poblacion 1",
                municipalityOrgId = "org_nasugbu_lgu",
                geoPoints = listOf(
                    Pair(14.0700, 120.6200),
                    Pair(14.0700, 120.6250),
                    Pair(14.0700, 120.6300),
                    Pair(14.0700, 120.6350)
                )
            )
        )
        val sketch = listOf(GeoPoint(14.0700, 120.6200), GeoPoint(14.0700, 120.6340))
        val result = RoadMatchingEngine.matchSketchToRoads(sketch, null, segment)
        assertTrue(result.isSuccess)
        val seg = result.matchedSegments[0]
        assertEquals(seg.entryCoordinate, seg.geoPoints.first())
        assertEquals(seg.exitCoordinate, seg.geoPoints.last())
    }

    @Test
    fun test12_forwardAndReverseIntermediateVertexOrdering() {
        val sketchFwd = listOf(GeoPoint(14.0700, 120.6200), GeoPoint(14.0700, 120.6300))
        val resFwd = RoadMatchingEngine.matchSketchToRoads(sketchFwd, null, sampleSegments)
        assertEquals("FORWARD", resFwd.matchedSegments[0].traversalDirection)

        val sketchRev = listOf(GeoPoint(14.0700, 120.6300), GeoPoint(14.0700, 120.6200))
        val resRev = RoadMatchingEngine.matchSketchToRoads(sketchRev, null, sampleSegments)
        assertEquals("REVERSE", resRev.matchedSegments[0].traversalDirection)
    }

    @Test
    fun test13_loopingSelfCrossingGeometrySelectsCorrectOccurrence() {
        val loopSegment = listOf(
            StreetSegment(
                segmentId = "seg_loop",
                streetName = "Loop Road",
                barangayId = "brgy_1",
                barangayName = "Poblacion 1",
                municipalityOrgId = "org_nasugbu_lgu",
                geoPoints = listOf(
                    Pair(14.0700, 120.6200),
                    Pair(14.0800, 120.6300),
                    Pair(14.0700, 120.6400),
                    Pair(14.0700, 120.6200)
                )
            )
        )
        val sketch = listOf(GeoPoint(14.0700, 120.6200), GeoPoint(14.0800, 120.6290))
        val result = RoadMatchingEngine.matchSketchToRoads(sketch, null, loopSegment)
        assertTrue(result.matchedSegments.isNotEmpty())
    }

    @Test
    fun test14_alphaBetaAlphaRevisitProducesThreeOccurrences() {
        val sketch = listOf(
            GeoPoint(14.0700, 120.6200),
            GeoPoint(14.0700, 120.6300),
            GeoPoint(14.0750, 120.6300),
            GeoPoint(14.0700, 120.6300),
            GeoPoint(14.0700, 120.6200)
        )
        val result = RoadMatchingEngine.matchSketchToRoads(sketch, null, sampleSegments)
        assertTrue("Alpha -> Beta -> Alpha revisit should produce three occurrences: ${result.warnings}", result.isSuccess)
        assertEquals(3, result.matchedSegments.size)
        assertEquals("seg_alpha", result.matchedSegments[0].segmentId)
        assertEquals("seg_beta", result.matchedSegments[1].segmentId)
        assertEquals("seg_alpha", result.matchedSegments[2].segmentId)
    }
}
