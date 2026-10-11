package ai.factoredui.compose.stackedscene

import ai.factoredui.compose.scene.SceneFootprint
import ai.factoredui.compose.scene.SceneView

enum class YAxis(val wire: String) { UP("up"), DOWN("down") }

fun yAxisOf(raw: Any?): YAxis? = YAxis.entries.firstOrNull { it.wire == raw }

private fun rowsCountedFromTheOtherEdge(footprint: SceneFootprint, rows: Int): SceneFootprint =
    footprint.copy(row = rows - footprint.row - footprint.height)

fun sceneForYAxis(scene: SceneView, yAxis: YAxis): SceneView {
    if (yAxis == YAxis.UP) return scene
    val footprints = scene.footprints ?: return scene
    return scene.copy(footprints = footprints.copy(items = footprints.items.map { rowsCountedFromTheOtherEdge(it, scene.rows) }))
}
