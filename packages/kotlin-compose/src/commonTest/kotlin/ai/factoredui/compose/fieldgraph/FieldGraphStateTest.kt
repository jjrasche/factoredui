package ai.factoredui.compose.fieldgraph

import ai.factoredui.compose.fieldgraph.graph.FieldEdge
import ai.factoredui.compose.fieldgraph.graph.FieldGraphState
import ai.factoredui.compose.fieldgraph.graph.FieldGraphTopology
import ai.factoredui.compose.fieldgraph.graph.FieldNode
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FieldGraphStateTest {

    private fun topology(count: Int, group: String = "claim"): FieldGraphTopology =
        FieldGraphTopology(
            nodes = (0 until count).map { FieldNode(id = "n$it", group = group, label = "N$it") },
            edges = emptyList(),
        )

    @Test
    fun seedsAllNodesAtOutskirts() {
        val state = FieldGraphState(topology(6))
        val snap = state.snapshot()
        assertEquals(6, snap.nodes.size)
        snap.positions.values.forEach { pos ->
            assertTrue(pos.radiusFraction >= 0.75f, "seeded radius should be near the edge, got ${pos.radiusFraction}")
            assertFalse(pos.isUserAnchored)
        }
    }

    @Test
    fun moveNodeSetsRadiusFractionAndAnchors() {
        val state = FieldGraphState(topology(3))
        state.moveNode("n0", angle = 0f, radiusFraction = 0.1f)
        val pos = state.snapshot().positions["n0"]!!
        assertEquals(0.1f, pos.radiusFraction, absoluteTolerance = 1e-5f)
        assertTrue(pos.isUserAnchored)
    }

    @Test
    fun relevanceMagnitudeInvertsRadius() {
        val state = FieldGraphState(topology(3))
        state.moveNode("n0", angle = 0f, radiusFraction = 0.0f)
        assertEquals(1.0f, state.relevanceMagnitude("n0"), absoluteTolerance = 1e-5f)
        state.moveNode("n1", angle = 0f, radiusFraction = 1.0f)
        assertEquals(0.0f, state.relevanceMagnitude("n1"), absoluteTolerance = 1e-5f)
    }

    @Test
    fun frozenNodeDoesNotMoveOnStep() {
        val state = FieldGraphState(topology(8))
        state.freezeNode("n0")
        val posBefore = state.snapshot().positions["n0"]!!
        repeat(10) { state.step() }
        val posAfter = state.snapshot().positions["n0"]!!
        assertEquals(posBefore.angle, posAfter.angle, absoluteTolerance = 1e-5f)
        assertEquals(posBefore.radiusFraction, posAfter.radiusFraction, absoluteTolerance = 1e-5f)
    }

    @Test
    fun anchoredNodeDoesNotMoveOnStep() {
        val state = FieldGraphState(topology(8))
        state.moveNode("n0", angle = (PI / 2).toFloat(), radiusFraction = 0.3f)
        val snapBefore = state.snapshot().positions["n0"]!!
        repeat(20) { state.step() }
        val snapAfter = state.snapshot().positions["n0"]!!
        assertEquals(snapBefore.angle, snapAfter.angle, absoluteTolerance = 1e-5f)
        assertEquals(snapBefore.radiusFraction, snapAfter.radiusFraction, absoluteTolerance = 1e-5f)
    }

    @Test
    fun stepDoesNotThrowWithManyCrowdedNodes() {
        val state = FieldGraphState(topology(12))
        repeat(30) { state.step() }
    }

    @Test
    fun edgesArePreservedInSnapshot() {
        val topo = FieldGraphTopology(
            nodes = listOf(FieldNode("a", "entity", "A"), FieldNode("b", "claim", "B")),
            edges = listOf(FieldEdge("a", "b", "affects")),
        )
        val state = FieldGraphState(topo)
        val snap = state.snapshot()
        assertEquals(1, snap.edges.size)
        assertEquals("a", snap.edges[0].fromId)
        assertEquals("b", snap.edges[0].toId)
    }
}
