package ai.factoredui.compose.renderer

import ai.factoredui.compose.crawl.driveScreen
import ai.factoredui.compose.schema.Spec
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import androidx.compose.ui.graphics.Color
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val PLAN_WIDTH_DP = 412
private const val PLAN_HEIGHT_DP = 915

private val SHAPES = """
  [
    { "id": "big", "kind": "rect", "x_mm": 0, "y_mm": 0, "width_mm": 4000, "depth_mm": 2000, "style": "area" },
    { "id": "edge", "kind": "line", "x_mm": 0, "y_mm": 0, "x2_mm": 4000, "y2_mm": 0, "style": "edge" },
    { "id": "pin", "kind": "marker", "x_mm": 4000, "y_mm": 2000, "style": "edge" },
    { "id": "name", "kind": "label", "x_mm": 2000, "y_mm": 1000, "text": "4000 × 2000 mm" }
  ]
"""

private fun planSpec(shapes: String): Spec = Json.decodeFromString(
    Spec.serializer(),
    """
    { "spec_version": 1, "renderer_min": 1, "root": { "id": "root", "type": "column", "props": {}, "children": [
      { "id": "plan", "type": "plan", "props": {
          "shapes": $shapes,
          "styles": { "area": { "fill": "#88aadd66", "stroke": "#335588" }, "edge": { "stroke": "#aa3322", "weight": 4 } },
          "legend": [ { "label": "Area", "style": "area" }, { "label": "Edge", "style": "edge" } ] } } ] } }
    """,
)

class PlanTest {
    @Test
    fun a_colour_with_eight_digits_carries_its_alpha_last() {
        assertEquals(Color(0xc0, 0x7a, 0x1f, 0x40), planColour("#c07a1f40"))
        assertEquals(Color(0xc0, 0x7a, 0x1f), planColour("#c07a1f"))
        assertNull(planColour("#c07a1"))
    }

    @Test
    fun the_plan_leaves_one_margin_round_its_drawing_and_no_more() {
        driveScreen(planSpec(SHAPES), widthDp = PLAN_WIDTH_DP, heightDp = PLAN_HEIGHT_DP).use { screen ->
            val area = assertNotNull(screen.boundsOf("plan/big"))
            val whole = assertNotNull(screen.boundsOf("plan"))
            val legend = assertNotNull(screen.boundsOf("plan/legend/0"))
            assertEquals(area.left.value - whole.left.value, area.top.value - whole.top.value, 1f)
            assertTrue(legend.top.value - area.bottom.value < 28f + 16f, "the legend sits ${legend.top.value - area.bottom.value}dp under the drawing")
        }
    }

    @Test
    fun on_a_wide_screen_the_drawing_stays_one_phone_width_and_sits_in_the_middle() {
        driveScreen(planSpec(SHAPES), widthDp = 1280, heightDp = 800).use { screen ->
            val area = assertNotNull(screen.boundsOf("plan/big"))
            assertTrue(area.right.value - area.left.value <= 420f, "the drawing is ${area.right.value - area.left.value}dp wide")
            assertEquals(640f, (area.left.value + area.right.value) / 2, 2f)
        }
    }

    @Test
    fun every_shape_is_placed_in_millimetres_with_north_at_the_top() {
        driveScreen(planSpec(SHAPES), widthDp = PLAN_WIDTH_DP, heightDp = PLAN_HEIGHT_DP).use { screen ->
            val area = assertNotNull(screen.boundsOf("plan/big"))
            val edge = assertNotNull(screen.boundsOf("plan/edge"))
            val pin = assertNotNull(screen.boundsOf("plan/pin"))
            assertEquals(2.0f, (area.right - area.left).value / (area.bottom - area.top).value, 0.01f)
            assertEquals(area.left.value, edge.left.value, 0.5f)
            assertEquals(area.right.value, edge.right.value, 0.5f)
            assertEquals(area.bottom.value, edge.top.value, 1.5f)
            assertEquals(area.top.value, pin.top.value, 1.5f)
            assertTrue(area.right.value <= PLAN_WIDTH_DP, "the plan overflows the screen width")
        }
    }

    @Test
    fun a_label_and_the_legend_are_drawn_as_text() {
        driveScreen(planSpec(SHAPES), widthDp = PLAN_WIDTH_DP, heightDp = PLAN_HEIGHT_DP).use { screen ->
            assertEquals(listOf("4000 × 2000 mm"), screen.textsUnder("plan/name"))
            assertEquals(listOf("Area"), screen.textsUnder("plan/legend/0"))
            assertEquals(listOf("Edge"), screen.textsUnder("plan/legend/1"))
        }
    }

    @Test
    fun a_shape_that_cannot_be_drawn_is_named_on_screen() {
        val spec = planSpec("""[ { "id": "bad", "kind": "rect", "x_mm": 0, "y_mm": 0, "width_mm": null, "depth_mm": 5 }, { "id": "ok", "kind": "marker", "x_mm": 1, "y_mm": 1 } ]""")
        driveScreen(spec, widthDp = PLAN_WIDTH_DP, heightDp = PLAN_HEIGHT_DP).use { screen ->
            val texts = screen.textsUnder("plan")
            assertTrue("plan shape 'bad': a rect needs width_mm and depth_mm as numbers; it is not drawn." in texts, texts.toString())
            assertNotNull(screen.boundsOf("plan/ok"))
        }
    }
}
