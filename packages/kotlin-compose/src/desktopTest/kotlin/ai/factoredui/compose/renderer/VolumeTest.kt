package ai.factoredui.compose.renderer

import ai.factoredui.compose.crawl.driveScreen
import ai.factoredui.compose.schema.Spec
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val VOLUME_WIDTH_DP = 412
private const val VOLUME_HEIGHT_DP = 915

private val BOXES = """
  [
    { "id": "slab", "x_mm": 2000, "y_mm": 1500, "z_mm": 0, "width_mm": 4000, "depth_mm": 3000, "height_mm": 400, "style": "base" },
    { "id": "tower", "x_mm": 1000, "y_mm": 1000, "z_mm": 400, "width_mm": 600, "depth_mm": 600, "height_mm": 2000, "style": "base", "estimate": true }
  ]
"""

private fun volumeSpec(boxes: String): Spec = Json.decodeFromString(
    Spec.serializer(),
    """
    { "spec_version": 1, "renderer_min": 1, "root": { "id": "root", "type": "column", "props": {}, "children": [
      { "id": "volume", "type": "volume", "props": {
          "boxes": $boxes,
          "styles": { "base": { "stroke": "#335588" } },
          "legend": [ { "label": "Base", "style": "base" }, { "label": "Size unknown", "style": "base", "hatched": true } ] } } ] } }
    """,
)

class VolumeTest {
    @Test
    fun a_drag_across_the_drawing_turns_the_view() {
        driveScreen(volumeSpec(BOXES), widthDp = VOLUME_WIDTH_DP, heightDp = VOLUME_HEIGHT_DP).use { screen ->
            val before = assertNotNull(screen.boundsOf("volume/tower"))
            screen.drag(200f, 200f, 100f, 240f)
            val after = assertNotNull(screen.boundsOf("volume/tower"))
            assertNotEquals(before, after, "the tower stayed where it was after a drag")
        }
    }

    @Test
    fun a_drag_with_no_movement_leaves_the_view_alone() {
        driveScreen(volumeSpec(BOXES), widthDp = VOLUME_WIDTH_DP, heightDp = VOLUME_HEIGHT_DP).use { screen ->
            val before = assertNotNull(screen.boundsOf("volume/tower"))
            screen.drag(200f, 200f, 200f, 200f)
            assertEquals(before, screen.boundsOf("volume/tower"))
        }
    }

    @Test
    fun the_wheel_zooms_and_reset_view_brings_the_first_framing_back() {
        driveScreen(volumeSpec(BOXES), widthDp = VOLUME_WIDTH_DP, heightDp = VOLUME_HEIGHT_DP).use { screen ->
            val before = assertNotNull(screen.boundsOf("volume/slab"))
            screen.scrollAt(200f, 200f, 3f)
            val zoomedOut = assertNotNull(screen.boundsOf("volume/slab"))
            assertTrue(zoomedOut.right.value - zoomedOut.left.value < before.right.value - before.left.value, "the slab did not shrink on zooming out")
            screen.tap("volume/reset")
            assertEquals(before, screen.boundsOf("volume/slab"))
        }
    }

    @Test
    fun the_drawing_stays_inside_the_screen_width_at_first() {
        driveScreen(volumeSpec(BOXES), widthDp = VOLUME_WIDTH_DP, heightDp = VOLUME_HEIGHT_DP).use { screen ->
            val slab = assertNotNull(screen.boundsOf("volume/slab"))
            assertTrue(slab.left.value >= 0f && slab.right.value <= VOLUME_WIDTH_DP, "the slab spans ${slab.left.value}..${slab.right.value}dp")
        }
    }

    @Test
    fun the_legend_names_what_is_hatched_and_a_box_that_cannot_be_drawn_is_named() {
        val spec = volumeSpec("""[ { "id": "ok", "x_mm": 0, "y_mm": 0, "z_mm": 0, "width_mm": 1, "depth_mm": 1, "height_mm": 1 }, { "id": "bad", "x_mm": 0, "y_mm": 0, "z_mm": 0, "width_mm": 1, "depth_mm": 1, "height_mm": null } ]""")
        driveScreen(spec, widthDp = VOLUME_WIDTH_DP, heightDp = VOLUME_HEIGHT_DP).use { screen ->
            assertEquals(listOf("Size unknown"), screen.textsUnder("volume/legend/1"))
            val texts = screen.textsUnder("volume")
            assertTrue("volume box 'bad': width_mm, depth_mm and height_mm must be numbers of 0 or more; it is not drawn." in texts, texts.toString())
        }
    }

    @Test
    fun a_hidden_box_is_not_drawn_and_the_others_keep_their_place() {
        val hiddenSpec = Json.decodeFromString(
            Spec.serializer(),
            """
            { "spec_version": 1, "renderer_min": 1, "root": { "id": "root", "type": "column", "props": {}, "children": [
              { "id": "volume", "type": "volume", "props": {
                  "boxes": $BOXES, "hidden": ["tower"],
                  "styles": { "base": { "stroke": "#335588" } }, "legend": [] } } ] } }
            """,
        )
        driveScreen(hiddenSpec, widthDp = VOLUME_WIDTH_DP, heightDp = VOLUME_HEIGHT_DP).use { screen ->
            assertEquals(null, screen.boundsOf("volume/tower"))
            assertNotNull(screen.boundsOf("volume/slab"))
        }
    }
}
