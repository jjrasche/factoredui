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

private fun specWith(extra: String): Spec = Json.decodeFromString(
    Spec.serializer(),
    """
    { "spec_version": 1, "renderer_min": 1, "root": { "id": "root", "type": "column", "props": {}, "children": [
      { "id": "volume", "type": "volume", "props": { $extra
          "boxes": [
            { "id": "hall", "x_mm": 0, "y_mm": 0, "z_mm": 0, "width_mm": 20000, "depth_mm": 6000, "height_mm": 3000, "style": "big" },
            { "id": "hut", "x_mm": 1000, "y_mm": 0, "z_mm": 0, "width_mm": 2000, "depth_mm": 2000, "height_mm": 2000, "style": "small" } ],
          "styles": { "big": { "stroke": "#335588" }, "small": { "stroke": "#cc2222" } }, "legend": [] } } ] } }
    """,
)

private fun pictureOf(extra: String): ByteArray = driveScreen(specWith(extra), widthDp = WIDTH_DP, heightDp = HEIGHT_DP).use { it.frame().png }

private fun widthOf(extra: String, id: String): Float = driveScreen(specWith(extra), widthDp = WIDTH_DP, heightDp = HEIGHT_DP).use {
    val bounds = assertNotNull(it.boundsOf("volume/$id"))
    bounds.right.value - bounds.left.value
}

class VolumeHighlightTest {
    @Test
    fun naming_boxes_in_highlight_changes_how_the_others_are_drawn() {
        assertFalse(pictureOf("").contentEquals(pictureOf(""" "highlight": ["hut"], """)))
    }

    @Test
    fun a_style_name_in_highlight_picks_the_boxes_of_that_style() {
        assertTrue(pictureOf(""" "highlight": ["hut"], """).contentEquals(pictureOf(""" "highlight": ["small"], """)))
    }

    @Test
    fun a_highlight_that_names_nothing_the_volume_holds_changes_nothing() {
        assertTrue(pictureOf("").contentEquals(pictureOf(""" "highlight": ["nothing here"], """)))
    }

    @Test
    fun focus_frames_the_named_boxes_closer_than_the_whole_scene() {
        assertTrue(widthOf(""" "focus": ["hut"], """, "hut") > 2 * widthOf("", "hut"), "focusing on the hut should make it much larger")
    }
}
