package ai.factoredui.compose.schema

enum class SwipeSide { LEFT, RIGHT }

data class SwipeActions(val left: ActionRef?, val right: ActionRef?)

fun swipeActionsOf(props: Map<String, SpecValue>): SwipeActions? {
    val fields = (props["on_swipe"] as? SpecValue.ObjectValue)?.value ?: return null
    return SwipeActions(actionRefOf(fields["left"]), actionRefOf(fields["right"])).takeIf { it.left != null || it.right != null }
}

fun swipeSideAfter(draggedPx: Float, thresholdPx: Float): SwipeSide? = when {
    draggedPx <= -thresholdPx -> SwipeSide.LEFT
    draggedPx >= thresholdPx -> SwipeSide.RIGHT
    else -> null
}
