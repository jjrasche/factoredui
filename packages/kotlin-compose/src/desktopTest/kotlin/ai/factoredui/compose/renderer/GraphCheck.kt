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
        ),
    )

    private fun twoNodes() = listOf(
        mapOf("id" to "rain", "label" to "rain", "group" to "soil", "status" to "running"),
        mapOf("id" to "intake", "label" to "intake", "group" to "livestock", "status" to "missing"),
    )

    private fun oneEdge(color: String = "#000000") = listOf(mapOf("from" to "rain", "to" to "intake", "color" to color))

    private fun SpecVisualCheck.pixel(x: Dp, y: Dp): Color = png().toPixelMap()[x.value.toInt(), y.value.toInt()]

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
        val check = SpecVisualCheck(this, contextOf(nodes, oneEdge()))
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
}
