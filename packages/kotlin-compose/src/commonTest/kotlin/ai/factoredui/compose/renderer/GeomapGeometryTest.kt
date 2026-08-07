package ai.factoredui.compose.renderer

import ai.factoredui.compose.schema.GeomapViewport
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GeomapTriangulatorTest {

    @Test
    fun aConvexSquareYieldsTwoTrianglesCoveringItsFullArea() {
        val square = doubleArrayOf(0.0, 0.0, 10.0, 0.0, 10.0, 10.0, 0.0, 10.0)
        val triangles = earClipTriangulate(square)
        assertEquals(6, triangles.size)
        assertEquals(100.0, totalTriangleArea(square, triangles), 1e-9)
    }

    @Test
    fun aClosingDuplicateVertexIsToleratedWithoutADegenerateTriangle() {
        val closedSquare = doubleArrayOf(0.0, 0.0, 10.0, 0.0, 10.0, 10.0, 0.0, 10.0, 0.0, 0.0)
        val triangles = earClipTriangulate(closedSquare)
        assertEquals(6, triangles.size)
        assertEquals(100.0, totalTriangleArea(closedSquare, triangles), 1e-9)
    }

    @Test
    fun aConcaveLShapeTriangulatesWithNoTriangleOutsideThePolygon() {
        val lShape = doubleArrayOf(0.0, 0.0, 10.0, 0.0, 10.0, 4.0, 4.0, 4.0, 4.0, 10.0, 0.0, 10.0)
        val triangles = earClipTriangulate(lShape)
        assertEquals((6 - 2) * 3, triangles.size)
        assertEquals(64.0, totalTriangleArea(lShape, triangles), 1e-9)
        assertAllTriangleCentroidsInside(lShape, triangles)
    }

    @Test
    fun aClockwiseRingTriangulatesIdenticallyToItsCounterClockwiseTwin() {
        val clockwiseL = doubleArrayOf(0.0, 10.0, 4.0, 10.0, 4.0, 4.0, 10.0, 4.0, 10.0, 0.0, 0.0, 0.0)
        val triangles = earClipTriangulate(clockwiseL)
        assertEquals((6 - 2) * 3, triangles.size)
        assertEquals(64.0, totalTriangleArea(clockwiseL, triangles), 1e-9)
    }

    @Test
    fun aParcelShapedRingWithOverTwoHundredVerticesPreservesItsArea() {
        val jaggedParcel = jaggedParcelRing(vertexCount = 220)
        val triangles = earClipTriangulate(jaggedParcel)
        assertEquals((220 - 2) * 3, triangles.size)
        val ringArea = abs(signedRingArea(jaggedParcel))
        assertEquals(ringArea, totalTriangleArea(jaggedParcel, triangles), ringArea * 1e-6)
        assertAllTriangleCentroidsInside(jaggedParcel, triangles)
    }

    private fun jaggedParcelRing(vertexCount: Int): DoubleArray {
        val ring = DoubleArray(vertexCount * 2)
        for (i in 0 until vertexCount) {
            val angle = 2.0 * PI * i / vertexCount
            val radius = 100.0 + 30.0 * sin(11.0 * angle) + 12.0 * cos(23.0 * angle)
            ring[i * 2] = radius * cos(angle)
            ring[i * 2 + 1] = radius * sin(angle)
        }
        return ring
    }

    private fun totalTriangleArea(ring: DoubleArray, triangles: IntArray): Double {
        var area = 0.0
        for (t in 0 until triangles.size / 3) {
            val a = triangles[t * 3]
            val b = triangles[t * 3 + 1]
            val c = triangles[t * 3 + 2]
            area += abs(
                (ring[b * 2] - ring[a * 2]) * (ring[c * 2 + 1] - ring[a * 2 + 1]) -
                    (ring[b * 2 + 1] - ring[a * 2 + 1]) * (ring[c * 2] - ring[a * 2]),
            ) / 2.0
        }
        return area
    }

    private fun assertAllTriangleCentroidsInside(ring: DoubleArray, triangles: IntArray) {
        for (t in 0 until triangles.size / 3) {
            val a = triangles[t * 3]
            val b = triangles[t * 3 + 1]
            val c = triangles[t * 3 + 2]
            val centroidX = (ring[a * 2] + ring[b * 2] + ring[c * 2]) / 3.0
            val centroidY = (ring[a * 2 + 1] + ring[b * 2 + 1] + ring[c * 2 + 1]) / 3.0
            assertTrue(
                isPointInRing(centroidX, centroidY, ring),
                "triangle $t centroid ($centroidX, $centroidY) fell outside the polygon",
            )
        }
    }
}

