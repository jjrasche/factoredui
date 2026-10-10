package ai.factoredui.compose.structure

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val SLACK = 1e-9

private fun assertClose(expected: Double, actual: Double, label: String) = assertTrue(abs(actual - expected) < SLACK, "$label: $actual vs $expected")

class PlanLayoutTest {
    @Test
    fun a_tile_is_the_window_width_over_the_column_count() =
        assertEquals(RectMm(1220.0, 1220.0, 1830.0, 1830.0), partRectMm(stackScene(), part("p", col = 2, row = 2)))

    @Test
    fun a_parts_rectangle_spans_its_width_and_height_in_tiles() =
        assertEquals(RectMm(1220.0, 4270.0, 9150.0, 4880.0), partRectMm(stackScene(), part("p", col = 2, row = 7, width = 13, height = 1)))

    @Test
    fun the_window_fits_the_canvas_with_equal_scale_on_both_axes_and_the_margin_kept() {
        val fit = fitWindow(RectMm(0.0, 0.0, 9760.0, 8540.0), 800.0, 600.0, 20.0)
        assertTrue(fit.scale > 0)
        val (rightX, topY) = mmToCanvas(fit, 9760.0, 8540.0)
        val (leftX, bottomY) = mmToCanvas(fit, 0.0, 0.0)
        assertTrue(leftX >= 20 - SLACK && rightX <= 780 + SLACK && topY >= 20 - SLACK && bottomY <= 580 + SLACK)
        assertClose((rightX - leftX) / 9760, (bottomY - topY) / 8540, "equal scale")
    }

    @Test
    fun south_the_low_y_is_at_the_bottom_of_the_canvas_and_east_is_to_the_right() {
        val fit = fitWindow(RectMm(0.0, 0.0, 1000.0, 1000.0), 400.0, 400.0, 0.0)
        val (southWestX, southWestY) = mmToCanvas(fit, 0.0, 0.0)
        val (northEastX, northEastY) = mmToCanvas(fit, 1000.0, 1000.0)
        assertTrue(southWestY > northEastY)
        assertTrue(northEastX > southWestX)
    }

    @Test
    fun a_vertical_exaggeration_stretches_the_vertical_scale_only_and_the_window_still_fits() {
        val fit = fitWindow(RectMm(0.0, 0.0, 9760.0, 1000.0), 800.0, 600.0, 20.0, exaggeration = 4.0)
        assertClose(fit.scale * 4, fit.vScale, "vertical scale")
        val (rightX) = mmToCanvas(fit, 9760.0, 0.0)
        val topY = mmToCanvas(fit, 0.0, 1000.0).second
        val bottomY = mmToCanvas(fit, 0.0, 0.0).second
        assertTrue(rightX <= 780 + SLACK && topY >= 20 - SLACK && bottomY <= 580 + SLACK)
        assertClose(1000 * fit.vScale, bottomY - topY, "height in pixels")
    }

    @Test
    fun an_exaggeration_that_would_overflow_the_height_is_reduced_so_the_drawing_still_fits() {
        val fit = fitWindow(RectMm(0.0, 0.0, 1000.0, 1000.0), 800.0, 400.0, 0.0, exaggeration = 4.0)
        assertTrue(mmToCanvas(fit, 0.0, 1000.0).second >= -SLACK && mmToCanvas(fit, 0.0, 0.0).second <= 400 + SLACK)
    }

    @Test
    fun a_canvas_point_maps_back_through_an_exaggerated_scale_too() {
        val fit = fitWindow(RectMm(0.0, 0.0, 9760.0, 1000.0), 800.0, 600.0, 20.0, exaggeration = 4.0)
        val (x, y) = mmToCanvas(fit, 3333.0, 444.0)
        val (backX, backY) = canvasToMm(fit, x, y)
        assertClose(3333.0, backX, "x")
        assertClose(444.0, backY, "y")
    }

    @Test
    fun a_window_with_no_height_still_fits_by_its_width() {
        val fit = fitWindow(RectMm(0.0, 0.0, 1000.0, 0.0), 400.0, 400.0, 0.0)
        assertClose(0.4, fit.scale, "scale")
    }

    @Test
    fun where_parts_overlap_on_the_plan_the_top_one_is_hit() {
        val parts = listOf(shown(part("beam", level = 1, col = 2, row = 2, width = 13)), shown(part("panel", level = 2, col = 2, row = 2, width = 4, height = 5)))
        assertEquals("panel", hitPartInView(stackScene(), parts, 1500.0, 1500.0, StructureView.PLAN)?.footprint?.id)
    }

    @Test
    fun a_point_on_a_lower_part_that_no_upper_part_covers_hits_the_lower_part() {
        val parts = listOf(shown(part("beam", level = 1, col = 2, row = 2, width = 13)), shown(part("panel", level = 2, col = 2, row = 2, width = 4, height = 5)))
        assertEquals("beam", hitPartInView(stackScene(), parts, 5000.0, 1500.0, StructureView.PLAN)?.footprint?.id)
    }

    @Test
    fun a_point_on_nothing_hits_nothing() =
        assertNull(hitPartInView(stackScene(), listOf(shown(part("panel", col = 2, row = 2, width = 4, height = 5))), 100.0, 100.0, StructureView.PLAN))

    @Test
    fun a_part_not_in_the_visible_list_cannot_be_hit() = assertNull(hitPartInView(stackScene(), emptyList(), 1500.0, 1500.0, StructureView.PLAN))

    @Test
    fun a_point_exactly_on_a_shared_edge_hits_only_one_part_the_later_in_draw_order() {
        val parts = listOf(shown(part("left", col = 0)), shown(part("right", col = 1)))
        assertEquals("right", hitPartInView(stackScene(), parts, 610.0, 300.0, StructureView.PLAN)?.footprint?.id)
    }
}
