package ai.factoredui.compose.renderer

import ai.factoredui.compose.crawl.driveScreen
import ai.factoredui.compose.schema.Spec
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val WIDTH_DP = 390
private const val HEIGHT_DP = 844

private fun specWith(boxValues: String, extra: String): Spec = Json.decodeFromString(
    Spec.serializer(),
    """
    { "spec_version": 1, "renderer_min": 1, "state": { "play": { "frame": 0 } },
      "root": { "id": "root", "type": "column", "props": {}, "children": [
        { "id": "volume", "type": "volume", "props": { $extra
            "boxes": [
              { "id": "hall", "x_mm": 0, "y_mm": 0, "z_mm": 0, "width_mm": 4000, "depth_mm": 3000, "height_mm": 2000, "style": "base" $boxValues },
              { "id": "hut", "x_mm": 3000, "y_mm": 0, "z_mm": 0, "width_mm": 1000, "depth_mm": 1000, "height_mm": 1000, "style": "base" } ],
            "styles": { "base": { "stroke": "#335588" } }, "legend": [] } } ] } }
    """,
)

private const val RAMP = """ "value_ramp": "heat", "min": 0, "max": 40, "unit": "C", "frame": "{play.frame}", """

class VolumeColourTest {
    @Test
    fun a_box_with_values_is_coloured_from_the_ramp_at_the_frame_shown() {
        driveScreen(specWith(""","values": [0, 40]""", RAMP), widthDp = WIDTH_DP, heightDp = HEIGHT_DP).use { screen ->
            val cold = screen.frame().png
            screen.type("play.frame", 1)
            val hot = screen.frame().png
            assertFalse(cold.contentEquals(hot), "the hall looks the same at frame 0 and frame 1")
        }
    }

    @Test
    fun the_legend_shows_the_range_with_its_unit() {
        driveScreen(specWith(""","values": [0, 40]""", RAMP), widthDp = WIDTH_DP, heightDp = HEIGHT_DP).use { screen ->
            assertNotNull(screen.boundsOfText("0 C"), "the minimum is not shown")
            assertNotNull(screen.boundsOfText("40 C"), "the maximum is not shown")
        }
    }

    @Test
    fun a_volume_with_a_ramp_but_no_values_draws_exactly_as_one_without() {
        val plain = driveScreen(specWith("", ""), widthDp = WIDTH_DP, heightDp = HEIGHT_DP).use { it.frame().png }
        val withRamp = driveScreen(specWith("", RAMP), widthDp = WIDTH_DP, heightDp = HEIGHT_DP).use { it.frame().png }
        assertTrue(plain.contentEquals(withRamp))
    }

    @Test
    fun a_ramp_that_cannot_be_read_is_named_and_the_boxes_keep_their_style() {
        driveScreen(specWith(""","values": [0, 40]""", """ "value_ramp": "rainbow", "min": 0, "max": 40, "frame": "{play.frame}", """), widthDp = WIDTH_DP, heightDp = HEIGHT_DP).use { screen ->
            assertNotNull(screen.boundsOfText("heatmap ramp 'rainbow' is not one of heat, cool_warm, viridis; give { stops: [[value, colour], ...] } or one of those names."))
        }
    }

    @Test
    fun three_boxes_over_three_frames_each_change_colour_at_every_frame() {
        val three = Json.decodeFromString(
            Spec.serializer(),
            """
            { "spec_version": 1, "renderer_min": 1, "state": { "play": { "frame": 0 } },
              "root": { "id": "root", "type": "column", "props": {}, "children": [
                { "id": "volume", "type": "volume", "props": { $RAMP
                    "boxes": [
                      { "id": "a", "x_mm": 0, "y_mm": 0, "z_mm": 0, "width_mm": 2000, "depth_mm": 2000, "height_mm": 2000, "values": [0, 20, 40] },
                      { "id": "b", "x_mm": 3000, "y_mm": 0, "z_mm": 0, "width_mm": 2000, "depth_mm": 2000, "height_mm": 2000, "values": [10, 30, 0] },
                      { "id": "c", "x_mm": 6000, "y_mm": 0, "z_mm": 0, "width_mm": 2000, "depth_mm": 2000, "height_mm": 2000, "values": [40, 0, 20] } ],
                    "styles": {}, "legend": [] } } ] } }
            """,
        )
        driveScreen(three, widthDp = WIDTH_DP, heightDp = HEIGHT_DP).use { screen ->
            val centres = listOf("a", "b", "c").associateWith { id ->
                val bounds = assertNotNull(screen.boundsOf("volume/$id"))
                ((bounds.left.value + bounds.right.value) / 2).toInt() to ((bounds.top.value + bounds.bottom.value) / 2).toInt()
            }
            val pixels = (0..2).map { frame ->
                screen.type("play.frame", frame)
                val image = javax.imageio.ImageIO.read(java.io.ByteArrayInputStream(screen.frame().png))
                centres.mapValues { (_, at) -> image.getRGB(at.first, at.second) }
            }
            listOf("a", "b", "c").forEach { id ->
                assertTrue(pixels.map { it.getValue(id) }.toSet().size == 3, "$id has the same colour at two frames: ${pixels.map { it.getValue(id) }}")
            }
            assertNotNull(screen.boundsOfText("0 C"))
            assertNotNull(screen.boundsOfText("40 C"))
        }
    }
}
