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

private const val VIEW = """{ "title": "Heat", "world": "w", "site": "big", "ramp": "cool_warm" }"""

/** Three days of hourly steps: day 0 mild, day 1 coldest, day 2 mild; the air stock follows the outside air plus 20, the water stock stays at 60. */
private fun season(): JsonObject {
    val outside = listOf(40.0, 5.0, 30.0)
    val steps = (0 until 72).joinToString(",") { t ->
        val day = t / 24
        val hour = t % 24
        val air = outside[day] + hour
        """{ "t": $t, "stocks": { "air_temperature_f": ${air + 20}, "water_temperature_f": 60.0, "soil_temperature_f": 50.0 }, "series": { "outside_air_f": $air, "sun_horizontal_w_per_m2": 0.0 } }"""
    }
    return Json.parseToJsonElement("""{ "clock": { "step": 1, "unit": "hour", "until": 72 }, "steps": [ $steps ] }""").jsonObject
}

private fun boxes() = listOf(
    mapOf("id" to "Site/air-box", "system" to "house", "style" to "glass", "x_mm" to 0.0, "y_mm" to 0.0, "z_mm" to 0.0, "width_mm" to 10.0, "depth_mm" to 10.0, "height_mm" to 10.0, "stock" to "air_temperature_f"),
    mapOf("id" to "Site/water-box", "system" to "tank", "style" to "water", "x_mm" to 20.0, "y_mm" to 0.0, "z_mm" to 0.0, "width_mm" to 10.0, "depth_mm" to 10.0, "height_mm" to 10.0, "stock" to "water_temperature_f"),
    mapOf("id" to "plain-box", "style" to "wall", "x_mm" to 40.0, "y_mm" to 0.0, "z_mm" to 0.0, "width_mm" to 10.0, "depth_mm" to 10.0, "height_mm" to 10.0),
)

private fun JsonObject.nodes(): List<JsonObject> = listOf(this) + (this["children"]?.jsonArray ?: emptyList()).flatMap { it.jsonObject.nodes() }

private fun JsonObject.props(id: String) = nodes().first { it["id"]!!.jsonPrimitive.content == id }["props"] as JsonObject

private val screen = readHeatScreen(VIEW)

private fun rootOf() = heatSpec(screen, boxes(), season())["root"]!!.jsonObject

private fun boxProps(id: String): JsonObject = rootOf().props("heat.volume")["boxes"]!!.jsonArray.map { it.jsonObject }.first { it["id"]!!.jsonPrimitive.content == id }

class HeatScreenTest {
    @Test
    fun the_day_shown_is_the_one_with_the_lowest_mean_outside_air() {
        assertEquals(1, heatDayOf(season()))
    }

    @Test
    fun a_box_with_a_stock_carries_that_stocks_hours_of_the_coldest_day() {
        val values = boxProps("Site/air-box")["values"]!!.jsonArray.map { it.jsonPrimitive.double }
        assertEquals(24, values.size)
        assertEquals(25.0, values.first())
        assertEquals(48.0, values.last())
    }

    @Test
    fun a_box_with_no_stock_carries_no_values_and_keeps_its_style() {
        assertTrue("values" !in boxProps("plain-box"))
        assertEquals("wall", boxProps("plain-box")["style"]!!.jsonPrimitive.content)
    }

    @Test
    fun the_ramp_range_reads_to_a_tenth_over_every_value_shown() {
        val volume = rootOf().props("heat.volume")
        assertEquals(25.0, volume["min"]!!.jsonPrimitive.double)
        assertEquals(60.0, volume["max"]!!.jsonPrimitive.double)
        assertEquals("F", volume["unit"]!!.jsonPrimitive.content)
        assertEquals("{heat.hour}", volume["frame"]!!.jsonPrimitive.content)
        assertEquals("cool_warm", volume["value_ramp"]!!.jsonPrimitive.content)
    }

    @Test
    fun one_slider_moves_the_hour_through_the_day() {
        val slider = rootOf().props("heat.slider")
        assertEquals("{heat.hour}", slider["value"]!!.jsonPrimitive.content)
        assertEquals(23.0, slider["max"]!!.jsonPrimitive.double)
    }

    @Test
    fun the_chart_draws_the_outside_air_and_every_stock_that_colours_a_box() {
        val chart = rootOf().props("heat.chart")
        assertEquals(listOf("outside air", "air temperature", "water temperature"), chart["series"]!!.jsonArray.map { it.jsonObject["id"]!!.jsonPrimitive.content })
        assertEquals(24, chart["rows"]!!.jsonArray.size)
    }

