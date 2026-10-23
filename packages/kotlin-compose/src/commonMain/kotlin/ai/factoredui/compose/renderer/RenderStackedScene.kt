package ai.factoredui.compose.renderer

import ai.factoredui.compose.scene.STACKED_SCENE_CAPABILITY
import ai.factoredui.compose.scene.SceneView
import ai.factoredui.compose.scene.readSceneViewValue
import ai.factoredui.compose.schema.ActionRef
import ai.factoredui.compose.schema.SpecNode
import ai.factoredui.compose.schema.SpecValue
import ai.factoredui.compose.schema.StackedSceneProps
import ai.factoredui.compose.schema.asLayoutProps
import ai.factoredui.compose.schema.bindingPath
import ai.factoredui.compose.schema.resolveGeomapSelection
import ai.factoredui.compose.schema.resolveStackedSceneProps
import ai.factoredui.compose.schema.selectionAfterTap
import ai.factoredui.compose.stackedscene.CanvasFit
import ai.factoredui.compose.stackedscene.LevelFocus
import ai.factoredui.compose.stackedscene.PartValue
import ai.factoredui.compose.stackedscene.StackedDrawing
import ai.factoredui.compose.stackedscene.VisiblePart
import ai.factoredui.compose.stackedscene.drawnPartsSummary
import ai.factoredui.compose.stackedscene.exaggerationNote
import ai.factoredui.compose.stackedscene.fitDrawing
import ai.factoredui.compose.stackedscene.hitDrawnPart
import ai.factoredui.compose.stackedscene.intrinsicSceneHeight
import ai.factoredui.compose.stackedscene.isEveryPartFlat
import ai.factoredui.compose.stackedscene.partPopupLines
import ai.factoredui.compose.stackedscene.partValueText
import ai.factoredui.compose.stackedscene.partValues
import ai.factoredui.compose.stackedscene.partsToDraw
import ai.factoredui.compose.stackedscene.popupAnchor
import ai.factoredui.compose.stackedscene.YAxis
import ai.factoredui.compose.stackedscene.sceneForYAxis
import ai.factoredui.compose.stackedscene.sceneYAxis
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal object StackedSceneRenderer : SceneRenderer {
    override val capability = STACKED_SCENE_CAPABILITY

    @Composable
    override fun Draw(frame: SceneFrame) = RenderStackedScene(frame.node, frame.resolvedProps, frame.context)
}

internal fun partsTag(nodeId: String) = "$nodeId:parts"

internal fun popupTag(nodeId: String) = "$nodeId:popup"

internal fun legendTag(nodeId: String) = "$nodeId:legend"

internal fun legendSwatchTag(nodeId: String, index: Int) = "${legendTag(nodeId)}:swatch:$index"

internal fun rampBarTag(nodeId: String) = "${legendTag(nodeId)}:ramp"

private val NOTICE_INK = Color(0xFFB3261E)

@Composable
internal fun RenderStackedScene(node: SpecNode, resolvedProps: Map<String, Any?>, context: RenderContext) {
    val payload = resolvedProps["scene_view"]
    val reading = remember(payload) { runCatching { readSceneViewValue(payload) } }
    val scene = reading.getOrNull()
    if (scene == null) {
        StackedSceneNotice(node.id, "The stacked scene cannot be drawn: ${reading.exceptionOrNull()?.message}")
    } else {
        val stated = resolveStackedSceneProps(resolvedProps, node.id)
        val choice = sceneYAxis(stated.yAxis, scene.grid?.north)
        val props = stated.copy(yAxis = choice.axis, notes = stated.notes + listOfNotNull(choice.notice))
        val oriented = remember(scene, choice.axis) { sceneForYAxis(scene, choice.axis) }
        RenderReadStackedScene(node, oriented, props, resolvedProps["selected"], context)
    }
}

@Composable
private fun StackedSceneNotice(nodeId: String, message: String) {
    Box(modifier = Modifier.fillMaxSize().nodeTag(nodeId)) {
        Text(message, modifier = Modifier.padding(16.dp), color = NOTICE_INK)
    }
}

private fun partTapAction(action: String, partId: String) = ActionRef(action = action, params = mapOf("id" to SpecValue.StringValue(partId)))

private class PartTapTarget(
    val nodeId: String,
    val scene: SceneView,
    val parts: List<VisiblePart>,
    val drawing: StackedDrawing,
    val fit: CanvasFit,
    val quarterTurns: Int,
    val selection: Set<String>,
    val selectedPath: String?,
    val onPartTap: String?,
    val context: RenderContext,
    val scope: CoroutineScope,
    val keepLocalSelection: (Set<String>) -> Unit,
)

