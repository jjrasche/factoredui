package ai.factoredui.compose.renderer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import ai.factoredui.compose.schema.ActionRef
import ai.factoredui.compose.schema.BindingResolver
import ai.factoredui.compose.schema.FieldNodeEntry
import ai.factoredui.compose.schema.SpecNode
import ai.factoredui.compose.schema.SpecNodeType
import ai.factoredui.compose.schema.SpecValue
import ai.factoredui.compose.schema.CanvasEdge
import ai.factoredui.compose.schema.CanvasProps
import ai.factoredui.compose.schema.CanvasViewport
import ai.factoredui.compose.schema.asCanvasProps
import ai.factoredui.compose.schema.afterTransformGesture
import ai.factoredui.compose.schema.bindingPath
import ai.factoredui.compose.schema.resolveCanvasEdges
import ai.factoredui.compose.schema.resolveCanvasViewport
import ai.factoredui.compose.schema.resolveFieldNodeEntries
import ai.factoredui.compose.schema.transformFor
import kotlinx.coroutines.launch

private const val NODE_CENTER_DP = 20f
private val EDGE_COLOR = Color(0xFF8A94AD)

@Composable
fun RenderCanvas(node: SpecNode, context: RenderContext) {
    val liveData by context.dataFlow.collectAsState()
    val props = node.props.asCanvasProps()
    val edges = effectiveCanvasEdges(props, liveData).connectorPairs()
    val liveEntries = liveFieldEntries(props.nodesBinding, liveData)
    if (liveEntries != null) {
        val viewport = props.viewportBinding?.let { binding ->
            resolveCanvasViewport(BindingResolver.resolveValue(SpecValue.StringValue(binding), liveData))
        }
        if (viewport == null) {
            AbsoluteFieldCanvas(node, context, props.onNodeArranged, props.onNodeTapped, liveEntries, edges)
        } else {
            ZoomableFieldCanvas(node, context, props.onNodeTapped, props.onViewportChanged, liveEntries, edges, viewport)
        }
    } else {
        StaticChildCanvas(node, context, liveData, edges)
    }
}

@Composable
private fun AbsoluteFieldCanvas(
    node: SpecNode,
    context: RenderContext,
    onNodeArranged: String?,
    onNodeTapped: String?,
    entries: List<FieldNodeEntry>,
    edges: List<Pair<String, String>>,
) {
    val scope = rememberCoroutineScope()
    val positions = entries.associate { it.id to Offset(it.x, it.y) }
    Box(modifier = Modifier.fillMaxSize().nodeTag(node.id)) {
        EdgeLayer(edges, positions)
        for (entry in entries) {
            Box(
                modifier = Modifier
                    .offset(entry.x.dp, entry.y.dp)
                    .alpha(entry.glow)
                    .nodeTag(entry.id)
                    .nodeTapDispatch(scope, context, node.id, onNodeTapped, entry.id)
                    .arrangeDrag(scope, context, node.id, onNodeArranged, entry),
            ) {
                RenderNode(labelNode(entry), context)
            }
        }
    }
}

@Composable
private fun ZoomableFieldCanvas(
    node: SpecNode,
    context: RenderContext,
    onNodeTapped: String?,
    onViewportChanged: String?,
    entries: List<FieldNodeEntry>,
    edges: List<Pair<String, String>>,
    hostViewport: CanvasViewport,
) {
    val scope = rememberCoroutineScope()
    var viewport by remember { mutableStateOf(hostViewport) }
    LaunchedEffect(hostViewport) { viewport = hostViewport }
    BoxWithConstraints(modifier = Modifier.fillMaxSize().nodeTag(node.id).clipToBounds()) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        val widthDp = maxWidth.value
        val heightDp = maxHeight.value
        val positions = entries.associate { it.id to Offset(it.x * widthDp, it.y * heightDp) }
        Box(
            modifier = Modifier.fillMaxSize().panZoomGestures(
                widthPx, heightPx,
                read = { viewport },
                onPan = { viewport = it },
                onSettled = {
                    if (onViewportChanged != null) {
                        scope.launch { context.dispatch(node.id, viewportAction(onViewportChanged, viewport)) }
                    }
                },
            ),
        )
        val transform = viewport.transformFor(widthPx, heightPx)
        Box(
            modifier = Modifier.fillMaxSize().graphicsLayer {
                translationX = transform.translationX
                translationY = transform.translationY
                scaleX = transform.scale
                scaleY = transform.scale
                transformOrigin = TransformOrigin(0f, 0f)
            },
        ) {
            EdgeLayer(edges, positions)
            for (entry in entries) {
                Box(
                    modifier = Modifier
                        .offset((entry.x * widthDp).dp, (entry.y * heightDp).dp)
                        .alpha(entry.glow)
                        .nodeTag(entry.id)
                        .nodeTapDispatch(scope, context, node.id, onNodeTapped, entry.id),
                ) {
                    RenderNode(labelNode(entry), context)
                }
            }
        }
    }
}

private fun Modifier.nodeTapDispatch(
    scope: kotlinx.coroutines.CoroutineScope,
    context: RenderContext,
    nodeId: String,
    onNodeTapped: String?,
    entryId: String,
): Modifier {
    if (onNodeTapped == null) return this
    return this.pointerInput(entryId, onNodeTapped) {
        detectTapGestures(onTap = {
            scope.launch { context.dispatch(nodeId, tappedAction(onNodeTapped, entryId)) }
        })
    }
}

private fun tappedAction(action: String, nodeId: String) = ActionRef(
    action = action,
    params = mapOf("node_id" to SpecValue.StringValue(nodeId)),
)

