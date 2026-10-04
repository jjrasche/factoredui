package ai.factoredui.compose.schema

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GraphPropsTest {

    @Test
    fun aNodeCarriesOnlyMeaningAndFallsBackToItsIdForALabel() {
        val nodes = resolveGraphNodes(
            listOf(
                mapOf("id" to "soil_water", "label" to "Soil water", "group" to "soil", "status" to "implemented", "kind" to "state"),
                mapOf("id" to "rain"),
            ),
        )
        assertEquals(2, nodes.size)
        assertEquals(GraphNodeEntry(id = "soil_water", label = "Soil water", group = "soil", status = "implemented", kind = "state"), nodes[0])
        assertEquals("rain", nodes[1].label)
        assertEquals(null, nodes[1].shape, "no shape stated, so the kind style or the default decides")
    }

    @Test
    fun aNodeWithoutAnIdIsDropped() {
        assertEquals(emptyList(), resolveGraphNodes(listOf(mapOf("label" to "orphan"))))
    }

    @Test
    fun optionalPinningAndRankAndShapeAreRead() {
        val node = resolveGraphNodes(
            listOf(mapOf("id" to "a", "x" to 120, "y" to 40.5, "rank" to 2, "shape" to "pill", "color" to "#112233")),
        ).single()
        assertEquals(120f, node.x)
        assertEquals(40.5f, node.y)
        assertEquals(2, node.rank)
        assertEquals(GraphNodeShape.PILL, node.shape)
        assertEquals("#112233", node.color)
    }

    @Test
    fun anUnknownShapeIsLeftUnstated() {
        assertEquals(null, resolveGraphNodes(listOf(mapOf("id" to "a", "shape" to "hexagon"))).single().shape)
    }

    @Test
    fun anEdgeNeedsBothEndsAndIsNotDashedByDefault() {
        val edges = resolveGraphEdges(
            listOf(
                mapOf("from" to "rain", "to" to "soil_water", "kind" to "equation", "status" to "implemented"),
                mapOf("from" to "rain"),
                mapOf("from" to "a", "to" to "b", "dash" to true, "color" to "#445566"),
            ),
        )
        assertEquals(2, edges.size)
        assertFalse(edges[0].dash)
        assertEquals("equation", edges[0].kind)
        assertTrue(edges[1].dash)
        assertEquals("#445566", edges[1].color)
    }

    @Test
    fun groupOrderKeepsOnlyStrings() {
        assertEquals(listOf("soil", "vegetation"), resolveGraphGroupOrder(listOf("soil", 3, "vegetation")))
        assertEquals(emptyList(), resolveGraphGroupOrder("not a list"))
    }

    @Test
    fun aStringMapKeepsOnlyStringPairs() {
        assertEquals(
            mapOf("implemented" to "#3FA34D"),
            resolveGraphStringMap(mapOf("implemented" to "#3FA34D", "missing" to 4, 7 to "#000000")),
        )
    }

    @Test
    fun explicitColoursWinAndTheRestGetDistinctPaletteColoursInOrderOfAppearance() {
        val assigned = assignGraphColors(
            keys = listOf("implemented", "derived", "missing", "implemented"),
            explicit = mapOf("missing" to "#C0392B"),
        )
        assertEquals("#C0392B", assigned.getValue("missing"))
        assertEquals(3, assigned.size)
        assertEquals(3, assigned.values.toSet().size, "every key reads as a distinct colour")
    }

    @Test
    fun paletteAssignmentIsStableForTheSameKeys() {
        val keys = listOf("a", "b", "c")
        assertEquals(assignGraphColors(keys, emptyMap()), assignGraphColors(keys, emptyMap()))
    }

    @Test
    fun aNodeIsNotASelfLoopUnlessItSaysSo() {
        val nodes = resolveGraphNodes(listOf(mapOf("id" to "a"), mapOf("id" to "stock", "self_loop" to true)))
        assertFalse(nodes[0].selfLoop)
        assertTrue(nodes[1].selfLoop)
    }

    @Test
    fun kindStylesMapEachHostKindToHowItIsDrawn() {
        val styles = resolveGraphKindStyles(
            mapOf(
                "input" to mapOf("shape" to "pill"),
                "aux" to mapOf("compact" to true, "muted" to true),
                "output" to mapOf("border_width" to 3),
                "plain" to mapOf<String, Any?>(),
            ),
        )
        assertEquals(GraphNodeShape.PILL, styles.getValue("input").shape)
        assertTrue(styles.getValue("aux").compact && styles.getValue("aux").muted)
        assertEquals(3f, styles.getValue("output").borderWidth)
        assertEquals(GraphKindStyle(), styles.getValue("plain"))
    }

    @Test
    fun aMalformedKindStyleIsIgnored() {
        assertEquals(emptyMap(), resolveGraphKindStyles(mapOf("a" to "pill")))
        assertEquals(emptyMap(), resolveGraphKindStyles(null))
    }

    @Test
    fun onNodeTapIsReadFromThePropsMap() {
        val props = mapOf("on_node_tap" to SpecValue.StringValue("flow.nodeTapped"))
        assertEquals("flow.nodeTapped", props.asGraphProps().onNodeTapped)
        assertEquals(null, emptyMap<String, SpecValue>().asGraphProps().onNodeTapped)
    }
}
