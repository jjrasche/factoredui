package ai.factoredui.compose.fieldgraph.graph

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class FieldNode(
    val id: String,
    val group: String,
    val label: String,
    val ageSecs: Float = 0f,
)

data class FieldEdge(
    val fromId: String,
    val toId: String,
    val kind: String,
)

data class FieldGraphTopology(
    val nodes: List<FieldNode>,
    val edges: List<FieldEdge>,
) {
    companion object {
        fun fromJson(json: Json, body: String): FieldGraphTopology {
            val dto = json.decodeFromString(TopologyDto.serializer(), body)
            return FieldGraphTopology(
                nodes = dto.nodes.map {
                    FieldNode(id = it.id, group = it.group ?: "claim", label = it.label ?: it.id, ageSecs = it.ageSecs ?: 0f)
                },
                edges = dto.edges.map {
                    FieldEdge(fromId = it.from, toId = it.to, kind = it.kind ?: "")
                },
            )
        }
    }
}

@Serializable
internal data class TopologyDto(
    val nodes: List<NodeDto> = emptyList(),
    val edges: List<EdgeDto> = emptyList(),
)

@Serializable
internal data class NodeDto(
    val id: String,
    val group: String? = null,
    val label: String? = null,
    @SerialName("age_seconds") val ageSecs: Float? = null,
)

@Serializable
internal data class EdgeDto(
    val from: String,
    val to: String,
    val kind: String? = null,
)
