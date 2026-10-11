package ai.factoredui.compose.stackedscene

import ai.factoredui.compose.scene.SceneFootprint
import ai.factoredui.compose.scene.SceneView

enum class YAxis(val wire: String) { UP("up"), DOWN("down") }

fun yAxisOf(raw: Any?): YAxis? = YAxis.entries.firstOrNull { it.wire == raw }

data class YAxisChoice(val axis: YAxis, val notice: String?)

private const val ROW_0_IS_NORTH = "row_0"

fun sceneYAxis(stated: YAxis?, gridNorth: String?): YAxisChoice = when {
    stated != null -> YAxisChoice(stated, null)
    gridNorth == null -> YAxisChoice(YAxis.UP, null)
    gridNorth == ROW_0_IS_NORTH -> YAxisChoice(YAxis.DOWN, null)
    else -> YAxisChoice(YAxis.UP, "the scene's grid north '$gridNorth' is not $ROW_0_IS_NORTH; y is drawn up.")
}

private fun rowsCountedFromTheOtherEdge(footprint: SceneFootprint, rows: Int): SceneFootprint =
    footprint.copy(row = rows - footprint.row - footprint.height)

fun sceneForYAxis(scene: SceneView, yAxis: YAxis): SceneView {
    if (yAxis == YAxis.UP) return scene
    val footprints = scene.footprints ?: return scene
    return scene.copy(footprints = footprints.copy(items = footprints.items.map { rowsCountedFromTheOtherEdge(it, scene.rows) }))
}
