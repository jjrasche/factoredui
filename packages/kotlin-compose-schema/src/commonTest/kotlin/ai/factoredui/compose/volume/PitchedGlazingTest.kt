package ai.factoredui.compose.volume

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val ROOF_PANEL = mapOf(
    "id" to "Greenhouse/roof-panel@11174,10205,3776~1219x2438",
    "style" to "multiwall polycarbonate",
    "x_mm" to 11783.5,
    "y_mm" to 11424.0,
    "z_mm" to 3776.0,
    "width_mm" to 1219.0,
    "depth_mm" to 2438.0,
    "height_mm" to 8.0,
    "rotation_deg" to 0.0,
    "pitch_deg" to 11.848,
    "system" to "lean_to_house",
)

private fun ringsOf(piece: Map<String, Any?>): BoxRings = boxRings(readVolume(listOf(piece), null, null).boxes.single())

class PitchedGlazingTest {

    @Test
    fun a_served_roof_panel_rises_about_250_mm_either_side_of_its_centre_along_its_depth() {
        val corners = ringsOf(ROOF_PANEL).high
        val rise = corners.maxOf { it.zMm } - corners.minOf { it.zMm }
        assertTrue(abs(rise - 500.4) < 2.0, "the panel spans $rise mm of height, expected about 500")
    }

    @Test
    fun the_same_panel_without_pitch_lies_level() {
        val corners = ringsOf(ROOF_PANEL - "pitch_deg").high
        assertEquals(1, corners.map { it.zMm }.distinct().size)
    }

    @Test
    fun the_panel_tips_about_its_width_axis_so_its_two_long_edges_stay_level() {
        val corners = ringsOf(ROOF_PANEL).high
        assertEquals(corners[0].zMm, corners[1].zMm, 1e-6)
        assertEquals(corners[2].zMm, corners[3].zMm, 1e-6)
    }
}
