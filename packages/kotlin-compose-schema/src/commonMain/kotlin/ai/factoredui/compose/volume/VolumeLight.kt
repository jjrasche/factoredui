package ai.factoredui.compose.volume

import kotlin.math.hypot
import kotlin.math.max

/** A unit vector in the engine's frame (x east, y north, z up). */
data class Direction(val x: Double, val y: Double, val z: Double)

private const val AMBIENT = 0.55
private const val DIRECT = 0.45

private fun unit(x: Double, y: Double, z: Double): Direction {
    val length = kotlin.math.sqrt(x * x + y * y + z * z)
    return Direction(x / length, y / length, z / length)
}

/** The way to the sun: from the south-west, high. */
val SUN: Direction = unit(-0.5, -0.6, 0.75)

val UP = Direction(0.0, 0.0, 1.0)

/** The outward normal of the vertical face standing on the ground edge from [from] to [to], going anticlockwise seen from above; null when the edge has no length. */
fun sideNormal(from: Pair<Double, Double>, to: Pair<Double, Double>): Direction? {
    val dx = to.first - from.first
    val dy = to.second - from.second
    val length = hypot(dx, dy)
    return if (length == 0.0) null else Direction(dy / length, -dx / length, 0.0)
}

/** How lit a face with this normal is, from the ambient floor at 0.55 up to about 0.9 for a face turned straight to the sun. */
fun sunShade(normal: Direction, sun: Direction = SUN): Double = AMBIENT + DIRECT * max(0.0, normal.x * sun.x + normal.y * sun.y + normal.z * sun.z)

private fun cross(origin: Pair<Double, Double>, a: Pair<Double, Double>, b: Pair<Double, Double>) =
    (a.first - origin.first) * (b.second - origin.second) - (a.second - origin.second) * (b.first - origin.first)

private fun hullHalf(points: List<Pair<Double, Double>>): List<Pair<Double, Double>> {
    val kept = ArrayList<Pair<Double, Double>>()
    points.forEach { point ->
        while (kept.size >= 2 && cross(kept[kept.size - 2], kept.last(), point) <= 0.0) kept.removeAt(kept.size - 1)
        kept.add(point)
    }
    return kept.dropLast(1)
}

/** The smallest convex outline of the points, anticlockwise. */
fun convexHull(points: List<Pair<Double, Double>>): List<Pair<Double, Double>> {
    val sorted = points.distinct().sortedWith(compareBy({ it.first }, { it.second }))
    if (sorted.size < 3) return sorted
    return hullHalf(sorted) + hullHalf(sorted.asReversed())
}

/** Where a box's shadow falls on the ground at [groundMm]: its eight corners slid along the sun's rays down to that height, as a convex outline in east-north pairs. */
fun shadowOutline(box: VolumeBox, groundMm: Double, sun: Direction = SUN): List<Pair<Double, Double>> {
    val points = boxRings(box).all.map { corner ->
        val drop = max(0.0, corner.zMm - groundMm)
        (corner.xMm - sun.x / sun.z * drop) to (corner.yMm - sun.y / sun.z * drop)
    }
    return convexHull(points)
}

/** The way to the sun a spec names as `[east, north, up]`: the default when it names none, or names a vector with no height (a sun on the horizon casts no finite shadow) or no length. */
fun readSun(resolved: Any?): Direction {
    val parts = (resolved as? List<*>)?.map { (it as? Number)?.toDouble()?.takeIf(Double::isFinite) }
    if (parts == null || parts.size != 3 || parts.any { it == null }) return SUN
    val (x, y, z) = parts.map { it!! }
    return if (z <= 0.0) SUN else unit(x, y, z)
}
