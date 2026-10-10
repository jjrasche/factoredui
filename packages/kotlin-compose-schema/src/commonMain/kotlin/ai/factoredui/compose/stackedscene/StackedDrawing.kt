package ai.factoredui.compose.stackedscene

import ai.factoredui.compose.scene.SceneFootprint
import ai.factoredui.compose.scene.SceneView

enum class StackedDrawing(val wire: String) { PLAN("plan"), ISO("iso"), SOUTH("south"), WEST("west") }

const val DRAWING_MARGIN_PX = 24.0

fun stackedDrawingOf(raw: Any?): StackedDrawing? = StackedDrawing.entries.firstOrNull { it.wire == raw }

fun flatViewOf(drawing: StackedDrawing): FlatView = when (drawing) {
    StackedDrawing.PLAN, StackedDrawing.ISO -> FlatView.PLAN
    StackedDrawing.SOUTH -> FlatView.SOUTH
    StackedDrawing.WEST -> FlatView.WEST
}

fun partsToDraw(
    scene: SceneView,
    drawing: StackedDrawing,
    focus: LevelFocus,
    showLower: Boolean,
    hideTopCount: Int,
    highlightIds: Set<String>,
): List<VisiblePart> {
    val shown = fadeUnhighlighted(visibleParts(scene, focus, showLower, hideTopCount), highlightIds)
    return if (drawing == StackedDrawing.ISO) orderForIso(scene, shown) else orderForView(scene, shown, flatViewOf(drawing))
}

fun fitDrawing(scene: SceneView, drawing: StackedDrawing, widthPx: Double, heightPx: Double): CanvasFit {
    if (drawing == StackedDrawing.ISO) return fitWindow(isoWindow(scene), widthPx, heightPx, DRAWING_MARGIN_PX)
    val view = flatViewOf(drawing)
    val exaggeration = exaggerationFor(scene, view, widthPx, heightPx, DRAWING_MARGIN_PX).toDouble()
    return fitWindow(viewWindow(scene, view), widthPx, heightPx, DRAWING_MARGIN_PX, exaggeration)
}

fun hitDrawnPart(scene: SceneView, parts: List<VisiblePart>, drawing: StackedDrawing, fit: CanvasFit, xPx: Double, yPx: Double): VisiblePart? {
    val (u, v) = canvasToMm(fit, xPx, yPx)
    return if (drawing == StackedDrawing.ISO) hitIso(scene, parts, u, v) else hitPartInView(scene, parts, u, v, flatViewOf(drawing))
}

fun popupAnchor(scene: SceneView, footprint: SceneFootprint, drawing: StackedDrawing, fit: CanvasFit): Pair<Double, Double> {
    if (drawing == StackedDrawing.ISO) {
        val top = projectFaces(partBox(scene, footprint)).top.map { (u, v) -> mmToCanvas(fit, u, v) }
        return top.sumOf { it.first } / top.size to top.minOf { it.second }
    }
    val rect = partRectInView(scene, footprint, flatViewOf(drawing))
    val (left, top) = mmToCanvas(fit, rect.x0, rect.y1)
    val (right, _) = mmToCanvas(fit, rect.x1, rect.y1)
    return (left + right) / 2 to top
}

fun partPopupLines(scene: SceneView, footprint: SceneFootprint, valueText: String?): List<String> {
    val label = scene.types.firstOrNull { it.id == footprint.type }?.label ?: footprint.type
    val second = valueText?.let { "${footprint.id} · $it" } ?: footprint.id
    return listOf(label, second)
}

fun exaggerationNote(fit: CanvasFit): String? {
    val times = kotlin.math.round(fit.vScale / fit.scale).toInt()
    return if (times > 1) "Heights are drawn $times times their true scale." else null
}

fun isEveryPartFlat(scene: SceneView, parts: List<VisiblePart>): Boolean =
    parts.isNotEmpty() && parts.all { elevationRange(scene, it.footprint).let { range -> range.topMm == range.baseMm } }

fun drawnPartsSummary(parts: List<VisiblePart>, highlightIds: Set<String>, selectedIds: Set<String>): String =
    "drawn=${parts.size} highlighted=${parts.count { it.footprint.id in highlightIds }} " +
        "faded=${parts.count { it.isDimmed }} selected=${selectedIds.sorted().joinToString(",")}"
