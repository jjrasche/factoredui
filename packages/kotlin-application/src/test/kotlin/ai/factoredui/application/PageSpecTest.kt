package ai.factoredui.application

import ai.factoredui.compose.schema.Spec
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private val SYSTEM_FILE = """
{ "id": "shed", "title": "A shed", "systems": {
    "alpha": { "solution": "plain", "settings": { "width_mm": 2000, "depth_mm": null, "count": 3, "sides": ["north", "east"] },
               "reasons": { "depth_mm": "nobody measured it" } },
    "beta": { "solution": "other", "settings": { "height_mm": 3000 }, "reasons": { "height_mm": "reading: the tall number is the height" } },
    "gamma": { "settings": {} } } }
"""

private val EXPANSION = """
{ "extent_mm": [4000, 3000],
  "objects": [
    { "id": "a1", "system": "alpha", "type": "slab", "x_mm": 100, "y_mm": 200, "width_mm": 2000, "depth_mm": 1000, "level": 1, "fields": {} },
    { "id": "b1", "system": "beta", "type": "cover", "x_mm": 0, "y_mm": 0, "width_mm": 4000, "depth_mm": 3000, "level": 3, "fields": {} },
    { "id": "g1", "system": "gamma", "type": "edge", "x_mm": 100, "y_mm": 200, "width_mm": 2000, "depth_mm": null, "level": 2, "fields": {} },
    { "id": "g2", "system": "gamma", "type": "edge", "x_mm": 100, "y_mm": 200, "width_mm": null, "depth_mm": 1000, "level": 2, "fields": {} },
    { "id": "g3", "system": "gamma", "type": "dot", "x_mm": 100, "y_mm": 200, "width_mm": null, "depth_mm": null, "level": 0, "fields": {} } ],
  "instances": [ { "id": "i1", "system": "gamma", "type": "bar", "x_mm": 1000, "y_mm": 200, "rotation_deg": 90, "length_mm": 1000, "fields": {} } ],
  "supplied": { "stock": { "value": 630, "unit": "board_foot", "reason": "from a note" } } }
"""

private fun page(): JsonObject = buildPageSpec(readSystemFile(SYSTEM_FILE), readExpansion(EXPANSION))

private fun JsonObject.nodes(): List<JsonObject> = listOf(this) + this["children"]!!.jsonArray.flatMap { it.jsonObject.nodes() }

private fun JsonObject.byId(id: String): JsonObject = nodes().first { it["id"]!!.jsonPrimitive.content == id }

private fun planShapes(): List<JsonObject> = page()["root"]!!.jsonObject.byId("plan")["props"]!!.jsonObject["shapes"]!!.jsonArray.map { it.jsonObject }

private fun JsonObject.text(key: String) = this[key]!!.jsonPrimitive.content

private fun JsonObject.number(key: String) = this[key]!!.jsonPrimitive.content.toDouble()

class PageSpecTest {
    @Test
    fun the_page_is_a_spec_the_renderer_can_decode() {
        Json { ignoreUnknownKeys = false }.decodeFromString(Spec.serializer(), page().toString())
    }

    @Test
    fun a_size_known_both_ways_is_a_rect_one_way_a_line_and_neither_a_marker() {
        val kinds = planShapes().associate { it.text("id") to it.text("kind") }
        assertEquals("rect", kinds["a1"])
        assertEquals("line", kinds["g1"])
        assertEquals("line", kinds["g2"])
        assertEquals("marker", kinds["g3"])
        val along = planShapes().first { it.text("id") == "g2" }
        assertEquals(1200.0, along.number("y2_mm"))
        assertEquals(100.0, along.number("x2_mm"))
    }

    @Test
    fun an_instance_is_a_line_centred_on_its_position_and_turned_by_its_rotation() {
        val bar = planShapes().first { it.text("id") == "i1" }
        assertEquals(1000.0, bar.number("x_mm"), 1e-6)
        assertEquals(1000.0, bar.number("x2_mm"), 1e-6)
        assertEquals(-300.0, bar.number("y_mm"), 1e-6)
        assertEquals(700.0, bar.number("y2_mm"), 1e-6)
    }

