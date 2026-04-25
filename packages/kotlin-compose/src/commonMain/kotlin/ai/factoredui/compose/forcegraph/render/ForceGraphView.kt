package ai.factoredui.compose.forcegraph.render

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import ai.factoredui.compose.forcegraph.graph.FunctionEdge
import ai.factoredui.compose.forcegraph.graph.FunctionGraphSnapshot
import ai.factoredui.compose.forcegraph.graph.FunctionGraphState
import ai.factoredui.compose.forcegraph.graph.PositionedFunctionNode
import ai.factoredui.compose.forcegraph.graph.SignalParticle
import ai.factoredui.compose.forcegraph.math.Camera
import ai.factoredui.compose.forcegraph.math.ProjectedPoint
import ai.factoredui.compose.forcegraph.math.project
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock

/**
 * 3D architectural graph renderer. Nodes = functions, grouped by domain
 * via domain-anchored force layout. Edges = signal kinds flowing between
 * functions (labeled by kind, colored by kind). Firing highlights layer
 * on top via [activeFunctionNames].
 *
 * Rendering order (painter's algorithm, back-to-front by depth):
 *   1. Solid background
 *   2. Edges (lines tinted by signal-kind color)
 *   3. Function nodes (spheres tinted by domain color)
 *   4. Firing pulse rings on top of active nodes
 */
private const val BASE_RADIUS_PX = 14f
private val BACKGROUND_COLOR = Color(0xFF0A0A12)

@Composable
fun ForceGraphView(
    graphState: FunctionGraphState,
    camera: Camera,
    modifier: Modifier = Modifier,
    activeFunctionNames: Set<String> = emptySet(),
    particles: List<SignalParticle> = emptyList(),
    nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    /**
     * Fired on every pointer move with (hovered-node-or-null, cursor offset
     * within the canvas). The parent can use this to render a tooltip,
     * highlight the node externally, etc. Hit-testing uses a generous 2x
     * radius so users don't have to be pixel-perfect.
     */
    onPointerHover: (PositionedFunctionNode?, Offset) -> Unit = { _, _ -> },
) {
    var snapshot by remember {
        mutableStateOf(FunctionGraphSnapshot(nodes = emptyList(), edges = emptyList(), domainAnchors = emptyMap()))
    }
    var cameraGeneration by remember { mutableStateOf(0) }

    LaunchedEffect(graphState) {
        while (true) {
            snapshot = graphState.snapshot()
            delay(33L)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures { _, drag ->
                    camera.drag(drag.x, drag.y)
                    cameraGeneration++
                }
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        when (event.type) {
                            PointerEventType.Scroll -> {
                                val scrollDeltaY = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                                camera.zoom(-scrollDeltaY)
                                cameraGeneration++
                                event.changes.forEach { it.consume() }
                            }
                            PointerEventType.Move -> {
                                val pos = event.changes.firstOrNull()?.position ?: continue
                                val width = size.width.toFloat()
                                val height = size.height.toFloat()
                                if (width <= 0f || height <= 0f) continue
                                val view = camera.viewMatrix()
                                val proj = camera.projectionMatrix(aspect = width / height)
                                val nearest = snapshot.nodes
                                    .asSequence()
                                    .map { node ->
                                        val p = node.position.project(view, proj, width, height)
                                        Triple(node, p, run {
                                            val dx = p.x - pos.x
                                            val dy = p.y - pos.y
                                            dx * dx + dy * dy
                                        })
                                    }
                                    .filter { (_, p, _) -> p.visible }
                                    .filter { (_, _, d2) ->
                                        // 2x base radius so the hit area is forgiving
                                        d2 <= (BASE_RADIUS_PX * 2f) * (BASE_RADIUS_PX * 2f)
                                    }
                                    .minByOrNull { (_, _, d2) -> d2 }
                                onPointerHover(nearest?.first, pos)
                            }
                            PointerEventType.Exit -> onPointerHover(null, Offset.Zero)
                            else -> Unit
                        }
                    }
                }
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            @Suppress("UNUSED_EXPRESSION")
            cameraGeneration

            val width = size.width
            val height = size.height
            drawRect(color = BACKGROUND_COLOR, size = size)

            val view = camera.viewMatrix()
            val proj = camera.projectionMatrix(aspect = if (height > 0f) width / height else 1f)

            val projections = snapshot.nodes.associate { node ->
                node.name to node.position.project(view, proj, width, height)
            }
            val domainByName = snapshot.nodes.associate { it.name to it.domainName }

            val now = nowMillis()
            drawEdges(snapshot.edges, projections, domainByName)
            drawParticles(particles, projections, now)
            drawFunctionNodes(snapshot.nodes, projections, activeFunctionNames, now)
        }
    }
}

