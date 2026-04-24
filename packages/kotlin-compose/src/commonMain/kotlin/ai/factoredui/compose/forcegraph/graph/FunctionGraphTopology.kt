package ai.factoredui.compose.forcegraph.graph

/**
 * Target-agnostic description of a function that might appear in the
 * visualization. JVM adapters build these from `FunctionMetadata + Domain`;
 * the web demo builds them by hand. The web target must not depend on
 * `:platform`, so this DTO lives in commonMain.
 */
data class FunctionNodeSpec(
    val name: String,
    val domainName: String,
    val consumes: List<String>,
    val emits: List<String>,
)

/**
 * A directed edge from one function to another, labeled by the signal kind
 * that carries the dispatch. If function A emits kind K and function B
 * consumes kind K, there is one edge A → B labeled K. Multiple kinds
 * between the same pair create multiple edges (parallel labeled edges).
 */
data class FunctionEdge(
    val fromFunction: String,
    val toFunction: String,
    val signalKind: String,
)

/**
 * Static topology of the agent's function graph. Built once at startup
 * from the declared metadata; the renderer reads it every frame but never
 * mutates it. Runtime activity (firings) is layered on via
 * [FiringHighlights].
 */
data class FunctionGraphTopology(
    val nodes: List<FunctionNodeSpec>,
    val edges: List<FunctionEdge>,
) {
    val domains: List<String> = nodes.map { it.domainName }.distinct().sorted()

    fun nodesByDomain(domainName: String): List<FunctionNodeSpec> =
        nodes.filter { it.domainName == domainName }

    companion object {
        /**
         * Build the topology from a flat list of function specs by walking
         * declared `consumes` / `emits` lists. Cost is O(n·max_consumes·
         * avg_emitters_per_kind); negligible at family-scale function
         * counts.
         *
         * Functions that consume a kind with no declared emitter show up
         * as nodes with no incoming edges — they're "external-input"
         * handlers (typically wired by adapters whose runtime emission
         * side isn't expressed in metadata). That's intentional; the
         * graph is the architectural view, not a runtime trace.
         */
        fun build(specs: List<FunctionNodeSpec>): FunctionGraphTopology {
            val emittersByKind: MutableMap<String, MutableList<String>> = HashMap()
            for (spec in specs) {
                for (kind in spec.emits) {
                    emittersByKind.getOrPut(kind) { mutableListOf() }.add(spec.name)
                }
            }
            val edges = mutableListOf<FunctionEdge>()
            for (consumer in specs) {
                for (kind in consumer.consumes) {
                    val emitters = emittersByKind[kind] ?: continue
                    for (emitter in emitters) {
                        if (emitter == consumer.name) continue // skip self-loops
                        edges += FunctionEdge(emitter, consumer.name, kind)
                    }
                }
            }
            return FunctionGraphTopology(specs, edges)
        }
    }
}
