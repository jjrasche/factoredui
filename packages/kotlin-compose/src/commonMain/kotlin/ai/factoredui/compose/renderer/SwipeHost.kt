package ai.factoredui.compose.renderer

import ai.factoredui.compose.schema.SpecNode
import ai.factoredui.compose.schema.SwipeSide
import ai.factoredui.compose.schema.swipeActionsOf
import ai.factoredui.compose.schema.swipeSideAfter
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private const val SWIPE_STEP_DP = 48

@Composable
internal fun SwipeHost(node: SpecNode, context: RenderContext, content: @Composable () -> Unit) {
    val swipe = swipeActionsOf(node.props) ?: return content()
    val scope = rememberCoroutineScope()
    val stepPx = with(LocalDensity.current) { SWIPE_STEP_DP.dp.toPx() }
    Box(
        modifier = Modifier.pointerInput(swipe, stepPx) {
            var dragged = 0f
            detectHorizontalDragGestures(
                onDragStart = { dragged = 0f },
                onHorizontalDrag = { _, delta -> dragged += delta },
                onDragEnd = {
                    val action = when (swipeSideAfter(dragged, stepPx)) {
                        SwipeSide.LEFT -> swipe.left
                        SwipeSide.RIGHT -> swipe.right
                        null -> null
                    }
                    action?.let { scope.launch { context.dispatch(node.id, it) } }
                },
            )
        },
    ) { content() }
}