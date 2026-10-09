package ai.factoredui.compose.renderer

import ai.factoredui.compose.scene.STRUCTURE_CAPABILITY
import ai.factoredui.compose.scene.SceneView
import ai.factoredui.compose.scene.readSceneViewValue
import ai.factoredui.compose.schema.ActionRef
import ai.factoredui.compose.schema.SpecNode
import ai.factoredui.compose.schema.SpecValue
import ai.factoredui.compose.schema.StructureProps
import ai.factoredui.compose.schema.bindingPath
import ai.factoredui.compose.schema.resolveGeomapSelection
import ai.factoredui.compose.schema.resolveStructureProps
import ai.factoredui.compose.schema.selectionAfterTap
import ai.factoredui.compose.structure.CanvasFit
import ai.factoredui.compose.structure.StructureDrawing
import ai.factoredui.compose.structure.UtilisationFigure
import ai.factoredui.compose.structure.VisiblePart
import ai.factoredui.compose.structure.drawnPartsSummary
import ai.factoredui.compose.structure.fitDrawing
import ai.factoredui.compose.structure.hitDrawnPart
import ai.factoredui.compose.structure.isEveryPartFlat
import ai.factoredui.compose.structure.partPopupLines
import ai.factoredui.compose.structure.partsToDraw
import ai.factoredui.compose.structure.popupAnchor
import ai.factoredui.compose.structure.utilisationByPart
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import kotlinx.coroutines.launch

internal object StructureRenderer : SceneRenderer {
    override val capability = STRUCTURE_CAPABILITY

    @Composable
    override fun Draw(frame: SceneFrame) = RenderStructure(frame.node, frame.resolvedProps, frame.context)
}

internal fun partsTag(nodeId: String) = "$nodeId:parts"

internal fun popupTag(nodeId: String) = "$nodeId:popup"

private val NOTICE_INK = Color(0xFFB3261E)

@Composable
internal fun RenderStructure(node: SpecNode, resolvedProps: Map<String, Any?>, context: RenderContext) {
    val payload = resolvedProps["scene_view"]
    val reading = remember(payload) { runCatching { readSceneViewValue(payload) } }
    val scene = reading.getOrNull()
    if (scene == null) {
        StructureNotice(node.id, "The structure cannot be drawn: ${reading.exceptionOrNull()?.message}")
    } else {
        RenderReadStructure(node, scene, resolveStructureProps(resolvedProps), resolvedProps["selected"], context)
    }
}

@Composable
private fun StructureNotice(nodeId: String, message: String) {
    Box(modifier = Modifier.fillMaxSize().nodeTag(nodeId)) {
        Text(message, modifier = Modifier.padding(16.dp), color = NOTICE_INK)
    }
}

private fun partTapAction(action: String, partId: String) = ActionRef(action = action, params = mapOf("id" to SpecValue.StringValue(partId)))

@Composable
private fun RenderReadStructure(node: SpecNode, scene: SceneView, props: StructureProps, resolvedSelected: Any?, context: RenderContext) {
    val selectedPath = node.props["selected"]?.bindingPath()
    var localSelection by remember { mutableStateOf(resolveGeomapSelection(resolvedSelected)) }
    val selection = if (selectedPath != null) resolveGeomapSelection(resolvedSelected) else localSelection
    val parts = remember(scene, props) {
        partsToDraw(scene, props.drawing, props.focus, props.showLower, props.hideTopCount, props.highlightIds)
    }
    val figures = remember(scene) { utilisationByPart(scene) }
    val scope = rememberCoroutineScope()
    val palette = structurePalette(LocalSpecTheme.current)
    BoxWithConstraints(modifier = Modifier.fillMaxSize().nodeTag(node.id).clipToBounds()) {
        val widthPx = constraints.maxWidth.toDouble()
        val heightPx = constraints.maxHeight.toDouble()
        val fit = remember(scene, props.drawing, widthPx, heightPx) { fitDrawing(scene, props.drawing, widthPx, heightPx) }
        val outlined = props.highlightIds + selection
        Canvas(
            modifier = Modifier.fillMaxSize()
                .testTag(partsTag(node.id))
                .semantics { stateDescription = drawnPartsSummary(parts, props.highlightIds, selection) }
                .pointerInput(parts, fit, selection, selectedPath, props.onPartTap) {
                    val onPartTap = props.onPartTap
                    detectTapGestures(onTap = { offset ->
                        val hit = hitDrawnPart(scene, parts, props.drawing, fit, offset.x.toDouble(), offset.y.toDouble())
                        val nextSelection = selectionAfterTap(selection, hit?.footprint?.id)
                        if (selectedPath != null) context.setBinding(selectedPath, nextSelection)
                        else localSelection = nextSelection.toSet()
                        if (hit != null && onPartTap != null) {
                            scope.launch { context.dispatch(node.id, partTapAction(onPartTap, hit.footprint.id)) }
                        }
                    })
                },
        ) {
            drawStructure(StructurePicture(scene, parts, props.drawing, fit, figures, outlined), palette)
        }
        StructureNotes(scene, parts, props, Modifier.align(Alignment.TopStart))
        SelectedPartPopup(node.id, scene, parts, selection, props.drawing, fit, figures)
    }
}

@Composable
private fun StructureNotes(scene: SceneView, parts: List<VisiblePart>, props: StructureProps, modifier: Modifier) {
    val notes = buildList {
        addAll(props.notes)
        if (props.drawing != StructureDrawing.PLAN && isEveryPartFlat(scene, parts)) add("The scene states no part heights, so every part is drawn flat at its base.")
        if (parts.isEmpty()) add("No part of the scene is shown at this focus.")
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
    drawing: StructureDrawing,
    fit: CanvasFit,
    figures: Map<String, UtilisationFigure>,
) {
    val selectedId = selection.singleOrNull() ?: return
    val part = parts.firstOrNull { it.footprint.id == selectedId } ?: return
    val anchor = popupAnchor(scene, part.footprint, drawing, fit)
    PartPopup(popupTag(nodeId), partPopupLines(scene, part.footprint, figures[selectedId]), anchor.first.toFloat(), anchor.second.toFloat())
}
