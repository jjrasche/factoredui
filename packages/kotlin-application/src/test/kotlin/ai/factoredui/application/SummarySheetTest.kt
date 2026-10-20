package ai.factoredui.application

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val LOADED = readLoaded(checkNotNull(SummarySheetTest::class.java.getResource("/live-load-summary.json")).readText())

private val GEOMETRY = readGeometry(LOADED.textOf("geometry"))

private val GROUPS = summaryGroups(LOADED, GEOMETRY)

private val META = SheetMeta("A house", "2026-10-20", "abc1234", "we deadbee", 7)

private fun lines(heading: String) = GROUPS.first { it.heading.startsWith(heading) }.lines

class SummarySheetTest {
    @Test
    fun every_system_is_listed_with_its_chosen_solution_and_each_setting_with_value_unit_and_reason() {
        val systems = lines("SYSTEMS")
        assertTrue(systems.any { it.startsWith("glazing: ") && it.contains("(solution two-stacked-4x8)") })
        assertTrue(systems.any { it.startsWith("  floor.thickness_mm = 114 mm; reason: assumed") })
        assertTrue(systems.any { it.contains("no reason given") || it.contains("not given") })
    }

    @Test
    fun every_solver_is_listed_with_its_status_and_the_sources_it_rests_on() {
        val solvers = lines("SOLVERS")
        assertTrue(solvers.any { it == "average-daylight-factor (light): known" })
        assertTrue(solvers.any { it == "gravity-load-path (structural): unknown" })
        assertTrue(solvers.any { it.startsWith("  sources: ") && it.contains("(published)") })
    }

    @Test
    fun every_rule_has_its_status() {
        val rules = lines("RULES")
        assertEquals(8, rules.size)
        assertTrue(rules.any { it.startsWith("posts-under-floor [foundation]: PASS:") })
    }

    @Test
    fun the_gravity_load_path_is_unknown_for_each_post_with_the_reason() {
        val gravity = lines("GRAVITY")
        assertEquals(4, gravity.count { it.startsWith("  post@") })
        assertTrue(gravity.filter { it.startsWith("  post@") }.all { it.contains("ground reaction unknown (not measured (size-unknown)") })
    }

    @Test
    fun each_header_has_its_utilisation_and_the_largest_is_stated() {
        val headers = lines("HEADER")
        assertEquals(3, headers.count { it.contains("utilisation 0") })
        assertTrue(headers.first().contains("largest 0.61"))
    }

    @Test
    fun the_build_simulation_totals_and_flows_are_given() {
        val simulation = lines("BUILD SIMULATION")
        assertTrue(simulation.first().startsWith("ended quiescent; clock step 1 minute until 1440"))
        assertTrue(simulation.any { it.startsWith("  labour_hours = 14") })
        assertTrue(simulation.any { it.startsWith("  set-wall-panels: ran 12 times") })
    }

    @Test
    fun the_assumptions_and_every_unmeasured_piece_are_listed_in_full() {
        assertEquals(readDescribed(LOADED.textOf("describe")).sumOf { system -> system.settings.count { it.reason?.contains("assum", ignoreCase = true) == true } }, lines("ASSUMPTIONS").size)
        assertEquals(GEOMETRY.unmeasured.size, lines("UNMEASURED").size)
    }

    @Test
    fun the_summary_fills_one_or_two_sheets_each_with_a_title_block_and_nothing_off_the_page() {
        val sheets = textSheets("Design summary", { page, pages -> "S-1 (page $page of $pages)" }, GROUPS, META)
        assertTrue(sheets.size in 1..2, "sheets ${sheets.size}")
        sheets.forEach { sheet ->
            val bounds = boundsOfAll(sheet.marks)!!
            assertTrue(bounds.left >= 0 && bounds.top >= 0 && bounds.right <= SHEET_WIDTH_MM && bounds.bottom <= SHEET_HEIGHT_MM)
            assertTrue(sheetSvg(sheet).contains(">Design summary<"))
        }
        assertTrue(sheetSvg(sheets.first()).contains(">S-1 (page 1 of ${sheets.size})<"))
    }
}
