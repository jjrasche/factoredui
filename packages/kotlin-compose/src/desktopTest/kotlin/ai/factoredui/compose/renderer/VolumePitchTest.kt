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

private fun specWith(sheetExtra: String): Spec = Json.decodeFromString(
    Spec.serializer(),
    """
    { "spec_version": 1, "renderer_min": 1, "root": { "id": "root", "type": "column", "props": {}, "children": [
      { "id": "volume", "type": "volume", "props": {
          "lighting": "sun",
          "boxes": [
            { "id": "slab", "x_mm": 0, "y_mm": 0, "z_mm": -200, "width_mm": 6000, "depth_mm": 6000, "height_mm": 200, "style": "base" },
            { "id": "sheet", "x_mm": 0, "y_mm": 0, "z_mm": 1000, "width_mm": 3000, "depth_mm": 2000, "height_mm": 40, "style": "base" $sheetExtra } ],
          "styles": { "base": { "stroke": "#335588" } }, "legend": [] } } ] } }
    """,
)

private fun pictureOf(sheetExtra: String): ByteArray = driveScreen(specWith(sheetExtra), widthDp = WIDTH_DP, heightDp = HEIGHT_DP).use { it.frame().png }

class VolumePitchTest {
    @Test
    fun a_box_with_a_pitch_of_zero_draws_exactly_as_one_with_no_pitch() {
        assertTrue(pictureOf("").contentEquals(pictureOf(""","pitch_deg": 0""")))
    }

    @Test
    fun a_pitched_box_is_drawn_differently_and_reaches_higher_on_the_screen_than_a_flat_one() {
        val flat = driveScreen(specWith(""), widthDp = WIDTH_DP, heightDp = HEIGHT_DP).use { assertNotNull(it.boundsOf("volume/sheet")) }
        val tipped = driveScreen(specWith(""","pitch_deg": 35"""), widthDp = WIDTH_DP, heightDp = HEIGHT_DP).use { assertNotNull(it.boundsOf("volume/sheet")) }
        assertFalse(pictureOf("").contentEquals(pictureOf(""","pitch_deg": 35""")), "the pitched sheet draws the same picture")
        assertTrue(tipped.bottom.value - tipped.top.value != flat.bottom.value - flat.top.value, "$tipped vs $flat")
    }
}
