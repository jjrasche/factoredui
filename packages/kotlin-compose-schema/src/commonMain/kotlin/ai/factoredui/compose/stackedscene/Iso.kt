package ai.factoredui.compose.stackedscene

import ai.factoredui.compose.scene.SceneFootprint
import ai.factoredui.compose.scene.SceneView
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

const val ISO_ELEVATION_DEG = 35.264

private const val ISO_AZIMUTH_DEG = 45.0
private const val RADIANS = PI / 180
private const val SIDE_PAD_SHARE = 0.24
private const val TOP_PAD_SHARE = 0.06

data class IsoPoint(val u: Double, val v: Double, val depth: Double)

data class PartBox(val x0: Double, val y0: Double, val x1: Double, val y1: Double, val z0: Double, val z1: Double)

data class IsoFaces(val top: List<Pair<Double, Double>>, val west: List<Pair<Double, Double>>, val south: List<Pair<Double, Double>>)

fun isoProject(xMm: Double, yMm: Double, zMm: Double): IsoPoint {
    val azimuth = ISO_AZIMUTH_DEG * RADIANS
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

private fun isBehind(a: PartBox, b: PartBox): Boolean = a.x0 >= b.x1 || a.y0 >= b.y1 || a.z1 <= b.z0

private fun centreDepth(box: PartBox): Double = isoProject((box.x0 + box.x1) / 2, (box.y0 + box.y1) / 2, (box.z0 + box.z1) / 2).depth

/** true: a is painted before b; false: after; null: the pair puts no constraint on the order. */
private fun isPaintedBefore(a: PartBox, b: PartBox): Boolean? {
    val aBehind = isBehind(a, b)
    val bBehind = isBehind(b, a)
    if (aBehind != bBehind) return aBehind
    if (aBehind) return null
    if (a.z0 != b.z0) return a.z0 < b.z0
    val depthA = centreDepth(a)
    val depthB = centreDepth(b)
    return if (depthA == depthB) null else depthA > depthB
}

private fun orderingEdges(boxes: List<PartBox>): List<List<Int>> {
    val edges = boxes.map { mutableListOf<Int>() }
    for (i in boxes.indices) {
        for (j in i + 1 until boxes.size) {
            when (isPaintedBefore(boxes[i], boxes[j])) {
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

fun orderForIso(scene: SceneView, parts: List<VisiblePart>): List<VisiblePart> {
    val boxes = parts.map { partBox(scene, it.footprint) }
    return topologicalOrder(orderingEdges(boxes)).map { parts[it] }
}

private fun projectedCorners(vararg corners: Triple<Double, Double, Double>): List<Pair<Double, Double>> =
    corners.map { (x, y, z) -> isoProject(x, y, z).let { it.u to it.v } }

fun projectFaces(box: PartBox): IsoFaces = with(box) {
    IsoFaces(
        top = projectedCorners(Triple(x0, y0, z1), Triple(x1, y0, z1), Triple(x1, y1, z1), Triple(x0, y1, z1)),
        west = projectedCorners(Triple(x0, y0, z0), Triple(x0, y1, z0), Triple(x0, y1, z1), Triple(x0, y0, z1)),
        south = projectedCorners(Triple(x0, y0, z0), Triple(x1, y0, z0), Triple(x1, y0, z1), Triple(x0, y0, z1)),
    )
}

private fun cornersOf(box: PartBox): List<IsoPoint> =
    listOf(box.x0, box.x1).flatMap { x -> listOf(box.y0, box.y1).flatMap { y -> listOf(box.z0, box.z1).map { z -> isoProject(x, y, z) } } }

/** Framed on every part, not just the shown ones, so the drawing holds still as levels are hidden. */
fun isoWindow(scene: SceneView): RectMm {
    val parts = scene.footprints?.items.orEmpty()
    val boxes = if (parts.isEmpty()) listOf(scene.window.let { PartBox(it.x0.toDouble(), it.y0.toDouble(), it.x1.toDouble(), it.y1.toDouble(), 0.0, 0.0) }) else parts.map { partBox(scene, it) }
    val points = boxes.flatMap(::cornersOf)
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

fun hitIso(scene: SceneView, ordered: List<VisiblePart>, u: Double, v: Double): VisiblePart? = ordered.lastOrNull { part ->
    val faces = projectFaces(partBox(scene, part.footprint))
    listOf(faces.top, faces.west, faces.south).any { isPointInPolygon(u, v, it) }
}
