package ai.factoredui.compose.forcegraph.graph

import ai.factoredui.compose.forcegraph.layout.ForceLayout
import ai.factoredui.compose.forcegraph.layout.LayoutEdge
import ai.factoredui.compose.forcegraph.layout.LayoutNode
import ai.factoredui.compose.forcegraph.math.Vec3
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * A function node pinned in 3D space. Position is updated in-place by the
 * force layout; identity (name + domain) is immutable.
 */
data class FunctionNode(
    val name: String,
    val domainName: String,
    val consumes: List<String>,
    val emits: List<String>,
    val layoutNode: LayoutNode,
)

/**
 * Static state for the architectural visualization. Built once from a
 * [FunctionGraphTopology]; the force layout is the only mutable part.
 * Runtime activity (firings) lives in [FiringHighlights] and is composed
 * by the renderer at draw time.
 *
 * Layout strategy: each domain gets an anchor point on a ring around the
 * y-axis, with a modest vertical offset per-function hashed from the
 * function name. Anchors keep domains visually distinct even when no
 * edges connect two clusters.
 */
class FunctionGraphState(
    val topology: FunctionGraphTopology,
    domainRadius: Float = 7f,
) {
    val layout: ForceLayout = ForceLayout(
        springRestLength = 2.5f,
        repulsionStrength = 60f,
        springStiffness = 0.5f,
    )
    private val mutex = Mutex()

    val nodes: List<FunctionNode>
    private val nodesByName: Map<String, FunctionNode>

    init {
        val anchorByDomain: Map<String, Vec3> = buildDomainAnchors(topology.domains, domainRadius)
        val built = buildNodes(topology, anchorByDomain)
        nodes = built
        nodesByName = built.associateBy { it.name }
        layout.nodes.addAll(built.map { it.layoutNode })
        for (edge in topology.edges) {
            layout.edges.add(LayoutEdge(fromId = edge.fromFunction, toId = edge.toFunction))
        }
    }

    suspend fun stepLayout(dtSeconds: Float): Unit = mutex.withLock {
        layout.step(dtSeconds)
    }

    /**
     * Snapshot the current positions. Returned in an immutable structure
     * the renderer can iterate without holding the mutex.
     */
    suspend fun snapshot(): FunctionGraphSnapshot = mutex.withLock {
        FunctionGraphSnapshot(
            nodes = nodes.map { node ->
                PositionedFunctionNode(
                    name = node.name,
                    domainName = node.domainName,
                    position = node.layoutNode.position,
                )
            },
            edges = topology.edges,
            domainAnchors = layout.nodes.mapNotNull { ln ->
                ln.anchorPosition?.let { ln.groupName to it }
            }.toMap(),
        )
    }

    fun nodeByName(name: String): FunctionNode? = nodesByName[name]

    private fun buildNodes(
        topology: FunctionGraphTopology,
        anchors: Map<String, Vec3>,
    ): List<FunctionNode> {
        val result = mutableListOf<FunctionNode>()
        val perDomainCounter: MutableMap<String, Int> = HashMap()
        for (spec in topology.nodes) {
            val domainAnchor = anchors[spec.domainName] ?: Vec3.ZERO
            val localIndex = perDomainCounter.getOrPut(spec.domainName) { 0 }
            perDomainCounter[spec.domainName] = localIndex + 1
            val jitter = fibonacciJitter(spec.name.hashCode())
            val start = domainAnchor + jitter
            val layoutNode = LayoutNode(
                id = spec.name,
                metadataId = spec.name,
                groupName = spec.domainName,
                position = start,
                anchorPosition = domainAnchor,
                anchorStrength = 0.18f,
            )
            result += FunctionNode(
                name = spec.name,
                domainName = spec.domainName,
                consumes = spec.consumes,
                emits = spec.emits,
                layoutNode = layoutNode,
            )
        }
        return result
    }

    companion object {
        fun buildDomainAnchors(domains: List<String>, radius: Float): Map<String, Vec3> {
            if (domains.isEmpty()) return emptyMap()
            val result = HashMap<String, Vec3>(domains.size)
            val step = 2.0 * PI / domains.size.coerceAtLeast(1)
            for ((index, domainName) in domains.withIndex()) {
                val angle = index * step
                val y = 0.6f * (if (index % 2 == 0) 1f else -1f)
                result[domainName] = Vec3(
                    x = radius * cos(angle).toFloat(),
                    y = y,
                    z = radius * sin(angle).toFloat(),
                )
            }
            return result
        }

        /** Deterministic small offset per function so co-domain nodes don't stack. */
        private fun fibonacciJitter(seed: Int): Vec3 {
            val phi = 2.3998f
            val a = (seed.toFloat() * phi)
            return Vec3(
                x = 0.6f * cos(a),
                y = 0.4f * sin(a * 1.3f),
                z = 0.6f * sin(a),
            )
        }
    }
}

data class PositionedFunctionNode(
    val name: String,
    val domainName: String,
    val position: Vec3,
)

data class FunctionGraphSnapshot(
    val nodes: List<PositionedFunctionNode>,
    val edges: List<FunctionEdge>,
    val domainAnchors: Map<String, Vec3>,
)
