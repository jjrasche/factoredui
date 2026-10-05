package ai.factoredui.compose.renderer

import ai.factoredui.compose.layout.FlowEdgeRoute
import ai.factoredui.compose.layout.FlowEdgeSpec
import ai.factoredui.compose.layout.FlowLayout
import ai.factoredui.compose.layout.FlowNodeBox
import ai.factoredui.compose.layout.FlowNodeSpec
import ai.factoredui.compose.layout.FlowPoint
import ai.factoredui.compose.layout.FlowView
import ai.factoredui.compose.layout.afterGesture
import ai.factoredui.compose.layout.fitFlowView
import ai.factoredui.compose.layout.flowRouteMidpoint
import ai.factoredui.compose.layout.hitTestFlowEdge
import ai.factoredui.compose.layout.layoutFlowGraph
import ai.factoredui.compose.schema.ActionRef
import ai.factoredui.compose.schema.GraphEdgeEntry
import ai.factoredui.compose.schema.GraphKindStyle
import ai.factoredui.compose.schema.GraphLegendEntry
import ai.factoredui.compose.schema.GraphNodeEntry
import ai.factoredui.compose.schema.GraphNodeShape
import ai.factoredui.compose.schema.SpecNode
import ai.factoredui.compose.schema.SpecValue
import ai.factoredui.compose.schema.asGraphProps
import ai.factoredui.compose.schema.assignGraphColors
import ai.factoredui.compose.schema.bindingPath
import ai.factoredui.compose.schema.resolveGraphEdges
import ai.factoredui.compose.schema.resolveGraphGroupOrder
import ai.factoredui.compose.schema.resolveGraphKindStyles
import ai.factoredui.compose.schema.resolveGraphLegend
import ai.factoredui.compose.schema.resolveGraphNodes
import ai.factoredui.compose.schema.resolveGraphStringMap
import ai.factoredui.compose.schema.resolveGraphZoom
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
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
import androidx.compose.ui.graphics.lerp
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
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlinx.coroutines.launch

private val NODE_TEXT = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium)
private val COMPACT_TEXT = TextStyle(fontSize = 10.sp, lineHeight = 13.sp, fontWeight = FontWeight.Medium)
private val LANE_TEXT = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold)
private val LEGEND_TEXT = TextStyle(fontSize = 11.sp, lineHeight = 14.sp)
private const val NODE_PAD_X_DP = 10f
private const val NODE_PAD_Y_DP = 6f
private const val COMPACT_PAD_X_DP = 6f
private const val COMPACT_PAD_Y_DP = 3f
private const val NODE_WIDTH_SLACK_DP = 2f
private const val MAX_NODE_WIDTH_DP = 190f
private const val DEFAULT_BORDER_DP = 1f
private const val SELECTED_BORDER_DP = 2.5f
private const val DASH_DP = 4f
private const val DASHED_OUTLINE_MIN_DP = 1.5f
private const val MUTED_BLEND = 0.55f
private const val PARKED_ALPHA = 0.75f
private const val LANE_GUTTER_DP = 24f
private const val LANE_TINT_ALPHA = 0.09f
private const val LANE_CORNER_DP = 8f
private const val NODE_CORNER_DP = 5f
private const val EDGE_WIDTH_DP = 1.2f
private const val HIGHLIGHT_EDGE_WIDTH_DP = 2.6f
private const val RESTING_EDGE_ALPHA = 0.55f
private const val DIMMED_EDGE_ALPHA = 0.1f
private const val BACK_EDGE_FADE = 0.6f
private const val ARROW_LENGTH_DP = 8f
private const val ARROW_HALF_WIDTH_DP = 4f
private const val DIMMED_NODE_ALPHA = 0.3f
private const val WHEEL_ZOOM_RATE = 0.12f
private const val BADGE_DP = 14f
private const val BADGE_STROKE_DP = 1.5f
private const val SELF_LOOP_START_DEGREES = 40.0
private const val SELF_LOOP_END_DEGREES = 320.0
private const val EDGE_HIT_TOLERANCE_DP = 8f
private const val MARKER_RADIUS_DP = 3.2f
private const val LEGEND_NODE_W_DP = 24f
private const val LEGEND_NODE_H_DP = 14f
private const val LEGEND_COMPACT_W_DP = 18f
private const val LEGEND_COMPACT_H_DP = 10f
private const val LEGEND_EDGE_W_DP = 30f
private val UNSTATED_FILL = Color(0xFFE3E6EE)
private val UNSTATED_FILL_DARK = Color(0xFF3A4150)
private val TEXT_ON_LIGHT = Color(0xFF1A1A1A)
private const val WHITE_TEXT_BELOW = 0.5f
private const val LANE_TINT_ALPHA_DARK = 0.16f
private const val LANE_LABEL_LIFT_DARK = 0.4f
private const val RESTING_EDGE_ALPHA_DARK = 0.75f
private const val DIMMED_EDGE_ALPHA_DARK = 0.22f

