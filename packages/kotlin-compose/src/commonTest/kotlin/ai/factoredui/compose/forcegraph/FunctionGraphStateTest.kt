package ai.factoredui.compose.forcegraph

import ai.factoredui.compose.forcegraph.graph.FunctionGraphState
import ai.factoredui.compose.forcegraph.graph.FunctionGraphTopology
import ai.factoredui.compose.forcegraph.graph.FunctionNodeSpec
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FunctionGraphStateTest {

    private fun spec(name: String, domain: String, consumes: List<String> = emptyList(), emits: List<String> = emptyList()) =
        FunctionNodeSpec(name = name, domainName = domain, consumes = consumes, emits = emits)

    private fun twoNodeTopo(): FunctionGraphTopology = FunctionGraphTopology.build(
        listOf(
            spec("alpha", "core", emits = listOf("sig")),
            spec("beta", "core", consumes = listOf("sig")),
        )
    )

    @Test
    fun constructsAllNodes() {
        val state = FunctionGraphState(twoNodeTopo())
        assertEquals(2, state.nodes.size)
    }

    @Test
    fun nodeByNameFindsExisting() {
        val state = FunctionGraphState(twoNodeTopo())
        assertNotNull(state.nodeByName("alpha"))
        assertNotNull(state.nodeByName("beta"))
    }

    @Test
    fun nodeByNameReturnsNullForMissing() {
        val state = FunctionGraphState(twoNodeTopo())
        assertNull(state.nodeByName("nothere"))
    }

    @Test
    fun stepDoesNotThrow() = runTest {
        val state = FunctionGraphState(twoNodeTopo())
        repeat(10) { state.stepLayout(0.016f) }
    }

    @Test
    fun snapshotContainsEdges() = runTest {
        val state = FunctionGraphState(twoNodeTopo())
        val snap = state.snapshot()
        assertEquals(1, snap.edges.size)
        assertEquals("alpha", snap.edges[0].fromFunction)
        assertEquals("beta", snap.edges[0].toFunction)
    }

    @Test
    fun domainAnchorsAreSeparateForMultipleDomains() {
        val topo = FunctionGraphTopology.build(listOf(
            spec("a", "domainA"),
            spec("b", "domainB"),
        ))
        val anchors = FunctionGraphState.buildDomainAnchors(topo.domains, radius = 7f)
        assertEquals(2, anchors.size)
        val dA = anchors["domainA"]!!
        val dB = anchors["domainB"]!!
        val dist = kotlin.math.sqrt(
            (dA.x - dB.x) * (dA.x - dB.x) +
            (dA.y - dB.y) * (dA.y - dB.y) +
            (dA.z - dB.z) * (dA.z - dB.z)
        )
        assertTrue(dist > 1f, "domain anchors should be spatially separated, got dist=$dist")
    }

    @Test
    fun stepDoesNotThrowWithManyCrowdedNodes() = runTest {
        val specs = (0 until 20).map { spec("fn$it", "d${it % 3}") }
        val state = FunctionGraphState(FunctionGraphTopology.build(specs))
        repeat(30) { state.stepLayout(0.016f) }
    }
}
