package ai.factoredui.compose.fieldgraph.graph

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

data class FieldNodePosition(
    val angle: Float,
    val radiusFraction: Float,
    val isUserAnchored: Boolean = false,
    val settledness: Float = 0f,
)

data class FieldGraphSnapshot(
    val nodes: List<FieldNode>,
    val positions: Map<String, FieldNodePosition>,
    val edges: List<FieldEdge>,
)

class FieldGraphState(topology: FieldGraphTopology) {
    val logItems: List<FieldLogItem> = topology.logItems
    private val nodes: List<FieldNode> = topology.nodes
    private val edges: List<FieldEdge> = topology.edges
    private val positions: MutableMap<String, FieldNodePosition> = LinkedHashMap()
    private var frozenNodeId: String? = null
    private var virtualTimeOffsetSecs: Float = 0f

    init {
        seedPositions()
    }

    private fun seedPositions() {
        val count = nodes.size.coerceAtLeast(1)
        nodes.forEachIndexed { index, node ->
            val baseAngle = (2f * PI.toFloat() * index) / count
            val jitter = (node.id.hashCode() * 0.000001f) % (PI.toFloat() * 0.2f)
            val radiusBand = 0.80f + (index % 4) * 0.05f
            positions[node.id] = FieldNodePosition(
                angle = baseAngle + jitter,
                radiusFraction = radiusBand.coerceAtMost(0.97f),
                isUserAnchored = false,
                settledness = 0f,
            )
        }
    }

    fun freezeNode(nodeId: String) { frozenNodeId = nodeId }
    fun releaseNode() { frozenNodeId = null }
    fun advanceTime(deltaSecs: Float) { virtualTimeOffsetSecs += deltaSecs }

    fun moveNode(nodeId: String, angle: Float, radiusFraction: Float) {
        positions[nodeId] = FieldNodePosition(
            angle = angle,
            radiusFraction = radiusFraction.coerceIn(0f, 1f),
            isUserAnchored = true,
            settledness = 1f,
        )
    }

    fun relevanceMagnitude(nodeId: String): Float =
        1f - (positions[nodeId]?.radiusFraction ?: 1f)

    fun step() {
        val frozen = frozenNodeId
        val movers = positions.entries
            .filter { (id, pos) -> id != frozen && !pos.isUserAnchored && pos.settledness < 1f }
            .sortedBy { (_, pos) -> pos.settledness }
            .take(4)
        for ((id, pos) in movers) {
            positions[id] = nudgeTowardSpread(id, pos)
        }
    }

    private fun nudgeTowardSpread(id: String, pos: FieldNodePosition): FieldNodePosition {
        var angleAdjust = 0f
        for ((otherId, otherPos) in positions) {
            if (otherId == id) continue
            val radDiff = abs(pos.radiusFraction - otherPos.radiusFraction)
            val angleDiff = angleDelta(pos.angle, otherPos.angle)
            if (abs(angleDiff) < 0.25f && radDiff < 0.12f) {
                angleAdjust += if (angleDiff >= 0f) 0.025f else -0.025f
            }
        }
        return pos.copy(
            angle = pos.angle + angleAdjust,
            settledness = (pos.settledness + 0.015f).coerceAtMost(1f),
        )
    }

    private fun angleDelta(a: Float, b: Float): Float {
        var d = a - b
        while (d > PI.toFloat()) d -= 2f * PI.toFloat()
        while (d < -PI.toFloat()) d += 2f * PI.toFloat()
        return d
    }

    fun snapshot(): FieldGraphSnapshot = FieldGraphSnapshot(
        nodes = nodes.map { it.copy(ageSecs = it.ageSecs + virtualTimeOffsetSecs) },
        positions = positions.toMap(),
        edges = edges.toList(),
    )
}
