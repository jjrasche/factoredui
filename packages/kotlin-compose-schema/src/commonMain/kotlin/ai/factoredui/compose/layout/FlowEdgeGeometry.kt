package ai.factoredui.compose.layout

import kotlin.math.sqrt

private const val STEPS_PER_SEGMENT = 16

fun sampleFlowRoute(route: FlowEdgeRoute, stepsPerSegment: Int = STEPS_PER_SEGMENT): List<FlowPoint> {
    val sampled = ArrayList<FlowPoint>()
    sampled.add(route.points.first())
    route.points.zipWithNext().forEach { (from, to) ->
        val midX = (from.x + to.x) / 2f
        for (step in 1..stepsPerSegment) {
            val t = step.toFloat() / stepsPerSegment
            val u = 1f - t
            sampled.add(
                FlowPoint(
                    x = u * u * u * from.x + 3 * u * u * t * midX + 3 * u * t * t * midX + t * t * t * to.x,
                    y = u * u * u * from.y + 3 * u * u * t * from.y + 3 * u * t * t * to.y + t * t * t * to.y,
                ),
            )
        }
    }
    return sampled
}

fun hitTestFlowEdge(routes: List<FlowEdgeRoute>, x: Float, y: Float, tolerance: Float): FlowEdgeRoute? =
    routes.map { it to distanceToPolyline(sampleFlowRoute(it), x, y) }
        .filter { it.second <= tolerance }
        .minByOrNull { it.second }
        ?.first

fun flowRouteMidpoint(route: FlowEdgeRoute): FlowPoint {
    val sampled = sampleFlowRoute(route)
    val lengths = sampled.zipWithNext().map { (a, b) -> distance(a.x, a.y, b.x, b.y) }
    var remaining = lengths.sum() / 2f
    for ((index, length) in lengths.withIndex()) {
        if (remaining <= length) {
            val fraction = if (length == 0f) 0f else remaining / length
            val from = sampled[index]
            val to = sampled[index + 1]
            return FlowPoint(from.x + (to.x - from.x) * fraction, from.y + (to.y - from.y) * fraction)
        }
        remaining -= length
    }
    return sampled.last()
}

private fun distanceToPolyline(points: List<FlowPoint>, x: Float, y: Float): Float =
    points.zipWithNext().minOf { (a, b) -> distanceToSegment(a, b, x, y) }

private fun distanceToSegment(a: FlowPoint, b: FlowPoint, x: Float, y: Float): Float {
    val dx = b.x - a.x
    val dy = b.y - a.y
    val lengthSquared = dx * dx + dy * dy
    val t = if (lengthSquared == 0f) 0f else (((x - a.x) * dx + (y - a.y) * dy) / lengthSquared).coerceIn(0f, 1f)
    return distance(x, y, a.x + t * dx, a.y + t * dy)
}

private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
    val dx = x2 - x1
    val dy = y2 - y1
    return sqrt(dx * dx + dy * dy)
}
