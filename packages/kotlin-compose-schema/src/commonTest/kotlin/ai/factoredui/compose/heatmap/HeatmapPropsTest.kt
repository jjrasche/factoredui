package ai.factoredui.compose.heatmap

import ai.factoredui.compose.ramp.rampColourFor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val AXIS_3 = mapOf("origin" to 0, "step" to 1, "count" to 3)
private val AXIS_2 = mapOf("origin" to 10, "step" to 5, "count" to 2)

private fun heat(extra: Map<String, Any?> = emptyMap()): Map<String, Any?> =
    mapOf("x" to AXIS_3, "y" to AXIS_2, "frames" to listOf(listOf(1, 2, 3, 4, 5, 6), listOf(6, 5, 4, 3, 2, 1)), "min" to 0, "max" to 10, "ramp" to "heat") + extra

private fun drawn(resolved: Map<String, Any?>): HeatmapProps = (readHeatmapProps(resolved) as HeatmapReading.Drawn).props

private fun refused(resolved: Map<String, Any?>): List<String> = (readHeatmapProps(resolved) as HeatmapReading.Refused).notices

class HeatmapPropsTest {
    @Test
    fun the_axes_the_frames_and_the_range_are_read() {
        val props = drawn(heat(mapOf("unit" to "F")))
        assertEquals(HeatAxis(10.0, 5.0, 2, null).last, props.y.last)
        assertEquals(listOf(listOf(1.0, 2.0, 3.0, 4.0, 5.0, 6.0), listOf(6.0, 5.0, 4.0, 3.0, 2.0, 1.0)), props.frames)
        assertEquals(0.0 to 10.0, props.min to props.max)
        assertEquals("F", props.unit)
    }

    @Test
    fun a_named_ramp_runs_from_its_first_colour_at_min_to_its_last_at_max() {
        val ramp = drawn(heat()).ramp
        assertEquals(0xFF1B1B3AL, rampColourFor(ramp, 0.0))
        assertEquals(0xFFFFF3B0L, rampColourFor(ramp, 10.0))
    }

    @Test
    fun a_ramp_of_stops_needs_no_min_and_max_and_takes_them_from_its_first_and_last_stop() {
        val props = drawn(heat(mapOf("ramp" to mapOf("stops" to listOf(listOf(20, "#0000ff"), listOf(80, "#ff0000"))), "min" to null, "max" to null)))
        assertEquals(20.0 to 80.0, props.min to props.max)
    }

    @Test
    fun a_cell_that_is_not_a_number_is_blank_and_a_short_frame_is_named() {
        val props = drawn(heat(mapOf("frames" to listOf(listOf(1, "x", null, 4, 5)))))
        assertEquals(listOf(1.0, null, null, 4.0, 5.0, null), props.frames.single())
        assertEquals(listOf("heatmap frame 0 holds 5 cells, not 6; the cells it lacks are left blank."), props.notices)
    }

    @Test
    fun an_axis_without_a_step_above_zero_or_a_count_refuses_the_drawing() {
        assertTrue(refused(heat(mapOf("x" to mapOf("origin" to 0, "step" to 0, "count" to 3)))).single().startsWith("heatmap x needs"))
        assertTrue(refused(heat(mapOf("y" to mapOf("origin" to 0, "step" to 1, "count" to 0)))).single().startsWith("heatmap y needs"))
    }

    @Test
    fun a_ramp_name_it_does_not_know_or_a_name_with_no_range_refuses_the_drawing() {
        assertTrue(refused(heat(mapOf("ramp" to "rainbow"))).single().contains("is not one of heat, cool_warm, viridis"))
        assertTrue(refused(heat(mapOf("min" to 5, "max" to 5))).single().contains("needs min and max"))
    }

    @Test
    fun the_frame_index_is_held_between_the_first_and_last_frame() {
        assertEquals(listOf(0, 0, 1, 2, 2), listOf(null, -3, 1.9, 2, 40).map { heatFrameIndex(it, 3) })
        assertEquals(0, heatFrameIndex(5, 0))
    }

    @Test
    fun the_legend_text_carries_the_unit_when_there_is_one() {
        assertEquals("68 F", heatLegendText(68.0, "F"))
        assertEquals("68", heatLegendText(68.0, null))
    }
}
