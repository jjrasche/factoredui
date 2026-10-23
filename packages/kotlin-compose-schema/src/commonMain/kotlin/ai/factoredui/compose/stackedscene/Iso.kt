package ai.factoredui.compose.stackedscene

import ai.factoredui.compose.scene.SceneFootprint
import ai.factoredui.compose.scene.SceneView
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

const val ISO_ELEVATION_DEG = 35.264
const val ISO_AZIMUTH_DEG = 45.0
const val QUARTER_TURN_DEG = 90.0
const val QUARTER_TURN_DRAG_DP = 48.0

private const val ASPECTS = 4
private const val RADIANS = PI / 180
private const val SIDE_PAD_SHARE = 0.24
private const val TOP_PAD_SHARE = 0.06

data class IsoPoint(val u: Double, val v: Double, val depth: Double)

data class PartBox(val x0: Double, val y0: Double, val x1: Double, val y1: Double, val z0: Double, val z1: Double)

data class IsoFaces(val top: List<Pair<Double, Double>>, val xSide: List<Pair<Double, Double>>, val ySide: List<Pair<Double, Double>>)

/** The iso has four aspects, a quarter turn apart; any whole number of quarter turns names one of them, 0 to 3. */
fun aspectOf(quarterTurns: Int): Int = ((quarterTurns % ASPECTS) + ASPECTS) % ASPECTS

/** A horizontal drag of at least [QUARTER_TURN_DRAG_DP] steps one aspect in its direction; a shorter one steps none. */
fun aspectAfterDrag(quarterTurns: Int, travelDp: Double): Int? {
    if (abs(travelDp) < QUARTER_TURN_DRAG_DP) return null
    return aspectOf(if (travelDp > 0) quarterTurns + 1 else quarterTurns - 1)
}

fun isoAzimuthDeg(quarterTurns: Int): Double = ISO_AZIMUTH_DEG + QUARTER_TURN_DEG * aspectOf(quarterTurns)

fun isoProject(xMm: Double, yMm: Double, zMm: Double, quarterTurns: Int): IsoPoint {
    val azimuth = isoAzimuthDeg(quarterTurns) * RADIANS
    val elevation = ISO_ELEVATION_DEG * RADIANS
    val u = xMm * cos(azimuth) - yMm * sin(azimuth)
    val depth = xMm * sin(azimuth) + yMm * cos(azimuth)
    return IsoPoint(u, zMm * cos(elevation) + depth * sin(elevation), depth)
}

fun partBox(scene: SceneView, footprint: SceneFootprint): PartBox {
    val rect = partRectMm(scene, footprint)
    val range = elevationRange(scene, footprint)
    return PartBox(rect.x0, rect.y0, rect.x1, rect.y1, range.baseMm.toDouble(), range.topMm.toDouble())
}

/** Which end of a horizontal axis the camera looks from. */
private enum class CameraEnd { LOW, HIGH }

private data class Viewpoint(val x: CameraEnd, val y: CameraEnd)

private fun viewpointOf(quarterTurns: Int): Viewpoint = when (aspectOf(quarterTurns)) {
    0 -> Viewpoint(CameraEnd.LOW, CameraEnd.LOW)
    1 -> Viewpoint(CameraEnd.LOW, CameraEnd.HIGH)
    2 -> Viewpoint(CameraEnd.HIGH, CameraEnd.HIGH)
    else -> Viewpoint(CameraEnd.HIGH, CameraEnd.LOW)
}

private fun isFartherAlong(end: CameraEnd, aMin: Double, aMax: Double, bMin: Double, bMax: Double): Boolean = when (end) {
    CameraEnd.LOW -> aMin >= bMax
    CameraEnd.HIGH -> aMax <= bMin
}

private fun isBehind(a: PartBox, b: PartBox, view: Viewpoint): Boolean =
    isFartherAlong(view.x, a.x0, a.x1, b.x0, b.x1) || isFartherAlong(view.y, a.y0, a.y1, b.y0, b.y1) || a.z1 <= b.z0

private fun centreDepth(box: PartBox, quarterTurns: Int): Double =
    isoProject((box.x0 + box.x1) / 2, (box.y0 + box.y1) / 2, (box.z0 + box.z1) / 2, quarterTurns).depth

/** true: a is painted before b; false: after; null: the pair puts no constraint on the order. */
private fun isPaintedBefore(a: PartBox, b: PartBox, quarterTurns: Int): Boolean? {
    val view = viewpointOf(quarterTurns)
    val aBehind = isBehind(a, b, view)
    val bBehind = isBehind(b, a, view)
    if (aBehind != bBehind) return aBehind
    if (aBehind) return null
    if (a.z0 != b.z0) return a.z0 < b.z0
    val depthA = centreDepth(a, quarterTurns)
    val depthB = centreDepth(b, quarterTurns)
    return if (depthA == depthB) null else depthA > depthB
}

