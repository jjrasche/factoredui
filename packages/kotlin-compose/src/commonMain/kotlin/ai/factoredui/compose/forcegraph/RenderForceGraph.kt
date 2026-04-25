package ai.factoredui.compose.forcegraph

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.factoredui.compose.forcegraph.graph.FiringHighlights
import ai.factoredui.compose.forcegraph.graph.FunctionGraphState
import ai.factoredui.compose.forcegraph.graph.FunctionGraphTopology
import ai.factoredui.compose.forcegraph.graph.FunctionNodeSpec
import ai.factoredui.compose.forcegraph.graph.PositionedFunctionNode
import ai.factoredui.compose.forcegraph.graph.SignalParticles
import ai.factoredui.compose.forcegraph.math.Camera
import ai.factoredui.compose.forcegraph.render.ForceGraphView
import ai.factoredui.compose.forcegraph.render.KindColor
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
 * Three layers, all driven by data the primitive fetches itself:
 *  1. The 3D force-directed canvas — nodes, edges, firing pulses, particles.
 *  2. A side panel listing recent live events with a kind-substring filter.
 *  3. A floating hover tooltip showing function + domain when the cursor
 *     is over a node.
 *
 * Expected wire formats are documented on [ForceGraphProps] in
 * spec-types.ts and SpecProps.kt.
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

    LaunchedEffect(props.topologyUrl) {
        if (props.topologyUrl.isEmpty()) {
            loadError = "forcegraph: missing topology_url"
            return@LaunchedEffect
        }
        runCatching {
            val response: HttpResponse = httpClient.get(props.topologyUrl)
            val body = response.bodyAsText()
            json.decodeFromString(ForceGraphTopologyDto.serializer(), body)
        }.fold(
            onSuccess = { dto ->
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

    // Recent-events ring + filter + hover state — all UI state, not exposed
    // outside the primitive.
    val recentEvents = remember { mutableStateListOf<EventEntry>() }
    var filterText by remember { mutableStateOf("") }
    var hoveredNode by remember { mutableStateOf<PositionedFunctionNode?>(null) }
    var hoverPos by remember { mutableStateOf(Offset.Zero) }

    val filteredEvents by remember(filterText) {
        derivedStateOf {
            val needle = filterText.trim().lowercase()
            if (needle.isEmpty()) recentEvents.toList()
            else recentEvents.filter { it.matches(needle) }
        }
    }

    LaunchedEffect(state) {
        val dt = 33f / 1000f
        while (true) {
            state.stepLayout(dt)
            highlights.sweep()
            particles.sweep()
            delay(33L)
        }
    }

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

                    // Mirror server-side observation events into the
                    // sidebar log. Bounded to MAX_LOG_ROWS to keep
                    // memory + render cost flat.
                    val entry = EventEntry.from(frame, p)
                    if (entry != null) {
                        recentEvents.add(entry)
                        while (recentEvents.size > MAX_LOG_ROWS) recentEvents.removeAt(0)
                    }

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

    Row(modifier = modifier.fillMaxSize().background(Color(0xFF0A0A12))) {
        // Canvas + tooltip overlay.
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            ForceGraphView(
                graphState = state,
                camera = camera,
                modifier = Modifier.fillMaxSize(),
                activeFunctionNames = active,
                particles = activeParticles,
                onPointerHover = { node, pos ->
                    hoveredNode = node
                    hoverPos = pos
                },
            )
            HoverTooltip(node = hoveredNode, position = hoverPos)
        }
        // Side panel: filter + scrolling event log.
        SignalLogPanel(
            events = filteredEvents,
            filterText = filterText,
            onFilterChange = { filterText = it },
            totalCount = recentEvents.size,
        )
    }
}

@Composable
private fun HoverTooltip(node: PositionedFunctionNode?, position: Offset) {
    if (node == null) return
    val density = LocalDensity.current
    Box(
        modifier = Modifier
            .offset(
                x = with(density) { (position.x + 14f).toDp() },
                y = with(density) { (position.y + 14f).toDp() },
            )
            .background(Color(0xCC0F1116), RoundedCornerShape(4.dp))
            .border(1.dp, Color(0x55FFFFFF), RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Column {
            Text(
                text = node.name,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                text = node.domainName,
                color = KindColor.colorFor(node.domainName),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
private fun SignalLogPanel(
    events: List<EventEntry>,
    filterText: String,
    onFilterChange: (String) -> Unit,
    totalCount: Int,
) {
    val listState = rememberLazyListState()
    // Auto-scroll to the newest event when the list grows, but only if the
    // user is already near the bottom (so scrolling up to read history
    // doesn't get yanked back).
    LaunchedEffect(events.size) {
        if (events.isEmpty()) return@LaunchedEffect
        val info = listState.layoutInfo
        val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
        val atBottom = lastVisible >= events.size - 2
        if (atBottom) {
            listState.scrollToItem(events.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .width(320.dp)
            .fillMaxHeight()
            .background(Color(0xFF0F1116))
            .padding(8.dp),
    ) {
        Text(
            text = "signals · $totalCount events",
            color = Color(0xFFA0A0B0),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        OutlinedTextField(
            value = filterText,
            onValueChange = onFilterChange,
            placeholder = {
                Text("filter by kind / function", fontSize = 11.sp, color = Color(0xFF666666))
            },
            singleLine = true,
            textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color.White),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF1C1E21),
                unfocusedContainerColor = Color(0xFF1C1E21),
                focusedIndicatorColor = Color(0x66FFFFFF),
                unfocusedIndicatorColor = Color(0x33FFFFFF),
                cursorColor = Color.White,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(6.dp))
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(events, key = { it.id }) { entry ->
                EventRow(entry)
            }
        }
    }
}

@Composable
private fun EventRow(entry: EventEntry) {
    val typeColor = when (entry.type) {
        "firing_started" -> Color(0xFFFFA94D)
        "firing_completed" -> Color(0xFF8CE99A)
        "signal_emitted" -> Color(0xFF74C0FC)
        else -> Color(0xFFAAAAAA)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(2.dp))
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = entry.timestamp,
            color = Color(0xFF555555),
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(end = 6.dp),
        )
        Text(
            text = entry.label,
            color = typeColor,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
}

// --- State + DTOs ---

private const val MAX_LOG_ROWS = 500

private data class EventEntry(
    val id: Long,
    val timestamp: String,
    val type: String,
    val kind: String?,
    val function: String?,
    val producer: String?,
    val label: String,
) {
    fun matches(needle: String): Boolean =
        kind?.lowercase()?.contains(needle) == true ||
        function?.lowercase()?.contains(needle) == true ||
        producer?.lowercase()?.contains(needle) == true ||
        type.lowercase().contains(needle)

    companion object {
        private var nextId: Long = 0
        fun from(frame: ForceGraphStreamFrame, p: ForceGraphStreamPayload): EventEntry? {
            val type = p.type ?: return null
            val ts = (frame.created_at ?: "").let {
                if (it.length >= 23) it.substring(11, 23) else "--:--:--.---"
            }
            val label = when (type) {
                "firing_started" -> "▶ ${p.function ?: "?"}"
                "firing_completed" -> "✓ ${p.function ?: "?"}"
                "signal_emitted" -> "• ${p.kind ?: "?"} from=${p.producer ?: "?"}"
                else -> "? $type"
            }
            return EventEntry(
                id = nextId++,
                timestamp = ts,
                type = type,
                kind = p.kind,
                function = p.function,
                producer = p.producer,
                label = label,
            )
        }
    }
}

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
internal data class ForceGraphStreamFrame(
    val created_at: String? = null,
    val payload: ForceGraphStreamPayload? = null,
)

@Serializable
internal data class ForceGraphStreamPayload(
    val type: String? = null,
    val function: String? = null,
    val producer: String? = null,
    val consumers: List<String>? = null,
    val kind: String? = null,
)
