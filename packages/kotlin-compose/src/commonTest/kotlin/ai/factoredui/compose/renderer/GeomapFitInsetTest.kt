package ai.factoredui.compose.renderer

import ai.factoredui.compose.schema.GeomapViewport
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val WIDTH = 400f
private const val HEIGHT = 800f
private val BOX = WorldBounds(0.40, 0.40, 0.4001, 0.4001)

private fun screenYOf(worldY: Double, view: GeomapViewport): Double =
    HEIGHT / 2.0 + (worldY - latToWorldY(view.lat)) * geomapScalePx(view.zoom)

class GeomapFitInsetTest {
    @Test
    fun a_smaller_margin_zooms_out_so_the_box_fills_less_of_the_map() {
        val tight = fitGeomapViewport(BOX, WIDTH, HEIGHT, margin = 0.9)
        val loose = fitGeomapViewport(BOX, WIDTH, HEIGHT, margin = 0.45)
        assertEquals(1.0, (tight.zoom - loose.zoom).toDouble(), 0.01, "halving the share of the map should cost one zoom level")
    }

    @Test
    fun with_no_inset_the_box_sits_at_the_middle_of_the_map_as_it_always_did() {
        val view = fitGeomapViewport(BOX, WIDTH, HEIGHT)
        assertEquals(BOX.centerY, latToWorldY(view.lat), 1e-12)
        assertEquals(BOX.centerX, lonToWorldX(view.lon), 1e-12)
    }

    @Test
    fun a_bottom_inset_lifts_the_whole_box_into_the_part_of_the_map_above_it() {
        val inset = 300f
        val view = fitGeomapViewport(BOX, WIDTH, HEIGHT, margin = 0.6, insetBottomPx = inset)
        val top = screenYOf(BOX.minY, view)
        val bottom = screenYOf(BOX.maxY, view)
        assertTrue(top >= 0.0, "the top of the box is cut off at $top")
        assertTrue(bottom <= HEIGHT - inset, "the bottom of the box is at $bottom, under the inset that starts at ${HEIGHT - inset}")
    }

    @Test
    fun the_box_fills_only_the_margins_share_of_the_part_that_is_left() {
        val inset = 300f
        val view = fitGeomapViewport(BOX, WIDTH, HEIGHT, margin = 0.6, insetBottomPx = inset)
        val shown = screenYOf(BOX.maxY, view) - screenYOf(BOX.minY, view)
        val widthShown = (BOX.maxX - BOX.minX) * geomapScalePx(view.zoom)
        val larger = maxOf(shown / (HEIGHT - inset), widthShown / WIDTH)
        assertTrue(abs(larger - 0.6) < 0.02, "the box fills $larger of the free part, not 0.6")
    }
}