private fun orderingEdges(boxes: List<PartBox>, quarterTurns: Int): List<List<Int>> {
    val edges = boxes.map { mutableListOf<Int>() }
    for (i in boxes.indices) {
        for (j in i + 1 until boxes.size) {
            when (isPaintedBefore(boxes[i], boxes[j], quarterTurns)) {
                true -> edges[i] += j
                false -> edges[j] += i
                null -> Unit
            }
        }
    }
    return edges
}

/** Kahn's sort taking the lowest ready index first, so ties keep input order; any cycle is appended in input order. */
private fun topologicalOrder(edges: List<List<Int>>): List<Int> {
    val waiting = IntArray(edges.size)
    edges.forEach { targets -> targets.forEach { waiting[it] += 1 } }
    val ready = edges.indices.filter { waiting[it] == 0 }.toMutableList()
    val order = mutableListOf<Int>()
    while (ready.isNotEmpty()) {
        val next = ready.min()
        ready.remove(next)
        order += next
        edges[next].forEach { target ->
            waiting[target] -= 1
            if (waiting[target] == 0) ready += target
        }
    }
    val placed = order.toSet()
    return order + edges.indices.filter { it !in placed }
}

fun orderForIso(scene: SceneView, parts: List<VisiblePart>, quarterTurns: Int): List<VisiblePart> {
    val boxes = parts.map { partBox(scene, it.footprint) }
    return topologicalOrder(orderingEdges(boxes, quarterTurns)).map { parts[it] }
}

private fun projectedCorners(quarterTurns: Int, vararg corners: Triple<Double, Double, Double>): List<Pair<Double, Double>> =
    corners.map { (x, y, z) -> isoProject(x, y, z, quarterTurns).let { it.u to it.v } }

/** The top and the two side faces that face the camera at this aspect: one across x (west or east) and one across y (south or north). */
fun projectFaces(box: PartBox, quarterTurns: Int): IsoFaces = with(box) {
    val view = viewpointOf(quarterTurns)
    val faceX = if (view.x == CameraEnd.LOW) x0 else x1
    val faceY = if (view.y == CameraEnd.LOW) y0 else y1
    IsoFaces(
        top = projectedCorners(quarterTurns, Triple(x0, y0, z1), Triple(x1, y0, z1), Triple(x1, y1, z1), Triple(x0, y1, z1)),
        xSide = projectedCorners(quarterTurns, Triple(faceX, y0, z0), Triple(faceX, y1, z0), Triple(faceX, y1, z1), Triple(faceX, y0, z1)),
        ySide = projectedCorners(quarterTurns, Triple(x0, faceY, z0), Triple(x1, faceY, z0), Triple(x1, faceY, z1), Triple(x0, faceY, z1)),
    )
}

private fun cornersOf(box: PartBox, quarterTurns: Int): List<IsoPoint> =
    listOf(box.x0, box.x1).flatMap { x -> listOf(box.y0, box.y1).flatMap { y -> listOf(box.z0, box.z1).map { z -> isoProject(x, y, z, quarterTurns) } } }

/** Framed on every part, not just the shown ones, so the drawing holds still as levels are hidden. */
fun isoWindow(scene: SceneView, quarterTurns: Int): RectMm {
    val parts = scene.footprints?.items.orEmpty()
    val boxes = if (parts.isEmpty()) listOf(scene.window.let { PartBox(it.x0.toDouble(), it.y0.toDouble(), it.x1.toDouble(), it.y1.toDouble(), 0.0, 0.0) }) else parts.map { partBox(scene, it) }
    val points = boxes.flatMap { cornersOf(it, quarterTurns) }
    val left = points.minOf { it.u }
    val right = points.maxOf { it.u }
    val bottom = points.minOf { it.v }
    val top = points.maxOf { it.v }
    return RectMm(left - (right - left) * SIDE_PAD_SHARE, bottom - (top - bottom) * TOP_PAD_SHARE, right + (right - left) * SIDE_PAD_SHARE, top + (top - bottom) * TOP_PAD_SHARE)
}

fun isPointInPolygon(x: Double, y: Double, polygon: List<Pair<Double, Double>>): Boolean {
    var inside = false
    var previous = polygon.lastIndex
    for (current in polygon.indices) {
        val (xi, yi) = polygon[current]
        val (xj, yj) = polygon[previous]
        if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) inside = !inside
        previous = current
    }
    return inside
}

fun hitIso(scene: SceneView, ordered: List<VisiblePart>, u: Double, v: Double, quarterTurns: Int): VisiblePart? = ordered.lastOrNull { part ->
    val faces = projectFaces(partBox(scene, part.footprint), quarterTurns)
    listOf(faces.top, faces.xSide, faces.ySide).any { isPointInPolygon(u, v, it) }
}
