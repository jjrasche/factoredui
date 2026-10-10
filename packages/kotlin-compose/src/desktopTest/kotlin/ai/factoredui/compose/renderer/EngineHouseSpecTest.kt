package ai.factoredui.compose.renderer

import ai.factoredui.compose.crawl.driveScreen
import ai.factoredui.compose.scene.Report
import ai.factoredui.compose.scene.readReports
import ai.factoredui.compose.scene.reportsState
import ai.factoredui.compose.schema.Spec
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private val json = Json { ignoreUnknownKeys = true }

internal const val HOUSE_SCREEN_WIDTH = 900
internal const val HOUSE_SCREEN_HEIGHT = 1000

internal fun engineHouseSpec(): Spec = json.decodeFromString(
    Spec.serializer(),
    checkNotNull(EngineHouseSpecTest::class.java.getResource("/specs/engine-house.spec.json")) { "engine-house.spec.json is not on the test classpath" }.readText(),
)

internal fun engineHouseReports(): List<Report> = readReports(
    checkNotNull(EngineHouseSpecTest::class.java.getResource("/house-floor.reports.json")) { "house-floor.reports.json is not on the test classpath" }.readText(),
)

internal fun bomRowIds(rowId: String): List<String> = engineHouseReports().first { it.id == "bom" }.rows.first { it.id == rowId }.objectIds

internal fun engineHouseStore(view: String, picked: List<String> = emptyList(), house: Map<String, Any?> = emptyMap()): Map<String, Any?> = mapOf(
    "house" to mapOf("scene" to engineHouseText(), "view" to view, "focus" to "all", "hide_top" to "0", "show_lower" to false) + house,
    "reports" to reportsState(engineHouseReports()),
    "bom" to mapOf("picked" to picked),
)

private fun pickedIn(store: Map<String, Any?>): Any? = (store["bom"] as? Map<*, *>)?.get("picked")

private fun houseIn(store: Map<String, Any?>): Map<*, *> = store["house"] as Map<*, *>

private val COLUMNS = listOf("part", "quantity", "each", "total", "weight")

class EngineHouseSpecTest {

    @Test
    fun tapping_a_bom_row_writes_that_rows_object_ids_to_bom_picked_and_tapping_another_moves_the_highlight() {
        driveScreen(engineHouseSpec(), engineHouseStore("plan"), HOUSE_SCREEN_WIDTH, HOUSE_SCREEN_HEIGHT).use { screen ->
            assertEquals("drawn=31 highlighted=0 faded=0 selected=", screen.stateDescriptionOf(partsTag("house")))

            screen.tapText("Cast pier")
            assertEquals(bomRowIds("pier"), pickedIn(screen.store()))
            assertEquals("drawn=31 highlighted=12 faded=19 selected=", screen.stateDescriptionOf(partsTag("house")))

            screen.tapText("Edge LVL beam, 20 ft")
            assertEquals(listOf("e13", "e16"), pickedIn(screen.store()))
            assertEquals("drawn=31 highlighted=2 faded=29 selected=", screen.stateDescriptionOf(partsTag("house")))
        }
    }

    @Test
    fun tapping_the_total_row_picks_every_part_and_tapping_hardware_picks_none() {
        driveScreen(engineHouseSpec(), engineHouseStore("plan"), HOUSE_SCREEN_WIDTH, HOUSE_SCREEN_HEIGHT).use { screen ->
            screen.tapText("Total")
            assertEquals("drawn=31 highlighted=31 faded=0 selected=", screen.stateDescriptionOf(partsTag("house")))
            screen.tapText("Bracing, bolts and ground cover")
            assertEquals(emptyList<String>(), pickedIn(screen.store()))
            assertEquals("drawn=31 highlighted=0 faded=0 selected=", screen.stateDescriptionOf(partsTag("house")))
        }
    }

