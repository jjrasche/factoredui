package ai.factoredui.application

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private const val VIEW = """{
  "title": "Earth battery",
  "world": "earth-battery",
  "grid": "grids.soil_temperature_c",
  "inlet": "series.pipe_inlet_air_c",
  "outlet": "series.pipe_outlet_air_c",
  "layouts": ["serpentine", "parallel-runs"],
  "labels": { "parallel-runs": "Parallel" },
  "axis_labels": { "y": "Across the house", "z": "Depth" },
  "ramp": "heat"
}"""

private fun answer(base: Double, fixture: Boolean = false): String = """{
  ${if (fixture) """"fixture": "made-up numbers, not a simulation",""" else ""}
  "grids": { "soil_temperature_c": { "plane": "across", "at_x_mm": 10000,
    "axes": [ { "name": "y", "unit": "mm", "origin_mm": 0, "step_mm": 609.6, "count": 3 }, { "name": "z", "unit": "mm", "origin_mm": 0, "step_mm": -304.8, "count": 2 } ],
    "times_h": [0, 1],
    "values": [ $base, ${base + 1}, ${base + 2}, ${base + 3}, ${base + 4}, ${base + 5},  ${base + 6}, ${base + 7}, ${base + 8}, ${base + 9}, ${base + 10}, ${base + 11} ] } },
  "series": {
    "pipe_inlet_air_c": { "p1": { "unit": "C", "t_h": [0, 1], "v": [30, 31] } },
    "pipe_outlet_air_c": { "p1": { "unit": "C", "t_h": [0, 1], "v": [$base, ${base + 1}] } }
  }
}"""

private fun answers(fixture: Boolean = false): Map<String, JsonObject> =
    mapOf("serpentine" to Json.parseToJsonElement(answer(10.0, fixture)).jsonObject, "parallel-runs" to Json.parseToJsonElement(answer(20.0, fixture)).jsonObject)

private fun JsonObject.nodes(): List<JsonObject> = listOf(this) + this["children"]!!.jsonArray.flatMap { it.jsonObject.nodes() }

private fun JsonObject.node(id: String) = nodes().first { it["id"]!!.jsonPrimitive.content == id }

private fun JsonObject.props(id: String) = node(id)["props"]!!.jsonObject

private fun specOf(fixture: Boolean = false) = fieldScreenSpec(readFieldScreen(VIEW), answers(fixture))

private fun rootOf(fixture: Boolean = false) = specOf(fixture)["root"]!!.jsonObject

class FieldScreenTest {
    @Test
    fun one_tab_per_layout_the_view_names_by_its_label_when_it_gives_one() {
        assertEquals(listOf("serpentine", "Parallel"), rootOf().props("layouts")["items"]!!.jsonArray.map { it.jsonPrimitive.content })
    }

    @Test
    fun each_layouts_heatmap_cuts_the_flat_values_into_one_frame_per_hour() {
        val frames = rootOf().props("serpentine.heatmap")["frames"]!!.jsonArray
        assertEquals(2, frames.size)
        assertEquals(listOf(10.0, 11.0, 12.0, 13.0, 14.0, 15.0), frames[0].jsonArray.map { it.jsonPrimitive.double })
        assertEquals(listOf(16.0, 17.0, 18.0, 19.0, 20.0, 21.0), frames[1].jsonArray.map { it.jsonPrimitive.double })
    }

    @Test
    fun the_heatmap_axes_are_the_grids_axes_a_downward_axis_drawn_as_its_depth() {
        val axes = rootOf().props("parallel-runs.heatmap")
        assertEquals(2.0, axes["x"]!!.jsonObject["step"]!!.jsonPrimitive.double, 1e-9)
        assertEquals(3.0, axes["x"]!!.jsonObject["count"]!!.jsonPrimitive.double)
        assertEquals(1.0, axes["y"]!!.jsonObject["step"]!!.jsonPrimitive.double, 1e-9)
        assertEquals("ft", axes["y"]!!.jsonObject["unit"]!!.jsonPrimitive.content)
        assertEquals("Depth (ft)", axes["y"]!!.jsonObject["label"]!!.jsonPrimitive.content)
        assertEquals("Across the house (ft)", axes["x"]!!.jsonObject["label"]!!.jsonPrimitive.content)
    }

    @Test
    fun graded_cells_pass_their_edges_to_the_heatmap_and_count_one_fewer() {
        val graded = answer(10.0).replace(""""origin_mm": 0, "step_mm": 609.6, "count": 3""", """"edges_mm": [0, 304.8, 914.4, 2438.4]""")
        val answers = mapOf("serpentine" to Json.parseToJsonElement(graded).jsonObject, "parallel-runs" to Json.parseToJsonElement(graded).jsonObject)
        val axes = fieldScreenSpec(readFieldScreen(VIEW), answers)["root"]!!.jsonObject.props("serpentine.heatmap")["x"]!!.jsonObject
        assertEquals(listOf(0.0, 1.0, 3.0, 8.0), axes["edges"]!!.jsonArray.map { Math.round(it.jsonPrimitive.double * 1e9) / 1e9 })
        assertEquals(3.0, axes["count"]!!.jsonPrimitive.double)
        assertTrue("step" !in axes)
    }

