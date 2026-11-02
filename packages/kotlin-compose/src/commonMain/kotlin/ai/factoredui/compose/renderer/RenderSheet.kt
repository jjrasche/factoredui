package ai.factoredui.compose.renderer

import ai.factoredui.compose.layout.SheetProps
import ai.factoredui.compose.layout.SheetStop
import ai.factoredui.compose.layout.readSheetProps
import ai.factoredui.compose.schema.SpecNode
import ai.factoredui.compose.schema.SpecValue
import ai.factoredui.compose.schema.bindingPath
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

private const val HANDLE_HEIGHT_DP = 44
private const val HANDLE_BAR_WIDTH_DP = 40
private const val HANDLE_BAR_HEIGHT_DP = 4
private const val DRAG_STEP_DP = 40
private const val FULL_FRACTION = 0.92f

private fun slotOf(child: SpecNode): String? = (child.props["slot"] as? SpecValue.StringValue)?.value

private fun SheetStop.opensSlot(slot: String?): Boolean = when (slot) {
    "peek" -> true
    "half" -> this != SheetStop.PEEK
    else -> this == SheetStop.FULL
}

private fun slotNotices(children: List<SpecNode>): List<String> =
    children.filter { slotOf(it) !in SheetStop.entries.map(SheetStop::wire) }
        .map { "sheet child '${it.id}' needs a slot of peek, half or full; it is shown at full." }

@Composable
internal fun RenderSheet(node: SpecNode, resolvedProps: Map<String, Any?>, context: RenderContext) {
    val props = readSheetProps(resolvedProps)
    val stopPath = node.props["stop"]?.bindingPath()
    var heldStop by remember { mutableStateOf(props.stop) }
    val stop = if (stopPath != null) props.stop else heldStop
    val peekPath = node.props["peek_to"]?.bindingPath()
    LaunchedEffect(peekPath, props.peekDp) { peekPath?.let { context.setBinding(it, props.peekDp) } }
    val move = { next: SheetStop -> if (stopPath != null) context.setBinding(stopPath, next.wire) else heldStop = next }
    RecordDrawn {
        shownProps = mapOf("stop" to stop.wire)
        notices = props.notices + slotNotices(node.children)
    }
    Box(modifier = Modifier.fillMaxSize().nodeTag(node.id)) {
        BottomSheet(node, props, stop, move, context)
    }
}

@Composable
private fun SheetSlots(node: SpecNode, context: RenderContext, shows: (String?) -> Boolean) {
    val theme = LocalSpecTheme.current
    Column(verticalArrangement = Arrangement.spacedBy(theme.space.md.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = theme.space.gutter.dp)) {
        node.children.filter { shows(slotOf(it)) }.forEach { RenderNode(node = it, context = context) }
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.BottomSheet(node: SpecNode, props: SheetProps, stop: SheetStop, move: (SheetStop) -> Unit, context: RenderContext) {
    val theme = LocalSpecTheme.current
    BoxWithConstraints(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
        val shownHeight = when (stop) {
            SheetStop.PEEK -> props.peekDp.dp
            SheetStop.HALF -> props.halfDp.dp
            SheetStop.FULL -> maxHeight * FULL_FRACTION
        }
        Column(modifier = Modifier.fillMaxWidth().height(shownHeight).clip(RoundedCornerShape(topStart = theme.radius.xl.dp, topEnd = theme.radius.xl.dp)).background(props.background?.let { theme.tokenColor(it) ?: it.parseColor() } ?: theme.raised)) {
            SheetHandle(node.id, stop, move)
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) { SheetSlots(node, context, stop::opensSlot) }
        }
    }
}

@Composable
private fun SheetHandle(sheetId: String, stop: SheetStop, move: (SheetStop) -> Unit) {
    val theme = LocalSpecTheme.current
    val dragStepPx = with(LocalDensity.current) { DRAG_STEP_DP.dp.toPx() }
    val after = if (stop == SheetStop.FULL) SheetStop.PEEK else stop.up()
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxWidth().height(HANDLE_HEIGHT_DP.dp).nodeTag("$sheetId/handle")
            .pointerInput(stop) { detectTapGestures { move(after) } }
            .pointerInput(stop) {
                var dragged = 0f
                detectVerticalDragGestures(
                    onDragStart = { dragged = 0f },
                    onVerticalDrag = { _, delta -> dragged += delta },
                    onDragEnd = {
                        if (dragged <= -dragStepPx) move(stop.up()) else if (dragged >= dragStepPx) move(stop.down())
                    },
                )
            },
    ) {
        Box(modifier = Modifier.width(HANDLE_BAR_WIDTH_DP.dp).height(HANDLE_BAR_HEIGHT_DP.dp).clip(RoundedCornerShape(theme.radius.sm.dp)).background(theme.muted))
    }
}
