package ai.factoredui.compose.structure

import ai.factoredui.compose.scene.SceneFootprint
import ai.factoredui.compose.scene.SceneView

enum class StructureDrawing(val wire: String) { PLAN("plan"), ISO("iso"), SOUTH("south"), WEST("west") }

const val STRUCTURE_MARGIN_PX = 24.0

fun structureDrawingOf(raw: Any?): StructureDrawing? = StructureDrawing.entries.firstOrNull { it.wire == raw }

fun flatViewOf(drawing: StructureDrawing): StructureView = when (drawing) {
    StructureDrawing.PLAN, StructureDrawing.ISO -> StructureView.PLAN
    StructureDrawing.SOUTH -> StructureView.SOUTH
    StructureDrawing.WEST -> StructureView.WEST
}

fun partsToDraw(
    scene: SceneView,
    drawing: StructureDrawing,
    focus: LevelFocus,
    showLower: Boolean,
    hideTopCount: Int,
    highlightIds: Set<String>,
): List<VisiblePart> {
    val shown = fadeUnhighlighted(visibleParts(scene, focus, showLower, hideTopCount), highlightIds)
    return if (drawing == StructureDrawing.ISO) orderForIso(scene, shown) else orderForView(scene, shown, flatViewOf(drawing))
}

fun fitDrawing(scene: SceneView, drawing: StructureDrawing, widthPx: Double, heightPx: Double): CanvasFit {
    if (drawing == StructureDrawing.ISO) return fitWindow(isoWindow(scene), widthPx, heightPx, STRUCTURE_MARGIN_PX)
    val view = flatViewOf(drawing)
    val exaggeration = exaggerationFor(scene, view, widthPx, heightPx, STRUCTURE_MARGIN_PX).toDouble()
    return fitWindow(viewWindow(scene, view), widthPx, heightPx, STRUCTURE_MARGIN_PX, exaggeration)
}

fun hitDrawnPart(scene: SceneView, parts: List<VisiblePart>, drawing: StructureDrawing, fit: CanvasFit, xPx: Double, yPx: Double): VisiblePart? {
    val (u, v) = canvasToMm(fit, xPx, yPx)
    return if (drawing == StructureDrawing.ISO) hitIso(scene, parts, u, v) else hitPartInView(scene, parts, u, v, flatViewOf(drawing))
}

fun popupAnchor(scene: SceneView, footprint: SceneFootprint, drawing: StructureDrawing, fit: CanvasFit): Pair<Double, Double> {
    if (drawing == StructureDrawing.ISO) {
        val top = projectFaces(partBox(scene, footprint)).top.map { (u, v) -> mmToCanvas(fit, u, v) }
        return top.sumOf { it.first } / top.size to top.minOf { it.second }
    }
    val rect = partRectInView(scene, footprint, flatViewOf(drawing))
    val (left, top) = mmToCanvas(fit, rect.x0, rect.y1)
    val (right, _) = mmToCanvas(fit, rect.x1, rect.y1)
    return (left + right) / 2 to top
}

fun partPopupLines(scene: SceneView, footprint: SceneFootprint, figure: UtilisationFigure?): List<String> {
    val label = scene.types.firstOrNull { it.id == footprint.type }?.label ?: footprint.type
    val second = figure?.let { "${footprint.id} · ${percentText(it.value)} of capacity" } ?: footprint.id
    return listOf(label, second)
}

fun isEveryPartFlat(scene: SceneView, parts: List<VisiblePart>): Boolean =
    parts.isNotEmpty() && parts.all { elevationRange(scene, it.footprint).let { range -> range.topMm == range.baseMm } }

fun drawnPartsSummary(parts: List<VisiblePart>, highlightIds: Set<String>, selectedIds: Set<String>): String =
    "drawn=${parts.size} highlighted=${parts.count { it.footprint.id in highlightIds }} " +
        "faded=${parts.count { it.isDimmed }} selected=${selectedIds.sorted().joinToString(",")}"