    @Test
    fun every_layouts_colours_share_one_range_so_layouts_compare() {
        val low = rootOf().props("serpentine.heatmap")["min"]!!.jsonPrimitive.double
        val high = rootOf().props("serpentine.heatmap")["max"]!!.jsonPrimitive.double
        assertEquals(10.0, low)
        assertEquals(31.0, high)
        assertEquals(low, rootOf().props("parallel-runs.heatmap")["min"]!!.jsonPrimitive.double)
        assertEquals(high, rootOf().props("parallel-runs.heatmap")["max"]!!.jsonPrimitive.double)
    }

    @Test
    fun the_heatmap_frame_and_the_slider_share_one_binding() {
        val root = rootOf()
        assertEquals("{frame}", root.props("serpentine.heatmap")["frame"]!!.jsonPrimitive.content)
        assertEquals("{frame}", root.props("frame.slider")["value"]!!.jsonPrimitive.content)
        assertEquals(1.0, root.props("frame.slider")["max"]!!.jsonPrimitive.double)
    }

    @Test
    fun the_chart_draws_each_pipes_inlet_and_outlet_over_the_hours() {
        val chart = rootOf().props("serpentine.chart")
        assertEquals(listOf("p1 inlet", "p1 outlet"), chart["series"]!!.jsonArray.map { it.jsonObject["id"]!!.jsonPrimitive.content })
        val hourOne = chart["rows"]!!.jsonArray[1].jsonObject
        assertEquals(1.0, hourOne["t"]!!.jsonPrimitive.double)
        assertEquals(31.0, hourOne["inlet"]!!.jsonObject["p1"]!!.jsonPrimitive.double)
        assertEquals(11.0, hourOne["outlet"]!!.jsonObject["p1"]!!.jsonPrimitive.double)
    }

    @Test
    fun the_frame_slider_has_its_own_row_above_the_layout_tabs() {
        val page = rootOf().nodes().first { it["id"]!!.jsonPrimitive.content == "page" }
        val ids = page["children"]!!.jsonArray.map { it.jsonObject["id"]!!.jsonPrimitive.content }
        assertTrue(ids.indexOf("frame.slider") in 0 until ids.indexOf("layouts"))
    }

    @Test
    fun a_fixture_answer_says_so_on_screen() {
        assertEquals("FIXTURE: made-up numbers, not a simulation", rootOf(fixture = true).props("fixture.notice")["value"]!!.jsonPrimitive.content)
        assertTrue(rootOf(fixture = false).nodes().none { it["id"]!!.jsonPrimitive.content == "fixture.notice" })
    }

    @Test
    fun an_underresolved_layout_says_so_with_both_lengths_and_a_resolved_one_says_nothing() {
        val resolution = """"resolution": { "near_pipe_cell_m": 0.5, "daily_penetration_m": 0.12, "underresolved": %s, "reason": "r" },"""
        val flagged = Json.parseToJsonElement(answer(10.0).replaceFirst("{", "{" + resolution.format("true"))).jsonObject
        val clear = Json.parseToJsonElement(answer(20.0).replaceFirst("{", "{" + resolution.format("false"))).jsonObject
        val root = fieldScreenSpec(readFieldScreen(VIEW), mapOf("serpentine" to flagged, "parallel-runs" to clear))["root"]!!.jsonObject
        assertEquals("near-pipe soil coarse: daily storage underresolved (near-pipe cell 0.5 m, daily penetration 0.12 m)", root.props("serpentine.resolution")["value"]!!.jsonPrimitive.content)
        assertTrue(root.nodes().none { it["id"]!!.jsonPrimitive.content == "parallel-runs.resolution" })
    }

    @Test
    fun a_layout_with_no_answer_is_refused_by_name() {
        val missing = assertFailsWith<FieldFormatException> { fieldScreenSpec(readFieldScreen(VIEW), answers().minus("parallel-runs")) }
        assertTrue("parallel-runs" in missing.message!!)
    }

    @Test
    fun a_view_with_no_layouts_is_refused() {
        assertFailsWith<FieldFormatException> { readFieldScreen(VIEW.replace(""""layouts": ["serpentine", "parallel-runs"],""", """"layouts": [],""")) }
    }

    @Test
    fun feet_are_rounded_to_hundredths_so_an_axis_end_reads_cleanly() {
        val long = answer(10.0).replace(""""origin_mm": 0, "step_mm": 609.6, "count": 3""", """"edges_mm": [0, 1000, 20500, 20501]""")
        val answers = mapOf("serpentine" to Json.parseToJsonElement(long).jsonObject, "parallel-runs" to Json.parseToJsonElement(long).jsonObject)
        val x = fieldScreenSpec(readFieldScreen(VIEW), answers)["root"]!!.jsonObject.props("serpentine.heatmap")["x"]!!.jsonObject
        assertEquals(listOf(0.0, 3.28, 67.26, 67.26), x["edges"]!!.jsonArray.map { it.jsonPrimitive.double })
    }

