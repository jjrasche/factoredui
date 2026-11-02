package ai.factoredui.application

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private const val TRACE = """{
  "clock": { "step": 1.0, "unit": "minute", "until": 900 },
  "steps": [
    { "t": 0, "stocks": { "air_temperature_f": 60.0, "glass_temperature_f": 0.0, "water_g": 0.0, "battery_kwh": 0.0, "relative_humidity": 0.1 }, "stocks_incomplete": { "battery_kwh": ["no banked usable energy"] } },
    { "t": 10, "stocks": { "air_temperature_f": 40.0, "glass_temperature_f": -3.0, "water_g": 5.0, "battery_kwh": -0.1, "relative_humidity": 0.5 }, "stocks_incomplete": { "battery_kwh": ["no banked usable energy"] } },
    { "t": 20, "stocks": { "air_temperature_f": 65.0, "glass_temperature_f": 15.0, "water_g": 9.0, "battery_kwh": -0.2, "relative_humidity": 0.2 }, "stocks_incomplete": { "battery_kwh": ["no banked usable energy"] } } ],
  "stock_extremes": { "air_temperature_f": { "low": 40.0, "high": 65.0 }, "glass_temperature_f": { "low": -3.0, "high": 15.0 }, "water_g": { "low": 0.0, "high": 9.0 }, "relative_humidity": { "low": 0.1, "high": 0.5 } }
}"""

private fun JsonObject.nodes(): List<JsonObject> = listOf(this) + this["children"]!!.jsonArray.flatMap { it.jsonObject.nodes() }

private fun JsonObject.shapes() = nodes().filter { it["type"]!!.jsonPrimitive.content == "plan" }.flatMap { it["props"]!!.jsonObject["shapes"]!!.jsonArray.map { shape -> shape.jsonObject } }

private fun JsonObject.texts() = nodes().filter { it["type"]!!.jsonPrimitive.content == "text" }.map { it["props"]!!.jsonObject["value"]!!.jsonPrimitive.content }

class TraceTest {
    private val trace = readTrace(TRACE)

    @Test
    fun stocks_that_share_a_unit_word_are_drawn_in_one_panel_and_the_others_each_in_their_own() {
        assertEquals(listOf("f", "water g", "relative humidity"), trace.panels.map { it.title })
        assertEquals(listOf("air_temperature_f", "glass_temperature_f"), trace.panels.first().series.map { it.name })
    }

    @Test
    fun a_series_is_the_stock_at_each_step_in_order() {
        assertEquals(listOf(0.0 to 60.0, 10.0 to 40.0, 20.0 to 65.0), trace.panels.first().series.first().points)
    }

    @Test
    fun a_stock_the_engine_calls_incomplete_is_not_drawn_and_its_reason_is_kept() {
        assertTrue(trace.panels.flatMap { it.series }.none { it.name == "battery_kwh" })
        assertEquals(mapOf("battery_kwh" to "no banked usable energy"), trace.notDrawn)
    }

    @Test
    fun a_stock_the_engine_gave_no_extremes_for_is_not_drawn() {
        val withoutExtremes = readTrace(TRACE.replace(""", "relative_humidity": { "low": 0.1, "high": 0.5 }""", ""))
        assertTrue(withoutExtremes.panels.flatMap { it.series }.none { it.name == "relative_humidity" })
        assertEquals("the engine gave no extremes", withoutExtremes.notDrawn["relative_humidity"])
    }

    @Test
    fun each_series_is_a_line_between_each_pair_of_steps() {
        val lines = traceNode(Traced(trace)).shapes().filter { it["kind"]!!.jsonPrimitive.content == "line" }
        assertEquals(2 * 2 + 2 + 2, lines.size)
    }

    @Test
    fun a_stretch_where_a_stock_does_not_change_is_one_line_not_one_for_each_step() {
        val steady = readTrace(TRACE.replace(""""water_g": 5.0""", """"water_g": 0.0""").replace(""""water_g": 9.0""", """"water_g": 0.0"""))
        val lines = traceNode(Traced(steady)).shapes().filter { (it["id"]!!.jsonPrimitive.content).startsWith("water_g/") }
        assertEquals(1, lines.size)
    }

    @Test
    fun the_words_say_each_series_with_its_low_and_high() {
        val words = traceNode(Traced(trace)).texts()
        assertTrue(words.any { it.startsWith("air temperature f") && "40" in it && "65" in it }, words.toString())
        assertTrue(words.any { it.contains("battery kwh") && it.contains("no banked usable energy") }, words.toString())
    }

    @Test
    fun a_refused_simulation_is_said_not_drawn() {
        assertFailsWith<BuildFormatException> { readTrace("""{ "refused": { "message": "no clock" } }""") }
        val words = traceNode(Traced(null, "no clock")).texts()
        assertTrue(words.any { it.contains("no clock") })
    }
}
