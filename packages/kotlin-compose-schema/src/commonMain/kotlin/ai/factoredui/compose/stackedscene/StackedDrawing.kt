package ai.factoredui.compose.stackedscene

import ai.factoredui.compose.scene.SceneFootprint
import ai.factoredui.compose.scene.SceneView
import ai.factoredui.compose.schema.displayedText

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
    quarterTurns: Int = 0,
): List<VisiblePart> {
    val shown = fadeUnhighlighted(visibleParts(scene, focus, showLower, hideTopCount), highlightIds)
    return if (drawing == StackedDrawing.ISO) orderForIso(scene, shown, quarterTurns) else orderForView(scene, shown, flatViewOf(drawing))
}

/** The plan and the elevations ignore [quarterTurns]; [camera] zooms and shifts every view. */
fun fitDrawing(scene: SceneView, drawing: StackedDrawing, widthPx: Double, heightPx: Double, quarterTurns: Int = 0, camera: SceneCamera = SceneCamera()): CanvasFit =
    cameraFit(fittedDrawing(scene, drawing, widthPx, heightPx, quarterTurns), camera, widthPx, heightPx)

private fun fittedDrawing(scene: SceneView, drawing: StackedDrawing, widthPx: Double, heightPx: Double, quarterTurns: Int): CanvasFit {
    if (drawing == StackedDrawing.ISO) return fitWindow(isoWindow(scene, quarterTurns), widthPx, heightPx, DRAWING_MARGIN_PX)
    val view = flatViewOf(drawing)
    val exaggeration = exaggerationFor(scene, view, widthPx, heightPx, DRAWING_MARGIN_PX).toDouble()
    return fitWindow(viewWindow(scene, view), widthPx, heightPx, DRAWING_MARGIN_PX, exaggeration)
}

const val MIN_INTRINSIC_SCENE_HEIGHT = 200.0
private const val MOST_HEIGHT_PER_WIDTH = 1.5

/** The height a scene takes when its container sets none, as in a scroll: its window's shape at [width], held between bounds; the iso's first aspect sets it, so a quarter turn never moves the page. */
fun intrinsicSceneHeight(scene: SceneView, drawing: StackedDrawing, width: Double): Double {
    val window = if (drawing == StackedDrawing.ISO) isoWindow(scene, 0) else viewWindow(scene, flatViewOf(drawing))
    val shape = (window.y1 - window.y0) / (window.x1 - window.x0)
    val natural = (width - 2 * DRAWING_MARGIN_PX) * shape + 2 * DRAWING_MARGIN_PX
    return natural.coerceIn(MIN_INTRINSIC_SCENE_HEIGHT, maxOf(MIN_INTRINSIC_SCENE_HEIGHT, width * MOST_HEIGHT_PER_WIDTH))
}

fun hitDrawnPart(scene: SceneView, parts: List<VisiblePart>, drawing: StackedDrawing, fit: CanvasFit, xPx: Double, yPx: Double, quarterTurns: Int = 0): VisiblePart? {
    val (u, v) = canvasToMm(fit, xPx, yPx)
    return if (drawing == StackedDrawing.ISO) hitIso(scene, parts, u, v, quarterTurns) else hitPartInView(scene, parts, u, v, flatViewOf(drawing))
}

/** Every part the point crosses, nearest to the viewer first: [parts] are in drawing order, back to front. */
fun hitAllDrawnParts(scene: SceneView, parts: List<VisiblePart>, drawing: StackedDrawing, fit: CanvasFit, xPx: Double, yPx: Double, quarterTurns: Int = 0): List<VisiblePart> {
    val (u, v) = canvasToMm(fit, xPx, yPx)
    return if (drawing == StackedDrawing.ISO) hitIsoAll(scene, parts, u, v, quarterTurns) else hitPartsInView(scene, parts, u, v, flatViewOf(drawing))
}

fun popupAnchor(scene: SceneView, footprint: SceneFootprint, drawing: StackedDrawing, fit: CanvasFit, quarterTurns: Int = 0): Pair<Double, Double> {
    if (drawing == StackedDrawing.ISO) {
        val top = projectFaces(partBox(scene, footprint), quarterTurns).top.map { (u, v) -> mmToCanvas(fit, u, v) }
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

fun partValueText(value: PartValue?, scale: ColourScale?): String? {
    if (scale == null) return null
    val figure = value?.let { displayedText(it.value, scale.format, null) }
    return listOfNotNull(figure, bandLabelFor(scale, value?.value)).joinToString(" · ").ifEmpty { null }
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