private fun DrawScope.drawParticles(
    particles: List<SignalParticle>,
    projections: Map<String, ProjectedPoint>,
    nowMillis: Long,
) {
    for (particle in particles) {
        val from = projections[particle.fromFunction] ?: continue
        val to = projections[particle.toFunction] ?: continue
        if (!from.visible && !to.visible) continue
        val t = particle.progress(nowMillis)
        // Ease-out cubic so the particle slows as it approaches the
        // destination — reads as "arriving," not "bouncing past."
        val eased = 1f - (1f - t) * (1f - t) * (1f - t)
        val x = from.x + (to.x - from.x) * eased
        val y = from.y + (to.y - from.y) * eased
        // Depth for sizing — interpolate the endpoints' depth too.
        val depth = from.depth + (to.depth - from.depth) * eased
        val depthFactor = 1f / (1f + kotlin.math.max(0f, depth))
        val kindColor = KindColor.colorFor(particle.kind)
        // Fade in the first 15 % and fade out the last 15 % so spawn/
        // expire don't pop.
        val fade = when {
            t < 0.15f -> t / 0.15f
            t > 0.85f -> (1f - t) / 0.15f
            else -> 1f
        }
        val coreRadius = 6.5f * depthFactor
        // Glow halo
        drawCircle(
            color = kindColor.copy(alpha = 0.35f * fade),
            radius = coreRadius * 2.2f,
            center = Offset(x, y),
        )
        // Bright core
        drawCircle(
            color = kindColor.copy(alpha = (0.9f * fade).coerceIn(0f, 1f)),
            radius = coreRadius,
            center = Offset(x, y),
        )
        // Tiny white hot-center
        drawCircle(
            color = Color.White.copy(alpha = (0.85f * fade).coerceIn(0f, 1f)),
            radius = coreRadius * 0.35f,
            center = Offset(x, y),
        )
    }
}

private fun DrawScope.drawEdges(
    edges: List<FunctionEdge>,
    projections: Map<String, ProjectedPoint>,
    domainByName: Map<String, String>,
) {
    val sortedEdges = edges.sortedByDescending { edge ->
        val a = projections[edge.fromFunction]?.depth ?: Float.MAX_VALUE
        val b = projections[edge.toFunction]?.depth ?: Float.MAX_VALUE
        maxOf(a, b)
    }
    for (edge in sortedEdges) {
        val start = projections[edge.fromFunction] ?: continue
        val end = projections[edge.toFunction] ?: continue
        if (!start.visible && !end.visible) continue
        val kindColor = KindColor.colorFor(edge.signalKind)
        // Intra-domain edges get a little dimmer — the interesting flow
        // is cross-domain, and dim-inside-bright-outside reads that way.
        val crossDomain = domainByName[edge.fromFunction] != domainByName[edge.toFunction]
        val alpha = if (crossDomain) 0.55f else 0.28f
        drawLine(
            color = kindColor.copy(alpha = alpha),
            start = Offset(start.x, start.y),
            end = Offset(end.x, end.y),
            strokeWidth = if (crossDomain) 1.8f else 1.2f,
        )
    }
}

private fun DrawScope.drawFunctionNodes(
    nodes: List<PositionedFunctionNode>,
    projections: Map<String, ProjectedPoint>,
    activeFunctionNames: Set<String>,
    nowMillis: Long,
) {
    val ordered = nodes.sortedByDescending { projections[it.name]?.depth ?: Float.MAX_VALUE }
    for (node in ordered) {
        val projected = projections[node.name] ?: continue
        if (!projected.visible) continue
        val depthFactor = 1f / (1f + kotlin.math.max(0f, projected.depth))
        val radius = BASE_RADIUS_PX * depthFactor
        val domainColor = KindColor.colorFor(node.domainName)
        val alpha = (0.55f + 0.45f * depthFactor).coerceIn(0f, 1f)
        drawCircle(
            color = domainColor.copy(alpha = alpha),
            radius = radius,
            center = Offset(projected.x, projected.y),
        )
        drawCircle(
            color = Color.Black.copy(alpha = 0.35f),
            radius = radius,
            center = Offset(projected.x, projected.y),
            style = Stroke(width = 1f),
        )

        if (node.name in activeFunctionNames) {
            // Firing highlight — layered treatment that pops against the
            // dark bg: a bright colored halo, a pulsing white outer ring
            // keyed to wall-clock time, and a restated solid core so the
            // kind color remains obvious.
            drawCircle(
                color = domainColor.copy(alpha = 0.65f),
                radius = radius * 2.6f,
                center = Offset(projected.x, projected.y),
            )
            val pulse = 0.5f + 0.5f * kotlin.math.sin((nowMillis % 800L).toFloat() / 800f * 2f * kotlin.math.PI.toFloat())
            drawCircle(
                color = Color.White.copy(alpha = 0.35f + 0.5f * pulse),
                radius = radius * (3.1f + 0.6f * pulse),
                center = Offset(projected.x, projected.y),
                style = Stroke(width = 2.8f),
            )
            drawCircle(
                color = domainColor.copy(alpha = 1f),
                radius = radius * 1.25f,
                center = Offset(projected.x, projected.y),
            )
        }
    }
}