class GeomapHitTestTest {

    private val lShape = doubleArrayOf(0.0, 0.0, 10.0, 0.0, 10.0, 4.0, 4.0, 4.0, 4.0, 10.0, 0.0, 10.0)

    @Test
    fun aPointInsideTheRingHits() {
        assertTrue(isPointInRing(2.0, 2.0, lShape))
    }

    @Test
    fun aPointOutsideTheBoundingBoxMisses() {
        assertFalse(isPointInRing(20.0, 20.0, lShape))
    }

    @Test
    fun aPointInTheConcaveNotchInsideTheBoundingBoxMisses() {
        assertFalse(isPointInRing(8.0, 8.0, lShape))
    }

    @Test
    fun disjointRingsBehaveAsAMultiPolygonUnderEvenOdd() {
        val leftSquare = doubleArrayOf(0.0, 0.0, 1.0, 0.0, 1.0, 1.0, 0.0, 1.0)
        val rightSquare = doubleArrayOf(5.0, 0.0, 6.0, 0.0, 6.0, 1.0, 5.0, 1.0)
        val rings = listOf(leftSquare, rightSquare)
        assertTrue(isPointInRings(0.5, 0.5, rings))
        assertTrue(isPointInRings(5.5, 0.5, rings))
        assertFalse(isPointInRings(3.0, 0.5, rings))
    }
}

class GeomapViewportMathTest {

    @Test
    fun lonLatWorldRoundTripIsStable() {
        val lon = -84.6131334
        val lat = 42.6953012
        assertEquals(lon, worldXToLon(lonToWorldX(lon)), 1e-9)
        assertEquals(lat, worldYToLat(latToWorldY(lat)), 1e-9)
    }

    @Test
    fun aPurePanGestureKeepsZoomAndShiftsCenterAgainstTheDrag() {
        val viewport = GeomapViewport(lon = -84.6, lat = 42.69, zoom = 14f)
        val panned = viewport.afterGeomapGesture(
            centroidXpx = 200f, centroidYpx = 200f, panXpx = 120f, panYpx = 0f,
            zoomDelta = 1f, widthPx = 400f, heightPx = 400f,
        )
        assertEquals(viewport.zoom, panned.zoom)
        assertTrue(panned.lon < viewport.lon, "dragging content right must move the camera west")
        assertEquals(viewport.lat, panned.lat, 1e-9)
    }

    @Test
    fun zoomingAboutACentroidKeepsTheGeoPointUnderItFixed() {
        val viewport = GeomapViewport(lon = -84.6, lat = 42.69, zoom = 14f)
        val centroidX = 300f
        val centroidY = 100f
        val scaleBefore = geomapScalePx(viewport.zoom)
        val anchorLonWorld = lonToWorldX(viewport.lon) + (centroidX - 200.0) / scaleBefore
        val zoomed = viewport.afterGeomapGesture(
            centroidXpx = centroidX, centroidYpx = centroidY, panXpx = 0f, panYpx = 0f,
            zoomDelta = 2f, widthPx = 400f, heightPx = 400f,
        )
        val scaleAfter = geomapScalePx(zoomed.zoom)
        val anchorAfter = lonToWorldX(zoomed.lon) + (centroidX - 200.0) / scaleAfter
        assertEquals(viewport.zoom + 1f, zoomed.zoom, 1e-4f)
        assertEquals(anchorLonWorld, anchorAfter, 1e-12)
    }

    @Test
    fun fitViewportCentersTheBoundsAndFitsThemOnScreen() {
        val bounds = WorldBounds(minX = 0.2, minY = 0.3, maxX = 0.21, maxY = 0.305)
        val viewport = fitGeomapViewport(bounds, widthPx = 800f, heightPx = 800f)
        assertEquals(worldXToLon(0.205), viewport.lon, 1e-9)
        val scale = geomapScalePx(viewport.zoom)
        assertTrue(bounds.width * scale <= 800.0, "fitted bounds must not overflow the viewport width")
        assertTrue(bounds.height * scale <= 800.0, "fitted bounds must not overflow the viewport height")
    }
}
