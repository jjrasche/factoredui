package ai.factoredui.compose.forcegraph

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ai.factoredui.compose.forcegraph.graph.FiringHighlights
import ai.factoredui.compose.forcegraph.graph.FunctionGraphState
import ai.factoredui.compose.forcegraph.graph.FunctionGraphTopology
import ai.factoredui.compose.forcegraph.graph.FunctionNodeSpec
import ai.factoredui.compose.forcegraph.graph.SignalParticles
import ai.factoredui.compose.forcegraph.math.Camera
import ai.factoredui.compose.forcegraph.render.ForceGraphView
import ai.factoredui.compose.schema.ForceGraphProps
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * forcegraph primitive renderer.
 *
 * Takes a [ForceGraphProps] describing a topology URL and optional live
 * event stream, fetches both, runs a force-directed physics simulation,
 * and renders with firing highlights + particles overlaying signal flow.
 *
 * Expected topology JSON shape (from `props.topologyUrl`):
 *   {
 *     "nodes": [ { "id": "...", "group": "...", "label": "..." } ],
 *     "edges": [ { "from": "...", "to": "...", "kind": "..." } ]
 *   }
 *
 * Expected event-stream shape (SSE `data:` lines from `props.eventStreamUrl`):
 *   {
 *     "payload": {
 *       "type": "firing_started" | "firing_completed" | "signal_emitted",
 *       "function": "...",              // firing_* only
 *       "producer": "...",              // signal_emitted only
 *       "consumers": [...],             // signal_emitted only
 *       "kind": "..."                   // signal_emitted only
 *     }
 *   }
 *
 * Agent-platform's signal-graph domain emits exactly this shape from
 * `GET /signal-graph/data` and `GET /signal-graph/stream`.
 */
@Composable
fun RenderForceGraph(props: ForceGraphProps, modifier: Modifier = Modifier) {
    val json = remember { Json { ignoreUnknownKeys = true } }
    val httpClient = remember { HttpClient() }
    DisposableEffect(httpClient) {
        onDispose { httpClient.close() }
    }

    var topology by remember { mutableStateOf<FunctionGraphTopology?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }

    // Fetch topology once on first composition (or when topologyUrl changes).
    LaunchedEffect(props.topologyUrl) {
        if (props.topologyUrl.isEmpty()) {
            loadError = "forcegraph: missing topology_url"
            return@LaunchedEffect
        }
        val result = runCatching {
            val response: HttpResponse = httpClient.get(props.topologyUrl)
            val body = response.bodyAsText()
            json.decodeFromString(ForceGraphTopologyDto.serializer(), body)
        }
        result.fold(
            onSuccess = { dto ->
                // Topology builder wires edges from consumes/emits, but the
                // server hands us the edges explicitly. Build a topology
                // with nodes + explicit edges, bypassing the derive step.
                topology = FunctionGraphTopology(
                    nodes = dto.nodes.map { FunctionNodeSpec(
                        name = it.id,
                        domainName = it.group ?: "default",
                        consumes = emptyList(),
                        emits = emptyList(),
                    ) },
                    edges = dto.edges.map { e ->
                        ai.factoredui.compose.forcegraph.graph.FunctionEdge(
                            fromFunction = e.from,
                            toFunction = e.to,
                            signalKind = e.kind ?: "",
                        )
                    },
                )
            },
            onFailure = { loadError = "forcegraph: fetch failed — ${it.message ?: it::class.simpleName}" },
        )
    }

    val localTopology = topology
    if (localTopology == null) {
        Box(modifier = modifier.fillMaxSize().background(Color(0xFF0A0A12))) {
            Text(
                text = loadError ?: "forcegraph: loading…",
                color = Color(0xFFA0A0B0),
                modifier = Modifier.padding(12.dp),
            )
        }
        return
    }

    val state = remember(localTopology) { FunctionGraphState(localTopology) }
    val highlights = remember { FiringHighlights() }
    val particles = remember { SignalParticles() }
    val camera = remember { Camera() }
    val active by highlights.active.collectAsState()
    val activeParticles by particles.active.collectAsState()

    // Physics + sweep loop at ~30fps.
    LaunchedEffect(state) {
        val dt = 33f / 1000f
        while (true) {
            state.stepLayout(dt)
            highlights.sweep()
            particles.sweep()
            delay(33L)
        }
    }

    // Live event stream. SSE over HTTP/1.1. Uses plain HTTP reads so we
    // stay on ktor-client-core without pulling in the SSE plugin — each
    // `data: <json>` line is parsed independently and fed to highlights
    // + particles.
    LaunchedEffect(props.eventStreamUrl) {
        val url = props.eventStreamUrl
        if (url.isNullOrEmpty()) return@LaunchedEffect
        runCatching {
            httpClient.get(url) { /* default Accept: */ }.let { response ->
                val channel = response.bodyAsChannel()
                while (true) {
                    val line = channel.readUTF8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    val payloadText = line.removePrefix("data:").trim()
                    if (payloadText.isEmpty()) continue
                    val frame = runCatching {
                        json.decodeFromString(ForceGraphStreamFrame.serializer(), payloadText)
                    }.getOrNull() ?: continue
                    val p = frame.payload ?: continue
                    when (p.type) {
                        "firing_started", "firing_completed" -> {
                            p.function?.let { highlights.mark(it) }
                        }
                        "signal_emitted" -> {
                            val from = p.producer ?: return@let
                            val kind = p.kind ?: ""
                            p.consumers?.forEach { to ->
                                particles.spawn(fromFunction = from, toFunction = to, kind = kind, durationMs = 700L)
                            }
                        }
                    }
                }
            }
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color(0xFF0A0A12))) {
        ForceGraphView(
            graphState = state,
            camera = camera,
            modifier = Modifier.fillMaxSize(),
            activeFunctionNames = active,
            particles = activeParticles,
        )
    }
}

// --- Wire-format DTOs ---

@Serializable
private data class ForceGraphTopologyDto(
    val nodes: List<ForceGraphNodeDto> = emptyList(),
    val edges: List<ForceGraphEdgeDto> = emptyList(),
)

@Serializable
private data class ForceGraphNodeDto(
    val id: String,
    val group: String? = null,
    val label: String? = null,
)

@Serializable
private data class ForceGraphEdgeDto(
    val from: String,
    val to: String,
    val kind: String? = null,
)

@Serializable
private data class ForceGraphStreamFrame(
    val payload: ForceGraphStreamPayload? = null,
)

@Serializable
private data class ForceGraphStreamPayload(
    val type: String? = null,
    val function: String? = null,
    val producer: String? = null,
    val consumers: List<String>? = null,
    val kind: String? = null,
)