private fun applyPartTap(target: PartTapTarget, xPx: Double, yPx: Double) = tapPart(target, findTappedPart(target, xPx, yPx))

private fun tapPart(target: PartTapTarget, hit: VisiblePart?) {
    val nextSelection = selectionAfterTap(target.selection, hit?.footprint?.id)
    writeSelection(target, nextSelection)
    dispatchPartTap(target, hit)
}

private fun tapPartById(target: PartTapTarget, partId: String): Boolean {
    val part = target.parts.firstOrNull { it.footprint.id == partId } ?: return false
    tapPart(target, part)
    return true
}

private fun findTappedPart(target: PartTapTarget, xPx: Double, yPx: Double): VisiblePart? =
    hitDrawnPart(target.scene, target.parts, target.drawing, target.fit, xPx, yPx, target.quarterTurns)

private fun writeSelection(target: PartTapTarget, nextSelection: List<String>) {
    val path = target.selectedPath
    if (path != null) target.context.setBinding(path, nextSelection) else target.keepLocalSelection(nextSelection.toSet())
}

private fun dispatchPartTap(target: PartTapTarget, hit: VisiblePart?) {
    val action = target.onPartTap ?: return
    val partId = hit?.footprint?.id ?: return
    target.scope.launch { target.context.dispatch(target.nodeId, partTapAction(action, partId)) }
}

@Composable
private fun RenderReadStackedScene(node: SpecNode, scene: SceneView, props: StackedSceneProps, resolvedSelected: Any?, context: RenderContext) {
    BoxWithConstraints {
        RenderSizedStackedScene(node, scene, props, resolvedSelected, context, sceneSize(scene, props.drawing, node.props.asLayoutProps().height))
    }
}

@Composable
private fun RenderSizedStackedScene(
    node: SpecNode,
    scene: SceneView,
    props: StackedSceneProps,
    resolvedSelected: Any?,
    context: RenderContext,
    sizeModifier: Modifier,
) {
    val selectedPath = node.props["selected"]?.bindingPath()
    var localSelection by remember { mutableStateOf(resolveGeomapSelection(resolvedSelected)) }
    val selection = if (selectedPath != null) resolveGeomapSelection(resolvedSelected) else localSelection
    val framingPaths = framingPathsOf(node)
    var heldFraming by remember { mutableStateOf<SceneFraming?>(null) }
    val framing = shownFraming(SceneFraming(props.quarterTurns, props.camera), heldFraming, framingPaths)
    val framingInHand = remember { FramingInHand() }
    SideEffect {
        framingInHand.drawn = framing
        framingInHand.moved = null
    }
    val moveFraming: (SceneFraming) -> Unit = { next ->
        writeFraming(context, framingPaths, framingInHand.current(), next) { heldFraming = it }
        framingInHand.moved = next
    }
    val parts = remember(scene, props.drawing, props.focus, props.showLower, props.hideTopCount, props.highlightIds, framing.quarterTurns) {
        partsToDraw(scene, props.drawing, props.focus, props.showLower, props.hideTopCount, props.highlightIds, framing.quarterTurns)
    }
    val values = remember(scene, props.colourScale) { props.colourScale?.let { partValues(scene, it.channel) }.orEmpty() }
    val scope = rememberCoroutineScope()
    val palette = stackedPalette(LocalSpecTheme.current)
    Column(modifier = sizeModifier.nodeTag(node.id).clipToBounds()) {
        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
            val widthPx = constraints.maxWidth.toDouble()
            val heightPx = constraints.maxHeight.toDouble()
            val fit = remember(scene, props.drawing, widthPx, heightPx, framing) { fitDrawing(scene, props.drawing, widthPx, heightPx, framing.quarterTurns, framing.camera) }
            val picture = StackedPicture(scene, parts, props.drawing, fit, framing.quarterTurns, values, props.colourScale, props.highlightIds + selection)
            val notices = sceneNotices(scene, parts, props, fit)
            val popup = popupLines(scene, parts, selection, props, values)
            val tapTarget = PartTapTarget(node.id, scene, parts, props.drawing, fit, framing.quarterTurns, selection, selectedPath, props.onPartTap, context, scope) {
                localSelection = it
            }
            val latestTapTarget by rememberUpdatedState(tapTarget)
            RecordDrawn {
                this.scene = sceneRecord(scene, props, framing, notices, partReadings(picture, props.highlightIds, selection), popup)
                partTap = { partId -> tapPartById(tapTarget, partId) }
            }
            val pointer = if (framingPaths.isGestureEnabled) {
                Modifier.framingGestures(props.drawing == StackedDrawing.ISO, framingInHand, moveFraming) { offset -> applyPartTap(latestTapTarget, offset.x.toDouble(), offset.y.toDouble()) }
            } else {
                Modifier.pointerInput(parts, fit, selection, selectedPath, props.onPartTap) {
                    detectTapGestures(onTap = { offset -> applyPartTap(tapTarget, offset.x.toDouble(), offset.y.toDouble()) })
                }
            }
            Canvas(
                modifier = Modifier.fillMaxSize()
                    .clipToBounds()
                    .canvasPlacement()
                    .testTag(partsTag(node.id))
                    .semantics { stateDescription = drawnPartsSummary(parts, props.highlightIds, selection) }
                    .then(pointer),
            ) {
                drawStackedScene(picture, palette)
            }
            StackedSceneNotes(notices, Modifier.align(Alignment.TopStart))
            SelectedPartPopup(node.id, scene, parts, selection, props, fit, framing.quarterTurns, popup)
        }
        ColourLegend(node.id, props.colourScale, Modifier.fillMaxWidth())
    }
}

