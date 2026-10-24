package ai.factoredui.compose.renderer

import ai.factoredui.compose.crawl.driveScreen
import ai.factoredui.compose.schema.Spec
import androidx.compose.ui.unit.DpRect
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val PHONE_WIDTH_DP = 390
private const val PHONE_HEIGHT_DP = 844
private const val COLUMNS = 40
private const val ROWS = 20

private fun frameOf(shift: Int): String = (0 until COLUMNS * ROWS).joinToString(",", "[", "]") { cell -> (((cell % COLUMNS) + shift * 10) % 40).toString() }

private val SPEC: Spec = Json.decodeFromString(
    Spec.serializer(),
    """
    { "spec_version": 1, "renderer_min": 1, "state": { "section": { "frame": 0 } },
      "root": { "id": "root", "type": "column", "props": {}, "children": [
        { "id": "heat", "type": "heatmap", "props": {
            "x": { "origin": 0, "step": 1, "count": $COLUMNS, "label": "Distance (ft)" },
            "y": { "origin": 0, "step": 2, "count": $ROWS, "label": "Depth (ft)" },
            "frames": [ ${frameOf(0)}, ${frameOf(1)}, ${frameOf(2)} ],
            "frame": "{section.frame}", "min": 0, "max": 40, "ramp": "heat", "unit": "F" } } ] } }
    """,
)

private fun nonGroundPixels(png: ByteArray, box: DpRect): List<Int> {
    val image = ImageIO.read(ByteArrayInputStream(png))
    val x = ((box.left.value + box.right.value) / 2).toInt()
    return (box.top.value.toInt() until box.bottom.value.toInt()).step(7).map { y -> image.getRGB(x, y) and 0xFFFFFF }
}

class HeatmapRenderTest {
    @Test
    fun a_40_by_20_grid_changes_colour_when_the_frame_changes() {
        driveScreen(SPEC, widthDp = PHONE_WIDTH_DP, heightDp = PHONE_HEIGHT_DP).use { screen ->
            val cells = assertNotNull(screen.boundsOf("heat/cells"))
            assertTrue(cells.right.value - cells.left.value > 300f, "the grid is ${cells.right.value - cells.left.value} dp wide")
            val first = screen.frame().png
            screen.type("section.frame", 2)
            val third = screen.frame().png
            assertFalse(first.contentEquals(third), "the grid looks the same on frame 0 and frame 2")
            assertTrue(nonGroundPixels(third, cells).toSet().size > 1, "the grid should hold several colours")
        }
    }

    @Test
    fun the_legend_shows_the_minimum_and_the_maximum_with_their_unit() {
        driveScreen(SPEC, widthDp = PHONE_WIDTH_DP, heightDp = PHONE_HEIGHT_DP).use { screen ->
            assertNotNull(screen.boundsOfText("0 F"), "the minimum is not shown")
            assertNotNull(screen.boundsOfText("40 F"), "the maximum is not shown")
            assertNotNull(screen.boundsOfText("Distance (ft)"))
        }
    }

    @Test
    fun a_frame_index_past_the_last_frame_draws_the_last_one() {
        driveScreen(SPEC, widthDp = PHONE_WIDTH_DP, heightDp = PHONE_HEIGHT_DP).use { screen ->
            screen.type("section.frame", 2)
            val last = screen.frame().png
            screen.type("section.frame", 99)
            assertTrue(last.contentEquals(screen.frame().png), "frame 99 should draw what frame 2 draws")
        }
    }
}