@Composable
private fun StaticChildCanvas(
    node: SpecNode,
    context: RenderContext,
    liveData: Map<String, Any?>,
    edges: List<Pair<String, String>>,
) {
    val positions = childPositions(node, liveData)
    Box(modifier = Modifier.fillMaxSize().nodeTag(node.id)) {
        EdgeLayer(edges, positions)
        for (child in node.children) {
            val position = positions[child.id] ?: Offset(0f, 0f)
            val writeX = child.props["x"]?.bindingPath()
            val writeY = child.props["y"]?.bindingPath()
            Box(modifier = Modifier.offset(position.x.dp, position.y.dp).then(curationDrag(context, writeX, writeY))) {
                RenderNode(child, context)
            }
        }
    }
}

@Composable
private fun EdgeLayer(edges: List<Pair<String, String>>, positions: Map<String, Offset>) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        for ((from, to) in edges) {
            val start = positions[from] ?: continue
            val end = positions[to] ?: continue
            drawLine(
                color = EDGE_COLOR,
                start = edgeEndpoint(start, density),
                end = edgeEndpoint(end, density),
                strokeWidth = 2f,
            )
        }
    }
}

private fun Modifier.arrangeDrag(
    scope: kotlinx.coroutines.CoroutineScope,
    context: RenderContext,
    nodeId: String,
    onNodeArranged: String?,
    entry: FieldNodeEntry,
): Modifier {
    if (onNodeArranged == null) return this
    var draggedX = entry.x
    var draggedY = entry.y
    return this.pointerInput(entry.id, onNodeArranged) {
        detectDragGestures(
            onDragEnd = {
                scope.launch { context.dispatch(nodeId, arrangedAction(onNodeArranged, entry.id, draggedX, draggedY)) }
            },
        ) { change, dragAmount ->
            change.consume()
            draggedX += dragAmount.x / density
            draggedY += dragAmount.y / density
        }
    }
}

private fun arrangedAction(action: String, nodeId: String, x: Float, y: Float) = ActionRef(
    action = action,
    params = mapOf(
        "node_id" to SpecValue.StringValue(nodeId),
        "x" to SpecValue.NumberValue(x.toDouble()),
        "y" to SpecValue.NumberValue(y.toDouble()),
    ),
)

private fun Modifier.panZoomGestures(
    widthPx: Float,
    heightPx: Float,
    read: () -> CanvasViewport,
    onPan: (CanvasViewport) -> Unit,
    onSettled: () -> Unit,
): Modifier = this
    .pointerInput(widthPx, heightPx) {
        detectTransformGestures { centroid, pan, zoom, _ ->
            onPan(read().afterTransformGesture(centroid.x, centroid.y, pan.x, pan.y, zoom, widthPx, heightPx))
        }
    }
    .pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            do {
                val event = awaitPointerEvent()
            } while (event.changes.any { it.pressed })
            onSettled()
        }
    }

private fun viewportAction(action: String, viewport: CanvasViewport) = ActionRef(
    action = action,
    params = mapOf(
        "x" to SpecValue.NumberValue(viewport.x.toDouble()),
        "y" to SpecValue.NumberValue(viewport.y.toDouble()),
        "zoom" to SpecValue.NumberValue(viewport.zoom.toDouble()),
    ),
)

private fun labelNode(entry: FieldNodeEntry) = SpecNode(
    id = "${entry.id}:label",
    type = SpecNodeType.TEXT,
    props = mapOf("value" to SpecValue.StringValue(entry.label)),
)

private fun liveFieldEntries(nodesBinding: String?, liveData: Map<String, Any?>): List<FieldNodeEntry>? {
    if (nodesBinding == null) return null
    val resolved = BindingResolver.resolveValue(SpecValue.StringValue(nodesBinding), liveData)
    return resolveFieldNodeEntries(resolved)
}

private fun effectiveCanvasEdges(props: CanvasProps, liveData: Map<String, Any?>): List<CanvasEdge> {
    val binding = props.edgesBinding ?: return props.edges
    val resolved = BindingResolver.resolveValue(SpecValue.StringValue(binding), liveData)
    return resolveCanvasEdges(resolved)
}

private fun List<ai.factoredui.compose.schema.CanvasEdge>.connectorPairs(): List<Pair<String, String>> =
    map { it.from to it.to }

private fun curationDrag(context: RenderContext, writeX: String?, writeY: String?): Modifier {
    if (writeX == null || writeY == null) return Modifier
    return Modifier.pointerInput(writeX, writeY) {
        detectDragGestures { change, dragAmount ->
            change.consume()
            context.setBinding(writeX, coordAtPath(context.data, writeX) + dragAmount.x / density)
            context.setBinding(writeY, coordAtPath(context.data, writeY) + dragAmount.y / density)
        }
    }
}

private fun coordAtPath(data: Map<String, Any?>, path: String): Float {
    var current: Any? = data
    for (segment in path.split(".")) current = (current as? Map<*, *>)?.get(segment)
    return (current as? Number)?.toFloat() ?: 0f
}

private fun childPositions(node: SpecNode, liveData: Map<String, Any?>): Map<String, Offset> =
    node.children.associate { child ->
        val resolved = BindingResolver.resolveProps(child.props, liveData)
        child.id to Offset(
            (resolved["x"] as? Number)?.toFloat() ?: 0f,
            (resolved["y"] as? Number)?.toFloat() ?: 0f,
        )
    }

private fun edgeEndpoint(nodeCorner: Offset, density: Float): Offset =
    Offset((nodeCorner.x + NODE_CENTER_DP) * density, (nodeCorner.y + NODE_CENTER_DP) * density)
