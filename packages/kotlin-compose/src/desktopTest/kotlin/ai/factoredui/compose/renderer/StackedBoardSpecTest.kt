package ai.factoredui.compose.renderer

import ai.factoredui.compose.crawl.driveScreen
import ai.factoredui.compose.scene.Report
import ai.factoredui.compose.scene.readReports
import ai.factoredui.compose.scene.reportsState
import ai.factoredui.compose.schema.Spec
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private val json = Json { ignoreUnknownKeys = true }

internal const val BOARD_SCREEN_WIDTH = 900
internal const val BOARD_SCREEN_HEIGHT = 1000

internal const val UNKNOWN_REASON = "not valued yet"

internal fun stackedBoardSpec(): Spec = json.decodeFromString(
    Spec.serializer(),
    checkNotNull(StackedBoardSpecTest::class.java.getResource("/specs/stacked-board.spec.json")) { "stacked-board.spec.json is not on the test classpath" }.readText(),
)

internal fun stackedBoardReports(): List<Report> = readReports(
    checkNotNull(StackedBoardSpecTest::class.java.getResource("/stacked-board.reports.json")) { "stacked-board.reports.json is not on the test classpath" }.readText(),
)

internal fun groupObjectIds(rowId: String): List<String> = stackedBoardReports().first { it.id == "groups" }.rows.first { it.id == rowId }.objectIds

internal fun stackedBoardStore(view: String, picked: List<String> = emptyList(), board: Map<String, Any?> = emptyMap()): Map<String, Any?> = mapOf(
    "board" to mapOf(
        "scene" to stackedBoardText(),
        "view" to view,
        "focus" to "all",
        "hide_top" to "0",
        "show_lower" to false,
        "picked" to picked,
        "unknown_reason" to UNKNOWN_REASON,
    ) + board,
    "reports" to reportsState(stackedBoardReports()),
)

private fun boardIn(store: Map<String, Any?>): Map<*, *> = store["board"] as Map<*, *>

private fun pickedIn(store: Map<String, Any?>): Any? = boardIn(store)["picked"]

private val COLUMNS = listOf("label", "count", "worth")

class StackedBoardSpecTest {

    @Test
    fun tapping_a_report_row_writes_its_object_ids_to_the_bound_highlight_and_tapping_another_moves_it() {
        driveScreen(stackedBoardSpec(), stackedBoardStore("plan"), BOARD_SCREEN_WIDTH, BOARD_SCREEN_HEIGHT).use { screen ->
            assertEquals("drawn=12 highlighted=0 faded=0 selected=", screen.stateDescriptionOf(partsTag("board")))

            screen.tapText("Blocks")
            assertEquals(groupObjectIds("blocks"), pickedIn(screen.store()))
            assertEquals("drawn=12 highlighted=6 faded=6 selected=", screen.stateDescriptionOf(partsTag("board")))

            screen.tapText("Bars")
            assertEquals(listOf("r1", "r2"), pickedIn(screen.store()))
            assertEquals("drawn=12 highlighted=2 faded=10 selected=", screen.stateDescriptionOf(partsTag("board")))
        }
    }

    @Test
    fun a_row_naming_every_part_highlights_them_all_and_a_row_naming_none_highlights_nothing() {
        driveScreen(stackedBoardSpec(), stackedBoardStore("plan"), BOARD_SCREEN_WIDTH, BOARD_SCREEN_HEIGHT).use { screen ->
            screen.tapText("Everything")
            assertEquals("drawn=12 highlighted=12 faded=0 selected=", screen.stateDescriptionOf(partsTag("board")))
            screen.tapText("Loose pieces")
            assertEquals(emptyList<String>(), pickedIn(screen.store()))
            assertEquals("drawn=12 highlighted=0 faded=0 selected=", screen.stateDescriptionOf(partsTag("board")))
        }
    }

    @Test
    fun each_row_prints_its_label_and_values_from_the_report_with_no_host_arithmetic() {
        driveScreen(stackedBoardSpec(), stackedBoardStore("plan"), BOARD_SCREEN_WIDTH, BOARD_SCREEN_HEIGHT).use { screen ->
            val shown = screen.textsUnder("stacked-board")
            assertTrue(listOf("Blocks", "6", "12.5").all { it in shown }, "$shown")
            assertTrue(listOf("Bars", "2", "40").all { it in shown }, "$shown")
            assertTrue(listOf("Loose pieces", "3", "Everything", "15").all { it in shown }, "$shown")
        }
    }