private class NodeLook(
    val shape: GraphNodeShape,
    val compact: Boolean,
    val muted: Boolean,
    val borderDp: Float,
    val dashedOutline: Boolean,
) {
    val text: TextStyle get() = if (compact) COMPACT_TEXT else NODE_TEXT
}

private fun lookOf(entry: GraphNodeEntry, kindStyles: Map<String, GraphKindStyle>, statusOutlines: Map<String, String>): NodeLook {
    val kind = entry.kind?.let { kindStyles[it] } ?: GraphKindStyle()
    return NodeLook(
        shape = entry.shape ?: kind.shape ?: GraphNodeShape.BOX,
        compact = kind.compact,
        muted = kind.muted,
        borderDp = kind.borderWidth ?: DEFAULT_BORDER_DP,
        dashedOutline = entry.status?.let { statusOutlines[it] } == "dashed",
    )
}

private class GraphFocus(val nodes: Set<String>, val edges: Set<Pair<String, String>>)

private fun focusOf(selectedNode: String?, selectedEdge: Pair<String, String>?, layout: FlowLayout): GraphFocus? {
    if (selectedEdge != null && layout.edges.any { it.from == selectedEdge.first && it.to == selectedEdge.second }) {
        return GraphFocus(setOf(selectedEdge.first, selectedEdge.second), setOf(selectedEdge))
    }
    if (selectedNode == null || selectedNode !in layout.nodes) return null
    val incident = layout.edges.filter { it.from == selectedNode || it.to == selectedNode }
    return GraphFocus(
        nodes = setOf(selectedNode) + incident.flatMap { listOf(it.from, it.to) },
        edges = incident.map { it.from to it.to }.toSet(),
    )
}

