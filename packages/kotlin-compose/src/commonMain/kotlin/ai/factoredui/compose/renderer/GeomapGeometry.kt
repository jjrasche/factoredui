package ai.factoredui.compose.renderer

import ai.factoredui.compose.schema.GeoPoint
import ai.factoredui.compose.schema.GeomapViewport
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sinh
import kotlin.math.tan

const val GEOMAP_TILE_PX = 256.0
const val MIN_GEOMAP_ZOOM = 1f
const val MAX_GEOMAP_ZOOM = 22f
private const val FIT_MARGIN = 0.92

fun lonToWorldX(lon: Double): Double = (lon + 180.0) / 360.0

fun latToWorldY(lat: Double): Double {
    val phi = lat * PI / 180.0
    return (1.0 - ln(tan(phi) + 1.0 / cos(phi)) / PI) / 2.0
}

fun worldXToLon(worldX: Double): Double = worldX * 360.0 - 180.0

fun worldYToLat(worldY: Double): Double =
    atan(sinh(PI * (1.0 - 2.0 * worldY))) * 180.0 / PI

fun geomapScalePx(zoom: Float): Double = GEOMAP_TILE_PX * 2.0.pow(zoom.toDouble())

data class WorldBounds(val minX: Double, val minY: Double, val maxX: Double, val maxY: Double) {
    val centerX: Double get() = (minX + maxX) / 2.0
    val centerY: Double get() = (minY + maxY) / 2.0
    val width: Double get() = maxX - minX
    val height: Double get() = maxY - minY
}

fun worldBoundsOf(rings: List<List<GeoPoint>>): WorldBounds? {
    var minX = Double.MAX_VALUE
    var minY = Double.MAX_VALUE
    var maxX = -Double.MAX_VALUE
    var maxY = -Double.MAX_VALUE
    var seen = false
    for (ring in rings) for (point in ring) {
        val x = lonToWorldX(point.lon)
        val y = latToWorldY(point.lat)
        if (x < minX) minX = x
        if (y < minY) minY = y
        if (x > maxX) maxX = x
        if (y > maxY) maxY = y
        seen = true
    }
    return if (seen) WorldBounds(minX, minY, maxX, maxY) else null
}

fun WorldBounds.union(other: WorldBounds): WorldBounds = WorldBounds(
    minX = min(minX, other.minX),
    minY = min(minY, other.minY),
    maxX = kotlin.math.max(maxX, other.maxX),
    maxY = kotlin.math.max(maxY, other.maxY),
)

fun fitGeomapViewport(bounds: WorldBounds?, widthPx: Float, heightPx: Float): GeomapViewport {
    if (bounds == null || widthPx <= 0f || heightPx <= 0f) return GeomapViewport(0.0, 0.0, 2f)
    val scaleForWidth = if (bounds.width > 0) widthPx * FIT_MARGIN / bounds.width else Double.MAX_VALUE
    val scaleForHeight = if (bounds.height > 0) heightPx * FIT_MARGIN / bounds.height else Double.MAX_VALUE
    val scale = min(scaleForWidth, scaleForHeight)
    val zoom = if (scale == Double.MAX_VALUE) MAX_GEOMAP_ZOOM else
        log2(scale / GEOMAP_TILE_PX).toFloat().coerceIn(MIN_GEOMAP_ZOOM, MAX_GEOMAP_ZOOM)
    return GeomapViewport(
        lon = worldXToLon(bounds.centerX),
        lat = worldYToLat(bounds.centerY),
        zoom = zoom,
    )
}

fun GeomapViewport.afterGeomapGesture(
    centroidXpx: Float,
    centroidYpx: Float,
    panXpx: Float,
    panYpx: Float,
    zoomDelta: Float,
    widthPx: Float,
    heightPx: Float,
): GeomapViewport {
    val safeDelta = if (zoomDelta > 0f) zoomDelta else 1f
    val newZoom = (zoom + log2(safeDelta.toDouble()).toFloat()).coerceIn(MIN_GEOMAP_ZOOM, MAX_GEOMAP_ZOOM)
    val scale = geomapScalePx(zoom)
    val newScale = geomapScalePx(newZoom)
    val anchorX = lonToWorldX(lon) + (centroidXpx - widthPx / 2.0) / scale
    val anchorY = latToWorldY(lat) + (centroidYpx - heightPx / 2.0) / scale
    val newCenterX = anchorX - (centroidXpx - widthPx / 2.0 + panXpx) / newScale
    val newCenterY = anchorY - (centroidYpx - heightPx / 2.0 + panYpx) / newScale
    return GeomapViewport(
        lon = worldXToLon(newCenterX),
        lat = worldYToLat(newCenterY),
        zoom = newZoom,
    )
}

