package ai.factoredui.application

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private const val SCREEN = """{
  "title": "One day",
  "rows": "steps",
  "chart": { "x": "t", "x_label": "hour", "axes": { "right": { "unit": "F" } },
    "series": [ { "id": "outside", "path": "series.outside_f", "axis": "right" }, { "id": "air", "path": "stocks.air_f", "axis": "right" } ] },
  "scrub": { "length": 23, "from": 48, "step": 24 }
}"""

private fun season(steps: Int) = """{ "unused": { "big": [1, 2, 3] }, "steps": [ ${(0 until steps).joinToString(",") { """{ "t": $it, "series": { "outside_f": ${30 + it}, "other": 9 }, "stocks": { "air_f": ${60 + it}, "water_f": 1 }, "noise": "x" }""" }} ] }"""

private fun JsonObject.nodes(): List<JsonObject> = listOf(this) + this["children"]!!.jsonArray.flatMap { it.jsonObject.nodes() }

private fun JsonObject.node(id: String) = nodes().first { it["id"]!!.jsonPrimitive.content == id }

private fun JsonObject.props(id: String) = node(id)["props"]!!.jsonObject

private val spec = chartScreenSpec(readChartScreen(SCREEN), kotlinx.serialization.json.Json.parseToJsonElement(season(100)).jsonObject)

private val root = spec["root"]!!.jsonObject

class ChartScreenTest {
    @Test
    fun the_chart_carries_the_screens_series_and_axes_unchanged() {
        val props = root.props("chart")
        assertEquals("hour", props["x_label"]!!.jsonPrimitive.content)
        assertEquals(listOf("outside", "air"), props["series"]!!.jsonArray.map { it.jsonObject["id"]!!.jsonPrimitive.content })
        assertEquals("F", props["axes"]!!.jsonObject["right"]!!.jsonObject["unit"]!!.jsonPrimitive.content)
    }

    @Test
    fun the_chart_rows_are_the_season_rows_cut_to_the_x_and_the_series_paths() {
        val rows = root.props("chart")["rows"]!!.jsonArray
        assertEquals(100, rows.size)
        assertEquals("""{"t":5,"series":{"outside_f":35},"stocks":{"air_f":65}}""", rows[5].toString())
    }

    @Test
    fun the_window_starts_where_the_day_binding_says_and_is_as_long_as_the_screen_says() {
        val window = root.props("chart")["window"]!!.jsonObject
        assertEquals("{day.from}", window["from"]!!.jsonPrimitive.content)
        assertEquals(23.0, window["length"]!!.jsonPrimitive.double)
    }

    @Test
    fun the_day_starts_where_the_screen_says() {
        assertEquals(48.0, spec["state"]!!.jsonObject["day.from"]!!.jsonPrimitive.double)
    }

    @Test
    fun the_day_starts_at_the_first_row_when_the_screen_names_no_start() {
        val bare = chartScreenSpec(readChartScreen(SCREEN.replace(""""from": 48, """, "")), kotlinx.serialization.json.Json.parseToJsonElement(season(100)).jsonObject)
        assertEquals(0.0, bare["state"]!!.jsonObject["day.from"]!!.jsonPrimitive.double)
    }

    @Test
    fun the_slider_runs_from_the_first_row_to_the_last_row_a_whole_day_still_fits() {
        val slider = root.props("scrub.slider")
        assertEquals(listOf(0.0, 76.0, 1.0), listOf("min", "max", "step").map { slider[it]!!.jsonPrimitive.double })
        assertEquals("{day.from}", slider["value"]!!.jsonPrimitive.content)
    }

    @Test
    fun the_day_buttons_move_the_start_by_the_step_and_keep_it_in_the_slider_range() {
        fun params(id: String) = root.node(id)["action"]!!.jsonObject["params"]!!.jsonObject
        assertEquals(listOf("day.from", -24.0, 0.0, 76.0), params("scrub.previous").let { listOf(it["path"]!!.jsonPrimitive.content, it["add"]!!.jsonPrimitive.double, it["min"]!!.jsonPrimitive.double, it["max"]!!.jsonPrimitive.double) })
        assertEquals(24.0, params("scrub.next")["add"]!!.jsonPrimitive.double)
    }

    @Test
    fun the_greenhouse_day_screen_reads_with_two_stacks_and_three_lines() {
        val screen = readChartScreen(checkNotNull(ChartScreenTest::class.java.getResource("/greenhouse-day.screen.json")).readText())
        val series = screen.chart["series"]!!.jsonArray.map { it.jsonObject }
        assertEquals(mapOf("gains" to 5, "losses" to 6), series.mapNotNull { it["stack"]?.jsonPrimitive?.content }.groupingBy { it }.eachCount())
        assertEquals(3, series.count { it["kind"]!!.jsonPrimitive.content == "line" })
        assertEquals(listOf(2757.0, 23.0, 24.0), listOf(screen.dayFrom!!, screen.dayLength, screen.dayStep))
    }

    @Test
    fun a_season_with_no_rows_says_so_instead_of_drawing_a_chart() {
        val empty = chartScreenSpec(readChartScreen(SCREEN), kotlinx.serialization.json.Json.parseToJsonElement("""{"steps":[]}""").jsonObject)
        val words = empty["root"]!!.jsonObject.nodes().filter { it["type"]!!.jsonPrimitive.content == "text" }.map { it["props"]!!.jsonObject["value"]!!.jsonPrimitive.content }
        assertTrue(words.any { it.contains("no rows") }, words.toString())
        assertTrue(empty["root"]!!.jsonObject.nodes().none { it["type"]!!.jsonPrimitive.content == "chart" })
    }

    @Test
    fun a_screen_missing_its_rows_key_or_its_series_is_refused() {
        assertFailsWith<ChartScreenFormatException> { readChartScreen("""{"chart":{"x":"t","series":[]}}""") }
        assertFailsWith<ChartScreenFormatException> { readChartScreen("""{"rows":"steps","chart":{"x":"t"}}""") }
        assertFailsWith<ChartScreenFormatException> { readChartScreen("not json") }
    }

    @Test
    fun a_season_whose_rows_key_is_not_a_list_is_refused() {
        assertFailsWith<ChartScreenFormatException> { chartScreenSpec(readChartScreen(SCREEN), JsonObject(mapOf("steps" to JsonPrimitive("x")))) }
        assertFailsWith<ChartScreenFormatException> { chartScreenSpec(readChartScreen(SCREEN), JsonObject(emptyMap())) }
    }
}