@Composable
internal fun RenderGraph(node: SpecNode, resolvedProps: Map<String, Any?>, context: RenderContext) {
    val props = node.props.asGraphProps()
    val entries = resolveGraphNodes(resolvedProps["nodes"])
    val edgeEntries = resolveGraphEdges(resolvedProps["edges"])
    val groupOrder = resolveGraphGroupOrder(resolvedProps["group_order"])
    val kindStyles = resolveGraphKindStyles(resolvedProps["kind_styles"])
    val statusOutlines = resolveGraphStringMap(resolvedProps["status_outlines"])
    val legend = resolveGraphLegend(resolvedProps["legend"])
    val zoom = resolveGraphZoom(resolvedProps["zoom"])
    val statusColors = assignGraphColors(
        entries.mapNotNull { it.status } + edgeEntries.mapNotNull { it.status },
        resolveGraphStringMap(resolvedProps["status_colors"]),
    )
    val groupColors = assignGraphColors(entries.mapNotNull { it.group }, resolveGraphStringMap(resolvedProps["group_colors"]))
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val theme = LocalSpecTheme.current
    val hasLanes = entries.any { it.group != null }
    val looks = remember(entries, kindStyles, statusOutlines) { entries.associate { it.id to lookOf(it, kindStyles, statusOutlines) } }
    val layout = remember(entries, edgeEntries, groupOrder, looks, density.density) {
        layoutFlowGraph(
            nodes = entries.map { measuredSpec(it, looks.getValue(it.id), measurer, density) },
            edges = edgeEntries.map { FlowEdgeSpec(it.from, it.to) },
            groupOrder = groupOrder,
            gutter = if (hasLanes) LANE_GUTTER_DP else 0f,
        )
    }
    val wired = remember(layout) { layout.edges.flatMap { listOf(it.from, it.to) }.toSet() }
    val edgeStyles = remember(edgeEntries) { edgeEntries.distinctBy { it.from to it.to }.associateBy { it.from to it.to } }
    val selectedPath = node.props["selected"]?.bindingPath()
    val selectedEdgePath = node.props["selected_edge"]?.bindingPath()
    val selectedNodePath = node.props["selected_node"]?.bindingPath()
    val nodeRecords = remember(resolvedProps["nodes"]) { recordsById(resolvedProps["nodes"]) }
    val edgeRecords = remember(resolvedProps["edges"]) { recordsByEnds(resolvedProps["edges"]) }
    var localSelected by remember { mutableStateOf<String?>(null) }
    var localSelectedEdge by remember { mutableStateOf<Pair<String, String>?>(null) }
    val selected = if (selectedPath != null) resolvedProps["selected"] as? String else localSelected
    val selectedEdge = if (selectedEdgePath != null) edgeOfBinding(resolvedProps["selected_edge"]) else localSelectedEdge
    val focus = remember(selected, selectedEdge, layout) { focusOf(selected, selectedEdge, layout) }
    val scope = rememberCoroutineScope()

    fun select(nodeId: String?, edge: Pair<String, String>?) {
        if (selectedPath != null) context.setBinding(selectedPath, nodeId) else localSelected = nodeId
        if (selectedNodePath != null) context.setBinding(selectedNodePath, nodeId?.let { nodeRecords[it] })
        if (selectedEdgePath != null) {
            context.setBinding(selectedEdgePath, edge?.let { edgeRecords[it] ?: mapOf("from" to it.first, "to" to it.second) })
        } else {
            localSelectedEdge = edge
        }
    }

    Column(modifier = Modifier.fillMaxSize().nodeTag(node.id)) {
        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
            val viewWidthPx = constraints.maxWidth.toFloat()
            val viewHeightPx = constraints.maxHeight.toFloat()
            val fit = remember(layout, viewWidthPx, viewHeightPx, density.density, zoom) {
                fitFlowView(layout.width * density.density, layout.height * density.density, viewWidthPx, viewHeightPx, zoom = zoom)
            }
            var gestureView by remember(layout, viewWidthPx, viewHeightPx, zoom) { mutableStateOf<FlowView?>(null) }
            val view = gestureView ?: fit
            val latestView by rememberUpdatedState(view)
            Box(
                modifier = Modifier.fillMaxSize()
                    .pointerInput(layout, selected, selectedEdge) {
                        detectTapGestures(onTap = { offset ->
                            val tapped = edgeAt(layout, offset, latestView, density.density)
                            select(null, tapped?.let { it.from to it.to })
                            val onEdgeTapped = props.onEdgeTapped
                            if (tapped != null && onEdgeTapped != null) {
                                scope.launch { context.dispatch(node.id, edgeTappedAction(onEdgeTapped, tapped)) }
                            }
                        })
                    }
                    .pointerInput(fit) {
                        detectTransformGestures { centroid, pan, zoomDelta, _ ->
                            gestureView = (gestureView ?: fit).afterGesture(centroid.x, centroid.y, pan.x, pan.y, zoomDelta)
                        }
                    }
                    .pointerInput(fit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.type != PointerEventType.Scroll) continue
                                val change = event.changes.first()
                                val zoomDelta = exp(-change.scrollDelta.y * WHEEL_ZOOM_RATE)
                                gestureView = (gestureView ?: fit).afterGesture(change.position.x, change.position.y, 0f, 0f, zoomDelta)
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
                        drawLanes(layout, groupColors, measurer, theme.muted, theme.isDark)
                        drawEdges(layout, edgeStyles, statusColors, theme.muted, focus, theme.isDark)
                    }
                    for (entry in entries) {
                        val box = layout.nodes[entry.id] ?: continue
                        val parked = entry.id !in wired
                        val dim = if (focus == null || entry.id in focus.nodes) 1f else DIMMED_NODE_ALPHA
                        GraphNodeBox(
                            entry = entry,
                            box = box,
                            look = looks.getValue(entry.id),
                            fill = nodeFill(entry, statusColors, if (theme.isDark) UNSTATED_FILL_DARK else UNSTATED_FILL),
                            border = entry.group?.let { colorOf(groupColors[it], theme.muted) } ?: theme.muted,
                            isSelected = entry.id == selected,
                            alpha = if (parked) dim * PARKED_ALPHA else dim,
                            onTap = {
                                select(if (entry.id == selected) null else entry.id, null)
                                val onNodeTapped = props.onNodeTapped
                                if (onNodeTapped != null) scope.launch { context.dispatch(node.id, nodeTappedAction(onNodeTapped, entry.id)) }
                            },
                        )
                        if (entry.selfLoop) SelfLoopBadge(box, if (parked) dim * PARKED_ALPHA else dim)
                    }
                }
            }
        }
        if (legend.isNotEmpty()) GraphLegend(node.id, legend)
    }
}

