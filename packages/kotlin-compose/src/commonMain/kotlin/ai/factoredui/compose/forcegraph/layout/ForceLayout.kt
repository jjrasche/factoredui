package ai.factoredui.compose.forcegraph.layout

import ai.factoredui.compose.forcegraph.math.Vec3
import kotlin.math.max

/**
 * A node managed by the force layout. Identity is carried through the
 * opaque `id`. Optional `anchorPosition` pulls the node toward a fixed
 * location (used to cluster nodes by domain — every function in a domain
 * shares the same anchor). `metadataId` is an opaque label the renderer
 * uses (function name today; signal id in earlier revisions).
 */
class LayoutNode(
    val id: String,
    val metadataId: String,
    val groupName: String,
    var position: Vec3,
    var velocity: Vec3 = Vec3.ZERO,
    val anchorPosition: Vec3? = null,
    val anchorStrength: Float = 0.25f,
)

data class LayoutEdge(val fromId: String, val toId: String)

/**
 * Quadratic-cost 3D spring/Coulomb simulator with optional per-node
 * anchors. Good for hundreds of nodes; swap for octree Barnes-Hut at
 * larger scale (same public API).
 *
 * Per-step integration:
 *   - Coulomb repulsion between every pair of nodes (1/r²)
 *   - Hooke spring attraction along each edge toward rest length
 *   - Anchor spring pulling each node toward its domain cluster center
 *     (only applied when `anchorPosition` is non-null)
 *   - Global centering pull toward origin (keeps disconnected components
 *     in frame when they don't declare an anchor)
 *   - Velocity damping (friction)
 */
class ForceLayout(
    val nodes: MutableList<LayoutNode> = mutableListOf(),
    val edges: MutableList<LayoutEdge> = mutableListOf(),
    val repulsionStrength: Float = 200f,
    val springStiffness: Float = 0.4f,
    val springRestLength: Float = 3f,
    val centeringStrength: Float = 0.02f,
    val damping: Float = 0.85f,
    val maxVelocity: Float = 20f,
) {

    fun step(dtSeconds: Float) {
        if (nodes.isEmpty()) return
        val forces = MutableList(nodes.size) { Vec3.ZERO }
        applyRepulsion(forces)
        applyAttraction(forces)
        applyAnchors(forces)
        applyCentering(forces)
        integrate(forces, dtSeconds)
    }

    private fun applyRepulsion(forces: MutableList<Vec3>) {
        for (i in nodes.indices) {
            for (j in i + 1 until nodes.size) {
                val a = nodes[i]
                val b = nodes[j]
                val delta = a.position - b.position
                val distSquared = max(delta.lengthSquared(), MIN_DIST_SQUARED)
                val magnitude = repulsionStrength / distSquared
                val direction = delta.normalize()
                val push = direction * magnitude
                forces[i] = forces[i] + push
                forces[j] = forces[j] - push
            }
        }
    }

    private fun applyAttraction(forces: MutableList<Vec3>) {
        val indexById = nodes.withIndex().associate { (i, node) -> node.id to i }
        for (edge in edges) {
            val i = indexById[edge.fromId] ?: continue
            val j = indexById[edge.toId] ?: continue
            val a = nodes[i]
            val b = nodes[j]
            val delta = b.position - a.position
            val distance = max(delta.length(), Vec3.EPSILON)
            val displacement = distance - springRestLength
            val direction = delta * (1f / distance)
            val pull = direction * (springStiffness * displacement)
            forces[i] = forces[i] + pull
            forces[j] = forces[j] - pull
        }
    }

    private fun applyAnchors(forces: MutableList<Vec3>) {
        for (i in nodes.indices) {
            val node = nodes[i]
            val anchor = node.anchorPosition ?: continue
            val delta = anchor - node.position
            forces[i] = forces[i] + delta * node.anchorStrength
        }
    }

    private fun applyCentering(forces: MutableList<Vec3>) {
        for (i in nodes.indices) {
            val node = nodes[i]
            // Nodes with an explicit anchor already have a clustering force;
            // skip the origin pull so anchored clusters can sit off-origin.
            if (node.anchorPosition != null) continue
            forces[i] = forces[i] + (-node.position) * centeringStrength
        }
    }

    private fun integrate(forces: MutableList<Vec3>, dt: Float) {
        for (i in nodes.indices) {
            val node = nodes[i]
            val newVelocity = clampVelocity((node.velocity + forces[i] * dt) * damping)
            node.velocity = newVelocity
            node.position = node.position + newVelocity * dt
        }
    }

    private fun clampVelocity(v: Vec3): Vec3 {
        val len = v.length()
        if (len <= maxVelocity) return v
        return v * (maxVelocity / len)
    }

    companion object {
        private const val MIN_DIST_SQUARED = 0.05f
    }
}