private val UNBOUNDED_SCENE_WIDTH = 480.dp

/** A scroll offers unbounded height; the scene then takes its own height, or else its window's shape, rather than collapsing to nothing. */
private fun BoxWithConstraintsScope.sceneSize(scene: SceneView, drawing: StackedDrawing, ownHeight: Float?): Modifier {
    if (constraints.hasBoundedHeight) return Modifier.fillMaxSize()
    val width = if (constraints.hasBoundedWidth) maxWidth else UNBOUNDED_SCENE_WIDTH
    val height = ownHeight?.dp ?: intrinsicSceneHeight(scene, drawing, width.value.toDouble()).dp
    return Modifier.width(width).height(height)
}

private fun sceneNotices(scene: SceneView, parts: List<VisiblePart>, props: StackedSceneProps, fit: CanvasFit): List<String> = buildList {
    addAll(props.notes)
    if (props.drawing != StackedDrawing.PLAN && isEveryPartFlat(scene, parts)) add("The scene states no part heights, so every part is drawn flat at its base.")
    if (parts.isEmpty()) add("No part of the scene is shown at this focus.")
    exaggerationNote(fit)?.let(::add)
}

private fun selectedPart(parts: List<VisiblePart>, selection: Set<String>): VisiblePart? {
    val selectedId = selection.singleOrNull() ?: return null
    return parts.firstOrNull { it.footprint.id == selectedId }
}

private fun popupLines(scene: SceneView, parts: List<VisiblePart>, selection: Set<String>, props: StackedSceneProps, values: Map<String, PartValue>): List<String>? {
    val part = selectedPart(parts, selection) ?: return null
    return partPopupLines(scene, part.footprint, partValueText(values[part.footprint.id], props.colourScale))
}

private fun focusText(focus: LevelFocus): String = when (focus) {
    LevelFocus.AllLevels -> "all"
    is LevelFocus.OneLevel -> focus.level.toString()
}

private fun sceneRecord(scene: SceneView, props: StackedSceneProps, framing: SceneFraming, notices: List<String>, parts: List<ScenePartRecord>, popup: List<String>?) = SceneRecord(
    view = props.drawing.wire,
    focus = focusText(props.focus),
    hideTop = props.hideTopCount,
    showLower = props.showLower,
    yAxis = (props.yAxis ?: YAxis.UP).wire,
    north = scene.grid?.north,
    activeScheme = props.activeScheme,
    quarterTurns = framing.quarterTurns,
    zoom = framing.camera.zoom,
    panX = framing.camera.panX,
    panY = framing.camera.panY,
    legend = legendReadings(props.colourScale),
    notices = notices,
    parts = parts,
    popup = popup?.joinToString(" / "),
)

@Composable
private fun StackedSceneNotes(notes: List<String>, modifier: Modifier) {
    if (notes.isEmpty()) return
    Column(modifier = modifier.padding(8.dp)) {
        notes.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = LocalSpecTheme.current.ink) }
    }
}

@Composable
private fun SelectedPartPopup(
    nodeId: String,
    scene: SceneView,
    parts: List<VisiblePart>,
    selection: Set<String>,
    props: StackedSceneProps,
    fit: CanvasFit,
    quarterTurns: Int,
    lines: List<String>?,
) {
    val part = selectedPart(parts, selection) ?: return
    val anchor = popupAnchor(scene, part.footprint, props.drawing, fit, quarterTurns)
    PartPopup(popupTag(nodeId), lines ?: return, anchor.first.toFloat(), anchor.second.toFloat())
}