private fun recordsById(resolvedNodes: Any?): Map<String, Map<*, *>> =
    (resolvedNodes as? List<*>).orEmpty().mapNotNull { entry ->
        val record = entry as? Map<*, *> ?: return@mapNotNull null
        (record["id"] as? String)?.let { it to record }
    }.toMap()

private fun recordsByEnds(resolvedEdges: Any?): Map<Pair<String, String>, Map<*, *>> =
    (resolvedEdges as? List<*>).orEmpty().mapNotNull { entry ->
        val record = entry as? Map<*, *> ?: return@mapNotNull null
        val from = record["from"] as? String ?: return@mapNotNull null
        val to = record["to"] as? String ?: return@mapNotNull null
        (from to to) to record
    }.reversed().toMap()

private fun edgeOfBinding(raw: Any?): Pair<String, String>? {
    val fields = raw as? Map<*, *> ?: return null
    val from = fields["from"] as? String ?: return null
    val to = fields["to"] as? String ?: return null
    return from to to
}

private fun edgeAt(layout: FlowLayout, tap: Offset, view: FlowView, density: Float): FlowEdgeRoute? {
    val contentX = (tap.x - view.translateX) / view.scale / density
    val contentY = (tap.y - view.translateY) / view.scale / density
    return hitTestFlowEdge(layout.edges, contentX, contentY, EDGE_HIT_TOLERANCE_DP / view.scale)
}

private fun measuredSpec(entry: GraphNodeEntry, look: NodeLook, measurer: TextMeasurer, density: Density): FlowNodeSpec {
    val padX = if (look.compact) COMPACT_PAD_X_DP else NODE_PAD_X_DP
    val padY = if (look.compact) COMPACT_PAD_Y_DP else NODE_PAD_Y_DP
    val maxTextWidthPx = ((MAX_NODE_WIDTH_DP - 2 * padX) * density.density).toInt().coerceAtLeast(1)
    val measured = measurer.measure(
        text = AnnotatedString(entry.label),
        style = look.text,
        overflow = TextOverflow.Ellipsis,
        softWrap = false,
        maxLines = 1,
        constraints = Constraints(maxWidth = maxTextWidthPx),
    ).size
    return FlowNodeSpec(
        id = entry.id,
        width = measured.width / density.density + 2 * padX + NODE_WIDTH_SLACK_DP,
        height = measured.height / density.density + 2 * padY,
        group = entry.group,
        rank = entry.rank,
        pinnedX = entry.x,
        pinnedY = entry.y,
    )
}

private fun colorOf(hex: String?, fallback: Color): Color = parseGeomapColor(hex)?.let { Color(it) } ?: fallback

private fun nodeFill(entry: GraphNodeEntry, statusColors: Map<String, String>, unstated: Color): Color =
    colorOf(entry.color ?: entry.status?.let { statusColors[it] }, unstated)

private fun readableOn(fill: Color): Color =
    if (fill.luminance() < WHITE_TEXT_BELOW) Color.White else TEXT_ON_LIGHT

