package ai.factoredui.compose.fieldgraph

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ai.factoredui.compose.fieldgraph.graph.FieldEdge
import ai.factoredui.compose.fieldgraph.graph.FieldGraphState
import ai.factoredui.compose.fieldgraph.graph.FieldGraphTopology
import ai.factoredui.compose.fieldgraph.graph.FieldNode
import ai.factoredui.compose.fieldgraph.render.FieldGraphView
import ai.factoredui.compose.renderer.RenderContext
import ai.factoredui.compose.renderer.dispatch
import ai.factoredui.compose.schema.ActionRef
import ai.factoredui.compose.schema.FieldGraphProps
import ai.factoredui.compose.schema.SpecValue
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Composable
fun RenderFieldGraph(
    props: FieldGraphProps,
    primitiveId: String,
    context: RenderContext,
    modifier: Modifier = Modifier,
) {
    val json = remember { Json { ignoreUnknownKeys = true } }
    val httpClient = remember { HttpClient() }
    DisposableEffect(httpClient) { onDispose { httpClient.close() } }

    var topology by remember { mutableStateOf<FieldGraphTopology?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(props.topologyUrl) {
        if (props.topologyUrl.isEmpty()) {
            loadError = "fieldgraph: missing topology_url"
            return@LaunchedEffect
        }
        runCatching {
            httpClient.get(props.topologyUrl).bodyAsText()
        }.fold(
            onSuccess = { body ->
                val dto = json.decodeFromString(FieldGraphTopologyDto.serializer(), body)
                topology = FieldGraphTopology(
                    nodes = dto.nodes.map {
                        FieldNode(id = it.id, group = it.group ?: "claim", label = it.label ?: it.id, ageSecs = it.ageSecs ?: 0f)
                    },
                    edges = dto.edges.map {
                        FieldEdge(fromId = it.from, toId = it.to, kind = it.kind ?: "")
                    },
                )
            },
            onFailure = { err ->
                loadError = "fieldgraph: fetch failed — ${err.message ?: err::class.simpleName}"
            },
        )
    }

    val localTopology = topology
    if (localTopology == null) {
        Box(modifier = modifier.fillMaxSize().background(Color(0xFF08080F))) {
            Text(
                text = loadError ?: "fieldgraph: loading…",
                color = Color(0xFFA0A0B0),
                modifier = Modifier.padding(12.dp),
            )
        }
        return
    }

    val state = remember(localTopology) { FieldGraphState(localTopology) }
    val coroutineScope = rememberCoroutineScope()

    FieldGraphView(
        graphState = state,
        reduceMotion = props.reduceMotion,
        modifier = modifier.fillMaxSize(),
        onNodeDragStart = { nodeId -> state.freezeNode(nodeId) },
        onNodeDragUpdate = { nodeId, angle, frac -> state.moveNode(nodeId, angle, frac) },
        onNodeDragRelease = { state.releaseNode() },
        onNodeDragComplete = { nodeId, magnitude ->
            val actionName = props.onRelevanceChangeAction ?: return@FieldGraphView
            coroutineScope.launch {
                context.dispatch(
                    primitiveId,
                    ActionRef(
                        action = actionName,
                        params = mapOf(
                            "node_id" to SpecValue.StringValue(nodeId),
                            "relevance_magnitude" to SpecValue.NumberValue(magnitude.toDouble()),
                        ),
                    ),
                )
            }
        },
        onNodeTap = { nodeId ->
            val actionName = props.onNodeTapAction ?: return@FieldGraphView
            coroutineScope.launch {
                context.dispatch(
                    primitiveId,
                    ActionRef(
                        action = actionName,
                        params = mapOf("node_id" to SpecValue.StringValue(nodeId)),
                    ),
                )
            }
        },
    )
}

@Serializable
private data class FieldGraphTopologyDto(
    val nodes: List<FieldGraphNodeDto> = emptyList(),
    val edges: List<FieldGraphEdgeDto> = emptyList(),
)

@Serializable
private data class FieldGraphNodeDto(
    val id: String,
    val group: String? = null,
    val label: String? = null,
    @kotlinx.serialization.SerialName("age_seconds") val ageSecs: Float? = null,
)

@Serializable
private data class FieldGraphEdgeDto(
    val from: String,
    val to: String,
    val kind: String? = null,
)
