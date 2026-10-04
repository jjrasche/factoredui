package ai.factoredui.compose.renderer

import ai.factoredui.compose.layout.FlowEdgeRoute
import ai.factoredui.compose.layout.FlowEdgeSpec
import ai.factoredui.compose.layout.FlowLayout
import ai.factoredui.compose.layout.FlowNodeBox
import ai.factoredui.compose.layout.FlowNodeSpec
import ai.factoredui.compose.layout.FlowView
import ai.factoredui.compose.layout.afterGesture
import ai.factoredui.compose.layout.fitFlowView
import ai.factoredui.compose.layout.layoutFlowGraph
import ai.factoredui.compose.schema.ActionRef
import ai.factoredui.compose.schema.GraphEdgeEntry
import ai.factoredui.compose.schema.GraphNodeEntry
import ai.factoredui.compose.schema.GraphNodeShape
import ai.factoredui.compose.schema.SpecNode
import ai.factoredui.compose.schema.SpecValue
import ai.factoredui.compose.schema.assignGraphColors
import ai.factoredui.compose.schema.asGraphProps
import ai.factoredui.compose.schema.bindingPath
import ai.factoredui.compose.schema.resolveGraphColorMap
import ai.factoredui.compose.schema.resolveGraphEdges
import ai.factoredui.compose.schema.resolveGraphGroupOrder
import ai.factoredui.compose.schema.resolveGraphNodes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.exp
import kotlinx.coroutines.launch

private val NODE_TEXT = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium)
private val LANE_TEXT = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold)
private const val NODE_PAD_X_DP = 10f
private const val NODE_PAD_Y_DP = 6f
private const val NODE_WIDTH_SLACK_DP = 2f
private const val MAX_NODE_WIDTH_DP = 190f
private const val LANE_GUTTER_DP = 24f
private const val LANE_TINT_ALPHA = 0.09f
private const val LANE_CORNER_DP = 8f
private const val EDGE_WIDTH_DP = 1.6f
private const val HIGHLIGHT_EDGE_WIDTH_DP = 2.6f
private const val ARROW_LENGTH_DP = 9f
private const val ARROW_HALF_WIDTH_DP = 4.5f
private const val DIMMED_EDGE_ALPHA = 0.14f
private const val DIMMED_NODE_ALPHA = 0.3f
private const val WHEEL_ZOOM_RATE = 0.12f
private val UNSTATED_FILL = Color(0xFFE3E6EE)

