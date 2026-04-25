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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
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
import ai.factoredui.compose.forcegraph.replay.ReplayControlBar
import ai.factoredui.compose.forcegraph.replay.ReplayController
import ai.factoredui.compose.forcegraph.replay.ReplayEvent
import ai.factoredui.compose.schema.ForceGraphProps
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * forcegraph primitive renderer.
 *
 * Four layers, all driven by data the primitive fetches itself:
 *  1. The 3D force-directed canvas — nodes, edges, firing pulses, particles.
 *  2. A side panel listing recent events with a kind-substring filter.
 *     In live mode, every SSE frame; in replay mode, events up to the cursor.
 *  3. A floating hover tooltip showing function + domain when the cursor
 *     is over a node.
 *  4. A bottom timeline / replay control bar (visible iff `history_url`
 *     is supplied) — play/pause, scrub slider, timestamp, counter, LIVE
 *     toggle. Replay mode pauses the SSE-as-source-of-truth pipeline and
 *     drives overlays from the cursor crossing historical events instead.
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
    val coroutineScope = rememberCoroutineScope()
    val active by highlights.active.collectAsState()
    val activeParticles by particles.active.collectAsState()

    // Replay controller. Always created; control bar only shows when a
    // history_url is configured. Live tail is bounded internally so a
    // long replay session can't OOM.
    val replay = remember { ReplayController() }
    val replayEvents by replay.events.collectAsState()
    val replayCursor by replay.cursor.collectAsState()
    val isLive by replay.isLive.collectAsState()
    val isPlaying by replay.isPlaying.collectAsState()

    var filterText by remember { mutableStateOf("") }
    var hoveredNode by remember { mutableStateOf<PositionedFunctionNode?>(null) }
    var hoverPos by remember { mutableStateOf(Offset.Zero) }

    // Visible event list:
    //   live mode → everything in the merged buffer (history + tail)
    //   replay mode → slice up to and including the cursor
    val visibleEvents by remember(replayEvents, replayCursor, isLive, filterText) {
        derivedStateOf {
            val raw = if (isLive) replayEvents else replay.visibleSlice()
            val needle = filterText.trim().lowercase()
            if (needle.isEmpty()) raw else raw.filter { it.matches(needle) }
        }
    }

    // Physics + sweep loop. Particles + highlights expire on wall-clock,
    // so this runs continuously regardless of replay state.
    LaunchedEffect(state) {
        val dt = 33f / 1000f
        while (true) {
            state.stepLayout(dt)
            highlights.sweep()
            particles.sweep()
            delay(33L)
        }
    }

    // Lazy history fetch. Triggers only the first time a non-null
    // history_url is provided. Re-fetching on toggle would be wasteful;
    // the live-tail buffer covers what arrives after the snapshot.
    LaunchedEffect(props.historyUrl) {
        val url = props.historyUrl
        if (url.isNullOrEmpty() || replay.hasHistory()) return@LaunchedEffect
        runCatching {
            val response: HttpResponse = httpClient.get(url)
            val body = response.bodyAsText()
            json.parseToJsonElement(body).jsonObject
        }.onSuccess { obj ->
            val parsed = obj["signals"]?.jsonArray.orEmpty().mapNotNull { node ->
                runCatching { ReplayEvent.fromHistoryRow(node.jsonObject) }.getOrNull()
            }
            replay.loadHistory(parsed)
        }
        // Errors are swallowed deliberately: replay is a non-essential
        // overlay, the live feed continues regardless.
    }

    // Replay playback ticker. Fires graph overlays each time the cursor
    // crosses an event during playback. No-op when not playing.
    LaunchedEffect(replay) {
        while (true) {
            delay(ReplayController.DEFAULT_STEP_INTERVAL_MS)
            val crossed = replay.advance()
            crossed.forEach { fireOverlaysFor(it, highlights, particles, coroutineScope, localTopology) }
        }
    }

    DisposableEffect(props.eventStreamUrl) {
        val url = props.eventStreamUrl
        if (url.isNullOrEmpty()) return@DisposableEffect onDispose { /* no-op */ }
        val sub = startSseSubscription(
            url = url,
            onMessage = { payloadText ->
                val frame = runCatching {
                    json.decodeFromString(ForceGraphStreamFrame.serializer(), payloadText)
                }.getOrNull() ?: return@startSseSubscription
                val p = frame.payload ?: return@startSseSubscription
                val event = ReplayEvent.fromLiveFrame(frame, p) ?: return@startSseSubscription

                // Always append to the replay buffer's live tail so
                // toggling back to LIVE picks up reality, not the
                // user's scrub position. appendLive is suspend
                // (Mutex-guarded); the SSE callback is invoked from
                // ktor's reader on JVM (Dispatchers.Default), so we
                // must launch on the composable scope to suspend.
                coroutineScope.launch { replay.appendLive(event) }

                // Live mode is the source of truth for graph overlays
                // (highlights + particles). Replay mode drives those
                // from the playback ticker; SSE-driven overlays would
                // double-fire and break the illusion.
                if (replay.isLive.value) {
                    fireOverlaysFor(event, highlights, particles, coroutineScope, localTopology)
                }
            },
            onError = { /* swallow — subscription is best-effort, retry happens on next composition */ },
        )
        onDispose { sub.close() }
    }

    Column(modifier = modifier.fillMaxSize().background(Color(0xFF0A0A12))) {
        Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
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
                events = visibleEvents,
                filterText = filterText,
                onFilterChange = { filterText = it },
                totalCount = if (isLive) replayEvents.size else (replayCursor + 1).coerceAtLeast(0),
                grandTotal = replayEvents.size,
                isLive = isLive,
            )
        }
        // Replay control bar — only when host wired a history endpoint.
        // Mutators are suspend (Mutex-guarded) so each callback launches
        // on the composable scope; cancellation cascades on disposal.
        if (!props.historyUrl.isNullOrEmpty()) {
            ReplayControlBar(
                events = replayEvents,
                cursor = replayCursor,
                isLive = isLive,
                isPlaying = isPlaying,
                onTogglePlay = {
                    coroutineScope.launch { replay.setPlaying(!replay.isPlaying.value) }
                },
                onSeek = { idx ->
                    coroutineScope.launch { replay.seekTo(idx) }
                },
                onToggleLive = {
                    coroutineScope.launch { replay.setLive(!replay.isLive.value) }
                },
            )
        }
    }
}

