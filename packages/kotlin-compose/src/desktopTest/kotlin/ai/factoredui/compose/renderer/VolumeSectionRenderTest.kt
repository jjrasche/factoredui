package ai.factoredui.compose.renderer

import ai.factoredui.compose.crawl.driveScreen
import ai.factoredui.compose.schema.Spec
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val WIDTH_DP = 390
private const val HEIGHT_DP = 844
private const val STEP_MM = 100
private const val FRAMES = 24
private const val SLOWEST_FIRST_FRAME_MS = 6000L
private const val SLOWEST_FRAME_CHANGE_MS = 2000L

private const val RAMP = """ "value_ramp": "heat", "min": 40, "max": 80, "unit": "F", "frame": "{heat.hour}", """

private fun frameJson(columns: Int, rows: Int, hour: Int): String {
    val cells = StringBuilder("[")
    for (row in 0 until rows) for (column in 0 until columns) {
        if (cells.length > 1) cells.append(',')
        cells.append(400 + (column * 400 / columns + row * 5 + hour * 3) % 400)
    }
    return cells.append(']').toString()
}

private fun framesJson(columns: Int, rows: Int, frames: Int): String = (0 until frames).joinToString(",", "[", "]") { frameJson(columns, rows, it) }

private fun sectionSpec(columns: Int, rows: Int, values: String, fieldExtra: String = """, "style": "cells" """, volumeExtra: String = """ "camera": "side", """): Spec = Json.decodeFromString(
    Spec.serializer(),
    """
    { "spec_version": 1, "renderer_min": 1, "state": { "heat": { "hour": 0 } },
      "root": { "id": "root", "type": "column", "props": {}, "children": [
        { "id": "volume", "type": "volume", "props": { $RAMP $volumeExtra "boxes": [], "styles": {}, "legend": [],
            "field": { "origin_mm": [0, 0, 0], "step_mm": $STEP_MM, "counts": [$columns, 1, $rows], "scale": 10, "values": $values $fieldExtra } } } ] } }
    """,
)

class VolumeSectionRenderTest {

    @Test
    fun a_side_section_of_cells_is_drawn_and_changes_with_the_frame() {
        driveScreen(sectionSpec(40, 20, framesJson(40, 20, 3)), widthDp = WIDTH_DP, heightDp = HEIGHT_DP).use { screen ->
            val first = screen.frame().png
            screen.type("heat.hour", 2)
            assertFalse(first.contentEquals(screen.frame().png), "the section looks the same at hour 0 and hour 2")
        }
    }

    @Test
    fun the_side_camera_draws_differently_from_the_orbit_camera() {
        val values = framesJson(40, 20, 1)
        val side = driveScreen(sectionSpec(40, 20, values), widthDp = WIDTH_DP, heightDp = HEIGHT_DP).use { it.frame().png }
        val orbit = driveScreen(sectionSpec(40, 20, values, volumeExtra = ""), widthDp = WIDTH_DP, heightDp = HEIGHT_DP).use { it.frame().png }
        assertFalse(side.contentEquals(orbit))
    }

    @Test
    fun cells_draw_differently_from_soft_discs() {
        val values = framesJson(40, 20, 1)
        val cells = driveScreen(sectionSpec(40, 20, values), widthDp = WIDTH_DP, heightDp = HEIGHT_DP).use { it.frame().png }
        val soft = driveScreen(sectionSpec(40, 20, values, fieldExtra = ""), widthDp = WIDTH_DP, heightDp = HEIGHT_DP).use { it.frame().png }
        assertFalse(cells.contentEquals(soft))
    }

    @Test
    fun a_camera_it_does_not_know_is_named() {
        driveScreen(sectionSpec(4, 2, framesJson(4, 2, 1), volumeExtra = """ "camera": "top", """), widthDp = WIDTH_DP, heightDp = HEIGHT_DP).use { screen ->
            assertNotNull(screen.boundsOfText("volume camera: 'top' is not orbit or side; orbit is used."))
        }
    }

    @Test
    fun a_frame_that_has_not_arrived_is_named_only_while_it_is_the_one_shown() {
        val values = "[${frameJson(4, 2, 0)}, null]"
        driveScreen(sectionSpec(4, 2, values), widthDp = WIDTH_DP, heightDp = HEIGHT_DP).use { screen ->
            screen.frame()
            assertNull(screen.boundsOfText("volume field: frame 1 is not loaded yet."))
            screen.type("heat.hour", 1)
            screen.frame()
            assertNotNull(screen.boundsOfText("volume field: frame 1 is not loaded yet."))
        }
    }

    @Test
    fun twenty_thousand_cells_over_24_frames_draws_its_first_frame_and_changes_frame_quickly() {
        val columns = 200
        val rows = 100
        val values = framesJson(columns, rows, FRAMES)
        val start = System.nanoTime()
        driveScreen(sectionSpec(columns, rows, values), widthDp = WIDTH_DP, heightDp = HEIGHT_DP).use { screen ->
            screen.frame()
            val firstFrameMs = (System.nanoTime() - start) / 1_000_000
            val changeStart = System.nanoTime()
            screen.type("heat.hour", 7)
            screen.frame()
            val changeMs = (System.nanoTime() - changeStart) / 1_000_000
            println("FIELD-SECTION-COST first_frame_ms=$firstFrameMs frame_change_ms=$changeMs cells=${columns * rows} frames=$FRAMES payload_bytes=${values.length}")
            assertTrue(firstFrameMs < SLOWEST_FIRST_FRAME_MS, "first frame took $firstFrameMs ms")
            assertTrue(changeMs < SLOWEST_FRAME_CHANGE_MS, "a frame change took $changeMs ms")
        }
    }
}
