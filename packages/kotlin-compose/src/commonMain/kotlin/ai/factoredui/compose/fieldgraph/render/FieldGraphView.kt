package ai.factoredui.compose.fieldgraph.render

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import ai.factoredui.compose.fieldgraph.graph.FieldEdge
import ai.factoredui.compose.fieldgraph.graph.FieldGraphSnapshot
import ai.factoredui.compose.fieldgraph.graph.FieldGraphState
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
private val BACKGROUND = Color(0xFF08080F)
private val RING_COLOR = Color(0x14FFFFFF)

private val GROUP_PALETTE = listOf(
    Color(0xFF74C0FC), Color(0xFF8CE99A), Color(0xFFFFA94D),
    Color(0xFFDA77F2), Color(0xFFFF8787), Color(0xFF63E6BE),
    Color(0xFFFFD43B), Color(0xFFA9E34B),
)

private fun groupColor(group: String): Color {
    val idx = (group.hashCode() and 0x7FFFFFFF) % GROUP_PALETTE.size
    return GROUP_PALETTE[idx]
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
) {
    var snapshot by remember(graphState) { mutableStateOf(graphState.snapshot()) }
    var draggedNodeId by remember { mutableStateOf<String?>(null) }
    var dragStartPos by remember { mutableStateOf(Offset.Zero) }
    var dragCurrentPos by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(graphState, reduceMotion) {
        while (true) {
            if (!reduceMotion) graphState.step()
            snapshot = graphState.snapshot()
            delay(33L)
        }
    }

    // Emit pixel positions into DomShadow so external drivers (adb scripts,
    // on-device test runners) can find nodes without coordinate guessing.
    // cx/cy/maxR are Canvas-local — the Canvas fills the full Box size.
    // Positions update every layout tick alongside the snapshot.
    val canvasSize = remember { androidx.compose.ui.unit.IntSize.Zero }
    androidx.compose.runtime.LaunchedEffect(snapshot) {
        // Canvas size is not directly available in LaunchedEffect; use snapshot
        // positions relative to a unit square so the consumer can scale.
        // Emitting radiusFraction + angle is coordinate-system-independent.
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

    Box(
        modifier = modifier.fillMaxSize()
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val maxR = minOf(cx, cy) * 0.88f
                    val snap = snapshot
                    val hitR = CLAIM_RADIUS * 2.5f
                    val hitRSq = hitR * hitR
                    val hitCandidates: List<Pair<String, Float>> = snap.positions.entries.map { entry ->
                        val nodePos = entry.value
                        val nx = cx + cos(nodePos.angle) * nodePos.radiusFraction * maxR
                        val ny = cy + sin(nodePos.angle) * nodePos.radiusFraction * maxR
                        val dx = nx - down.position.x
                        val dy = ny - down.position.y
                        entry.key to (dx * dx + dy * dy)
                    }
                    val hit: String = hitCandidates
                        .filter { it.second <= hitRSq }
                        .minByOrNull { it.second }
                        ?.first
                        ?: return@awaitEachGesture

                    dragStartPos = down.position
                    dragCurrentPos = down.position
                    var gestureStarted = false
                    var lastFrac = snap.positions[hit]?.radiusFraction ?: 1f

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break

                        if (!change.pressed) {
                            if (!gestureStarted) {
                                if (snap.nodes.any { it.id == hit && it.group != "entity" }) {
                                    onNodeTap(hit)
                                }
                            } else {
                                onNodeDragComplete(hit, 1f - lastFrac)
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
                            snapshot = graphState.snapshot()
                        }
                    }

                    if (gestureStarted && draggedNodeId != null) {
                        onNodeDragRelease()
                        draggedNodeId = null
                    }
                }
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val maxR = minOf(cx, cy) * 0.88f

            drawRect(color = BACKGROUND, size = size)
            drawRelevanceRings(cx, cy, maxR)
            drawEdges(snapshot, cx, cy, maxR)
            drawEntityNodes(snapshot, cx, cy, maxR)
            drawClaimNodes(snapshot, cx, cy, maxR)
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

private fun DrawScope.drawClaimNodes(s: FieldGraphSnapshot, cx: Float, cy: Float, maxR: Float) {
    for (node in s.nodes.filter { it.group != "entity" }) {
        val pos = s.positions[node.id] ?: continue
        val relevance = 1f - pos.radiusFraction
        val ageDecay = exp(-0.693f * node.ageSecs / AGE_HALF_LIFE_SEC).coerceIn(0.10f, 1f)
        val alpha = (0.30f + 0.70f * relevance) * ageDecay
        val center = Offset(
            cx + cos(pos.angle) * pos.radiusFraction * maxR,
            cy + sin(pos.angle) * pos.radiusFraction * maxR,
        )
        val color = groupColor(node.group)
        drawCircle(color = color.copy(alpha = alpha), radius = CLAIM_RADIUS, center = center)
        drawCircle(
            color = Color.Black.copy(alpha = 0.28f),
            radius = CLAIM_RADIUS,
            center = center,
            style = Stroke(width = 1f),
        )
    }
}
