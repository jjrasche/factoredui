package ai.factoredui.compose.fieldgraph

import ai.factoredui.compose.fieldgraph.graph.FieldGraphTopology
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FieldGraphTopologyTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun parsesFullTopologyFromJson() {
        val body = """
            {
              "nodes": [
                {"id": "a", "group": "claim", "label": "Alpha", "age_seconds": 10.0},
                {"id": "b", "group": "entity", "label": "Beta", "age_seconds": 5.5}
              ],
              "edges": [
                {"from": "a", "to": "b", "kind": "affects"}
              ]
            }
        """.trimIndent()
        val topo = FieldGraphTopology.fromJson(json, body)
        assertEquals(2, topo.nodes.size)
        assertEquals(1, topo.edges.size)
        val a = topo.nodes[0]
        assertEquals("a", a.id)
        assertEquals("claim", a.group)
        assertEquals("Alpha", a.label)
        assertEquals(10.0f, a.ageSecs, absoluteTolerance = 1e-5f)
        val edge = topo.edges[0]
        assertEquals("a", edge.fromId)
        assertEquals("b", edge.toId)
        assertEquals("affects", edge.kind)
    }

    @Test
    fun defaultsNullOptionalFieldsGracefully() {
        // Nodes with no group/label/age_seconds must not crash — this was the bug.
        val body = """{"nodes": [{"id": "x"}, {"id": "y"}], "edges": []}"""
        val topo = FieldGraphTopology.fromJson(json, body)
        assertEquals(2, topo.nodes.size)
        val x = topo.nodes[0]
        assertEquals("claim", x.group)   // default
        assertEquals("x", x.label)       // falls back to id
        assertEquals(0f, x.ageSecs, absoluteTolerance = 1e-5f)
    }

    @Test
    fun parsesEmptyTopology() {
        val body = """{"nodes": [], "edges": []}"""
        val topo = FieldGraphTopology.fromJson(json, body)
        assertTrue(topo.nodes.isEmpty())
        assertTrue(topo.edges.isEmpty())
    }

    @Test
    fun ignoresUnknownKeys() {
        val body = """
            {
              "nodes": [{"id": "n1", "future_field": "ignored"}],
              "edges": [],
              "meta": {"version": 2}
            }
        """.trimIndent()
        val topo = FieldGraphTopology.fromJson(json, body)
        assertEquals(1, topo.nodes.size)
        assertEquals("n1", topo.nodes[0].id)
    }

    @Test
    fun edgeKindDefaultsToEmpty() {
        val body = """{"nodes": [{"id":"a"},{"id":"b"}], "edges": [{"from":"a","to":"b"}]}"""
        val topo = FieldGraphTopology.fromJson(json, body)
        assertEquals("", topo.edges[0].kind)
    }
}
