package ai.factoredui.compose.volume

import kotlin.math.sqrt

private const val ON_PLANE_MM = 0.5
private const val FACING_SUN = 1e-6

private data class Plane(val origin: VolumePoint, val normal: Direction, val across: Direction, val up: Direction)

private fun dot(a: Direction, b: Direction) = a.x * b.x + a.y * b.y + a.z * b.z

private fun between(from: VolumePoint, to: VolumePoint) = Direction(to.xMm - from.xMm, to.yMm - from.yMm, to.zMm - from.zMm)

private fun crossOf(a: Direction, b: Direction) = Direction(a.y * b.z - a.z * b.y, a.z * b.x - a.x * b.z, a.x * b.y - a.y * b.x)

private fun unitOf(d: Direction): Direction {
    val length = sqrt(dot(d, d))
    return Direction(d.x / length, d.y / length, d.z / length)
}

private fun planeOf(face: List<VolumePoint>, normal: Direction): Plane {
    val across = unitOf(between(face[0], face[1]))
    return Plane(face[0], normal, across, crossOf(normal, across))
}

private fun heightAbove(plane: Plane, point: VolumePoint) = dot(between(plane.origin, point), plane.normal)

private fun planar(plane: Plane, point: VolumePoint): Pair<Double, Double> {
    val offset = between(plane.origin, point)
    return dot(offset, plane.across) to dot(offset, plane.up)
}

private fun spaceOf(plane: Plane, at: Pair<Double, Double>) = VolumePoint(
    plane.origin.xMm + plane.across.x * at.first + plane.up.x * at.second,
    plane.origin.yMm + plane.across.y * at.first + plane.up.y * at.second,
    plane.origin.zMm + plane.across.z * at.first + plane.up.z * at.second,
)

private val EDGES: List<Pair<Int, Int>> = (0 until 4).flatMap { i -> listOf(i to (i + 1) % 4, 4 + i to 4 + (i + 1) % 4, i to 4 + i) }

/** The points of the part of [occluder] above [plane], seen along the sun's rays: each corner above the plane slid down its ray onto it, each corner on it kept, and each edge crossing it contributing the point where it crosses. */
private fun shadowPoints(plane: Plane, occluder: BoxRings, toSun: Direction): List<VolumePoint> {
    val corners = occluder.all
    val heights = corners.map { heightAbove(plane, it) }
    if (heights.none { it > ON_PLANE_MM }) return emptyList()
    val slide = dot(toSun, plane.normal)
    val fallen = corners.indices.filter { heights[it] > -ON_PLANE_MM }.map { i ->
        val drop = if (heights[i] > ON_PLANE_MM) heights[i] / slide else 0.0
        VolumePoint(corners[i].xMm - toSun.x * drop, corners[i].yMm - toSun.y * drop, corners[i].zMm - toSun.z * drop)
    }
    val crossings = EDGES.filter { (a, b) -> (heights[a] > ON_PLANE_MM && heights[b] < -ON_PLANE_MM) || (heights[b] > ON_PLANE_MM && heights[a] < -ON_PLANE_MM) }.map { (a, b) ->
        val share = heights[a] / (heights[a] - heights[b])
        VolumePoint(
            corners[a].xMm + (corners[b].xMm - corners[a].xMm) * share,
            corners[a].yMm + (corners[b].yMm - corners[a].yMm) * share,
            corners[a].zMm + (corners[b].zMm - corners[a].zMm) * share,
        )
    }
    return fallen + crossings
}

private fun sideOf(from: Pair<Double, Double>, to: Pair<Double, Double>, point: Pair<Double, Double>) =
    (to.first - from.first) * (point.second - from.second) - (to.second - from.second) * (point.first - from.first)

private fun crossingOf(a: Pair<Double, Double>, b: Pair<Double, Double>, edgeFrom: Pair<Double, Double>, edgeTo: Pair<Double, Double>): Pair<Double, Double> {
    val near = sideOf(edgeFrom, edgeTo, a)
    val far = sideOf(edgeFrom, edgeTo, b)
    val share = near / (near - far)
    return (a.first + (b.first - a.first) * share) to (a.second + (b.second - a.second) * share)
}

/** [subject] cut to the half-plane left of the edge from [edgeFrom] to [edgeTo]. */
private fun cutToLeftOf(subject: List<Pair<Double, Double>>, edgeFrom: Pair<Double, Double>, edgeTo: Pair<Double, Double>): List<Pair<Double, Double>> =
    subject.indices.flatMap { i ->
        val current = subject[i]
        val previous = subject[(i + subject.size - 1) % subject.size]
        val currentIn = sideOf(edgeFrom, edgeTo, current) >= 0.0
        val previousIn = sideOf(edgeFrom, edgeTo, previous) >= 0.0
        when {
            currentIn && previousIn -> listOf(current)
            currentIn -> listOf(crossingOf(previous, current, edgeFrom, edgeTo), current)
            previousIn -> listOf(crossingOf(previous, current, edgeFrom, edgeTo))
            else -> emptyList()
        }
    }

/** [subject] cut to the convex anticlockwise [window]. */
private fun clipToWindow(subject: List<Pair<Double, Double>>, window: List<Pair<Double, Double>>): List<Pair<Double, Double>> =
    window.indices.fold(subject) { kept, i -> if (kept.size < 3) kept else cutToLeftOf(kept, window[i], window[(i + 1) % window.size]) }

private fun anticlockwise(polygon: List<Pair<Double, Double>>): List<Pair<Double, Double>> {
    val area = polygon.indices.sumOf { i -> polygon[i].first * polygon[(i + 1) % polygon.size].second - polygon[(i + 1) % polygon.size].first * polygon[i].second }
    return if (area >= 0.0) polygon else polygon.reversed()
}

private fun shadowOnFace(face: List<VolumePoint>, normal: Direction, occluders: List<BoxRings>, toSun: Direction): List<List<VolumePoint>> {
    val plane = planeOf(face, normal)
    val window = anticlockwise(face.map { planar(plane, it) })
    return occluders.mapNotNull { occluder ->
        val points = shadowPoints(plane, occluder, toSun).map { planar(plane, it) }
        val outline = if (points.size >= 3) convexHull(points) else emptyList()
        val covered = if (outline.size >= 3) clipToWindow(outline, window) else emptyList()
        covered.takeIf { it.size >= 3 }?.map { spaceOf(plane, it) }
    }
}

/** Where the other boxes' shadows fall on each lit face of a box, by the box's id: for the top face and then the four sides (the order [boxFaceNormals] gives), the polygons in millimetres that [occluders] shade when the sun is toward [toSun]. A box with a sloping top takes none, and a face turned from the sun takes none. */
fun shadowsOnFaces(receivers: List<VolumeBox>, occluders: List<VolumeBox>, toSun: Direction): Map<String, List<List<List<VolumePoint>>>> {
    val occluderRings = occluders.map { it.id to boxRings(it) }
    return receivers.filter { it.topRiseMm == 0.0 }.associate { receiver ->
        val rings = boxRings(receiver)
        val faces = listOf(rings.high) + rings.low.indices.map { i -> listOf(rings.low[i], rings.low[(i + 1) % 4], rings.high[(i + 1) % 4], rings.high[i]) }
        val others = occluderRings.filter { it.first != receiver.id }.map { it.second }
        receiver.id to faces.zip(boxFaceNormals(rings)).map { (face, normal) ->
            if (dot(normal, toSun) > FACING_SUN) shadowOnFace(face, normal, others, toSun) else emptyList()
        }
    }
}
