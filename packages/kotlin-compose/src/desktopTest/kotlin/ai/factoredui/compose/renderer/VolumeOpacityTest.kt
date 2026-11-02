package ai.factoredui.compose.renderer

import ai.factoredui.compose.crawl.driveScreen
import ai.factoredui.compose.schema.Spec
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val WIDTH_DP = 412
private const val HEIGHT_DP = 915

private val TOWER = """{ "id": "tower", "x_mm": 1500, "y_mm": 1500, "z_mm": 0, "width_mm": 1000, "depth_mm": 1000, "height_mm": 3000, "style": "tower" }"""

private val PANES = """
    { "id": "pane-south", "x_mm": 0, "y_mm": -200, "z_mm": 0, "width_mm": 4000, "depth_mm": 100, "height_mm": 3500, "style": "pane" },
    { "id": "pane-north", "x_mm": 0, "y_mm": 3800, "z_mm": 0, "width_mm": 4000, "depth_mm": 100, "height_mm": 3500, "style": "pane" },
    { "id": "pane-west", "x_mm": -200, "y_mm": 0, "z_mm": 0, "width_mm": 100, "depth_mm": 4000, "height_mm": 3500, "style": "pane" },
    { "id": "pane-east", "x_mm": 3800, "y_mm": 0, "z_mm": 0, "width_mm": 100, "depth_mm": 4000, "height_mm": 3500, "style": "pane" },
"""

private fun spec(boxes: String, paneStyle: String): Spec = Json.decodeFromString(
    Spec.serializer(),
    """
    { "spec_version": 1, "renderer_min": 1, "root": { "id": "root", "type": "column", "props": {}, "children": [
      { "id": "volume", "type": "volume", "props": {
          "boxes": [ $boxes ],
          "styles": { "tower": { "stroke": "#cc2222" }, "pane": $paneStyle },
          "legend": [] } } ] } }
    """,
)

private fun towerCentrePixel(boxes: String, paneStyle: String): Int =
    driveScreen(spec(boxes, paneStyle), widthDp = WIDTH_DP, heightDp = HEIGHT_DP).use { screen ->
        val tower = assertNotNull(screen.boundsOf("volume/tower"))
        val x = ((tower.left.value + tower.right.value) / 2).toInt()
        val y = ((tower.top.value + tower.bottom.value) / 2).toInt()
        ImageIO.read(ByteArrayInputStream(screen.frame().png)).getRGB(x, y) and 0xFFFFFF
    }

private fun distance(a: Int, b: Int): Int =
    listOf(16, 8, 0).sumOf { shift -> abs(((a shr shift) and 0xFF) - ((b shr shift) and 0xFF)) }

class VolumeOpacityTest {
    @Test
    fun a_box_in_front_at_a_quarter_opacity_leaves_the_box_behind_it_visible() {
        val alone = towerCentrePixel(TOWER, """{ "stroke": "#2255cc" }""")
        val hidden = towerCentrePixel("$PANES$TOWER", """{ "stroke": "#2255cc" }""")
        val seen = towerCentrePixel("$PANES$TOWER", """{ "stroke": "#2255cc", "opacity": 0.25 }""")
        assertTrue(distance(hidden, alone) > 60, "the opaque panes should hide the tower: $hidden vs $alone")
        assertTrue(distance(seen, alone) < distance(hidden, alone), "the tower shows through the panes at a quarter opacity: $seen, opaque $hidden, alone $alone")
    }
}
