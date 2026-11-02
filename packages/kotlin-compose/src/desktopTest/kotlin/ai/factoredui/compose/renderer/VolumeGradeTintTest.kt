package ai.factoredui.compose.renderer

import ai.factoredui.compose.crawl.driveScreen
import ai.factoredui.compose.schema.Spec
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse

private const val WIDTH_DP = 412
private const val HEIGHT_DP = 915

private fun spec(tint: String): Spec = Json.decodeFromString(
    Spec.serializer(),
    """
    { "spec_version": 1, "renderer_min": 1, "root": { "id": "root", "type": "column", "props": {}, "children": [
      { "id": "volume", "type": "volume", "props": {
          "grade_mm": 500 $tint,
          "boxes": [ { "id": "wall", "x_mm": 0, "y_mm": 0, "z_mm": 0, "width_mm": 4000, "depth_mm": 300, "height_mm": 2400, "style": "base" } ],
          "styles": { "base": { "stroke": "#335588" } }, "legend": [] } } ] } }
    """,
)

private fun pictureWith(tint: String): ByteArray = driveScreen(spec(tint), widthDp = WIDTH_DP, heightDp = HEIGHT_DP).use { it.frame().png }

class VolumeGradeTintTest {
    @Test
    fun a_volume_with_no_tint_draws_the_plane_in_its_default_brown_and_half_opacity() {
        assertContentEquals(pictureWith(""), pictureWith(""","grade_color": "#6b5a48", "grade_opacity": 0.5"""))
    }

    @Test
    fun a_tint_the_spec_names_changes_the_plane() {
        assertFalse(pictureWith("").contentEquals(pictureWith(""","grade_color": "#2255cc", "grade_opacity": 1""")))
    }
}