    @Test
    fun a_single_tube_series_without_pipe_ids_is_drawn_as_the_tubes_inlet_and_outlet() {
        val single = answer(10.0).replace(""""pipe_inlet_air_c": { "p1": { "unit": "C", "t_h": [0, 1], "v": [30, 31] } }""", """"pipe_inlet_air_c": { "unit": "C", "t_h": [0, 1], "v": [30, 31] }""")
            .replace(""""pipe_outlet_air_c": { "p1": { "unit": "C", "t_h": [0, 1], "v": [10.0, 11.0] } }""", """"pipe_outlet_air_c": { "unit": "C", "t_h": [0, 1], "v": [10.0, 11.0] }""")
        val answers = mapOf("serpentine" to Json.parseToJsonElement(single).jsonObject, "parallel-runs" to Json.parseToJsonElement(single).jsonObject)
        val chart = fieldScreenSpec(readFieldScreen(VIEW), answers)["root"]!!.jsonObject.props("serpentine.chart")
        assertEquals(listOf("tube inlet", "tube outlet"), chart["series"]!!.jsonArray.map { it.jsonObject["id"]!!.jsonPrimitive.content })
    }

    @Test
    fun an_answers_provenance_is_named_on_the_page_and_is_not_mistaken_for_a_layout() {
        val provenance = Json.parseToJsonElement("""{ "engine": "5d5a58e", "domains": "798c048", "config_sha256": "dd281761f0711e1f6ba049cd52696f5457389ff6" }""").jsonObject
        val root = fieldScreenSpec(readFieldScreen(VIEW), answers() + ("provenance" to provenance))["root"]!!.jsonObject
        assertEquals("From the engine: engine 5d5a58e, domains 798c048, config dd281761", root.props("provenance.note")["value"]!!.jsonPrimitive.content)
    }

    @Test
    fun the_colour_range_and_the_resolution_figures_are_rounded_for_reading() {
        val long = Json.parseToJsonElement(answer(10.04).replaceFirst("{", """{ "resolution": { "near_pipe_cell_m": 0.2915376005, "daily_penetration_m": 0.11839398, "underresolved": true, "reason": "r" }, """)).jsonObject
        val root = fieldScreenSpec(readFieldScreen(VIEW), mapOf("serpentine" to long, "parallel-runs" to long))["root"]!!.jsonObject
        assertEquals(10.0, root.props("serpentine.heatmap")["min"]!!.jsonPrimitive.double)
        assertEquals(21.1, root.props("serpentine.heatmap")["max"]!!.jsonPrimitive.double, 1e-9)
        assertTrue("(near-pipe cell 0.29 m, daily penetration 0.12 m)" in root.props("serpentine.resolution")["value"]!!.jsonPrimitive.content)
    }

    @Test
    fun temperatures_read_in_fahrenheit_when_the_view_asks_for_it() {
        val view = readFieldScreen(VIEW.replace("""  "ramp": "heat"""", """  "ramp": "heat", "temperature_unit": "F""""))
        val root = fieldScreenSpec(view, answers())["root"]!!.jsonObject
        assertEquals("F", root.props("serpentine.heatmap")["unit"]!!.jsonPrimitive.content)
        assertEquals(50.0, root.props("serpentine.heatmap")["min"]!!.jsonPrimitive.double)
        assertEquals("F", root.props("serpentine.chart")["axes"]!!.jsonObject["right"]!!.jsonObject["unit"]!!.jsonPrimitive.content)
        assertEquals(86.0, root.props("serpentine.chart")["rows"]!!.jsonArray[0].jsonObject["inlet"]!!.jsonObject["p1"]!!.jsonPrimitive.double, 1e-9)
    }

    @Test
    fun a_fan_that_never_ran_is_said_so_with_the_house_air_peak() {
        val stalled = Json.parseToJsonElement(answer(10.0).replaceFirst("{", """{ "house_air_c": { "unit": "C", "t_h": [0, 1], "v": [5.0, 12.2] }, "fan_on": { "unit": "1", "t_h": [0, 1], "v": [0, 0] }, """)).jsonObject
        val root = fieldScreenSpec(readFieldScreen(VIEW), mapOf("serpentine" to stalled, "parallel-runs" to stalled))["root"]!!.jsonObject
        assertEquals("Fan off all day, the house air peaked at 12.2 C, below the fan's switch-on temperature.", root.props("serpentine.fan")["value"]!!.jsonPrimitive.content)
        assertTrue(root.props("serpentine.chart")["series"]!!.jsonArray.any { it.jsonObject["id"]!!.jsonPrimitive.content == "house air" })
    }

    @Test
    fun the_page_scrolls_so_what_is_below_the_first_screen_can_be_reached() {
        assertEquals("scrollview", rootOf()["type"]!!.jsonPrimitive.content)
    }
}