@Composable
internal fun RenderGraph(node: SpecNode, resolvedProps: Map<String, Any?>, context: RenderContext) {
    val props = node.props.asGraphProps()
    val entries = resolveGraphNodes(resolvedProps["nodes"])
    val edgeEntries = resolveGraphEdges(resolvedProps["edges"])
    val groupOrder = resolveGraphGroupOrder(resolvedProps["group_order"])
    val statusColors = assignGraphColors(
        entries.mapNotNull { it.status } + edgeEntries.mapNotNull { it.status },
        resolveGraphColorMap(resolvedProps["status_colors"]),
    )
    val groupColors = assignGraphColors(entries.mapNotNull { it.group }, resolveGraphColorMap(resolvedProps["group_colors"]))
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val theme = LocalSpecTheme.current
    val hasLanes = entries.any { it.group != null }
    val layout = remember(entries, edgeEntries, groupOrder, density.density) {
        layoutFlowGraph(
            nodes = entries.map { measuredSpec(it, measurer, density) },
            edges = edgeEntries.map { FlowEdgeSpec(it.from, it.to) },
            groupOrder = groupOrder,
            gutter = if (hasLanes) LANE_GUTTER_DP else 0f,
        )
    }
    val edgeStyles = remember(edgeEntries) { edgeEntries.distinctBy { it.from to it.to }.associateBy { it.from to it.to } }
    val selectedPath = node.props["selected"]?.bindingPath()
    var localSelected by remember { mutableStateOf<String?>(null) }
    val selected = if (selectedPath != null) resolvedProps["selected"] as? String else localSelected
    val focus = remember(selected, layout) { focusOf(selected, layout) }
    val scope = rememberCoroutineScope()

    fun select(id: String?) {
        if (selectedPath != null) context.setBinding(selectedPath, id) else localSelected = id
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().nodeTag(node.id).clipToBounds()) {
        val viewWidthPx = constraints.maxWidth.toFloat()
        val viewHeightPx = constraints.maxHeight.toFloat()
        val fit = remember(layout, viewWidthPx, viewHeightPx, density.density) {
            fitFlowView(layout.width * density.density, layout.height * density.density, viewWidthPx, viewHeightPx)
        }
        var gestureView by remember(layout, viewWidthPx, viewHeightPx) { mutableStateOf<FlowView?>(null) }
        val view = gestureView ?: fit
        Box(
            modifier = Modifier.fillMaxSize()
                .pointerInput(selected) { detectTapGestures(onTap = { select(null) }) }
                .pointerInput(fit) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        gestureView = (gestureView ?: fit).afterGesture(centroid.x, centroid.y, pan.x, pan.y, zoom)
                    }
                }
                .pointerInput(fit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type != PointerEventType.Scroll) continue
                            val change = event.changes.first()
                            val zoom = exp(-change.scrollDelta.y * WHEEL_ZOOM_RATE)
                            gestureView = (gestureView ?: fit).afterGesture(change.position.x, change.position.y, 0f, 0f, zoom)
                            change.consume()
                        }
                    }
                },
        ) {
            Box(
                modifier = Modifier.wrapContentSize(Alignment.TopStart, unbounded = true).size(layout.width.dp, layout.height.dp).graphicsLayer {
                    transformOrigin = TransformOrigin(0f, 0f)
                    scaleX = view.scale
                    scaleY = view.scale
                    translationX = view.translateX
                    translationY = view.translateY
                },
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawLanes(layout, groupColors, measurer, theme.muted)
                    drawEdges(layout, edgeStyles, statusColors, theme.muted, focus)
                }
                for (entry in entries) {
                    val box = layout.nodes[entry.id] ?: continue
                    GraphNodeBox(
                        entry = entry,
                        box = box,
                        fill = nodeFill(entry, statusColors),
                        border = entry.group?.let { colorOf(groupColors[it], theme.muted) } ?: theme.muted,
                        isSelected = entry.id == selected,
                        alpha = if (focus == null || entry.id in focus.nodes) 1f else DIMMED_NODE_ALPHA,
                        onTap = {
                            select(if (entry.id == selected) null else entry.id)
                            val onNodeTapped = props.onNodeTapped
                            if (onNodeTapped != null) scope.launch { context.dispatch(node.id, nodeTappedAction(onNodeTapped, entry.id)) }
                        },
                    )
                }
            }
        }
    }
}

private class GraphFocus(val nodes: Set<String>, val edges: Set<Pair<String, String>>)

private fun focusOf(selected: String?, layout: FlowLayout): GraphFocus? {
    if (selected == null || selected !in layout.nodes) return null
    val incident = layout.edges.filter { it.from == selected || it.to == selected }
    return GraphFocus(
        nodes = setOf(selected) + incident.flatMap { listOf(it.from, it.to) },
        edges = incident.map { it.from to it.to }.toSet(),
    )
}

private fun measuredSpec(entry: GraphNodeEntry, measurer: TextMeasurer, density: Density): FlowNodeSpec {
    val padX = NODE_PAD_X_DP * density.density
    val maxTextWidthPx = ((MAX_NODE_WIDTH_DP * density.density) - 2 * padX).toInt().coerceAtLeast(1)
    val measured = measurer.measure(
        text = AnnotatedString(entry.label),
        style = NODE_TEXT,
        overflow = TextOverflow.Ellipsis,
        softWrap = false,
        maxLines = 1,
        constraints = Constraints(maxWidth = maxTextWidthPx),
    ).size
    return FlowNodeSpec(
        id = entry.id,
        width = measured.width / density.density + 2 * NODE_PAD_X_DP + NODE_WIDTH_SLACK_DP,
        height = measured.height / density.density + 2 * NODE_PAD_Y_DP,
        group = entry.group,
        rank = entry.rank,
        pinnedX = entry.x,
        pinnedY = entry.y,
    )
}

private fun colorOf(hex: String?, fallback: Color): Color = parseGeomapColor(hex)?.let { Color(it) } ?: fallback

private fun nodeFill(entry: GraphNodeEntry, statusColors: Map<String, String>): Color =
    colorOf(entry.color ?: entry.status?.let { statusColors[it] }, UNSTATED_FILL)

private fun readableOn(fill: Color, ink: Color): Color =
    if (fill.luminance() < 0.42f) Color.White else ink

