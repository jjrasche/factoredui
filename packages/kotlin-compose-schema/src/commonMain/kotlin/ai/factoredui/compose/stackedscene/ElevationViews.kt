package ai.factoredui.compose.stackedscene

import ai.factoredui.compose.scene.SceneFootprint
import ai.factoredui.compose.scene.SceneView
import kotlin.math.floor

enum class FlatView(val label: String) { PLAN("Plan"), SOUTH("From the south"), WEST("From the west") }

data class ElevationRange(val baseMm: Long, val topMm: Long)

data class FloorLevel(val level: Int, val label: String?, val zMm: Long?)

private const val HEADROOM_SHARE = 0.15
private const val TARGET_HEIGHT_SHARE = 0.75
private const val MAX_EXAGGERATION = 10.0

fun partHeightMm(scene: SceneView, footprint: SceneFootprint): Long? =
    footprint.heightMm ?: scene.types.firstOrNull { it.id == footprint.type }?.heightMm

/** A part whose height nothing states is flat at its base: inventing a height would draw a part the scene never described. */
fun elevationRange(scene: SceneView, footprint: SceneFootprint): ElevationRange =
    ElevationRange(footprint.zMm, footprint.zMm + (partHeightMm(scene, footprint) ?: 0L))

fun floorLevels(scene: SceneView): List<FloorLevel> = scene.stackLevels.map { FloorLevel(it.stackLevel, it.label, it.zBaseMm) }

private fun partsOf(scene: SceneView): List<SceneFootprint> = scene.footprints?.items ?: emptyList()

private fun planWindow(scene: SceneView): RectMm =
    scene.window.let { RectMm(it.x0.toDouble(), it.y0.toDouble(), it.x1.toDouble(), it.y1.toDouble()) }

fun viewWindow(scene: SceneView, view: FlatView): RectMm {
    val plan = planWindow(scene)
    if (view == FlatView.PLAN) return plan
    val ranges = partsOf(scene).map { elevationRange(scene, it) }
    val low = minOf(0L, ranges.minOfOrNull { it.baseMm } ?: 0L).toDouble()
    val high = maxOf(0L, ranges.maxOfOrNull { it.topMm } ?: 0L).toDouble()
    val (left, right) = if (view == FlatView.SOUTH) plan.x0 to plan.x1 else plan.y0 to plan.y1
    return RectMm(left, low, right, high + (high - low) * HEADROOM_SHARE)
}

/** The west view looks east, so the scene's north (high y) is on the viewer's left: y is mirrored across the window. */
fun partRectInView(scene: SceneView, footprint: SceneFootprint, view: FlatView): RectMm {
    val plan = partRectMm(scene, footprint)
    if (view == FlatView.PLAN) return plan
    val range = elevationRange(scene, footprint)
    val base = range.baseMm.toDouble()
    val top = range.topMm.toDouble()
    if (view == FlatView.SOUTH) return RectMm(plan.x0, base, plan.x1, top)
    val mirror = (scene.window.y0 + scene.window.y1).toDouble()
    return RectMm(mirror - plan.y1, base, mirror - plan.y0, top)
}

private fun depthInView(scene: SceneView, part: VisiblePart, view: FlatView): Double {
    val plan = partRectMm(scene, part.footprint)
    return if (view == FlatView.SOUTH) (plan.y0 + plan.y1) / 2 else (plan.x0 + plan.x1) / 2
}

fun orderForView(scene: SceneView, parts: List<VisiblePart>, view: FlatView): List<VisiblePart> {
    if (view == FlatView.PLAN) return parts
    return parts.sortedWith(compareByDescending<VisiblePart> { depthInView(scene, it, view) }.thenBy { it.level })
}

fun coveredFlags(scene: SceneView, parts: List<VisiblePart>, view: FlatView): List<Boolean> = parts.mapIndexed { index, part ->
    val rect = partRectInView(scene, part.footprint, view)
    val centreU = (rect.x0 + rect.x1) / 2
    val centreV = (rect.y0 + rect.y1) / 2
    parts.drop(index + 1).any { partRectInView(scene, it.footprint, view).contains(centreU, centreV) }
}

fun hitPartInView(scene: SceneView, parts: List<VisiblePart>, u: Double, v: Double, view: FlatView): VisiblePart? =
    parts.lastOrNull { partRectInView(scene, it.footprint, view).contains(u, v) }

fun exaggerationFor(scene: SceneView, view: FlatView, canvasWidth: Double, canvasHeight: Double, margin: Double): Int {
    if (view == FlatView.PLAN) return 1
    val window = viewWindow(scene, view)
    val pixelsPerMm = (canvasWidth - 2 * margin) / (window.x1 - window.x0)
    val wanted = floor(TARGET_HEIGHT_SHARE * (canvasHeight - 2 * margin) / (pixelsPerMm * (window.y1 - window.y0)))
    return wanted.coerceIn(1.0, MAX_EXAGGERATION).toInt()
}
