package ai.factoredui.compose.layout

data class FlowView(val scale: Float, val translateX: Float, val translateY: Float)

const val MIN_FLOW_SCALE = 0.05f
const val MAX_FLOW_SCALE = 8f
const val DEFAULT_FIT_MAX_SCALE = 1f
const val DEFAULT_FIT_MARGIN = 8f

fun fitFlowView(
    contentWidth: Float,
    contentHeight: Float,
    viewWidth: Float,
    viewHeight: Float,
    maxScale: Float = DEFAULT_FIT_MAX_SCALE,
    margin: Float = DEFAULT_FIT_MARGIN,
): FlowView {
    if (contentWidth <= 0f || contentHeight <= 0f) return FlowView(1f, 0f, 0f)
    val scale = minOf((viewWidth - 2 * margin) / contentWidth, (viewHeight - 2 * margin) / contentHeight, maxScale)
        .coerceAtLeast(MIN_FLOW_SCALE)
    return FlowView(
        scale = scale,
        translateX = (viewWidth - contentWidth * scale) / 2f,
        translateY = (viewHeight - contentHeight * scale) / 2f,
    )
}

fun FlowView.afterGesture(centroidX: Float, centroidY: Float, panX: Float, panY: Float, zoomDelta: Float): FlowView {
    val nextScale = (scale * zoomDelta).coerceIn(MIN_FLOW_SCALE, MAX_FLOW_SCALE)
    val ratio = nextScale / scale
    return FlowView(
        scale = nextScale,
        translateX = centroidX - (centroidX - translateX) * ratio + panX,
        translateY = centroidY - (centroidY - translateY) * ratio + panY,
    )
}
