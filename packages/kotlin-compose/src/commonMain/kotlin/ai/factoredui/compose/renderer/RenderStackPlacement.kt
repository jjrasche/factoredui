package ai.factoredui.compose.renderer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import ai.factoredui.compose.schema.SheetProps
import ai.factoredui.compose.schema.SpecNode
import ai.factoredui.compose.schema.StackAnchor
import ai.factoredui.compose.schema.asLayoutProps
import ai.factoredui.compose.schema.asStackPlacement

private const val SHEET_HANDLE_DP = 28
private const val SHEET_GRIP_WIDTH_DP = 44
private const val SHEET_GRIP_HEIGHT_DP = 4

@Composable
internal fun BoxScope.RenderStackChild(child: SpecNode, context: RenderContext) {
    val placement = child.props.asStackPlacement()
    val sheet = placement.sheet
    when {
        sheet != null -> RenderSheet(child, sheet, context)
        placement.anchor == StackAnchor.FILL -> Box(modifier = Modifier.fillMaxSize()) { RenderSizedChild(child, context) }
        placement.anchor == StackAnchor.TOP -> Box(modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth()) { RenderSizedChild(child, context) }
        placement.anchor == StackAnchor.BOTTOM -> Box(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()) { RenderSizedChild(child, context) }
        else -> RenderSizedChild(child, context)
    }
}

private fun nearerEnd(height: Float, sheet: SheetProps): Float =
    if (height - sheet.closedDp < sheet.openDp - height) sheet.closedDp else sheet.openDp

/** A bottom panel over the stack: its first child stays above the scrolling rest, a drag on the handle sets its height between the two the sheet names, and a tap on the handle flips it. */
@Composable
private fun BoxScope.RenderSheet(child: SpecNode, sheet: SheetProps, context: RenderContext) {
    val theme = LocalSpecTheme.current
    var heightDp by remember(child.id) { mutableStateOf(sheet.closedDp) }
    val layout = child.props.asLayoutProps()
    val ground = layout.background?.let { theme.tokenColor(it) ?: planColour(it) }
    val panel = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(heightDp.dp).clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
    Column(modifier = (if (ground != null) panel.background(ground) else panel).nodeTag(child.id)) {
        Box(
            modifier = Modifier.fillMaxWidth().height(SHEET_HANDLE_DP.dp)
                .pointerInput(sheet) {
                    detectVerticalDragGestures(
                        onDragEnd = { heightDp = nearerEnd(heightDp, sheet) },
                        onVerticalDrag = { change, delta ->
                            change.consume()
                            heightDp = (heightDp - delta / density).coerceIn(sheet.closedDp, sheet.openDp)
                        },
                    )
                }
                .clickable { heightDp = if (heightDp <= sheet.closedDp) sheet.openDp else sheet.closedDp }
                .nodeTag("${child.id}/handle"),
            contentAlignment = Alignment.Center,
        ) {
            Box(modifier = Modifier.width(SHEET_GRIP_WIDTH_DP.dp).height(SHEET_GRIP_HEIGHT_DP.dp).clip(RoundedCornerShape(2.dp)).background(theme.muted))
        }
        child.children.firstOrNull()?.let { RenderNode(node = it, context = context) }
        Column(modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(layout.padding.dp)) {
            child.children.drop(1).forEach { RenderNode(node = it, context = context) }
        }
    }
}
