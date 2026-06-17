package ai.factoredui.compose.fieldgraph.graph

data class FieldNode(
    val id: String,
    val group: String,
    val label: String,
)

data class FieldEdge(
    val fromId: String,
    val toId: String,
    val kind: String,
)

data class FieldGraphTopology(
    val nodes: List<FieldNode>,
    val edges: List<FieldEdge>,
)
