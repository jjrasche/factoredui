package ai.factoredui.compose.renderer

import ai.factoredui.compose.crawl.driveScreen
import ai.factoredui.compose.schema.Spec
import kotlinx.serialization.json.Json
import kotlin.math.exp
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val WIDTH_DP = 390
private const val HEIGHT_DP = 844
private const val COUNT_I = 70
private const val COUNT_J = 16
private const val COUNT_K = 8
private const val FRAMES = 24
private const val STEP_MM = 304.8
private const val SCALE = 10
private const val SLOWEST_FIRST_FRAME_MS = 4000L
private const val SLOWEST_FRAME_CHANGE_MS = 1000L

private val RAMP = """ "value_ramp": "heat", "min": 40, "max": 80, "unit": "F", "frame": "{heat.hour}", """

private fun blobAt(frame: Int, i: Int, j: Int, k: Int): Int {
    val centreI = frame * COUNT_I / FRAMES.toDouble()
    val distanceSquared = (i - centreI) * (i - centreI) + (j - 8.0) * (j - 8.0) + (k - 4.0) * (k - 4.0)
    return (600 + 200 * exp(-distanceSquared / 40.0)).toInt()
}

private fun framesJson(): String = (0 until FRAMES).joinToString(",", "[", "]") { frame ->
    val cells = ArrayList<Int>(COUNT_I * COUNT_J * COUNT_K)
    for (k in 0 until COUNT_K) for (j in 0 until COUNT_J) for (i in 0 until COUNT_I) cells.add(blobAt(frame, i, j, k))
    cells.joinToString(",", "[", "]")
}

private fun fieldProp(fieldExtra: String, values: String?): String =
    if (values == null) "" else """, "field": { "origin_mm": [0, 0, 0], "step_mm": $STEP_MM, "counts": [$COUNT_I, $COUNT_J, $COUNT_K], "scale": $SCALE, "values": $values $fieldExtra }"""

private fun specWith(fieldExtra: String, ramp: String, values: String?): Spec = Json.decodeFromString(
    Spec.serializer(),
    """
    { "spec_version": 1, "renderer_min": 1, "state": { "heat": { "hour": 0 } },
      "root": { "id": "root", "type": "column", "props": {}, "children": [
        { "id": "volume", "type": "volume", "props": { $ramp
            "boxes": [ { "id": "slab", "x_mm": 10668, "y_mm": 2438, "z_mm": -100, "width_mm": 21336, "depth_mm": 4877, "height_mm": 100, "style": "base" } ],
            "styles": { "base": { "stroke": "#335588" } }, "legend": [] ${fieldProp(fieldExtra, values)} } } ] } }
    """,
)

class VolumeFieldRenderTest {

    @Test
    fun the_field_is_drawn_and_changes_with_the_frame() {
        val values = framesJson()
        driveScreen(specWith("", RAMP, values), widthDp = WIDTH_DP, heightDp = HEIGHT_DP).use { screen ->
            val first = screen.frame().png
            screen.type("heat.hour", 12)
            val later = screen.frame().png
            assertFalse(first.contentEquals(later), "the field looks the same at hour 0 and hour 12")
        }
    }

    @Test
    fun a_volume_with_a_field_draws_differently_from_the_same_volume_without_one() {
        val withField = driveScreen(specWith("", RAMP, framesJson()), widthDp = WIDTH_DP, heightDp = HEIGHT_DP).use { it.frame().png }
        val without = driveScreen(specWith("", RAMP, null), widthDp = WIDTH_DP, heightDp = HEIGHT_DP).use { it.frame().png }
        assertFalse(withField.contentEquals(without))
    }

    @Test
    fun a_field_without_a_ramp_is_named_and_not_drawn() {
        driveScreen(specWith("", "", framesJson()), widthDp = WIDTH_DP, heightDp = HEIGHT_DP).use { screen ->
            assertNotNull(screen.boundsOfText("volume field: give value_ramp to colour it; it is not drawn."))
        }
    }

    @Test
    fun a_mask_keeps_cells_outside_the_house_clear() {
        val everyCell = COUNT_I * COUNT_J * COUNT_K
        val half = """, "mask": [${everyCell / 2}, ${everyCell - everyCell / 2}] """
        val insideCount = everyCell - everyCell / 2
        val values = (0 until FRAMES).joinToString(",", "[", "]") { frame -> (0 until insideCount).joinToString(",", "[", "]") { (600 + frame).toString() } }
        val masked = driveScreen(specWith(half, RAMP, values), widthDp = WIDTH_DP, heightDp = HEIGHT_DP).use { it.frame().png }
        val unmasked = driveScreen(specWith("", RAMP, framesJson()), widthDp = WIDTH_DP, heightDp = HEIGHT_DP).use { it.frame().png }
        assertFalse(masked.contentEquals(unmasked))
    }

    @Test
    fun cells_with_no_reading_draw_clear_exactly_like_cells_outside_the_mask() {
        val everyCell = COUNT_I * COUNT_J * COUNT_K
        val noReadings = (0 until FRAMES).joinToString(",", "[", "]") { (0 until everyCell).joinToString(",", "[", "]") { "null" } }
        val allOutside = (0 until FRAMES).joinToString(",", "[", "]") { "[]" }
        val unread = driveScreen(specWith("", RAMP, noReadings), widthDp = WIDTH_DP, heightDp = HEIGHT_DP).use { it.frame().png }
        val masked = driveScreen(specWith(""", "mask": [$everyCell] """, RAMP, allOutside), widthDp = WIDTH_DP, heightDp = HEIGHT_DP).use { it.frame().png }
        assertTrue(unread.contentEquals(masked), "an unread cell leaves a mark")
    }

    @Test
    fun about_nine_thousand_cells_over_24_frames_draws_its_first_frame_and_changes_frame_quickly() {
        val values = framesJson()
        val start = System.nanoTime()
        driveScreen(specWith("", RAMP, values), widthDp = WIDTH_DP, heightDp = HEIGHT_DP).use { screen ->
            screen.frame()
            val firstFrameMs = (System.nanoTime() - start) / 1_000_000
            val changeStart = System.nanoTime()
            screen.type("heat.hour", 7)
            screen.frame()
            val changeMs = (System.nanoTime() - changeStart) / 1_000_000
            println("FIELD-COST first_frame_ms=$firstFrameMs frame_change_ms=$changeMs cells=${COUNT_I * COUNT_J * COUNT_K}")
            assertTrue(firstFrameMs < SLOWEST_FIRST_FRAME_MS, "first frame took $firstFrameMs ms")
            assertTrue(changeMs < SLOWEST_FRAME_CHANGE_MS, "a frame change took $changeMs ms")
        }
    }
}
