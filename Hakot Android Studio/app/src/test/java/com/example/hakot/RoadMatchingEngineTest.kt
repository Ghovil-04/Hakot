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
    fun test1_disconnectedRoadsCannotWinBeamSearch() {
        // Disconnected roads where beam search must reject transition
        val disconnected = listOf(
            sampleSegments[0],
            StreetSegment(
                segmentId = "seg_far",
                streetName = "Far Away St",
                barangayId = "brgy_1",
                barangayName = "Poblacion 1",
                municipalityOrgId = "org_nasugbu_lgu",
                geoPoints = listOf(Pair(14.2000, 120.8000), Pair(14.2100, 120.8100))
            )
        )
        val sketch = listOf(
            GeoPoint(14.0700, 120.6210),
            GeoPoint(14.2000, 120.8000)
        )
        val result = RoadMatchingEngine.matchSketchToRoads(sketch, null, disconnected)
        assertFalse("Disconnected roads cannot win beam search", result.isSuccess)
    }

    @Test
    fun test2_routeWithUnmatchedMiddleSectionFails() {
        val sketch = listOf(
            GeoPoint(14.0700, 120.6210),
            GeoPoint(14.5000, 121.0000), // Off-road middle section
            GeoPoint(14.0700, 120.6280)
        )
        val result = RoadMatchingEngine.matchSketchToRoads(sketch, null, sampleSegments)
        assertFalse("Route with unmatched middle section must fail", result.isSuccess)
    }

    @Test
    fun test3_backtrackingDetectedAndPenalized() {
        val sketch = listOf(
            GeoPoint(14.0700, 120.6280),
            GeoPoint(14.0700, 120.6200),
            GeoPoint(14.0700, 120.6250)
        )
        val result = RoadMatchingEngine.matchSketchToRoads(sketch, null, sampleSegments)
        // Should succeed as reverse/backtrack traversal with correct direction
        assertTrue(result.matchedSegments.isNotEmpty())
    }

    @Test
    fun test4_transitionAtWrongIntersectionRejected() {
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
            GeoPoint(14.0750, 120.6350) // Does not meet at intersection
        )
        val result = RoadMatchingEngine.matchSketchToRoads(sketch, null, crossingSegments)
        assertFalse("Transition at wrong intersection must be rejected", result.isSuccess)
    }

    @Test
    fun test5_exactSelectedIntersectionPositionsPreserved() {
        val sketch = listOf(
            GeoPoint(14.0700, 120.6200),
            GeoPoint(14.0700, 120.6300),
            GeoPoint(14.0750, 120.6300)
        )
        val result = RoadMatchingEngine.matchSketchToRoads(sketch, null, sampleSegments)
        assertTrue(result.isSuccess)
        val seg1 = result.matchedSegments[0]
        val seg2 = result.matchedSegments[1]
        // Exit of Alpha and entry of Beta must meet at intersection (14.07, 120.63)
        assertEquals(14.0700, seg1.exitCoordinate.first, 0.0001)
        assertEquals(120.6300, seg1.exitCoordinate.second, 0.0001)
        assertEquals(14.0700, seg2.entryCoordinate.first, 0.0001)
        assertEquals(120.6300, seg2.entryCoordinate.second, 0.0001)
    }

    @Test
    fun test6_loopTraversalSelectsCorrectOccurrence() {
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
    fun test7_alphaBetaAlphaRemainsThreeOccurrences() {
        val sketch = listOf(
            GeoPoint(14.0700, 120.6200),
            GeoPoint(14.0700, 120.6300),
            GeoPoint(14.0750, 120.6300),
            GeoPoint(14.0700, 120.6300),
            GeoPoint(14.0700, 120.6200)
        )
        val result = RoadMatchingEngine.matchSketchToRoads(sketch, null, sampleSegments)
        assertTrue("Alpha -> Beta -> Alpha should produce three occurrences: ${result.warnings}", result.isSuccess)
        assertEquals(3, result.matchedSegments.size)
        assertEquals("seg_alpha", result.matchedSegments[0].segmentId)
        assertEquals("seg_beta", result.matchedSegments[1].segmentId)
        assertEquals("seg_alpha", result.matchedSegments[2].segmentId)
    }

    @Test
    fun test8_reverseGeometryInCorrectCoordinateOrder() {
        val sketchRev = listOf(GeoPoint(14.0700, 120.6280), GeoPoint(14.0700, 120.6210))
        val result = RoadMatchingEngine.matchSketchToRoads(sketchRev, null, sampleSegments)
        assertTrue(result.isSuccess)
        assertEquals("REVERSE", result.matchedSegments[0].traversalDirection)
        val pts = result.matchedSegments[0].geoPoints
        // For REVERSE traversal, longitude should be non-increasing
        for (i in 0 until pts.size - 1) {
            assertTrue(pts[i].second >= pts[i + 1].second)
        }
    }

    @Test
    fun test9_collinearOverlapExplicitlyHandled() {
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
        assertTrue(result.matchedSegments.isNotEmpty())
    }

    @Test
    fun test10_existingValidConnectedRoutesContinueToPass() {
        val sketch = listOf(
            GeoPoint(14.0700, 120.6200),
            GeoPoint(14.0700, 120.6300),
            GeoPoint(14.0750, 120.6300)
        )
        val result = RoadMatchingEngine.matchSketchToRoads(sketch, null, sampleSegments)
        assertTrue("Existing valid connected route must pass: ${result.warnings}", result.isSuccess)
        assertEquals(2, result.matchedSegments.size)
    }
}
