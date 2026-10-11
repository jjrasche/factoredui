package ai.factoredui.compose.renderer

import ai.factoredui.compose.scene.STACKED_SCENE_CAPABILITY
import ai.factoredui.compose.scene.SceneView
import ai.factoredui.compose.scene.readSceneViewValue
import ai.factoredui.compose.schema.ActionRef
import ai.factoredui.compose.schema.SpecNode
import ai.factoredui.compose.schema.SpecValue
import ai.factoredui.compose.schema.StackedSceneProps
import ai.factoredui.compose.schema.bindingPath
import ai.factoredui.compose.schema.resolveGeomapSelection
import ai.factoredui.compose.schema.resolveStackedSceneProps
import ai.factoredui.compose.schema.selectionAfterTap
import ai.factoredui.compose.stackedscene.CanvasFit
import ai.factoredui.compose.stackedscene.PartValue
import ai.factoredui.compose.stackedscene.StackedDrawing
import ai.factoredui.compose.stackedscene.VisiblePart
import ai.factoredui.compose.stackedscene.drawnPartsSummary
import ai.factoredui.compose.stackedscene.exaggerationNote
import ai.factoredui.compose.stackedscene.fitDrawing
import ai.factoredui.compose.stackedscene.hitDrawnPart
import ai.factoredui.compose.stackedscene.intrinsicSceneHeight
import ai.factoredui.compose.stackedscene.isEveryPartFlat
import ai.factoredui.compose.stackedscene.legendEntries
import ai.factoredui.compose.stackedscene.partPopupLines
import ai.factoredui.compose.stackedscene.partValueText
import ai.factoredui.compose.stackedscene.partValues
import ai.factoredui.compose.stackedscene.partsToDraw
import ai.factoredui.compose.stackedscene.popupAnchor
import ai.factoredui.compose.stackedscene.sceneForYAxis
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
import androidx.compose.runtime.remember
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

private val NOTICE_INK = Color(0xFFB3261E)

@Composable
internal fun RenderStackedScene(node: SpecNode, resolvedProps: Map<String, Any?>, context: RenderContext) {
    val payload = resolvedProps["scene_view"]
    val reading = remember(payload) { runCatching { readSceneViewValue(payload) } }
    val scene = reading.getOrNull()
    if (scene == null) {
        StackedSceneNotice(node.id, "The stacked scene cannot be drawn: ${reading.exceptionOrNull()?.message}")
    } else {
        val props = resolveStackedSceneProps(resolvedProps)
        val oriented = remember(scene, props.yAxis) { sceneForYAxis(scene, props.yAxis) }
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
    val selection: Set<String>,
    val selectedPath: String?,
    val onPartTap: String?,
    val context: RenderContext,
    val scope: CoroutineScope,
    val keepLocalSelection: (Set<String>) -> Unit,
)

private fun applyPartTap(target: PartTapTarget, xPx: Double, yPx: Double) {
    val hit = findTappedPart(target, xPx, yPx)
    val nextSelection = selectionAfterTap(target.selection, hit?.footprint?.id)
    writeSelection(target, nextSelection)
    dispatchPartTap(target, hit)
}

private fun findTappedPart(target: PartTapTarget, xPx: Double, yPx: Double): VisiblePart? =
    hitDrawnPart(target.scene, target.parts, target.drawing, target.fit, xPx, yPx)

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
        RenderSizedStackedScene(node, scene, props, resolvedSelected, context, sceneSize(scene, props.drawing))
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
    val parts = remember(scene, props) {
        partsToDraw(scene, props.drawing, props.focus, props.showLower, props.hideTopCount, props.highlightIds)
    }
    val values = remember(scene, props.colourScale) { props.colourScale?.let { partValues(scene, it.channel) }.orEmpty() }
    val scope = rememberCoroutineScope()
    val palette = stackedPalette(LocalSpecTheme.current)
    Column(modifier = sizeModifier.nodeTag(node.id).clipToBounds()) {
        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
            val widthPx = constraints.maxWidth.toDouble()
            val heightPx = constraints.maxHeight.toDouble()
            val fit = remember(scene, props.drawing, widthPx, heightPx) { fitDrawing(scene, props.drawing, widthPx, heightPx) }
            val outlined = props.highlightIds + selection
            Canvas(
                modifier = Modifier.fillMaxSize()
                    .clipToBounds()
                    .testTag(partsTag(node.id))
                    .semantics { stateDescription = drawnPartsSummary(parts, props.highlightIds, selection) }
                    .pointerInput(parts, fit, selection, selectedPath, props.onPartTap) {
                        val target = PartTapTarget(node.id, scene, parts, props.drawing, fit, selection, selectedPath, props.onPartTap, context, scope) {
                            localSelection = it
                        }
                        detectTapGestures(onTap = { offset -> applyPartTap(target, offset.x.toDouble(), offset.y.toDouble()) })
                    },
            ) {
                drawStackedScene(StackedPicture(scene, parts, props.drawing, fit, values, props.colourScale, outlined), palette)
            }
            StackedSceneNotes(scene, parts, props, fit, Modifier.align(Alignment.TopStart))
            SelectedPartPopup(node.id, scene, parts, selection, props, fit, values)
        }
        ColourLegend(node.id, legendEntries(props.colourScale), Modifier.fillMaxWidth())
    }
}

private val UNBOUNDED_SCENE_WIDTH = 480.dp

/** A scroll offers unbounded height; the scene then takes its window's shape rather than collapsing to nothing. */
private fun BoxWithConstraintsScope.sceneSize(scene: SceneView, drawing: StackedDrawing): Modifier {
    if (constraints.hasBoundedHeight) return Modifier.fillMaxSize()
    val width = if (constraints.hasBoundedWidth) maxWidth else UNBOUNDED_SCENE_WIDTH
    return Modifier.width(width).height(intrinsicSceneHeight(scene, drawing, width.value.toDouble()).dp)
}

@Composable
private fun StackedSceneNotes(scene: SceneView, parts: List<VisiblePart>, props: StackedSceneProps, fit: CanvasFit, modifier: Modifier) {
    val notes = buildList {
        addAll(props.notes)
        if (props.drawing != StackedDrawing.PLAN && isEveryPartFlat(scene, parts)) add("The scene states no part heights, so every part is drawn flat at its base.")
        if (parts.isEmpty()) add("No part of the scene is shown at this focus.")
        exaggerationNote(fit)?.let(::add)
    }
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
    values: Map<String, PartValue>,
) {
    val selectedId = selection.singleOrNull() ?: return
    val part = parts.firstOrNull { it.footprint.id == selectedId } ?: return
    val anchor = popupAnchor(scene, part.footprint, props.drawing, fit)
    val valueText = partValueText(values[selectedId], props.colourScale)
    PartPopup(popupTag(nodeId), partPopupLines(scene, part.footprint, valueText), anchor.first.toFloat(), anchor.second.toFloat())
}