private fun fireOverlaysFor(
    event: ReplayEvent,
    highlights: FiringHighlights,
    particles: SignalParticles,
    scope: kotlinx.coroutines.CoroutineScope,
    topology: FunctionGraphTopology,
) {
    when (event.type) {
        "firing_started", "firing_completed" -> {
            val fn = event.function ?: return
            scope.launch { highlights.mark(fn) }
        }
        "signal_emitted" -> {
            val from = event.producer ?: return
            val kind = event.kind ?: ""
            scope.launch {
                // Resolve `producer` against the topology. Servers may
                // pass either a function name (functions emitting from
                // their handler) or a domain/daemon name (heartbeats,
                // ad-hoc /signal POSTs). Match function names exactly;
                // if none match, treat `from` as a domain and pulse
                // every node in that group. Empty result = unattributable
                // emission, just no-op.
                val nodes = topology.nodes
                val matchedByName = nodes.filter { it.name == from }
                val resolved = if (matchedByName.isNotEmpty()) {
                    matchedByName.map { it.name }
                } else {
                    nodes.filter { it.domainName == from }.map { it.name }
                }
                // Pulse the producer (or its domain peers) on every
                // emission, regardless of whether anyone consumes the
                // kind. Without this, a platform with mostly-no-consumer
                // kinds (heartbeats, ad-hoc /signal POSTs) looks dead
                // even when traffic is flowing — particles only animate
                // edges, and there are no edges for unconsumed kinds.
                resolved.forEach { highlights.mark(it) }
                event.consumers.forEach { to ->
                    // Particles need both endpoints to exist as nodes —
                    // the producer might be a domain that maps to many,
                    // so spawn one particle per (resolved-from, to) pair.
                    val particleFrom = resolved.firstOrNull() ?: from
                    particles.spawn(
                        fromFunction = particleFrom,
                        toFunction = to,
                        kind = kind,
                        durationMs = 700L,
                    )
                }
            }
        }
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
    events: List<ReplayEvent>,
    filterText: String,
    onFilterChange: (String) -> Unit,
    totalCount: Int,
    grandTotal: Int,
    isLive: Boolean,
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
        val header = if (isLive) {
            "signals · $totalCount events"
        } else {
            "signals · $totalCount/$grandTotal (replay)"
        }
        Text(
            text = header,
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
private fun EventRow(entry: ReplayEvent) {
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

// --- DTOs + ReplayEvent factories ---

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

private fun ReplayEvent.matches(needle: String): Boolean =
    kind?.lowercase()?.contains(needle) == true ||
    function?.lowercase()?.contains(needle) == true ||
    producer?.lowercase()?.contains(needle) == true ||
    type.lowercase().contains(needle)

private object ReplayEventIds {
    private var next: Long = 0
    fun nextId(): Long {
        val id = next
        next += 1
        return id
    }
}

private fun timestampFromIso(iso: String?): String =
    (iso ?: "").let { if (it.length >= 23) it.substring(11, 23) else "--:--:--.---" }

private fun labelFor(type: String, function: String?, producer: String?, kind: String?): String =
    when (type) {
        "firing_started" -> "▶ ${function ?: "?"}"
        "firing_completed" -> "✓ ${function ?: "?"}"
        "signal_emitted" -> "• ${kind ?: "?"} from=${producer ?: "?"}"
        else -> "? $type"
    }

private fun ReplayEvent.Companion.fromLiveFrame(
    frame: ForceGraphStreamFrame,
    payload: ForceGraphStreamPayload,
): ReplayEvent? {
    val type = payload.type ?: return null
    return ReplayEvent(
        id = ReplayEventIds.nextId(),
        timestamp = timestampFromIso(frame.created_at),
        type = type,
        kind = payload.kind,
        function = payload.function,
        producer = payload.producer,
        consumers = payload.consumers ?: emptyList(),
        label = labelFor(type, payload.function, payload.producer, payload.kind),
    )
}

private fun ReplayEvent.Companion.fromHistoryRow(row: JsonObject): ReplayEvent? {
    // History rows come from the storage layer: { id, kind: "signalgraph.event",
    // payload: { type, function, producer, consumers, kind }, created_at, … }.
    // The inner payload mirrors the live SSE frame.
    val createdAt = row["created_at"]?.jsonPrimitive?.contentOrNullSafe()
    val payload = row["payload"]?.jsonObject ?: return null
    val type = payload["type"]?.jsonPrimitive?.contentOrNullSafe() ?: return null
    val function = payload["function"]?.jsonPrimitive?.contentOrNullSafe()
    val producer = payload["producer"]?.jsonPrimitive?.contentOrNullSafe()
    val kind = payload["kind"]?.jsonPrimitive?.contentOrNullSafe()
    val consumers = payload["consumers"]?.jsonArray
        ?.mapNotNull { it.jsonPrimitive.contentOrNullSafe() }
        ?: emptyList()
    return ReplayEvent(
        id = ReplayEventIds.nextId(),
        timestamp = timestampFromIso(createdAt),
        type = type,
        kind = kind,
        function = function,
        producer = producer,
        consumers = consumers,
        label = labelFor(type, function, producer, kind),
    )
}

// kotlinx-serialization's JsonPrimitive.contentOrNull was added later;
// older versions only expose `content`. This shim keeps us compatible
// either way and tolerates JsonNull gracefully.
private fun kotlinx.serialization.json.JsonPrimitive.contentOrNullSafe(): String? =
    if (this is kotlinx.serialization.json.JsonNull) null else content