    @Test
    fun a_value_the_report_leaves_null_shows_the_bound_reason_and_never_a_zero() {
        driveScreen(stackedBoardSpec(), stackedBoardStore("plan"), BOARD_SCREEN_WIDTH, BOARD_SCREEN_HEIGHT).use { screen ->
            val shown = screen.textsUnder("stacked-board")
            assertEquals(2, shown.count { it == UNKNOWN_REASON }, "$shown")
            assertTrue("0" !in shown, "$shown")
        }
    }

    @Test
    fun a_null_value_with_no_reason_bound_shows_no_reason_and_still_no_zero() {
        driveScreen(stackedBoardSpec(), stackedBoardStore("plan", board = mapOf("unknown_reason" to null)), BOARD_SCREEN_WIDTH, BOARD_SCREEN_HEIGHT).use { screen ->
            val shown = screen.textsUnder("stacked-board")
            assertEquals(0, shown.count { it == UNKNOWN_REASON }, "$shown")
            assertTrue("0" !in shown, "$shown")
        }
    }

    @Test
    fun the_header_cells_and_the_row_cells_start_at_the_same_x_column_by_column() {
        driveScreen(stackedBoardSpec(), stackedBoardStore("plan"), BOARD_SCREEN_WIDTH, BOARD_SCREEN_HEIGHT).use { screen ->
            COLUMNS.forEach { column ->
                val head = assertNotNull(screen.boundsOf("groups-head-$column"), column)
                val cell = assertNotNull(screen.boundsOf("groups-row-$column"), column)
                assertEquals(head.left.value, cell.left.value, 0.5f, column)
            }
        }
    }

    @Test
    fun the_level_select_focuses_one_level() {
        driveScreen(stackedBoardSpec(), stackedBoardStore("iso"), BOARD_SCREEN_WIDTH, BOARD_SCREEN_HEIGHT).use { screen ->
            screen.tapText("All levels")
            screen.tapText("Bars (level 1)")
            assertEquals("1", boardIn(screen.store())["focus"])
            assertEquals("drawn=2 highlighted=0 faded=0 selected=", screen.stateDescriptionOf(partsTag("board")))
        }
    }

    @Test
    fun the_toggle_writes_show_lower_so_a_focused_level_brings_the_levels_below_back_faded() {
        driveScreen(stackedBoardSpec(), stackedBoardStore("iso"), BOARD_SCREEN_WIDTH, BOARD_SCREEN_HEIGHT).use { screen ->
            screen.tap("level-lower")
            assertEquals(true, boardIn(screen.store())["show_lower"])
            screen.tapText("All levels")
            screen.tapText("Bars (level 1)")
            assertEquals("drawn=8 highlighted=0 faded=6 selected=", screen.stateDescriptionOf(partsTag("board")))
        }
    }

    @Test
    fun the_hide_select_peels_the_top_level_through_the_bound_count_and_raises_no_notice() {
        driveScreen(stackedBoardSpec(), stackedBoardStore("iso"), BOARD_SCREEN_WIDTH, BOARD_SCREEN_HEIGHT).use { screen ->
            screen.tapText("Hide nothing")
            screen.tapText("Hide the plates")
            assertEquals("1", boardIn(screen.store())["hide_top"])
            assertEquals("drawn=8 highlighted=0 faded=0 selected=", screen.stateDescriptionOf(partsTag("board")))
            assertTrue(screen.textsUnder("board").none { it.startsWith("hide_top") }, "${screen.textsUnder("board")}")
        }
    }

    @Test
    fun the_legend_names_each_labelled_stop_of_the_spec_scale_then_the_unknown_label() {
        driveScreen(stackedBoardSpec(), stackedBoardStore("plan"), BOARD_SCREEN_WIDTH, BOARD_SCREEN_HEIGHT).use { screen ->
            assertEquals(listOf("quiet", "busy", "over full", "no count"), screen.textsUnder(legendTag("board")))
        }
    }
}