// Ear clipping over a simple (possibly concave, hole-free) ring stored as [x0,y0,x1,y1,...].
// Returns vertex indices, three per triangle. Triangulated ONCE per data load, never per frame.
fun earClipTriangulate(ring: DoubleArray): IntArray {
    val vertexCount = openRingVertexCount(ring)
    if (vertexCount < 3) return IntArray(0)
    val counterClockwise = signedRingArea(ring, vertexCount) > 0.0
    val active = (0 until vertexCount).toMutableList()
    val triangles = IntArray((vertexCount - 2) * 3)
    var filled = 0
    while (active.size > 3) {
        val earIndex = findEarIndex(ring, active, counterClockwise)
        val clipIndex = earIndex ?: flattestCornerIndex(ring, active)
        val previous = active[(clipIndex + active.size - 1) % active.size]
        val next = active[(clipIndex + 1) % active.size]
        triangles[filled++] = previous
        triangles[filled++] = active[clipIndex]
        triangles[filled++] = next
        active.removeAt(clipIndex)
    }
    triangles[filled++] = active[0]
    triangles[filled++] = active[1]
    triangles[filled++] = active[2]
    return triangles.copyOf(filled)
}

fun openRingVertexCount(ring: DoubleArray): Int {
    val vertexCount = ring.size / 2
    if (vertexCount < 2) return vertexCount
    val closesOnItself = ring[0] == ring[(vertexCount - 1) * 2] &&
        ring[1] == ring[(vertexCount - 1) * 2 + 1]
    return if (closesOnItself) vertexCount - 1 else vertexCount
}

fun signedRingArea(ring: DoubleArray, vertexCount: Int = openRingVertexCount(ring)): Double {
    var doubledArea = 0.0
    var j = vertexCount - 1
    for (i in 0 until vertexCount) {
        doubledArea += ring[j * 2] * ring[i * 2 + 1] - ring[i * 2] * ring[j * 2 + 1]
        j = i
    }
    return doubledArea / 2.0
}

private fun findEarIndex(ring: DoubleArray, active: List<Int>, counterClockwise: Boolean): Int? {
    for (i in active.indices) {
        val previous = active[(i + active.size - 1) % active.size]
        val current = active[i]
        val next = active[(i + 1) % active.size]
        val turn = crossAt(ring, previous, current, next)
        val isConvex = if (counterClockwise) turn > 0.0 else turn < 0.0
        if (!isConvex) continue
        if (anyActiveVertexInsideTriangle(ring, active, previous, current, next)) continue
        return i
    }
    return null
}

private fun flattestCornerIndex(ring: DoubleArray, active: List<Int>): Int {
    var flattest = 0
    var smallestTurn = Double.MAX_VALUE
    for (i in active.indices) {
        val previous = active[(i + active.size - 1) % active.size]
        val next = active[(i + 1) % active.size]
        val turn = abs(crossAt(ring, previous, active[i], next))
        if (turn < smallestTurn) {
            smallestTurn = turn
            flattest = i
        }
    }
    return flattest
}

private fun crossAt(ring: DoubleArray, a: Int, b: Int, c: Int): Double {
    val abX = ring[b * 2] - ring[a * 2]
    val abY = ring[b * 2 + 1] - ring[a * 2 + 1]
    val acX = ring[c * 2] - ring[a * 2]
    val acY = ring[c * 2 + 1] - ring[a * 2 + 1]
    return abX * acY - abY * acX
}

private fun anyActiveVertexInsideTriangle(
    ring: DoubleArray,
    active: List<Int>,
    a: Int,
    b: Int,
    c: Int,
): Boolean {
    for (candidate in active) {
        if (candidate == a || candidate == b || candidate == c) continue
        if (isPointInTriangle(ring[candidate * 2], ring[candidate * 2 + 1], ring, a, b, c)) return true
    }
    return false
}

private fun isPointInTriangle(px: Double, py: Double, ring: DoubleArray, a: Int, b: Int, c: Int): Boolean {
    val d1 = edgeSide(px, py, ring, a, b)
    val d2 = edgeSide(px, py, ring, b, c)
    val d3 = edgeSide(px, py, ring, c, a)
    val hasNegative = d1 < 0 || d2 < 0 || d3 < 0
    val hasPositive = d1 > 0 || d2 > 0 || d3 > 0
    return !(hasNegative && hasPositive)
}

private fun edgeSide(px: Double, py: Double, ring: DoubleArray, from: Int, to: Int): Double =
    (px - ring[to * 2]) * (ring[from * 2 + 1] - ring[to * 2 + 1]) -
        (ring[from * 2] - ring[to * 2]) * (py - ring[to * 2 + 1])

fun isPointInRing(x: Double, y: Double, ring: DoubleArray): Boolean {
    val vertexCount = openRingVertexCount(ring)
    var inside = false
    var j = vertexCount - 1
    for (i in 0 until vertexCount) {
        val xi = ring[i * 2]
        val yi = ring[i * 2 + 1]
        val xj = ring[j * 2]
        val yj = ring[j * 2 + 1]
        val crossesRay = (yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi
        if (crossesRay) inside = !inside
        j = i
    }
    return inside
}

// Even-odd across rings: disjoint outers behave as a MultiPolygon, nested rings as holes.
fun isPointInRings(x: Double, y: Double, rings: List<DoubleArray>): Boolean {
    var inside = false
    for (ring in rings) if (isPointInRing(x, y, ring)) inside = !inside
    return inside
}

fun parseGeomapColor(hex: String?): Int? {
    if (hex == null) return null
    val body = hex.removePrefix("#")
    return runCatching {
        when (body.length) {
            6 -> (0xFF shl 24) or body.toLong(16).toInt()
            8 -> body.toLong(16).toInt()
            else -> null
        }
    }.getOrNull()
}
