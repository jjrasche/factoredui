package ai.factoredui.compose.schema

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CanvasPropsTest {

    @Test
    fun edgesAsBindingStringBecomesEdgesBindingNotLiteral() {
        val props = mapOf<String, SpecValue>(
            "nodes" to SpecValue.StringValue("{domain_nodes}"),
            "edges" to SpecValue.StringValue("{domain_edges}"),
        ).asCanvasProps()
        assertEquals("{domain_edges}", props.edgesBinding, "a string edges prop is a live binding, parallel to nodes")
        assertTrue(props.edges.isEmpty(), "a bound edges prop carries no baked literals")
    }

    @Test
    fun literalEdgeArrayStillParsesAndLeavesBindingNull() {
        val props = mapOf<String, SpecValue>(
            "edges" to SpecValue.ArrayValue(
                listOf(
                    SpecValue.ObjectValue(mapOf("from" to SpecValue.StringValue("a"), "to" to SpecValue.StringValue("b"))),
                ),
            ),
        ).asCanvasProps()
        assertNull(props.edgesBinding, "a literal edge array is not a binding")
        assertEquals(listOf(CanvasEdge("a", "b")), props.edges, "baked edges keep parsing — back-compat preserved")
    }

    @Test
    fun resolveCanvasEdgesReadsFromToOffAResolvedListAndDropsMalformed() {
        val resolved = listOf(
            mapOf("from" to "app", "to" to "memory"),
            mapOf("from" to "memory", "to" to "db"),
            mapOf("from" to "app"),
        )
        assertEquals(
            listOf(CanvasEdge("app", "memory"), CanvasEdge("memory", "db")),
            resolveCanvasEdges(resolved),
            "the live-edge resolver mirrors resolveFieldNodeEntries: read {from,to}, drop the malformed entry",
        )
    }
}