private fun Color.luminance(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue

@Composable
private fun GraphNodeBox(
    entry: GraphNodeEntry,
    box: FlowNodeBox,
    fill: Color,
    border: Color,
    isSelected: Boolean,
    alpha: Float,
    onTap: () -> Unit,
) {
    val theme = LocalSpecTheme.current
    val shape = if (entry.shape == GraphNodeShape.PILL) RoundedCornerShape(50) else RoundedCornerShape(5.dp)
    Box(
        modifier = Modifier
            .offset(box.x.dp, box.y.dp)
            .size(box.width.dp, box.height.dp)
            .alpha(alpha)
            .nodeTag(entry.id)
            .clip(shape)
            .background(fill)
            .border(if (isSelected) 2.5.dp else 1.dp, if (isSelected) theme.ink else border, shape)
            .pointerInput(entry.id) { detectTapGestures(onTap = { onTap() }) },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = entry.label,
            style = NODE_TEXT.copy(color = readableOn(fill, theme.ink)),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun nodeTappedAction(action: String, nodeId: String) = ActionRef(
    action = action,
    params = mapOf("node_id" to SpecValue.StringValue(nodeId)),
)

private fun DrawScope.drawLanes(layout: FlowLayout, groupColors: Map<String, String>, measurer: TextMeasurer, labelColor: Color) {
    for (lane in layout.lanes) {
        val group = lane.group ?: continue
        val tint = colorOf(groupColors[group], labelColor)
        val topLeft = Offset(0f, lane.top * density)
        val size = Size(layout.width * density, lane.height * density)
        drawRoundRect(tint.copy(alpha = LANE_TINT_ALPHA), topLeft, size, CornerRadius(LANE_CORNER_DP * density), style = Fill)
        val label = measurer.measure(
            text = AnnotatedString(group),
            style = LANE_TEXT.copy(color = tint),
            overflow = TextOverflow.Ellipsis,
            softWrap = false,
            maxLines = 1,
            constraints = Constraints(maxWidth = (size.height - 8f * density).toInt().coerceAtLeast(1)),
        )
        val center = Offset((LANE_GUTTER_DP / 2f + 10f) * density, topLeft.y + size.height / 2f)
        rotate(-90f, pivot = center) {
            drawText(label, topLeft = Offset(center.x - label.size.width / 2f, center.y - label.size.height / 2f))
        }
    }
}

private fun DrawScope.drawEdges(
    layout: FlowLayout,
    styles: Map<Pair<String, String>, GraphEdgeEntry>,
    statusColors: Map<String, String>,
    fallback: Color,
    focus: GraphFocus?,
) {
    val (lit, rest) = layout.edges.partition { focus != null && (it.from to it.to) in focus.edges }
    for (route in rest) drawEdge(route, styles[route.from to route.to], statusColors, fallback, focus != null, highlighted = false)
    for (route in lit) drawEdge(route, styles[route.from to route.to], statusColors, fallback, true, highlighted = true)
}

private fun DrawScope.drawEdge(
    route: FlowEdgeRoute,
    style: GraphEdgeEntry?,
    statusColors: Map<String, String>,
    fallback: Color,
    isFocusActive: Boolean,
    highlighted: Boolean,
) {
    val color = colorOf(style?.color ?: style?.status?.let { statusColors[it] }, fallback)
        .copy(alpha = if (isFocusActive && !highlighted) DIMMED_EDGE_ALPHA else 1f)
    val dashed = style?.dash == true || route.isBackEdge
    val points = route.points.map { Offset(it.x * density, it.y * density) }
    if (points.size < 2) return
    val tip = points.last()
    val travel = if (tip.x >= points[points.size - 2].x) 1f else -1f
    val arrowBase = Offset(tip.x - travel * ARROW_LENGTH_DP * density, tip.y)
    val path = Path().apply {
        moveTo(points.first().x, points.first().y)
        val bent = points.dropLast(1) + arrowBase
        bent.zipWithNext().forEach { (from, to) ->
            val midX = (from.x + to.x) / 2f
            cubicTo(midX, from.y, midX, to.y, to.x, to.y)
        }
    }
    val width = (if (highlighted) HIGHLIGHT_EDGE_WIDTH_DP else EDGE_WIDTH_DP) * density
    drawPath(
        path = path,
        color = color,
        style = Stroke(width = width, pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(10f * density, 6f * density)) else null),
    )
    val head = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(arrowBase.x, arrowBase.y - ARROW_HALF_WIDTH_DP * density)
        lineTo(arrowBase.x, arrowBase.y + ARROW_HALF_WIDTH_DP * density)
        close()
    }
    drawPath(head, color, style = Fill)
}
