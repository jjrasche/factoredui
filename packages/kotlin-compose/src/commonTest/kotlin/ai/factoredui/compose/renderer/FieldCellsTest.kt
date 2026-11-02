package ai.factoredui.compose.renderer

import ai.factoredui.compose.heatmap.readRampOrName
import ai.factoredui.compose.math.Camera
import ai.factoredui.compose.math.Vec3
import ai.factoredui.compose.volume.readVolumeField
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val SIZE_PX = 200f
private const val PIXELS_PER_METRE = 100f
private const val OVERLAP_PX = 0.3f

private fun twoCells(style: String, values: List<Any?>) = readVolumeField(
    mapOf(
        "origin_mm" to listOf(0, 0, 0), "step_mm" to 1000, "counts" to listOf(2, 1, 1), "style" to style,
        "scale" to 1, "values" to listOf(values),
    ),
).field!!

private fun sideCamera() = Camera(yawRadians = 0f, pitchRadians = 0f, distance = 10f, target = Vec3(1f, 0.5f, -0.5f), orthoHalfHeight = 1f)

class FieldCellsTest {

    @Test
    fun a_cell_in_an_orthographic_side_view_is_an_axis_aligned_square_one_step_wide_at_an_exact_place() {
        val field = twoCells("cells", listOf(1, 2))
        val splats = fieldSplatsOf(field, sideCamera(), SIZE_PX, SIZE_PX)
        val first = splats.cells.indexOf(0) * splats.verticesPerSplat * 2
        val xs = (0 until splats.verticesPerSplat).map { splats.positions[first + it * 2] }
        val ys = (0 until splats.verticesPerSplat).map { splats.positions[first + it * 2 + 1] }
        val half = PIXELS_PER_METRE / 2 + OVERLAP_PX
        assertEquals(setOf(50f - half, 50f + half), xs.toSet())
        assertEquals(setOf(100f - half, 100f + half), ys.toSet())
        assertEquals(6, splats.verticesPerSplat)
    }

    @Test
    fun the_same_cell_is_the_same_square_wherever_it_is_in_depth() {
        val near = twoCells("cells", listOf(1, 2))
        val deep = readVolumeField(
            mapOf("origin_mm" to listOf(0, 4000, 0), "step_mm" to 1000, "counts" to listOf(2, 1, 1), "style" to "cells", "values" to listOf(listOf(1, 2))),
        ).field!!
        val camera = sideCamera()
        assertEquals(fieldSplatsOf(near, camera, SIZE_PX, SIZE_PX).positions.toList(), fieldSplatsOf(deep, camera, SIZE_PX, SIZE_PX).positions.toList())
    }

    @Test
    fun a_square_is_one_solid_colour_and_a_cell_with_no_reading_is_clear() {
        val field = twoCells("cells", listOf(1, null))
        val splats = fieldSplatsOf(field, sideCamera(), SIZE_PX, SIZE_PX)
        val ramp = readRampOrName("heat", 0.0, 2.0).first!!
        val colours = fieldSplatColours(field, splats, 0, ramp)
        val read = splats.cells.indexOf(0) * splats.verticesPerSplat
        val unread = splats.cells.indexOf(1) * splats.verticesPerSplat
        assertEquals(1, colours.slice(read until read + 6).toSet().size)
        assertEquals(0xFF, colours[read] ushr 24 and 0xFF)
        assertTrue(colours.slice(unread until unread + 6).all { it == 0 })
    }

    @Test
    fun a_soft_disc_still_fades_to_a_clear_rim() {
        val field = twoCells("soft", listOf(1, 2))
        val splats = fieldSplatsOf(field, sideCamera(), SIZE_PX, SIZE_PX)
        val colours = fieldSplatColours(field, splats, 0, readRampOrName("heat", 0.0, 2.0).first!!)
        assertEquals(18, splats.verticesPerSplat)
        assertEquals(0, colours[1] ushr 24 and 0xFF)
        assertEquals(127, colours[0] ushr 24 and 0xFF)
    }
}