private fun Color.luminance(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue

private fun shapeOf(shape: GraphNodeShape) =
    if (shape == GraphNodeShape.PILL) RoundedCornerShape(50) else RoundedCornerShape(NODE_CORNER_DP.dp)

@Composable
private fun GraphNodeBox(
    entry: GraphNodeEntry,
    box: FlowNodeBox,
    look: NodeLook,
    fill: Color,
    border: Color,
    isSelected: Boolean,
    alpha: Float,
    onTap: () -> Unit,
) {
    val theme = LocalSpecTheme.current
    val shape = shapeOf(look.shape)
    val shownFill = if (look.muted) lerp(fill, theme.ground, MUTED_BLEND) else fill
    val outlineColor = if (isSelected || look.dashedOutline) theme.ink else border
    val outlineDp = when {
        isSelected -> maxOf(SELECTED_BORDER_DP, look.borderDp)
        look.dashedOutline -> maxOf(DASHED_OUTLINE_MIN_DP, look.borderDp)
        else -> look.borderDp
    }
    val outline = if (look.dashedOutline) {
        Modifier.drawBehind { drawDashedOutline(outlineColor, outlineDp, look.shape, density) }
    } else {
        Modifier.border(outlineDp.dp, outlineColor, shape)
    }
    Box(
        modifier = Modifier
            .offset(box.x.dp, box.y.dp)
            .size(box.width.dp, box.height.dp)
            .alpha(alpha)
            .nodeTag(entry.id)
            .clip(shape)
            .background(shownFill)
            .then(outline)
            .pointerInput(entry.id) { detectTapGestures(onTap = { onTap() }) },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = entry.label,
            style = look.text.copy(color = if (look.muted) theme.ink else readableOn(fill)),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun DrawScope.drawDashedOutline(color: Color, widthDp: Float, shape: GraphNodeShape, density: Float) {
    val width = widthDp * density
    val inset = width / 2f
    val corner = if (shape == GraphNodeShape.PILL) (size.height - width) / 2f else NODE_CORNER_DP * density
    drawRoundRect(
        color = color,
        topLeft = Offset(inset, inset),
        size = Size(size.width - width, size.height - width),
        cornerRadius = CornerRadius(corner),
        style = Stroke(width = width, pathEffect = PathEffect.dashPathEffect(floatArrayOf(DASH_DP * density, DASH_DP * density))),
    )
}

@Composable
private fun SelfLoopBadge(box: FlowNodeBox, alpha: Float) {
    val theme = LocalSpecTheme.current
    Canvas(
        modifier = Modifier
            .offset((box.x + box.width - BADGE_DP * 0.6f).dp, (box.y - BADGE_DP * 0.4f).dp)
            .size(BADGE_DP.dp)
            .alpha(alpha),
    ) {
        drawSelfLoopGlyph(theme.ink, theme.ground)
    }
}

private fun DrawScope.drawSelfLoopGlyph(ink: Color, ground: Color) {
    val stroke = BADGE_STROKE_DP * density
    val radius = size.minDimension / 2f - stroke
    val center = Offset(size.width / 2f, size.height / 2f)
    drawCircle(ground, radius = size.minDimension / 2f, center = center)
    drawArc(
        color = ink,
        startAngle = SELF_LOOP_START_DEGREES.toFloat(),
        sweepAngle = (SELF_LOOP_END_DEGREES - SELF_LOOP_START_DEGREES).toFloat(),
        useCenter = false,
        topLeft = Offset(center.x - radius, center.y - radius),
        size = Size(radius * 2, radius * 2),
        style = Stroke(width = stroke),
    )
    val endAngle = SELF_LOOP_END_DEGREES * PI / 180.0
    val end = Offset(center.x + radius * cos(endAngle).toFloat(), center.y + radius * sin(endAngle).toFloat())
    val tangent = Offset(-sin(endAngle).toFloat(), cos(endAngle).toFloat())
    val normal = Offset(-tangent.y, tangent.x)
    val head = Path().apply {
        moveTo(end.x + tangent.x * stroke * 2.4f, end.y + tangent.y * stroke * 2.4f)
        lineTo(end.x + normal.x * stroke * 1.8f, end.y + normal.y * stroke * 1.8f)
        lineTo(end.x - normal.x * stroke * 1.8f, end.y - normal.y * stroke * 1.8f)
        close()
    }
    drawPath(head, ink, style = Fill)
}

private fun nodeTappedAction(action: String, nodeId: String) = ActionRef(
    action = action,
    params = mapOf("node_id" to SpecValue.StringValue(nodeId)),
)

private fun edgeTappedAction(action: String, route: FlowEdgeRoute) = ActionRef(
    action = action,
    params = mapOf("from" to SpecValue.StringValue(route.from), "to" to SpecValue.StringValue(route.to)),
)

private fun DrawScope.drawLanes(layout: FlowLayout, groupColors: Map<String, String>, measurer: TextMeasurer, labelColor: Color, dark: Boolean) {
    for (lane in layout.lanes) {
        if (lane.group == null && layout.lanes.size == 1) continue
        val tint = colorOf(lane.group?.let { groupColors[it] }, labelColor)
        val topLeft = Offset(0f, lane.top * density)
        val size = Size(layout.width * density, lane.height * density)
        drawRoundRect(tint.copy(alpha = if (dark) LANE_TINT_ALPHA_DARK else LANE_TINT_ALPHA), topLeft, size, CornerRadius(LANE_CORNER_DP * density), style = Fill)
        val group = lane.group ?: continue
        val label = measurer.measure(
            text = AnnotatedString(group),
            style = LANE_TEXT.copy(color = if (dark) lerp(tint, Color.White, LANE_LABEL_LIFT_DARK) else tint),
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
    dark: Boolean,
) {
    val (lit, rest) = layout.edges.partition { focus != null && (it.from to it.to) in focus.edges }
    val restAlpha = if (dark) (if (focus == null) RESTING_EDGE_ALPHA_DARK else DIMMED_EDGE_ALPHA_DARK) else (if (focus == null) RESTING_EDGE_ALPHA else DIMMED_EDGE_ALPHA)
    for (route in rest) drawEdge(route, styles[route.from to route.to], statusColors, fallback, restAlpha, highlighted = false)
    for (route in lit) drawEdge(route, styles[route.from to route.to], statusColors, fallback, 1f, highlighted = true)
}

private fun DrawScope.drawEdge(
    route: FlowEdgeRoute,
    style: GraphEdgeEntry?,
    statusColors: Map<String, String>,
    fallback: Color,
    restingAlpha: Float,
    highlighted: Boolean,
) {
    val feedbackFade = if (route.isBackEdge && !highlighted) BACK_EDGE_FADE else 1f
    val color = colorOf(style?.color ?: style?.status?.let { statusColors[it] }, fallback).copy(alpha = restingAlpha * feedbackFade)
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
    if (style?.marker == "dot") drawEdgeDot(flowRouteMidpoint(route), color)
}

private fun DrawScope.drawEdgeDot(midpoint: FlowPoint, color: Color) {
    drawCircle(color, radius = MARKER_RADIUS_DP * density, center = Offset(midpoint.x * density, midpoint.y * density))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GraphLegend(nodeId: String, entries: List<GraphLegendEntry>) {
    val theme = LocalSpecTheme.current
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        entries.forEachIndexed { index, entry ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                LegendSwatch(entry, Modifier.nodeTag("$nodeId:legend:$index"))
                Text(text = entry.label, style = LEGEND_TEXT.copy(color = theme.ink), maxLines = 1)
            }
        }
    }
}

@Composable
private fun LegendSwatch(entry: GraphLegendEntry, modifier: Modifier) {
    val theme = LocalSpecTheme.current
    when {
        entry.kind == "badge" && entry.icon == "circular-arrow" ->
            Canvas(modifier = modifier.size(BADGE_DP.dp)) { drawSelfLoopGlyph(theme.ink, theme.ground) }
        entry.kind == "edge" -> Canvas(modifier = modifier.size(LEGEND_EDGE_W_DP.dp, LEGEND_NODE_H_DP.dp)) {
            val middle = LEGEND_NODE_H_DP / 2f
            val route = FlowEdgeRoute("", "", listOf(FlowPoint(2f, middle), FlowPoint(LEGEND_EDGE_W_DP - 2f, middle)), isBackEdge = false)
            drawEdge(route, GraphEdgeEntry("", "", color = entry.color, dash = entry.dash, marker = entry.marker), emptyMap(), theme.muted, 1f, highlighted = false)
        }
        else -> LegendChip(entry, modifier)
    }
}

@Composable
private fun LegendChip(entry: GraphLegendEntry, modifier: Modifier) {
    val theme = LocalSpecTheme.current
    val shape = shapeOf(entry.shape ?: GraphNodeShape.BOX)
    val fill = colorOf(entry.color, if (theme.isDark) UNSTATED_FILL_DARK else UNSTATED_FILL).let { if (entry.muted) lerp(it, theme.ground, MUTED_BLEND) else it }
    val width = if (entry.compact) LEGEND_COMPACT_W_DP else LEGEND_NODE_W_DP
    val height = if (entry.compact) LEGEND_COMPACT_H_DP else LEGEND_NODE_H_DP
    val outlineDp = entry.borderWidth ?: DEFAULT_BORDER_DP
    val outline = if (entry.outline == "dashed") {
        Modifier.drawBehind { drawDashedOutline(theme.ink, outlineDp, entry.shape ?: GraphNodeShape.BOX, density) }
    } else {
        Modifier.border(outlineDp.dp, theme.muted, shape)
    }
    Box(modifier = modifier.size(width.dp, height.dp).clip(shape).background(fill).then(outline))
}
