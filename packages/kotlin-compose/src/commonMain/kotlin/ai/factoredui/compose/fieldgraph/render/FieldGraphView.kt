package ai.factoredui.compose.fieldgraph.render

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.factoredui.compose.fieldgraph.graph.FieldEdge
import ai.factoredui.compose.fieldgraph.graph.FieldGraphSnapshot
import ai.factoredui.compose.fieldgraph.graph.FieldGraphState
import ai.factoredui.compose.fieldgraph.graph.FieldLogItem
import ai.factoredui.compose.fieldgraph.graph.FieldNode
import ai.factoredui.compose.fieldgraph.graph.FieldNodePosition
import ai.factoredui.compose.testing.DomShadow
import kotlinx.coroutines.delay
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

private const val CLAIM_RADIUS = 16f
private const val ENTITY_RADIUS = 11f
private const val AGE_HALF_LIFE_SEC = 43200f
private const val TAP_MAX_MOVE_PX = 8f
private const val MIN_CLAIM_ALPHA = 0.30f
private val BACKGROUND = Color(0xFF08080F)
private val RING_COLOR = Color(0x14FFFFFF)

// Log column layout constants
private const val LOG_COLUMN_WIDTH_DP = 80
private const val LEFT_EDGE_AUTO_EXPAND_FRACTION = 0.15f
private const val AUTO_EXPAND_HOLD_MS = 1500L

private val GROUP_PALETTE = listOf(
    Color(0xFF74C0FC), Color(0xFF8CE99A), Color(0xFFFFA94D),
    Color(0xFFDA77F2), Color(0xFFFF8787), Color(0xFF63E6BE),
    Color(0xFFFFD43B), Color(0xFFA9E34B),
)

private fun groupColor(group: String): Color {
    val idx = (group.hashCode() and 0x7FFFFFFF) % GROUP_PALETTE.size
    return GROUP_PALETTE[idx]
}

/**
 * Mutable reference to log column layout bounds, written from layout callbacks
 * and read synchronously from the gesture coroutine. Uses a plain class so reads
 * are not snapshot-gated — the value is always the most recently written one.
 * All reads and writes happen on the main thread in Compose UI tests.
 */
private class LogColumnLayoutRef {
    var collapseButtonBounds: Rect = Rect.Zero
    val itemBounds: MutableMap<String, Rect> = mutableMapOf()
}

