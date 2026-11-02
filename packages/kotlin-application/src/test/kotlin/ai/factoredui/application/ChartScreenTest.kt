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
        assertEquals("""{"t":5,"series":{"outside_f":35},"stocks":{"air_f":65},"at":"Step 5"}""", rows[5].toString())
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
    fun the_slider_snaps_to_whole_days_from_the_first_row_to_the_last_day_that_fits() {
        val slider = root.props("scrub.slider")
        assertEquals(listOf(0.0, 72.0, 24.0), listOf("min", "max", "step").map { slider[it]!!.jsonPrimitive.double })
        assertEquals("{day.from}", slider["value"]!!.jsonPrimitive.content)
    }

    @Test
    fun the_slider_is_the_only_scrub_control_and_a_label_says_where_the_window_starts() {
        assertTrue(root.nodes().none { it["type"]!!.jsonPrimitive.content == "button" })
        assertEquals("{day.label}", root.props("scrub.label")["value"]!!.jsonPrimitive.content)
        val chart = root.props("chart")
        assertEquals(listOf("at", "{day.label}"), listOf("row_label", "window_label").map { chart[it]!!.jsonPrimitive.content })
    }

    @Test
    fun the_page_is_dark_and_drawn_on_the_ground() {
        assertEquals("dark", spec["theme"]!!.jsonPrimitive.content)
        assertEquals("ground", root["props"]!!.jsonObject["background"]!!.jsonPrimitive.content)
    }

    private fun labelsFor(clock: String, steps: Int = 3): List<String> {
        val season = kotlinx.serialization.json.Json.parseToJsonElement("""{ "clock": $clock, "steps": [ ${(0 until steps).joinToString(",") { """{ "t": ${it * 25}, "series": { "outside_f": 1 }, "stocks": { "air_f": 2 } }""" }} ] }""").jsonObject
        val screen = chartScreenSpec(readChartScreen(SCREEN), season)["root"]!!.jsonObject
        return screen.props("chart")["rows"]!!.jsonArray.map { it.jsonObject["at"]!!.jsonPrimitive.content }
    }

    @Test
    fun with_a_start_the_rows_are_labelled_with_the_date_and_time_of_each_step() {
        assertEquals(listOf("Tue 3 Nov, 21:00", "Wed 4 Nov, 22:00", "Thu 5 Nov, 23:00"), labelsFor("""{ "step": 1.0, "unit": "hour", "start": "2020-11-03T21:00:00" }"""))
    }

    @Test
    fun a_start_with_an_offset_or_only_a_date_is_read_too() {
        assertEquals("Tue 3 Nov, 21:00", labelsFor("""{ "step": 1, "unit": "hour", "start": "2020-11-03T21:00:00+00:00" }""").first())
        assertEquals("Tue 3 Nov, 00:00", labelsFor("""{ "step": 1, "unit": "hour", "start": "2020-11-03" }""").first())
    }

    @Test
    fun with_no_start_an_hourly_season_counts_days_and_hours() {
        assertEquals(listOf("Day 1, 00:00", "Day 2, 01:00", "Day 3, 02:00"), labelsFor("""{ "step": 1, "unit": "hour" }"""))
    }

    @Test
    fun a_clock_that_says_nothing_usable_labels_the_rows_by_their_step() {
        assertEquals(listOf("Step 0", "Step 25", "Step 50"), labelsFor("""{ "step": 1, "unit": "fortnight" }"""))
    }

    @Test
    fun a_step_of_more_than_one_unit_is_counted_in_its_own_size() {
        assertEquals(listOf("Day 1, 00:00", "Day 3, 02:00"), labelsFor("""{ "step": 2, "unit": "hour" }""", steps = 2))
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