    @Test
    fun rects_are_painted_from_the_highest_level_down_and_before_lines_and_markers() {
        val order = planShapes().filter { it.text("kind") != "label" && !it.text("id").startsWith("arrow:") }.map { it.text("id") }
        assertEquals(listOf("b1", "a1", "g1", "g2", "g3", "i1"), order)
    }

    @Test
    fun the_system_seen_over_another_is_dashed_and_each_system_has_its_own_colour_by_order() {
        val styles = page()["root"]!!.jsonObject.byId("plan")["props"]!!.jsonObject["styles"]!!.jsonObject
        assertEquals("true", styles["beta"]!!.jsonObject.text("dash"))
        assertEquals("false", styles["alpha"]!!.jsonObject.text("dash"))
        assertEquals(3, styles.filterKeys { it != "north arrow" }.values.map { it.jsonObject.text("stroke") }.toSet().size)
    }

    @Test
    fun a_rect_is_labelled_with_its_system_and_its_millimetres_and_north_is_drawn_as_an_arrow_not_a_glyph() {
        val labels = planShapes().filter { it.text("kind") == "label" }.associate { it.text("id") to it.text("text") }
        assertEquals("alpha 2000 × 1000 mm", labels["label:a1"])
        assertEquals("beta 4000 × 3000 mm", labels["label:b1"])
        assertEquals("N", labels["arrow:label"])
        val arrowLines = planShapes().filter { it.text("id").startsWith("arrow:") && it.text("kind") == "line" }
        assertEquals(listOf("arrow:shaft", "arrow:west", "arrow:east"), arrowLines.map { it.text("id") })
        assertEquals(emptyList(), labels.values.filter { text -> text.any { it.code > 0x2000 } })
    }

    @Test
    fun a_missing_setting_shows_a_dash_and_its_reason_and_a_reading_is_marked_open() {
        val root = page()["root"]!!.jsonObject
        val missing = root.byId("alpha.depth_mm.value")
        assertEquals("–", missing["props"]!!.jsonObject.text("missing"))
        assertTrue(missing["props"]!!.jsonObject["value"] is kotlinx.serialization.json.JsonNull)
        assertEquals("nobody measured it", root.byId("alpha.depth_mm.reason")["props"]!!.jsonObject.text("value"))
        assertEquals("open reading", root.byId("beta.height_mm.open")["props"]!!.jsonObject.text("label"))
        assertEquals("the tall number is the height", root.byId("beta.height_mm.reason")["props"]!!.jsonObject.text("value"))
    }

    @Test
    fun a_length_is_formatted_as_millimetres_and_a_list_is_joined() {
        val root = page()["root"]!!.jsonObject
        assertEquals(" mm", root.byId("alpha.width_mm.value")["props"]!!.jsonObject["format"]!!.jsonObject.text("suffix"))
        assertEquals("north, east", root.byId("alpha.sides.value")["props"]!!.jsonObject.text("value"))
        assertEquals("3", root.byId("alpha.count.value")["props"]!!.jsonObject.text("value"))
    }

    @Test
    fun the_table_holds_every_object_and_instance_and_the_word_tile_appears_nowhere() {
        val rows = page()["root"]!!.jsonObject.byId("objects")["props"]!!.jsonObject["view"]!!.jsonObject["rows"]!!.jsonArray
        assertEquals(6, rows.size)
        assertEquals("i1 · 90°", rows.last().jsonObject.text("label"))
        assertTrue(!page().toString().contains("tile", ignoreCase = true))
    }

    @Test
    fun the_supplied_figure_shows_its_value_unit_and_reason() {
        val root = page()["root"]!!.jsonObject
        assertEquals("630 board foot", root.byId("supplied.stock.value")["props"]!!.jsonObject.text("value"))
        assertEquals("from a note", root.byId("supplied.stock.reason")["props"]!!.jsonObject.text("value"))
    }

    @Test
    fun a_refused_expansion_is_an_error_carrying_the_engines_reason() {
        val failure = assertFailsWith<ExpansionFormatException> { readExpansion("""{ "refused": { "rule": "r", "message": "no" } }""") }
        assertTrue(failure.message!!.contains("\"message\":\"no\""))
    }
}
