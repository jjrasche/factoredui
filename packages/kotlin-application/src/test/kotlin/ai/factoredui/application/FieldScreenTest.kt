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
  "ramp": "heat"
}"""

private fun answer(base: Double, fixture: Boolean = false): String = """{
  ${if (fixture) """"fixture": "made-up numbers, not a simulation",""" else ""}
  "grids": { "soil_temperature_c": { "plane": "across", "at_x_mm": 10000,
    "axes": [ { "name": "y", "unit": "mm", "origin_mm": 0, "step_mm": 500, "count": 3 }, { "name": "z", "unit": "mm", "origin_mm": 0, "step_mm": -250, "count": 2 } ],
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
    fun one_tab_per_layout_the_view_names() {
        assertEquals(listOf("serpentine", "parallel-runs"), rootOf().props("layouts")["items"]!!.jsonArray.map { it.jsonPrimitive.content })
    }

    @Test
    fun each_layouts_heatmap_cuts_the_flat_values_into_one_frame_per_hour() {
        val frames = rootOf().props("serpentine.heatmap")["frames"]!!.jsonArray
        assertEquals(2, frames.size)
        assertEquals(listOf(10.0, 11.0, 12.0, 13.0, 14.0, 15.0), frames[0].jsonArray.map { it.jsonPrimitive.double })
        assertEquals(listOf(16.0, 17.0, 18.0, 19.0, 20.0, 21.0), frames[1].jsonArray.map { it.jsonPrimitive.double })
    }

    @Test
    fun the_heatmap_axes_are_the_grids_axes_with_their_origin_step_and_count() {
        val axes = rootOf().props("parallel-runs.heatmap")["axes"]!!.jsonObject
        assertEquals(500.0, axes["x"]!!.jsonObject["step"]!!.jsonPrimitive.double)
        assertEquals(3.0, axes["x"]!!.jsonObject["count"]!!.jsonPrimitive.double)
        assertEquals(-250.0, axes["y"]!!.jsonObject["step"]!!.jsonPrimitive.double)
        assertEquals("mm", axes["y"]!!.jsonObject["unit"]!!.jsonPrimitive.content)
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
    fun a_fixture_answer_says_so_on_screen() {
        assertEquals("FIXTURE: made-up numbers, not a simulation", rootOf(fixture = true).props("fixture.notice")["value"]!!.jsonPrimitive.content)
        assertTrue(rootOf(fixture = false).nodes().none { it["id"]!!.jsonPrimitive.content == "fixture.notice" })
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
}
