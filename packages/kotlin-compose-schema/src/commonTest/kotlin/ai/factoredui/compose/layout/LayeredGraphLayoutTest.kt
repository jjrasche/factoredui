package ai.factoredui.compose.layout

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LayeredGraphLayoutTest {

    @Test
    fun sourceIsAboveItsDependencies() {
        val positions = layeredGraphLayout(
            nodes = listOf("app", "memory", "db"),
            edges = listOf("app" to "memory", "memory" to "db"),
            width = 900f, height = 600f,
        )
        assertTrue(positions.getValue("app").y < positions.getValue("memory").y, "a dependency source sits above what it depends on")
        assertTrue(positions.getValue("memory").y < positions.getValue("db").y, "the chain descends by layer")
    }

    @Test
    fun longestPathSetsTheLayerSoADiamondBottomsOut() {
        val positions = layeredGraphLayout(
            nodes = listOf("a", "b", "c", "d"),
            edges = listOf("a" to "b", "a" to "c", "b" to "d", "c" to "d"),
            width = 900f, height = 600f,
        )
        val yA = positions.getValue("a").y
        val yBC = positions.getValue("b").y
        val yD = positions.getValue("d").y
        assertEquals(positions.getValue("b").y, positions.getValue("c").y, "peers at the same depth share a layer/row")
        assertTrue(yA < yBC && yBC < yD, "the diamond's sink bottoms out below its two middle peers")
        assertTrue(positions.getValue("b").x != positions.getValue("c").x, "peers in a layer spread horizontally, no overlap")
    }

    @Test
    fun everyNodeGetsAPositionInsideTheViewport() {
        val nodes = (1..33).map { "d$it" }
        val edges = (2..33).map { "d1" to "d$it" }
        val positions = layeredGraphLayout(nodes, edges, width = 800f, height = 1200f)
        assertEquals(33, positions.size, "every declared node is placed")
        positions.values.forEach { p ->
            assertTrue(p.x in 0f..800f && p.y in 0f..1200f, "positions stay inside the viewport (${p.x},${p.y})")
        }
    }

    @Test
    fun aCycleDoesNotHangOrDropNodes() {
        val positions = layeredGraphLayout(
            nodes = listOf("x", "y", "z"),
            edges = listOf("x" to "y", "y" to "z", "z" to "x"),
            width = 400f, height = 400f,
        )
        assertEquals(3, positions.size, "a cycle is laid out defensively — every node still placed, no infinite loop")
    }
}