    @Test
    fun the_page_names_the_day_it_shows() {
        val text = rootOf().nodes().mapNotNull { (it["props"] as? JsonObject)?.get("value")?.jsonPrimitive?.content }
        assertTrue(text.any { "Day 2 of the season, the coldest by outside air" in it })
    }

    @Test
    fun a_season_with_no_whole_day_is_refused() {
        val short = Json.parseToJsonElement("""{ "steps": [ { "t": 0, "stocks": {}, "series": { "outside_air_f": 1 } } ] }""").jsonObject
        assertFailsWith<HeatFormatException> { heatSpec(screen, boxes(), short) }
    }

    @Test
    fun the_slider_starts_at_the_coldest_hour_of_the_day() {
        val spec = heatSpec(screen, boxes(), season())
        assertEquals(0.0, spec["state"]!!.jsonObject["heat.hour"]!!.jsonPrimitive.double)
    }

    @Test
    fun the_hour_buttons_step_the_hour_within_the_day() {
        val button = rootOf().nodes().first { it["id"]!!.jsonPrimitive.content == "heat.later" }
        val params = button["action"]!!.jsonObject["params"]!!.jsonObject
        assertEquals(1.0, params["add"]!!.jsonPrimitive.double)
        assertEquals(23.0, params["max"]!!.jsonPrimitive.double)
    }

    @Test
    fun the_styles_a_view_calls_clear_are_drawn_nearly_see_through_so_the_inside_carries_the_colour() {
        val clear = readHeatScreen("""{ "title": "Heat", "world": "w", "site": "big", "clear_styles": ["glass"] }""")
        val styles = heatSpec(clear, boxes(), season())["root"]!!.jsonObject.props("heat.volume")["styles"]!!.jsonObject
        assertEquals(0.1, styles["glass"]!!.jsonObject["opacity"]!!.jsonPrimitive.double)
        assertTrue("opacity" !in styles["wall"]!!.jsonObject)
    }

    @Test
    fun a_box_a_view_names_is_drawn_nearly_see_through_whatever_its_style_so_the_pieces_inside_carry_the_colour() {
        val view = readHeatScreen("""{ "title": "Heat", "world": "w", "site": "big", "clear_boxes": ["Site/water-box"] }""")
        val volume = heatSpec(view, boxes(), season())["root"]!!.jsonObject.props("heat.volume")
        val water = volume["boxes"]!!.jsonArray.map { it.jsonObject }.first { it["id"]!!.jsonPrimitive.content == "Site/water-box" }
        assertEquals("clear: water", water["style"]!!.jsonPrimitive.content)
        assertEquals(0.1, volume["styles"]!!.jsonObject["clear: water"]!!.jsonObject["opacity"]!!.jsonPrimitive.double)
        assertTrue("opacity" !in volume["styles"]!!.jsonObject["glass"]!!.jsonObject)
        assertTrue("values" in water)
    }

    @Test
    fun an_air_zone_is_one_translucent_box_over_its_pieces_coloured_by_the_zones_air_stock() {
        val view = readHeatScreen("""{ "title": "Heat", "world": "w", "site": "big", "air_zones": [ { "label": "House air", "member": "Site", "systems": ["house", "tank"], "stock": "air_temperature_f" } ] }""")
        val volume = heatSpec(view, boxes(), season())["root"]!!.jsonObject.props("heat.volume")
        val air = volume["boxes"]!!.jsonArray.map { it.jsonObject }.first { it["id"]!!.jsonPrimitive.content == "air:House air" }
        assertEquals(30.0, air["width_mm"]!!.jsonPrimitive.double)
        assertEquals(10.0, air["height_mm"]!!.jsonPrimitive.double)
        assertEquals(10.0, air["x_mm"]!!.jsonPrimitive.double)
        assertEquals(24, air["values"]!!.jsonArray.size)
        assertEquals(0.14, volume["styles"]!!.jsonObject["air: House air"]!!.jsonObject["opacity"]!!.jsonPrimitive.double)
    }

    @Test
    fun an_air_zone_with_no_pieces_is_refused_by_name() {
        val view = readHeatScreen("""{ "title": "Heat", "world": "w", "site": "big", "air_zones": [ { "label": "Loft air", "member": "Site", "systems": ["loft"], "stock": "air_temperature_f" } ] }""")
        assertTrue("Loft air" in assertFailsWith<HeatFormatException> { heatSpec(view, boxes(), season()) }.message!!)
    }
}