@Composable
fun FieldGraphView(
    graphState: FieldGraphState,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
    onNodeDragStart: (nodeId: String) -> Unit = {},
    onNodeDragUpdate: (nodeId: String, angle: Float, radiusFraction: Float) -> Unit = { _, _, _ -> },
    onNodeDragRelease: () -> Unit = {},
    onNodeDragComplete: (nodeId: String, relevanceMagnitude: Float) -> Unit = { _, _ -> },
    onNodeTap: (nodeId: String) -> Unit = {},
    onLogItemTap: (nodeId: String) -> Unit = {},
    onLogItemToField: (nodeId: String, xFraction: Float, yFraction: Float) -> Unit = { _, _, _ -> },
    onNodeToLog: (nodeId: String) -> Unit = {},
    onLogToggle: (collapsed: Boolean) -> Unit = {},
) {
    val logItems = graphState.logItems
    var snapshot by remember(graphState) { mutableStateOf(graphState.snapshot()) }
    var draggedNodeId by remember { mutableStateOf<String?>(null) }
    var dragStartPos by remember { mutableStateOf(Offset.Zero) }
    var dragCurrentPos by remember { mutableStateOf(Offset.Zero) }

    // Log column state — mutableStateOf so recomposition fires when changed
    val isLogCollapsedState = remember { mutableStateOf(false) }
    var isLogCollapsed by isLogCollapsedState

    // Non-reactive ref for layout bounds: written from layout callbacks, read from gesture coroutine
    val logLayoutRef = remember { LogColumnLayoutRef() }

    // Track left-edge hold for auto-expand
    var isDraggingNearLeftEdge by remember { mutableStateOf(false) }

    LaunchedEffect(graphState, reduceMotion) {
        while (true) {
            if (!reduceMotion) graphState.step()
            snapshot = graphState.snapshot()
            delay(33L)
        }
    }

    // Auto-expand log when a node is held near the left edge for AUTO_EXPAND_HOLD_MS
    LaunchedEffect(isDraggingNearLeftEdge, isLogCollapsed) {
        if (isDraggingNearLeftEdge && isLogCollapsed) {
            delay(AUTO_EXPAND_HOLD_MS)
            isLogCollapsed = false
            onLogToggle(false)
        }
    }

    // Emit field node positions into DomShadow
    androidx.compose.runtime.LaunchedEffect(snapshot) {
        val snap = snapshot
        snap.nodes.forEach { node ->
            val pos = snap.positions[node.id] ?: return@forEach
            DomShadow.emit(
                id = "fieldgraph:node:${node.id}",
                role = "field-node",
                attrs = mapOf(
                    "node-id" to node.id,
                    "group" to node.group,
                    "label" to node.label.take(60),
                    "angle" to pos.angle.toString(),
                    "radius-fraction" to pos.radiusFraction.toString(),
                    "is-user-anchored" to pos.isUserAnchored.toString(),
                    "age-secs" to node.ageSecs.toString(),
                ),
            )
        }
    }

    // Emit log-column state into DomShadow — reactive to isLogCollapsed
    SideEffect {
        DomShadow.emit(
            id = "fieldgraph:log-column",
            role = "log-column",
            attrs = mapOf("collapsed" to isLogCollapsed.toString()),
        )
    }

    Box(
        modifier = modifier.fillMaxSize()
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val pos = down.position
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val maxR = minOf(cx, cy) * 0.88f
                    val leftEdgeThreshold = size.width * LEFT_EDGE_AUTO_EXPAND_FRACTION
                    val snap = snapshot
                    val collapsed = isLogCollapsedState.value

                    // Log column interaction — collapse button (always visible when log has items)
                    if (logItems.isNotEmpty()) {
                        val collapseButtonBounds = logLayoutRef.collapseButtonBounds
                        // Fallback zone: top of log column (top 40px, left 80px) if layout not yet recorded
                        val collapseZone = if (collapseButtonBounds != Rect.Zero) collapseButtonBounds
                            else Rect(0f, 0f, LOG_COLUMN_WIDTH_DP.toFloat(), 40f)
                        if (collapseZone.contains(pos)) {
                            awaitPointerEvent() // wait for UP
                            val newCollapsed = !collapsed
                            isLogCollapsedState.value = newCollapsed
                            // Update DomShadow immediately so assertions in the same frame see the new state
                            DomShadow.emit(
                                id = "fieldgraph:log-column",
                                role = "log-column",
                                attrs = mapOf("collapsed" to newCollapsed.toString()),
                            )
                            onLogToggle(newCollapsed)
                            return@awaitEachGesture
                        }

                        // Log item tap — only when expanded
                        if (!collapsed) {
                            val hitItemId = logLayoutRef.itemBounds.entries
                                .firstOrNull { (_, bounds) -> bounds.contains(pos) }
                                ?.key
                            if (hitItemId != null) {
                                // Might be a tap or a drag — check after UP/move
                                var moved = false
                                var currentPos = pos
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull() ?: break
                                    if (!change.pressed) break
                                    currentPos = change.position
                                    val dx = currentPos.x - pos.x
                                    val dy = currentPos.y - pos.y
                                    if (sqrt(dx * dx + dy * dy) > viewConfiguration.touchSlop) {
                                        moved = true
                                    }
                                    change.consume()
                                }
                                if (!moved) {
                                    onLogItemTap(hitItemId)
                                } else {
                                    // Drag from log item onto canvas
                                    onLogItemToField(
                                        hitItemId,
                                        (currentPos.x / size.width).coerceIn(0f, 1f),
                                        (currentPos.y / size.height).coerceIn(0f, 1f),
                                    )
                                }
                                return@awaitEachGesture
                            }
                        }
                    }

                    // Field node hit test
                    val hitR = CLAIM_RADIUS * 2.5f
                    val hitRSq = hitR * hitR
                    val hitCandidates: List<Pair<String, Float>> = snap.positions.entries.map { entry ->
                        val nodePos = entry.value
                        val nx = cx + cos(nodePos.angle) * nodePos.radiusFraction * maxR
                        val ny = cy + sin(nodePos.angle) * nodePos.radiusFraction * maxR
                        val dx = nx - pos.x
                        val dy = ny - pos.y
                        entry.key to (dx * dx + dy * dy)
                    }
                    val hit: String = hitCandidates
                        .filter { it.second <= hitRSq }
                        .minByOrNull { it.second }
                        ?.first
                        ?: return@awaitEachGesture

                    dragStartPos = pos
                    dragCurrentPos = pos
                    var gestureStarted = false
                    var lastFrac = snap.positions[hit]?.radiusFraction ?: 1f

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break

                        if (!change.pressed) {
                            isDraggingNearLeftEdge = false
                            if (!gestureStarted) {
                                if (snap.nodes.any { it.id == hit && it.group != "entity" }) {
                                    onNodeTap(hit)
                                }
                            } else {
                                val dropX = dragCurrentPos.x
                                // Drop in log column zone → eject node to log
                                if (dropX < LOG_COLUMN_WIDTH_DP && !isLogCollapsedState.value) {
                                    onNodeToLog(hit)
                                } else {
                                    onNodeDragComplete(hit, 1f - lastFrac)
                                }
                                onNodeDragRelease()
                                draggedNodeId = null
                            }
                            break
                        }

                        change.consume()
                        dragCurrentPos = change.position
                        val ddx = dragCurrentPos.x - dragStartPos.x
                        val ddy = dragCurrentPos.y - dragStartPos.y
                        val totalMove = sqrt(ddx * ddx + ddy * ddy)

                        if (!gestureStarted && totalMove >= viewConfiguration.touchSlop) {
                            gestureStarted = true
                            draggedNodeId = hit
                            onNodeDragStart(hit)
                        }

                        if (gestureStarted) {
                            val px = change.position.x - cx
                            val py = change.position.y - cy
                            val dist = sqrt(px * px + py * py)
                            val angle = atan2(py, px)
                            val frac = (dist / maxR).coerceIn(0f, 1f)
                            lastFrac = frac
                            if (totalMove > TAP_MAX_MOVE_PX) {
                                onNodeDragUpdate(hit, angle, frac)
                            }
                            isDraggingNearLeftEdge = change.position.x < leftEdgeThreshold
                            snapshot = graphState.snapshot()
                        }
                    }

                    if (gestureStarted && draggedNodeId != null) {
                        isDraggingNearLeftEdge = false
                        onNodeDragRelease()
                        draggedNodeId = null
                    }
                }
            },
    ) {
        val textMeasurer = rememberTextMeasurer()
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val maxR = minOf(cx, cy) * 0.88f

            drawRect(color = BACKGROUND, size = size)
            drawRelevanceRings(cx, cy, maxR)
            drawEdges(snapshot, cx, cy, maxR)
            drawEntityNodes(snapshot, cx, cy, maxR)
            drawClaimNodes(snapshot, cx, cy, maxR, textMeasurer)
        }

        // Log column overlay — renders visually; interaction is handled in the Box's pointerInput
        if (logItems.isNotEmpty()) {
            LogColumnOverlay(
                logItems = logItems,
                isCollapsed = isLogCollapsed,
                overlayModifier = Modifier
                    .align(Alignment.TopStart)
                    .width(LOG_COLUMN_WIDTH_DP.dp)
                    .fillMaxHeight(),
                onCollapseButtonLayout = { bounds ->
                    logLayoutRef.collapseButtonBounds = bounds
                },
                onItemLayout = { id, bounds ->
                    logLayoutRef.itemBounds[id] = bounds
                },
                onItemDispose = { id ->
                    logLayoutRef.itemBounds.remove(id)
                },
            )
        }
    }
}

