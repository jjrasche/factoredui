package ai.factoredui.compose.volume

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun fieldWith(extra: Map<String, Any?>): Any = mapOf(
    "origin_mm" to listOf(1000, 2000, 300),
    "step_mm" to 100,
    "counts" to listOf(2, 2, 2),
) + extra

class VolumeFieldTest {

    @Test
    fun no_mask_means_every_cell_is_inside() {
        assertContentEquals(IntArray(8) { it }, readVolumeField(fieldWith(emptyMap())).field!!.inside)
    }

    @Test
    fun a_mask_alternates_outside_and_inside_runs_starting_outside() {
        assertContentEquals(intArrayOf(1, 2, 7), fieldInsideCells(listOf(1, 2, 4, 1), 8))
        assertContentEquals(intArrayOf(0, 1), fieldInsideCells(listOf(0, 2, 6), 8))
    }

    @Test
    fun a_mask_that_does_not_add_up_to_the_grid_is_refused_with_a_notice() {
        val read = readVolumeField(fieldWith(mapOf("mask" to listOf(1, 2))))
        assertNull(read.field)
        assertTrue(read.notices.single().contains("8 cells"))
    }

    @Test
    fun values_are_divided_by_the_scale_and_follow_the_inside_cells() {
        val field = readVolumeField(fieldWith(mapOf("mask" to listOf(1, 2, 5), "scale" to 10, "values" to listOf(listOf(655, null), listOf(700, 710))))).field!!
        assertEquals(2, field.frames.size)
        assertEquals(65.5, field.frames[0][0])
        assertTrue(field.frames[0][1].isNaN())
        assertEquals(71.0, field.frames[1][1])
    }

    @Test
    fun a_frame_with_the_wrong_number_of_values_is_noticed_and_padded_with_no_reading() {
        val read = readVolumeField(fieldWith(mapOf("mask" to listOf(0, 2, 6), "values" to listOf(listOf(1)))))
        assertEquals(1, read.notices.size)
        assertTrue(read.field!!.frames[0][1].isNaN())
    }

    @Test
    fun a_cell_centre_is_half_a_step_in_from_the_origin_with_i_running_fastest() {
        val field = readVolumeField(fieldWith(emptyMap())).field!!
        assertEquals(VolumePoint(1050.0, 2050.0, 350.0), field.centreOf(0))
        assertEquals(VolumePoint(1150.0, 2050.0, 350.0), field.centreOf(1))
        assertEquals(VolumePoint(1050.0, 2150.0, 350.0), field.centreOf(2))
        assertEquals(VolumePoint(1150.0, 2150.0, 450.0), field.centreOf(7))
    }

    @Test
    fun the_span_covers_the_whole_grid_and_unions_with_the_boxes() {
        val field = readVolumeField(fieldWith(emptyMap())).field!!
        assertEquals(VolumeSpanMm(1000.0, 2000.0, 300.0, 1200.0, 2200.0, 500.0), field.span())
        val boxes = VolumeSpanMm(0.0, 0.0, 0.0, 100.0, 100.0, 100.0)
        assertEquals(VolumeSpanMm(0.0, 0.0, 0.0, 1200.0, 2200.0, 500.0), unionSpan(boxes, field.span()))
        assertNotNull(unionSpan(null, field.span()))
    }

    @Test
    fun a_field_without_a_usable_grid_is_refused() {
        assertNull(readVolumeField(mapOf("origin_mm" to listOf(0, 0, 0), "step_mm" to 0, "counts" to listOf(1, 1, 1))).field)
        assertNull(readVolumeField(null).field)
    }

    @Test
    fun the_cells_style_is_solid_by_default_and_soft_is_the_default_style() {
        assertTrue(readVolumeField(fieldWith(mapOf("style" to "cells"))).field!!.isCells)
        assertEquals(1.0, readVolumeField(fieldWith(mapOf("style" to "cells"))).field!!.opacity)
        assertEquals(0.5, readVolumeField(fieldWith(mapOf("style" to "soft"))).field!!.opacity)
        assertEquals(false, readVolumeField(fieldWith(emptyMap())).field!!.isCells)
    }

    @Test
    fun an_unknown_style_is_named_and_soft_is_used() {
        val read = readVolumeField(fieldWith(mapOf("style" to "squares")))
        assertEquals(false, read.field!!.isCells)
        assertEquals("volume field: style must be soft or cells, not 'squares'; soft is drawn.", read.notices.single())
    }

    @Test
    fun a_null_frame_is_one_that_has_not_arrived_and_draws_clear_without_a_notice() {
        val read = readVolumeField(fieldWith(mapOf("values" to listOf(listOf(1, 2, 3, 4, 5, 6, 7, 8), null, listOf(8, 7, 6, 5, 4, 3, 2, 1)))))
        assertEquals(setOf(1), read.field!!.notLoaded)
        assertEquals(3, read.field!!.frames.size)
        assertTrue(read.field!!.frames[1].all { it.isNaN() })
        assertTrue(read.notices.isEmpty())
    }

    @Test
    fun a_short_frame_is_still_named() {
        assertTrue(readVolumeField(fieldWith(mapOf("values" to listOf(listOf(1, 2))))).notices.single().contains("frame 0 has 2 values for 8"))
    }
}