    @Test
    fun each_bom_row_prints_its_label_quantity_unit_cost_and_line_total_from_the_engine_report() {
        driveScreen(engineHouseSpec(), engineHouseStore("plan"), HOUSE_SCREEN_WIDTH, HOUSE_SCREEN_HEIGHT).use { screen ->
            val shown = screen.textsUnder("engine-house")
            assertTrue(listOf("Cast pier", "12", "$92.37", "$1,108.44").all { it in shown }, "$shown")
            assertTrue(listOf("Interior 2-ply LVL beam, 20 ft", "2", "$248", "$496").all { it in shown }, "$shown")
            assertTrue(listOf("Site-built SIP floor panel", "15", "$251.87", "$3,778.05").all { it in shown }, "$shown")
        }
    }

    @Test
    fun hardware_and_the_total_are_ordinary_report_rows_and_no_host_total_is_drawn() {
        driveScreen(engineHouseSpec(), engineHouseStore("plan"), HOUSE_SCREEN_WIDTH, HOUSE_SCREEN_HEIGHT).use { screen ->
            val shown = screen.textsUnder("engine-house")
            assertTrue(listOf("6x6x12 post stick", "$60.48", "Bracing, bolts and ground cover", "$800", "Total", "31", "$6,490.97").all { it in shown }, "$shown")
            assertFalse(shown.any { it.startsWith("Parts total") || it == "Engine total" || it == "$5,630.49" }, "$shown")
        }
    }

    @Test
    fun every_row_says_its_weight_is_not_known_and_the_totals_unknown_unit_cost_draws_the_dash() {
        driveScreen(engineHouseSpec(), engineHouseStore("plan"), HOUSE_SCREEN_WIDTH, HOUSE_SCREEN_HEIGHT).use { screen ->
            val shown = screen.textsUnder("engine-house")
            assertEquals(7, shown.count { it == "weight not known" }, "$shown")
            assertEquals(1, shown.count { it == "–" }, "$shown")
        }
    }

    @Test
    fun the_header_cells_and_the_row_cells_start_at_the_same_x_column_by_column() {
        driveScreen(engineHouseSpec(), engineHouseStore("plan"), HOUSE_SCREEN_WIDTH, HOUSE_SCREEN_HEIGHT).use { screen ->
            COLUMNS.forEach { column ->
                val head = assertNotNull(screen.boundsOf("bom-head-$column"), column)
                val cell = assertNotNull(screen.boundsOf("bom-row-$column"), column)
                assertEquals(head.left.value, cell.left.value, 0.5f, column)
            }
        }
    }

    @Test
    fun the_level_selects_focus_the_beam_level_and_hide_the_panels_through_the_house_bindings() {
        driveScreen(engineHouseSpec(), engineHouseStore("iso"), HOUSE_SCREEN_WIDTH, HOUSE_SCREEN_HEIGHT).use { screen ->
            screen.tapText("All levels")
            screen.tapText("Beams (level 1)")
            assertEquals("1", houseIn(screen.store())["focus"])
            assertEquals("drawn=4 highlighted=0 faded=0 selected=", screen.stateDescriptionOf(partsTag("house")))

            screen.tapText("Show levels below")
            assertEquals("drawn=16 highlighted=0 faded=12 selected=", screen.stateDescriptionOf(partsTag("house")))
        }
        driveScreen(engineHouseSpec(), engineHouseStore("iso"), HOUSE_SCREEN_WIDTH, HOUSE_SCREEN_HEIGHT).use { screen ->
            screen.tapText("Hide nothing")
            screen.tapText("Hide the panels")
            assertEquals("1", houseIn(screen.store())["hide_top"])
            assertEquals("drawn=16 highlighted=0 faded=0 selected=", screen.stateDescriptionOf(partsTag("house")))
            assertTrue(screen.textsUnder("house").none { it.startsWith("hide_top") }, "${screen.textsUnder("house")}")
        }
    }
}
