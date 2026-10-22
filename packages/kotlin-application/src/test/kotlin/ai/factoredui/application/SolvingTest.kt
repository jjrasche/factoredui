package ai.factoredui.application

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private val SOLVE_ANSWER = """
{ "domains": ["light", "thermal"], "solvers": [
    { "function": "average-daylight-factor", "domain": "light", "status": "known", "detail": "", "values": { "daylight_factor_percent": 7.365156175244344 } },
    { "function": "glazing-balance", "domain": "thermal", "status": "unknown", "detail": "no glazing area is given", "values": { "heat_loss_watt": null, "solar_gain_watt": null } } ] }
"""

private val SYSTEM = readSystemFile("""{ "id": "shed", "title": "A shed", "systems": { "alpha": { "settings": {} } } }""")
private val NOTHING_BUILT = readExpansion("""{ "extent_mm": [1000, 1000], "objects": [], "instances": [] }""")

private fun pageWith(solving: Solving?): JsonObject = buildPageSpec(SYSTEM, NOTHING_BUILT, solving)["root"]!!.jsonObject

private fun JsonObject.nodes(): List<JsonObject> = listOf(this) + this["children"]!!.jsonArray.flatMap { it.jsonObject.nodes() }

private fun JsonObject.byId(id: String): JsonObject = nodes().first { it["id"]!!.jsonPrimitive.content == id }

private fun JsonObject.valueOf(id: String): String = byId(id)["props"]!!.jsonObject["value"]!!.jsonPrimitive.content

class SolvingTest {
    @Test
    fun the_answer_of_solve_is_read_as_one_entry_per_solver_with_its_status_detail_and_values() {
        val solving = readSolving(SOLVE_ANSWER)
        assertEquals(listOf("average-daylight-factor", "glazing-balance"), solving.answers.map { it.function })
        assertEquals(listOf("known", "unknown"), solving.answers.map { it.status })
        assertEquals("no glazing area is given", solving.answers[1].detail)
        assertEquals(7.365156175244344, solving.answers[0].values["daylight_factor_percent"])
        assertEquals(null, solving.answers[1].values.getValue("heat_loss_watt"))
    }

    @Test
    fun an_answer_that_is_not_the_shape_of_solve_is_an_error() {
        assertFailsWith<SolvingFormatException> { readSolving("""{ "refused": { "rule": "r", "message": "no" } }""") }
        assertFailsWith<SolvingFormatException> { readSolving("not json") }
    }

    @Test
    fun a_known_solver_is_a_card_with_its_domain_status_and_every_number_to_three_places_with_its_unit() {
        val page = pageWith(readSolving(SOLVE_ANSWER))
        assertEquals("Average daylight factor", page.valueOf("solver.light.average-daylight-factor.title"))
        assertEquals("Light", page.valueOf("solver.light.average-daylight-factor.domain"))
        assertEquals("known", page.valueOf("solver.light.average-daylight-factor.status"))
        assertEquals("Daylight factor", page.valueOf("solver.light.average-daylight-factor.daylight_factor_percent.name"))
        assertEquals("7.365 %", page.valueOf("solver.light.average-daylight-factor.daylight_factor_percent.value"))
    }

    @Test
    fun a_solvers_figures_stay_hidden_until_its_switch_is_turned_on() {
        val page = pageWith(readSolving(SOLVE_ANSWER))
        val figures = page.byId("solver.light.average-daylight-factor.figures")
        assertEquals("{ui.solver.light.average_daylight_factor}", figures["visible"]!!.jsonPrimitive.content)
        assertEquals("{ui.solver.light.average_daylight_factor}", page.byId("solver.light.average-daylight-factor.figures.switch")["props"]!!.jsonObject["value"]!!.jsonPrimitive.content)
    }

    @Test
    fun a_detail_that_names_a_snake_case_input_reads_it_as_words() {
        val page = pageWith(readSolving("""{ "solvers": [ { "function": "cargo-fit", "domain": "vehicles", "status": "unknown", "detail": "not counted: sheet_load, board_load", "values": {} } ] }"""))
        assertEquals("not counted: sheet load, board load", page.valueOf("solver.vehicles.cargo-fit.detail"))
    }

    @Test
    fun an_unknown_solver_shows_its_detail_and_a_dash_for_each_number() {
        val page = pageWith(readSolving(SOLVE_ANSWER))
        assertEquals("no glazing area is given", page.valueOf("solver.thermal.glazing-balance.detail"))
        val heatLoss = page.byId("solver.thermal.glazing-balance.heat_loss_watt.value")["props"]!!.jsonObject
        assertEquals("–", heatLoss["missing"]!!.jsonPrimitive.content)
    }

    @Test
    fun a_solve_that_failed_is_said_on_the_page_and_a_page_without_solving_has_no_solver_section() {
        val failed = pageWith(Solving(emptyList(), failure = "the runtime did not answer"))
        assertTrue(failed.valueOf("solvers.failure").contains("the runtime did not answer"))
        assertTrue(pageWith(null).nodes().none { it["id"]!!.jsonPrimitive.content.startsWith("solver") })
    }

    @Test
    fun a_whole_number_keeps_no_decimals_and_the_word_tile_stays_out() {
        val whole = readSolving("""{ "solvers": [ { "function": "f", "domain": "d", "status": "known", "detail": "", "values": { "n": 12.0 } } ] }""")
        assertEquals("12", pageWith(whole).valueOf("solver.d.f.n.value"))
        assertTrue(!pageWith(readSolving(SOLVE_ANSWER)).toString().contains("tile", ignoreCase = true))
    }
}
