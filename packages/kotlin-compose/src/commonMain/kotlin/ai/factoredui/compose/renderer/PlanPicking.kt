package ai.factoredui.compose.renderer

import kotlin.math.hypot

/** A drawn thing's place on the screen, in pixels, for picking: a [Reach] is a point or a line within a tap's reach, an [Area] is a rectangle that holds the tap. */
internal sealed interface PickTarget {
    val id: String

    data class Reach(override val id: String, val fromX: Float, val fromY: Float, val toX: Float, val toY: Float) : PickTarget

    data class Area(override val id: String, val left: Float, val top: Float, val right: Float, val bottom: Float) : PickTarget
}

private fun distanceToSegment(x: Float, y: Float, target: PickTarget.Reach): Float {
    val dx = target.toX - target.fromX
    val dy = target.toY - target.fromY
    val lengthSquared = dx * dx + dy * dy
    val along = if (lengthSquared == 0f) 0f else (((x - target.fromX) * dx + (y - target.fromY) * dy) / lengthSquared).coerceIn(0f, 1f)
    return hypot(x - (target.fromX + along * dx), y - (target.fromY + along * dy))
}

private fun contains(target: PickTarget.Area, x: Float, y: Float) = x >= target.left && x <= target.right && y >= target.top && y <= target.bottom

private fun areaOf(target: PickTarget.Area) = (target.right - target.left) * (target.bottom - target.top)

/** A line or point within [reachPx] of the tap wins, the nearest of them; failing that the smallest rectangle holding the tap, so a room is picked through the walls and a floor through its roof. */
internal fun pickTarget(targets: List<PickTarget>, x: Float, y: Float, reachPx: Float): String? {
    val near = targets.filterIsInstance<PickTarget.Reach>().map { it to distanceToSegment(x, y, it) }.filter { it.second <= reachPx }.minByOrNull { it.second }
    if (near != null) return near.first.id
    return targets.filterIsInstance<PickTarget.Area>().filter { contains(it, x, y) }.minByOrNull(::areaOf)?.id
}
