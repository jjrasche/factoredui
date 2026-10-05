package ai.factoredui.compose.renderer

import ai.factoredui.compose.schema.SpecNode
import ai.factoredui.compose.schema.SpecNodeType
import ai.factoredui.compose.schema.SpecValue
import ai.factoredui.compose.testing.SpecVisualCheck
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class GraphDarkCheck {

    private fun luminance(color: Color) = 0.2126f * color.red + 0.7152f * color.green + 0.0722f * color.blue

    private fun graph(theme: String) = SpecNode(
        id = "flow",
        type = SpecNodeType.GRAPH,
        props = mapOf(
            "theme" to SpecValue.StringValue(theme),
            "nodes" to SpecValue.StringValue("{nodes}"),
            "edges" to SpecValue.StringValue("{edges}"),
            "status_colors" to SpecValue.StringValue("{colors}"),
            "status_outlines" to SpecValue.StringValue("{outlines}"),
            "legend" to SpecValue.StringValue("{legend}"),
        ),
    )

    private val nodes = listOf(
        mapOf("id" to "a", "label" to "implemented", "group" to "soil", "status" to "implemented"),
        mapOf("id" to "b", "label" to "derived", "group" to "soil", "status" to "derived"),
        mapOf("id" to "c", "label" to "missing", "group" to "soil", "status" to "missing"),
        mapOf("id" to "d", "label" to "no status", "group" to "soil"),
    )

    private val edges = listOf(
        mapOf("from" to "a", "to" to "b", "status" to "implemented"),
        mapOf("from" to "b", "to" to "c", "status" to "derived"),
        mapOf("from" to "c", "to" to "d", "status" to "missing"),
    )

    private val legend = listOf(
        mapOf("label" to "state", "kind" to "node"),
        mapOf("label" to "missing", "kind" to "badge", "color" to "#C0392B", "outline" to "dashed"),
    )

    private fun world() = RenderContext(
        initialData = mapOf(
            "nodes" to nodes,
            "edges" to edges,
            "colors" to mapOf("implemented" to "#3FA34D", "derived" to "#4C78C9", "missing" to "#C0392B"),
            "outlines" to mapOf("missing" to "dashed"),
            "legend" to legend,
        ),
    )

    private fun SpecVisualCheck.pixel(x: Dp, y: Dp): Color = png().toPixelMap()[x.value.toInt(), y.value.toInt()]

    private fun SpecVisualCheck.countAround(nodeId: String, matches: (Color) -> Boolean): Int {
        val region = region(nodeId)
        val image = png().toPixelMap()
        var count = 0
        for (x in region.left.value.toInt() until minOf(region.right.value.toInt(), image.width)) {
            for (y in region.top.value.toInt() until minOf(region.bottom.value.toInt(), image.height)) if (matches(image[x, y])) count++
        }
        return count
    }

    private fun groundOf(check: SpecVisualCheck) = luminance(check.pixel(2.dp, 2.dp))

    @Test
    fun everyStatusFillStandsClearOfTheDarkGround() = runComposeUiTest {
        val check = SpecVisualCheck(this, world())
        check.render(graph("dark"), viewport = 700.dp)
        val ground = groundOf(check)
        listOf("a", "b", "c").forEach { id ->
            val region = check.region(id)
            val fill = luminance(check.pixel(region.left + 5.dp, (region.top + region.bottom) / 2))
            assertTrue(fill > ground + 0.12f, "$id fill is clearly lighter than the ground: fill=$fill ground=$ground")
        }
    }

    @Test
    fun aNodeWithoutAStatusIsAMidDarkSlateNotALightSlab() = runComposeUiTest {
        val check = SpecVisualCheck(this, world())
        check.render(graph("dark"), viewport = 700.dp)
        val region = check.region("d")
        val fill = luminance(check.pixel(region.left + 5.dp, (region.top + region.bottom) / 2))
        assertTrue(fill < 0.4f, "an unstated node is not a glaring light box in dark: $fill")
    }

    @Test
    fun nodeLabelsReadAsHighContrastTextOnTheirFill() = runComposeUiTest {
        val check = SpecVisualCheck(this, world())
        check.render(graph("dark"), viewport = 700.dp)
        listOf("a", "b", "c", "d").forEach { id ->
            val region = check.region(id)
            val fill = luminance(check.pixel(region.left + 5.dp, (region.top + region.bottom) / 2))
            val strongest = check.countAround(id) { kotlin.math.abs(luminance(it) - fill) > 0.4f && it.alpha > 0.9f }
            assertTrue(strongest > 8, "$id label has glyph pixels contrasting with its fill: $strongest")
        }
    }

    @Test
    fun theMissingNodesDashedOutlineIsLightAgainstTheDarkGround() = runComposeUiTest {
        val check = SpecVisualCheck(this, world())
        check.render(graph("dark"), viewport = 700.dp)
        val region = check.region("c")
        val topEdge = (region.left.value.toInt() + 8 until region.right.value.toInt() - 8).map { x -> maxOf(luminance(check.pixel(x.dp, region.top)), luminance(check.pixel(x.dp, region.top + 1.dp))) }
        assertTrue(topEdge.max() > 0.7f, "the dashed outline is bright in dark: ${topEdge.max()}")
        assertTrue(topEdge.min() < topEdge.max() - 0.2f, "and it is dashed, not solid")
    }

    @Test
    fun anEdgeIsVisibleAgainstTheDarkGroundAtRest() = runComposeUiTest {
        val check = SpecVisualCheck(this, world())
        check.render(graph("dark"), viewport = 700.dp)
        val ground = groundOf(check)
        val a = check.region("a")
        val b = check.region("b")
        val columnX = ((a.right + b.left) / 2).value.toInt()
        val brightest = (a.top.value.toInt()..b.bottom.value.toInt()).maxOf { y -> luminance(check.pixel(columnX.dp, y.dp)) }
        assertTrue(brightest > ground + 0.2f, "an edge crosses the gap between a and b clearly: $brightest vs $ground")
    }

    @Test
    fun theLegendSwatchesAndLabelsAreReadableInDark() = runComposeUiTest {
        val check = SpecVisualCheck(this, world())
        check.render(graph("dark"), viewport = 700.dp)
        val stateSwatch = check.region("flow:legend:0")
        val swatch = luminance(check.pixel(stateSwatch.left + 4.dp, (stateSwatch.top + stateSwatch.bottom) / 2))
        assertTrue(swatch < 0.45f, "the neutral node swatch is dark, not a light chip: $swatch")
        val labelPixels = check.countAround("flow") { luminance(it) > 0.8f }
        assertTrue(labelPixels > 40, "legend and node text paint light pixels: $labelPixels")
    }

    @Test
    fun lightModeKeepsItsLightUnstatedFill() = runComposeUiTest {
        val check = SpecVisualCheck(this, world())
        check.render(graph("light"), viewport = 700.dp)
        val region = check.region("d")
        val fill = luminance(check.pixel(region.left + 5.dp, (region.top + region.bottom) / 2))
        assertTrue(fill > 0.8f, "light mode is unchanged: $fill")
    }
}
