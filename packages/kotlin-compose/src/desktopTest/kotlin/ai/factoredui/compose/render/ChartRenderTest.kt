package ai.factoredui.compose.render

import ai.factoredui.compose.renderer.RenderContext
import ai.factoredui.compose.schema.Spec
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.awt.Point
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

private val json = Json { ignoreUnknownKeys = true }

private fun specOf(root: String): Spec = json.decodeFromString(Spec.serializer(), """{"spec_version":1,"renderer_min":1,"root":$root}""")

private val DAY = (0 until 24).map { hour ->
    mapOf(
        "t" to hour,
        "series" to mapOf("outside_air_f" to 30.0 + hour),
        "stocks" to mapOf("water_temperature_f" to 60.0 + hour / 2.0, "air_temperature_f" to 65.0 + (hour % 5)),
        "flows" to mapOf("gain_w" to hour * 10.0, "wall_loss_w" to -(hour * 4.0), "glass_loss_w" to -(hour * 2.0)),
    )
}

private const val TEMPERATURES = """
    {"id":"chart","type":"chart","props":{"rows":"{season.steps}","x":"t","x_label":"hour",
      "axes":{"right":{"unit":"°F"}},
      "series":[
        {"id":"outside","label":"Outside air","path":"series.outside_air_f","axis":"right"},
        {"id":"water","label":"Water","path":"stocks.water_temperature_f","axis":"right"},
        {"id":"air","label":"Air","path":"stocks.air_temperature_f","axis":"right"}]}}
"""

private const val HEAT = """
    {"id":"chart","type":"chart","props":{"rows":"{season.steps}","x":"t",
      "axes":{"left":{"unit":"W","label":"Heat flow"}},
      "series":[
        {"id":"gain","label":"Gain","path":"flows.gain_w","kind":"area"},
        {"id":"wall","label":"Wall loss","path":"flows.wall_loss_w","kind":"area"},
        {"id":"glass","label":"Glass loss","path":"flows.glass_loss_w","kind":"area"}]}}
"""

private fun chartAfter(root: String, rows: List<Map<String, Any?>> = DAY, act: (SceneSurface, JsonObject) -> Unit = { _, _ -> }): JsonObject {
    val context = RenderContext(initialData = mapOf("season" to mapOf("steps" to rows)))
    SceneSurface(specOf(root), context, 600, 500, ticking = false).use { surface ->
        val before = surface.onScreenThread { nodeById(screenJson(screenNodes(context.screen)!!), "chart")!! }
        act(surface, before)
        return surface.onScreenThread { nodeById(screenJson(screenNodes(context.screen)!!), "chart")!! }
    }
}

private fun JsonObject.seriesNamed(id: String) = getValue("chart").jsonObject.getValue("series").jsonArray.map { it.jsonObject }.first { it.getValue("id").jsonPrimitive.content == id }

private fun JsonObject.pointsOf(id: String) = seriesNamed(id).getValue("runs").jsonArray.flatMap { run -> run.jsonArray.map { point -> point.jsonArray.map { it.jsonPrimitive.double } } }

class ChartRenderTest {
    @Test
    fun the_plotted_values_read_back_are_the_rows_values_at_the_rows_x() {
        val node = chartAfter(TEMPERATURES)
        assertEquals(DAY.map { listOf(it["t"].toString().toDouble(), 30.0 + it["t"].toString().toDouble()) }, node.pointsOf("outside").map { it.take(2) })
        assertEquals(DAY.map { (it["stocks"] as Map<*, *>)["water_temperature_f"] as Double }, node.pointsOf("water").map { it[1] })
        assertEquals(24, node.pointsOf("air").size)
    }

    @Test
    fun the_right_axis_carries_its_unit_and_round_ticks_that_enclose_the_values() {
        val axes = chartAfter(TEMPERATURES).getValue("chart").jsonObject.getValue("axes").jsonArray.map { it.jsonObject }
        assertEquals(listOf("right"), axes.map { it.getValue("side").jsonPrimitive.content })
        assertEquals("°F", axes.single().getValue("unit").jsonPrimitive.content)
        val ticks = axes.single().getValue("ticks").jsonArray.map { it.jsonPrimitive.double }
        assertTrue(ticks.first() <= 30.0 && ticks.last() >= 53.0, ticks.toString())
    }

    @Test
    fun stacked_areas_read_back_with_the_band_each_fills() {
        val node = chartAfter(HEAT)
        val atHour10 = listOf("gain", "wall", "glass").map { id -> node.pointsOf(id)[10] }
        assertEquals(listOf(10.0, 100.0, 0.0, 100.0), atHour10[0])
        assertEquals(listOf(10.0, -40.0, 0.0, -40.0), atHour10[1])
        assertEquals(listOf(10.0, -20.0, -40.0, -60.0), atHour10[2])
    }

    @Test
    fun hovering_reads_each_series_at_the_nearest_row_and_leaving_clears_it() {
        val node = chartAfter(TEMPERATURES) { surface, before ->
            val bounds = before.getValue("bounds").jsonObject
            surface.hover(Point(bounds.getValue("x").jsonPrimitive.int + bounds.getValue("w").jsonPrimitive.int / 2, bounds.getValue("y").jsonPrimitive.int + bounds.getValue("h").jsonPrimitive.int / 2))
        }
        val hover = node.getValue("chart").jsonObject.getValue("hover")
        assertNotEquals(JsonNull, hover)
        val values = hover.jsonObject.getValue("values").jsonArray.map { it.jsonObject.getValue("id").jsonPrimitive.content }
        assertEquals(listOf("outside", "water", "air"), values)
        val x = hover.jsonObject.getValue("x").jsonPrimitive.double
        assertTrue(x in 0.0..23.0, x.toString())
    }

    @Test
    fun the_x_axis_ticks_read_back_with_their_clock_labels_and_replace_the_end_labels() {
        val node = chartAfter(TEMPERATURES.replace(""""x_label":"hour",""", """"x_label":"hour","x_axis":{"every":3,"as":"hour_of_day"},"""))
        val ticks = node.getValue("chart").jsonObject.getValue("x").jsonObject.getValue("ticks").jsonArray.map { it.jsonObject }
        assertEquals((0..7).map { it * 3.0 }, ticks.map { it.getValue("x").jsonPrimitive.double })
        assertEquals((0..7).map { "%02d:00".format(it * 3) }, ticks.map { it.getValue("label").jsonPrimitive.content })
    }

    @Test
    fun a_chart_with_nothing_to_draw_says_so_in_its_notices_and_draws_no_series() {
        val node = chartAfter(TEMPERATURES, rows = emptyList())
        assertEquals(JsonArray(emptyList()), node.getValue("chart").jsonObject.getValue("series"))
        assertTrue(node.getValue("notices").jsonArray.isNotEmpty())
    }
}
