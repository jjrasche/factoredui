package ai.factoredui.compose.forcegraph

import ai.factoredui.compose.forcegraph.graph.FunctionEdge
import ai.factoredui.compose.forcegraph.graph.FunctionGraphTopology
import ai.factoredui.compose.forcegraph.graph.FunctionNodeSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FunctionGraphTopologyTest {

    private fun spec(name: String, domain: String, consumes: List<String> = emptyList(), emits: List<String> = emptyList()) =
        FunctionNodeSpec(name = name, domainName = domain, consumes = consumes, emits = emits)

    @Test
    fun buildConnectsEmitterToConsumer() {
        val specs = listOf(
            spec("emitFoo", "core", emits = listOf("foo")),
            spec("consumeFoo", "core", consumes = listOf("foo")),
        )
        val topo = FunctionGraphTopology.build(specs)
        assertEquals(1, topo.edges.size)
        assertEquals(FunctionEdge("emitFoo", "consumeFoo", "foo"), topo.edges[0])
    }

    @Test
    fun buildSkipsSelfLoops() {
        val specs = listOf(spec("selfLoop", "core", consumes = listOf("x"), emits = listOf("x")))
        val topo = FunctionGraphTopology.build(specs)
        assertTrue(topo.edges.isEmpty())
    }

    @Test
    fun buildProducesNoDuplicateEdgesForSingleKind() {
        val specs = listOf(
            spec("a", "d", emits = listOf("sig")),
            spec("b", "d", consumes = listOf("sig")),
        )
        val topo = FunctionGraphTopology.build(specs)
        assertEquals(1, topo.edges.size)
    }

    @Test
    fun buildReturnsAllNodesUnchanged() {
        val specs = listOf(
            spec("a", "d1"),
            spec("b", "d2"),
            spec("c", "d1"),
        )
        val topo = FunctionGraphTopology.build(specs)
        assertEquals(3, topo.nodes.size)
        assertEquals(specs, topo.nodes)
    }

    @Test
    fun domainsAreSortedAndDistinct() {
        val specs = listOf(
            spec("z", "beta"),
            spec("a", "alpha"),
            spec("m", "beta"),
        )
        val topo = FunctionGraphTopology.build(specs)
        assertEquals(listOf("alpha", "beta"), topo.domains)
    }

    @Test
    fun buildWithNoSpecsProducesEmptyTopology() {
        val topo = FunctionGraphTopology.build(emptyList())
        assertTrue(topo.nodes.isEmpty())
        assertTrue(topo.edges.isEmpty())
        assertTrue(topo.domains.isEmpty())
    }

    @Test
    fun nodesByDomainFiltersCorrectly() {
        val specs = listOf(spec("a", "d1"), spec("b", "d2"), spec("c", "d1"))
        val topo = FunctionGraphTopology.build(specs)
        assertEquals(2, topo.nodesByDomain("d1").size)
        assertEquals(1, topo.nodesByDomain("d2").size)
        assertTrue(topo.nodesByDomain("missing").isEmpty())
    }
}
