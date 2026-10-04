package ai.factoredui.compose.renderer

import ai.factoredui.compose.adapter.ActionHandler
import ai.factoredui.compose.schema.SpecNode
import ai.factoredui.compose.schema.SpecNodeType
import ai.factoredui.compose.schema.SpecValue
import ai.factoredui.compose.testing.SpecVisualCheck
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class GraphCheck {

    private val graph = SpecNode(
        id = "flow",
        type = SpecNodeType.GRAPH,
        props = mapOf(
            "nodes" to SpecValue.StringValue("{flow_nodes}"),
            "edges" to SpecValue.StringValue("{flow_edges}"),
            "status_colors" to SpecValue.StringValue("{flow_colors}"),
            "group_order" to SpecValue.StringValue("{flow_groups}"),
            "kind_styles" to SpecValue.StringValue("{flow_kinds}"),
            "status_outlines" to SpecValue.StringValue("{flow_outlines}"),
            "on_node_tap" to SpecValue.StringValue("flow.nodeTapped"),
        ),
    )

    private val statusColors = mapOf("running" to "#3FA34D", "missing" to "#C0392B")

    private fun contextOf(
        nodes: List<Map<String, Any?>>,
        edges: List<Map<String, Any?>>,
        actions: Map<String, ActionHandler> = emptyMap(),
    ) = RenderContext(
        actions = actions,
        initialData = mapOf(
            "flow_nodes" to nodes,
            "flow_edges" to edges,
            "flow_colors" to statusColors,
            "flow_groups" to listOf("soil", "livestock"),
            "flow_kinds" to mapOf(
                "input" to mapOf("shape" to "pill"),
                "aux" to mapOf("compact" to true, "muted" to true),
            ),
            "flow_outlines" to mapOf("missing" to "dashed"),
        ),
    )

    private fun twoNodes() = listOf(
        mapOf("id" to "rain", "label" to "rain", "group" to "soil", "status" to "running"),
        mapOf("id" to "intake", "label" to "intake", "group" to "livestock", "status" to "missing"),
    )

    private fun oneEdge(color: String = "#000000") = listOf(mapOf("from" to "rain", "to" to "intake", "color" to color))

    private fun SpecVisualCheck.pixel(x: Dp, y: Dp): Color = png().toPixelMap()[x.value.toInt(), y.value.toInt()]

    private fun Color.isFilledWith(other: Color) = alpha > 0.5f && isNear(other)

    private fun Color.isBlank() = alpha < 0.05f || isNear(Color.White)

    private fun Color.isNear(other: Color, tolerance: Float = 0.08f) =
        abs(red - other.red) < tolerance && abs(green - other.green) < tolerance && abs(blue - other.blue) < tolerance

    private fun DpRect.centerY(): Dp = (top + bottom) / 2

    @Test
    fun aConsumerRendersRightOfItsSource() = runComposeUiTest {
        val check = SpecVisualCheck(this, contextOf(twoNodes(), oneEdge()))
        check.render(graph, viewport = 500.dp)
        check.assertRenderedToPixels()
        check.assertLeftOf("rain", "intake")
    }

    @Test
    fun groupsRenderAsStackedLanesInTheDeclaredOrder() = runComposeUiTest {
        val check = SpecVisualCheck(this, contextOf(twoNodes(), oneEdge()))
        check.render(graph, viewport = 500.dp)
        check.assertAbove("rain", "intake")
    }

    @Test
    fun aNodeIsFilledWithTheColourOfItsStatus() = runComposeUiTest {
        val check = SpecVisualCheck(this, contextOf(twoNodes(), oneEdge()))
        check.render(graph, viewport = 500.dp)
        val rain = check.region("rain")
        val intake = check.region("intake")
        assertTrue(check.pixel(rain.left + 4.dp, rain.centerY()).isNear(Color(0xFF3FA34D)), "running fills green")
        assertTrue(check.pixel(intake.left + 4.dp, intake.centerY()).isNear(Color(0xFFC0392B)), "missing fills red")
    }

    @Test
    fun anEdgeEndsInAnArrowheadAtItsTargetAndNotAtItsSource() = runComposeUiTest {
        val edgeColor = Color(0xFF0000FF)
        val sameLane = twoNodes().map { it + ("group" to "soil") }
        val check = SpecVisualCheck(this, contextOf(sameLane, oneEdge("#0000FF")))
        check.render(graph, viewport = 500.dp)
        val rain = check.region("rain")
        val intake = check.region("intake")
        val towardSource = check.pixel(rain.right + 8.dp, rain.centerY() + 2.dp)
        val towardTarget = check.pixel(intake.left - 8.dp, intake.centerY() + 2.dp)
        assertTrue(towardTarget.isNear(edgeColor, 0.25f), "the arrowhead is wider than the line a little before the target")
        assertTrue(!towardSource.isNear(edgeColor, 0.25f), "the line itself is too thin to reach that pixel at the source end")
    }

    @Test
    fun tappingANodeDispatchesItsId() = runComposeUiTest {
        var tapped: Any? = null
        val capture: ActionHandler = { params -> tapped = params["node_id"] }
        val check = SpecVisualCheck(this, contextOf(twoNodes(), oneEdge(), mapOf("flow.nodeTapped" to capture)))
        check.render(graph, viewport = 500.dp)
        check.tap("intake")
        assertEquals("intake", tapped)
    }

    @Test
    fun selectingANodeDimsNodesThatAreNotItsNeighbours() = runComposeUiTest {
        val nodes = twoNodes() + mapOf("id" to "bystander", "label" to "bystander", "group" to "soil", "status" to "running")
        val edges = oneEdge() + mapOf("from" to "rain", "to" to "bystander")
        val check = SpecVisualCheck(this, contextOf(nodes, edges))
        check.render(graph, viewport = 500.dp)
        val before = check.region("bystander").let { check.pixel(it.left + 4.dp, it.centerY()) }
        check.tap("intake")
        val after = check.region("bystander").let { check.pixel(it.left + 4.dp, it.centerY()) }
        assertTrue(before.alpha > 0.95f && after.alpha < 0.6f, "the bystander turns translucent: before=$before after=$after")
    }

    @Test
    fun aDensePipelineFitsTheViewWithoutScrolling() = runComposeUiTest {
        val nodes = (0 until 41).map { mapOf("id" to "n$it", "label" to "state_variable_$it", "group" to listOf("soil", "livestock", "vegetation", "infrastructure")[it % 4], "status" to "running") }
        val edges = (1 until 41).map { mapOf("from" to "n${(it - 1) / 3}", "to" to "n$it") }
        val check = SpecVisualCheck(this, contextOf(nodes, edges))
        check.render(graph, viewport = 700.dp)
        val image = check.png().toPixelMap()
        val edge = 3
        val ringIsBlank = (0 until image.width).all { x -> listOf(0, 1, 2, image.height - 3, image.height - 2, image.height - 1).all { image[x, it].isBlank() } } &&
            (0 until image.height).all { y -> listOf(0, 1, 2, image.width - 3, image.width - 2, image.width - 1).all { image[it, y].isBlank() } }
        assertTrue(ringIsBlank, "nothing is painted in the outer $edge px ring, so the whole graph is in view")
        assertTrue((0 until image.width).any { x -> (0 until image.height).any { y -> !image[x, y].isBlank() } }, "and the graph did paint something")
    }

    private fun SpecVisualCheck.isInk(x: Dp, y: Dp) = pixel(x, y).let { it.alpha > 0.5f && it.red < 0.3f && it.green < 0.3f && it.blue < 0.3f }

    private fun sameLane(vararg extra: Map<String, Any?>) = listOf(
        mapOf("id" to "rain", "label" to "rain", "group" to "soil", "status" to "running"),
        mapOf("id" to "intake", "label" to "intake", "group" to "soil", "status" to "running"),
    ) + extra

    @Test
    fun aPillKindRoundsTheNodeCornersAndABoxDoesNot() = runComposeUiTest {
        val nodes = sameLane(mapOf("id" to "pilled", "label" to "pilled", "group" to "soil", "status" to "running", "kind" to "input"))
        val check = SpecVisualCheck(this, contextOf(nodes, oneEdge()))
        check.render(graph, viewport = 500.dp)
        val pill = check.region("pilled")
        val box = check.region("rain")
        assertTrue(check.pixel(box.left + 2.dp, box.top + 2.dp).isFilledWith(Color(0xFF3FA34D)), "a box fills its corner")
        assertTrue(!check.pixel(pill.left + 2.dp, pill.top + 2.dp).isFilledWith(Color(0xFF3FA34D)), "a pill leaves its corner empty")
    }

    @Test
    fun aCompactKindDrawsASmallerNodeThanAnUnstyledOne() = runComposeUiTest {
        val nodes = sameLane(mapOf("id" to "tag", "label" to "tag", "group" to "soil", "status" to "running", "kind" to "aux"))
        val check = SpecVisualCheck(this, contextOf(nodes, oneEdge()))
        check.render(graph, viewport = 500.dp)
        val tag = check.region("tag")
        val plain = check.region("rain")
        assertTrue(tag.bottom - tag.top < plain.bottom - plain.top, "the compact tag is shorter than a normal node")
    }

    @Test
    fun aMissingStatusHasADashedOutlineNotJustAColour() = runComposeUiTest {
        val nodes = listOf(
            mapOf("id" to "gap", "label" to "gap in the model", "group" to "soil", "status" to "missing"),
            mapOf("id" to "solid", "label" to "solid in the model", "group" to "soil", "status" to "running"),
        )
        val check = SpecVisualCheck(this, contextOf(nodes, emptyList()))
        check.render(graph, viewport = 500.dp)
        fun inkTransitionsAlongTop(id: String): Int {
            val region = check.region(id)
            val inkFlags = (region.left.value.toInt() + 8 until region.right.value.toInt() - 8).map { x -> check.isInk(x.dp, region.top) }
            return inkFlags.zipWithNext().count { (a, b) -> a != b }
        }
        assertTrue(inkTransitionsAlongTop("gap") >= 3, "the missing node's top edge alternates ink and fill")
        assertTrue(inkTransitionsAlongTop("solid") <= 1, "a solid node's top edge does not alternate")
    }

    @Test
    fun aSelfLoopNodeCarriesACircularArrowBadgeAboveItsCorner() = runComposeUiTest {
        val nodes = sameLane(mapOf("id" to "stock", "label" to "stock", "group" to "soil", "status" to "running", "self_loop" to true))
        val check = SpecVisualCheck(this, contextOf(nodes, oneEdge()))
        check.render(graph, viewport = 500.dp)
        fun inkAboveRightCorner(id: String): Boolean {
            val region = check.region(id)
            return (region.right.value.toInt() - 8..region.right.value.toInt() + 6).any { x ->
                (region.top.value.toInt() - 6 until region.top.value.toInt()).any { y -> check.isInk(x.dp, y.dp) }
            }
        }
        assertTrue(inkAboveRightCorner("stock"), "the badge arc is drawn above the corner")
        assertTrue(!inkAboveRightCorner("rain"), "a node that does not loop has no badge")
    }

    @Test
    fun anUnconnectedNodeIsDrawnMutedBesideAConnectedOne() = runComposeUiTest {
        val nodes = sameLane(mapOf("id" to "lonely", "label" to "lonely", "group" to "soil", "status" to "running"))
        val check = SpecVisualCheck(this, contextOf(nodes, oneEdge()))
        check.render(graph, viewport = 500.dp)
        val connected = check.region("rain").let { check.pixel(it.left + 4.dp, it.centerY()) }
        val parked = check.region("lonely").let { check.pixel(it.left + 4.dp, it.centerY()) }
        assertTrue(connected.alpha > 0.95f && parked.alpha < 0.9f, "the parked node is translucent: connected=$connected parked=$parked")
    }
}