@Composable
private fun LogColumnOverlay(
    logItems: List<FieldLogItem>,
    isCollapsed: Boolean,
    overlayModifier: Modifier,
    onCollapseButtonLayout: (Rect) -> Unit,
    onItemLayout: (String, Rect) -> Unit,
    onItemDispose: (String) -> Unit,
) {
    val sortedItems = remember(logItems) {
        logItems.sortedByDescending { it.placedAtMs }
    }

    Box(modifier = overlayModifier.background(Color(0xCC1A1A2E))) {
        Column {
            LogCollapseButtonView(
                isCollapsed = isCollapsed,
                onLayout = onCollapseButtonLayout,
            )
            if (!isCollapsed) {
                sortedItems.forEach { item ->
                    LogItemRowView(
                        item = item,
                        onLayout = { bounds -> onItemLayout(item.id, bounds) },
                        onDispose = { onItemDispose(item.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun LogCollapseButtonView(
    isCollapsed: Boolean,
    onLayout: (Rect) -> Unit,
) {
    val label = if (isCollapsed) ">" else "<"

    Box(
        modifier = Modifier
            .width(LOG_COLUMN_WIDTH_DP.dp)
            .padding(4.dp)
            .onGloballyPositioned { coords ->
                val rootPos = coords.positionInRoot()
                val sz = coords.size
                val bounds = Rect(
                    left = rootPos.x,
                    top = rootPos.y,
                    right = rootPos.x + sz.width,
                    bottom = rootPos.y + sz.height,
                )
                onLayout(bounds)
                DomShadow.emit(
                    id = "fieldgraph:log-collapse-btn",
                    role = "log-collapse-button",
                    attrs = mapOf(
                        "collapsed" to isCollapsed.toString(),
                        "pixel-x" to (rootPos.x + sz.width / 2f).toString(),
                        "pixel-y" to (rootPos.y + sz.height / 2f).toString(),
                    ),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, color = Color.White, fontSize = 12.sp)
    }

    DisposableEffect(Unit) {
        onDispose { DomShadow.remove("fieldgraph:log-collapse-btn") }
    }
}

@Composable
private fun LogItemRowView(
    item: FieldLogItem,
    onLayout: (Rect) -> Unit,
    onDispose: () -> Unit,
) {
    val domId = "fieldgraph:log-item:${item.id}"

    Box(
        modifier = Modifier
            .width(LOG_COLUMN_WIDTH_DP.dp)
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .onGloballyPositioned { coords ->
                val rootPos = coords.positionInRoot()
                val sz = coords.size
                val bounds = Rect(
                    left = rootPos.x,
                    top = rootPos.y,
                    right = rootPos.x + sz.width,
                    bottom = rootPos.y + sz.height,
                )
                onLayout(bounds)
                DomShadow.emit(
                    id = domId,
                    role = "log-item",
                    attrs = mapOf(
                        "id" to item.id,
                        "label" to item.label,
                        "entity-name" to item.entityName,
                        "placed-at-ms" to item.placedAtMs.toString(),
                        "pixel-x" to (rootPos.x + sz.width / 2f).toString(),
                        "pixel-y" to (rootPos.y + sz.height / 2f).toString(),
                    ),
                )
            },
    ) {
        Text(
            text = item.label.take(20),
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 10.sp,
            modifier = Modifier.padding(2.dp),
        )
    }

    DisposableEffect(item.id) {
        onDispose {
            DomShadow.remove(domId)
            onDispose()
        }
    }
}

private fun DrawScope.drawRelevanceRings(cx: Float, cy: Float, maxR: Float) {
    for (i in 1..4) {
        drawCircle(
            color = RING_COLOR,
            radius = maxR * i / 4f,
            center = Offset(cx, cy),
            style = Stroke(width = 1f),
        )
    }
    drawCircle(color = Color(0x28FFFFFF), radius = 3.5f, center = Offset(cx, cy))
}

private fun DrawScope.drawEdges(s: FieldGraphSnapshot, cx: Float, cy: Float, maxR: Float) {
    for (edge in s.edges) {
        val fPos = s.positions[edge.fromId] ?: continue
        val tPos = s.positions[edge.toId] ?: continue
        val avgRelevance = 1f - (fPos.radiusFraction + tPos.radiusFraction) / 2f
        val alpha = (0.08f + 0.22f * avgRelevance).coerceIn(0f, 0.5f)
        drawLine(
            color = Color.White.copy(alpha = alpha),
            start = Offset(
                cx + cos(fPos.angle) * fPos.radiusFraction * maxR,
                cy + sin(fPos.angle) * fPos.radiusFraction * maxR,
            ),
            end = Offset(
                cx + cos(tPos.angle) * tPos.radiusFraction * maxR,
                cy + sin(tPos.angle) * tPos.radiusFraction * maxR,
            ),
            strokeWidth = 1f,
        )
    }
}

private fun DrawScope.drawEntityNodes(s: FieldGraphSnapshot, cx: Float, cy: Float, maxR: Float) {
    for (node in s.nodes.filter { it.group == "entity" }) {
        val pos = s.positions[node.id] ?: continue
        val alpha = (0.18f + 0.22f * (1f - pos.radiusFraction)).coerceIn(0f, 1f)
        drawCircle(
            color = Color(0xFF8BABCF).copy(alpha = alpha),
            radius = ENTITY_RADIUS,
            center = Offset(
                cx + cos(pos.angle) * pos.radiusFraction * maxR,
                cy + sin(pos.angle) * pos.radiusFraction * maxR,
            ),
            style = Stroke(width = 1.5f),
        )
    }
}

private fun DrawScope.drawClaimNodes(s: FieldGraphSnapshot, cx: Float, cy: Float, maxR: Float, textMeasurer: TextMeasurer) {
    for (node in s.nodes.filter { it.group != "entity" }) {
        val pos = s.positions[node.id] ?: continue
        val relevance = 1f - pos.radiusFraction
        val ageDecay = exp(-0.693f * node.ageSecs / AGE_HALF_LIFE_SEC).coerceIn(0f, 1f)
        val alpha = ((0.30f + 0.70f * relevance) * ageDecay).coerceAtLeast(MIN_CLAIM_ALPHA)
        val center = Offset(
            cx + cos(pos.angle) * pos.radiusFraction * maxR,
            cy + sin(pos.angle) * pos.radiusFraction * maxR,
        )
        drawCircle(color = Color.White.copy(alpha = alpha), radius = CLAIM_RADIUS, center = center)
        drawCircle(
            color = Color.Black.copy(alpha = 0.28f),
            radius = CLAIM_RADIUS,
            center = center,
            style = Stroke(width = 1f),
        )
        val labelWords = node.label.split(" ").take(3).joinToString(" ")
        val measured = textMeasurer.measure(
            labelWords,
            style = TextStyle(color = Color.White.copy(alpha = alpha), fontSize = 9.sp),
        )
        drawText(
            textLayoutResult = measured,
            topLeft = Offset(center.x - measured.size.width / 2f, center.y + CLAIM_RADIUS + 3f),
        )
    }
}
