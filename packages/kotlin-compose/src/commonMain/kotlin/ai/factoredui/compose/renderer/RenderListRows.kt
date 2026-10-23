package ai.factoredui.compose.renderer

import ai.factoredui.compose.schema.BindingResolver
import ai.factoredui.compose.schema.SpecNode
import ai.factoredui.compose.schema.bindingPath
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

private const val SWIPE_STEP_DP = 48
private const val CHIP_GAP_DP = 8

internal fun stepAfterSwipe(current: Int, draggedPx: Float, stepPx: Float, rowCount: Int): Int = when {
    rowCount <= 0 -> current
    draggedPx <= -stepPx -> minOf(current + 1, rowCount - 1)
    draggedPx >= stepPx -> maxOf(current - 1, 0)
    else -> current
}

/** Rows side by side; with step bound, a swipe steps the number along them and the row at it is brought into view. */
@Composable
internal fun HorizontalRows(node: SpecNode, rows: List<Any>, itemTemplate: SpecNode, context: RenderContext, listPath: String) {
    val stepPath = node.props["step"]?.bindingPath()
    val step = stepPath?.let { (BindingResolver.resolveBinding("{$it}", context.data) as? Number)?.toInt() }
    val state = rememberLazyListState()
    val stepPx = with(LocalDensity.current) { SWIPE_STEP_DP.dp.toPx() }
    LaunchedEffect(step, rows.size) {
        if (step != null && step in rows.indices) state.animateScrollToItem(step)
    }
    val swipe = if (stepPath == null) Modifier else Modifier.pointerInput(rows.size, step) {
        var dragged = 0f
        detectHorizontalDragGestures(
            onDragStart = { dragged = 0f },
            onHorizontalDrag = { _, delta -> dragged += delta },
            onDragEnd = { context.setBinding(stepPath, stepAfterSwipe(step ?: 0, dragged, stepPx, rows.size)) },
        )
    }
    LazyRow(state = state, userScrollEnabled = stepPath == null, horizontalArrangement = Arrangement.spacedBy(CHIP_GAP_DP.dp), modifier = swipe) {
        itemsIndexed(rows) { index, row ->
            CompositionLocalProvider(LocalPathPrefix provides "$listPath/$index/") {
                RenderNode(node = itemTemplate, context = context.withAdditionalData(mapOf("row" to row, "item" to row)))
            }
        }
    }
}
