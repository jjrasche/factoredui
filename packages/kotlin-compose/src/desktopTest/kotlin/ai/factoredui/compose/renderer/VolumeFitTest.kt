package ai.factoredui.compose.renderer

import ai.factoredui.compose.crawl.driveScreen
import ai.factoredui.compose.schema.Spec
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val PHONE_WIDTH_DP = 390
private const val PHONE_HEIGHT_DP = 844
private const val DECLARED_HEIGHT_DP = 220
private const val STOOL_IDS = """["seat", "leg1", "leg2", "leg3", "leg4"]"""

private fun stoolSpec(extra: String): Spec = Json.decodeFromString(
    Spec.serializer(),
    """
    { "spec_version": 1, "renderer_min": 1, "root": { "id": "root", "type": "column", "props": {}, "children": [
      { "id": "volume", "type": "volume", "props": { "height": $DECLARED_HEIGHT_DP, $extra
          "boxes": [
            { "id": "seat", "x_mm": 0, "y_mm": 0, "z_mm": 200, "width_mm": 305, "depth_mm": 229, "height_mm": 30, "style": "wood" },
            { "id": "leg1", "x_mm": -130, "y_mm": -95, "z_mm": 0, "width_mm": 30, "depth_mm": 30, "height_mm": 200, "style": "wood" },
            { "id": "leg2", "x_mm": 130, "y_mm": -95, "z_mm": 0, "width_mm": 30, "depth_mm": 30, "height_mm": 200, "style": "wood" },
            { "id": "leg3", "x_mm": -130, "y_mm": 95, "z_mm": 0, "width_mm": 30, "depth_mm": 30, "height_mm": 200, "style": "wood" },
            { "id": "leg4", "x_mm": 130, "y_mm": 95, "z_mm": 0, "width_mm": 30, "depth_mm": 30, "height_mm": 200, "style": "wood" } ],
          "styles": { "wood": { "stroke": "#8a6a3a" } }, "legend": [] } },
      { "id": "below", "type": "text", "props": { "value": "text below the volume" } } ] } }
    """,
)

class VolumeFitTest {
    @Test
    fun a_small_object_named_in_focus_fills_most_of_the_width_of_a_phone() {
        driveScreen(stoolSpec(""" "focus": $STOOL_IDS, """), widthDp = PHONE_WIDTH_DP, heightDp = PHONE_HEIGHT_DP).use { screen ->
            val bounds = listOf("seat", "leg1", "leg2", "leg3", "leg4").map { assertNotNull(screen.boundsOf("volume/$it"), it) }
            val width = bounds.maxOf { it.right.value } - bounds.minOf { it.left.value }
            assertTrue(width >= 0.6f * PHONE_WIDTH_DP, "the stool is $width dp wide of $PHONE_WIDTH_DP; volume ${screen.boundsOf("volume")}; boxes $bounds")
        }
    }

    @Test
    fun the_volume_never_draws_below_its_declared_height_and_the_text_after_it_is_clear() {
        driveScreen(stoolSpec(""" "focus": $STOOL_IDS, """), widthDp = PHONE_WIDTH_DP, heightDp = PHONE_HEIGHT_DP).use { screen ->
            val volume = assertNotNull(screen.boundsOf("volume"))
            val reset = assertNotNull(screen.boundsOf("volume/reset"))
            val below = assertNotNull(screen.boundsOfText("text below the volume"))
            assertTrue(reset.bottom.value <= volume.top.value + DECLARED_HEIGHT_DP + 1f, "Reset view ends at ${reset.bottom.value}, the volume at ${volume.top.value + DECLARED_HEIGHT_DP}")
            assertTrue(below.top.value >= volume.top.value + DECLARED_HEIGHT_DP - 1f, "the text starts at ${below.top.value}")
            val seat = assertNotNull(screen.boundsOf("volume/seat"))
            assertTrue(seat.bottom.value <= volume.top.value + DECLARED_HEIGHT_DP, "the stool spills past the declared height")
        }
    }

    @Test
    fun the_same_stool_with_no_focus_is_still_framed_whole_and_not_tiny() {
        driveScreen(stoolSpec(""), widthDp = PHONE_WIDTH_DP, heightDp = PHONE_HEIGHT_DP).use { screen ->
            val seat = assertNotNull(screen.boundsOf("volume/seat"))
            assertTrue(seat.right.value - seat.left.value > 60f, "the seat is ${seat.right.value - seat.left.value} dp wide")
        }
    }
}
