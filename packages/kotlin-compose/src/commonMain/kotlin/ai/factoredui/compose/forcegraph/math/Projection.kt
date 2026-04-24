package ai.factoredui.compose.forcegraph.math

/**
 * Projected screen-space point. `visible=false` marks points behind the
 * near plane; renderer should skip them but still include them in
 * depth-ordering bookkeeping when they are endpoints of visible edges.
 */
data class ProjectedPoint(
    val x: Float,
    val y: Float,
    val depth: Float,
    val visible: Boolean,
)

/**
 * Project a world-space point to screen coordinates using the given view
 * and projection matrices. Applies perspective divide, maps NDC to
 * screen-space (0..screenW, 0..screenH), and flips y so +y is down.
 */
fun Vec3.project(
    view: Matrix4,
    projection: Matrix4,
    screenW: Float,
    screenH: Float,
): ProjectedPoint {
    val viewCoords = view.transform(this)
    val viewPoint = Vec3(viewCoords[0], viewCoords[1], viewCoords[2])
    val clipCoords = projection.transform(viewPoint)
    val w = clipCoords[3]
    if (w <= Vec3.EPSILON) {
        // Point is at or behind the camera's near plane — not visible, but
        // return a sentinel so the caller can still compute something
        // sensible. depth is left large so sort order deprioritizes it.
        return ProjectedPoint(x = 0f, y = 0f, depth = Float.MAX_VALUE, visible = false)
    }
    val ndcX = clipCoords[0] / w
    val ndcY = clipCoords[1] / w
    val ndcZ = clipCoords[2] / w
    val screenX = (ndcX * 0.5f + 0.5f) * screenW
    val screenY = (1f - (ndcY * 0.5f + 0.5f)) * screenH
    return ProjectedPoint(x = screenX, y = screenY, depth = ndcZ, visible = true)
}
