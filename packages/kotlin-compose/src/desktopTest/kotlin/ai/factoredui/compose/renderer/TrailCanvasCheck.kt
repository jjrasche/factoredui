package ai.factoredui.compose.renderer

import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import ai.factoredui.compose.schema.SpecNode
import ai.factoredui.compose.schema.SpecNodeType
import ai.factoredui.compose.schema.SpecValue
import ai.factoredui.compose.testing.SpecVisualCheck
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class TrailCanvasCheck {

    private fun userTrail(): Map<String, Any?> = mapOf(
        "id" to "user",
        "color" to "#3B82F6",
        "points" to listOf(
            mapOf("x" to 0.1, "y" to 0.5),
            mapOf("x" to 0.3, "y" to 0.4),
            mapOf("x" to 0.5, "y" to 0.5),
            mapOf("x" to 0.7, "y" to 0.45),
        ),
    )

    private fun advisorTrail(): Map<String, Any?> = mapOf(
        "id" to "advisor",
        "color" to "#F59E0B",
        "points" to listOf(
            mapOf("x" to 0.2, "y" to 0.8),
            mapOf("x" to 0.45, "y" to 0.75),
            mapOf("x" to 0.65, "y" to 0.82),
        ),
    )

    private val compassField = SpecNode(
        id = "compass",
        type = SpecNodeType.CANVAS,
        props = mapOf(
            "nodes" to SpecValue.StringValue("{field_nodes}"),
            "paths" to SpecValue.StringValue("{field_trails}"),
            "viewport" to SpecValue.StringValue("{camera}"),
        ),
    )

    @Test
    fun twoColoredTrailsBothRenderAsDistinguishablePaths() = runComposeUiTest {
        val context = RenderContext(
            initialData = mapOf(
                "field_nodes" to emptyList<Map<String, Any?>>(),
                "field_trails" to listOf(userTrail(), advisorTrail()),
                "camera" to mapOf("x" to 0.0, "y" to 0.0, "zoom" to 1.0),
            ),
        )
        val check = SpecVisualCheck(this, context)
        check.render(compassField, viewport = 400.dp)
        check.assertRenderedToPixels()

        val pixels = check.png().toPixelMap()
        var bluePixels = 0
        var amberPixels = 0
        for (y in 0 until pixels.height) {
            for (x in 0 until pixels.width) {
                val color = pixels[x, y]
                if (color.blue > 0.7f && color.red < 0.45f && color.green in 0.35f..0.7f) bluePixels++
                if (color.red > 0.8f && color.green in 0.45f..0.75f && color.blue < 0.25f) amberPixels++
            }
        }
        assertTrue(bluePixels > 0, "the user's blue comet-tail must draw blue pixels (found $bluePixels)")
        assertTrue(amberPixels > 0, "the advisor's amber comet-tail must draw amber pixels — two colors = the multi-path compass (found $amberPixels)")
    }

    @Test
    fun aTrailWithNoPathsBindingLeavesTheFieldBlankButRendered() = runComposeUiTest {
        val context = RenderContext(
            initialData = mapOf(
                "field_nodes" to emptyList<Map<String, Any?>>(),
                "camera" to mapOf("x" to 0.0, "y" to 0.0, "zoom" to 1.0),
            ),
        )
        val bareField = SpecNode(
            id = "compass",
            type = SpecNodeType.CANVAS,
            props = mapOf(
                "nodes" to SpecValue.StringValue("{field_nodes}"),
                "viewport" to SpecValue.StringValue("{camera}"),
            ),
        )
        val check = SpecVisualCheck(this, context)
        check.render(bareField, viewport = 400.dp)
        check.assertRenderedToPixels()
    }
}
