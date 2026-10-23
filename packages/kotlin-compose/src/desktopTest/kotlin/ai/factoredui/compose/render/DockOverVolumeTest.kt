package ai.factoredui.compose.render

import ai.factoredui.compose.crawl.driveScreen
import ai.factoredui.compose.renderer.SpecTheme
import ai.factoredui.compose.schema.Spec
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val PHONE_WIDTH_DP = 390
private const val PHONE_HEIGHT_DP = 844
private const val DOCK_PEEK_DP = 260

private val BODY_LINES = (1..40).joinToString(",") { """{ "id": "line$it", "type": "text", "props": { "value": "System number $it" } }""" }

private val SPEC = Json.decodeFromString(
    Spec.serializer(),
    """
    { "spec_version": 1, "renderer_min": 1, "theme": "dark",
      "state": { "pick": { "items": [], "through": -1, "hidden": [], "stop": "peek", "selected": [] } },
      "root": { "id": "root", "type": "stack", "props": { "fill": true }, "children": [
        { "id": "volume", "type": "volume", "props": {
            "boxes": [
              { "id": "slab", "x_mm": 2000, "y_mm": 1500, "z_mm": 0, "width_mm": 4000, "depth_mm": 3000, "height_mm": 400, "style": "base" },
              { "id": "tower", "x_mm": 1000, "y_mm": 1000, "z_mm": 400, "width_mm": 600, "depth_mm": 600, "height_mm": 2000, "style": "base" } ],
            "styles": { "base": { "stroke": "#335588" } }, "legend": [],
            "fill": true, "anchor": "fill", "bottom_inset_dp": "{pick.inset}",
            "selected": "{pick.selected}", "picked": "{pick.items}", "through": "{pick.through}", "hidden": "{pick.hidden}", "detail_record": "{piece}" } },
        { "id": "dock", "type": "sheet", "props": { "peek_height": $DOCK_PEEK_DP, "half_height": 520, "background": "surface", "peek_to": "{pick.inset}" },
          "visible": "{pick.through}=-1", "children": [
            { "id": "dock.body", "type": "column", "props": { "gap": 12, "padding": 16, "slot": "peek" }, "children": [
              { "id": "system.row", "type": "row", "props": { "gap": 8 }, "children": [
                { "id": "system.name", "type": "text", "props": { "value": "glazing", "flex": 1 } },
                { "id": "system.caption", "type": "text", "props": { "value": "a solution whose name is a good deal longer than the room beside it", "flex": 1 } } ] },
              $BODY_LINES ] } ] } ] } }
    """,
)

class DockOverVolumeTest {
    @Test
    fun on_a_phone_the_world_is_drawn_above_a_dock_that_peeks_at_its_peek_height() {
        driveScreen(SPEC, widthDp = PHONE_WIDTH_DP, heightDp = PHONE_HEIGHT_DP, theme = SpecTheme.DARK).use { screen ->
            val slab = assertNotNull(screen.boundsOf("volume/slab"), "the volume's slab is not drawn")
            assertTrue(slab.bottom.value <= PHONE_HEIGHT_DP - DOCK_PEEK_DP + 40f, "the slab reaches ${slab.bottom.value} dp, into the dock")
            val handle = assertNotNull(screen.boundsOf("dock/handle"), "the dock has no handle")
            assertTrue(handle.top.value in (PHONE_HEIGHT_DP - DOCK_PEEK_DP - 12f)..(PHONE_HEIGHT_DP - DOCK_PEEK_DP + 12f), "the dock starts at ${handle.top.value} dp of $PHONE_HEIGHT_DP")
        }
    }

    @Test
    fun a_row_whose_trailing_text_is_long_still_leaves_its_label_room() {
        driveScreen(SPEC, widthDp = PHONE_WIDTH_DP, heightDp = PHONE_HEIGHT_DP, theme = SpecTheme.DARK).use { screen ->
            val label = assertNotNull(screen.boundsOfText("glazing"), "the label is not drawn")
            assertTrue(label.right.value - label.left.value > 40f, "the label is ${label.right.value - label.left.value} dp wide")
        }
    }
}
