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
    { "id": "a1", "system": "alpha", "type": "slab", "x_mm": 100, "y_mm": 200, "width_mm": 2000, "depth_mm": 1000, "level": 1, "fields": { "thickness_mm": 200 } },
    { "id": "b1", "system": "beta", "type": "cover", "x_mm": 0, "y_mm": 0, "width_mm": 4000, "depth_mm": 3000, "level": 3, "fields": { "thickness_mm": null } },
    { "id": "g1", "system": "gamma", "type": "edge", "x_mm": 100, "y_mm": 200, "width_mm": 2000, "depth_mm": null, "level": 2, "fields": { "height_mm": 3000, "thickness_mm": null } },
    { "id": "g2", "system": "gamma", "type": "edge", "x_mm": 100, "y_mm": 200, "width_mm": null, "depth_mm": 1000, "level": 2, "fields": { "height_mm": 3000 } },
    { "id": "g3", "system": "gamma", "type": "dot", "x_mm": 100, "y_mm": 200, "width_mm": null, "depth_mm": null, "level": 0, "fields": { "above_grade_mm": null } } ],
  "instances": [ { "id": "i1", "system": "gamma", "type": "bar", "x_mm": 1000, "y_mm": 200, "rotation_deg": 90, "length_mm": 1000, "elevation_mm": 250, "fields": { "glass_height_mm": null } } ],
  "supplied": { "stock": { "value": 630, "unit": "board_foot", "reason": "from a note" } } }
"""

private val GEOMETRY = readGeometry(
    """{ "pieces": [
        { "id": "a1", "system": "alpha", "x": 100, "y": 200, "z": 0, "dx": 2000, "dy": 1000, "dz": 200 },
        { "id": "b1", "system": "beta", "x": 0, "y": 0, "z": 300, "dx": 4000, "dy": 3000, "dz": 50 },
        { "id": "g1", "system": "gamma", "x": 100, "y": 200, "z": 0, "dx": 2000, "dy": 10, "dz": 3000 },
        { "id": "g2", "system": "gamma", "x": 100, "y": 200, "z": 0, "dx": 10, "dy": 1000, "dz": 3000 },
        { "id": "g3", "system": "gamma", "x": 100, "y": 200, "z": 0, "dx": 10, "dy": 10, "dz": 10 },
        { "id": "i1", "system": "gamma", "x": 1000, "y": -300, "z": 250, "dx": 10, "dy": 1000, "dz": 100 } ],
      "contacts": [], "cuts": [], "unmeasured": [] }""",
)

private fun page(): JsonObject = buildPageSpec(readSystemFile(SYSTEM_FILE), readExpansion(EXPANSION))

private fun pageDrawn(): JsonObject = buildPageSpec(readSystemFile(SYSTEM_FILE), readExpansion(EXPANSION), geometried = Geometried(GEOMETRY))

private fun JsonObject.nodes(): List<JsonObject> = listOf(this) + this["children"]!!.jsonArray.flatMap { it.jsonObject.nodes() }

private fun JsonObject.byId(id: String): JsonObject = nodes().first { it["id"]!!.jsonPrimitive.content == id }

private fun planShapes(): List<JsonObject> = pageDrawn()["root"]!!.jsonObject.byId("plan")["props"]!!.jsonObject["shapes"]!!.jsonArray.map { it.jsonObject }

private fun JsonObject.text(key: String) = this[key]!!.jsonPrimitive.content

private fun volumeBoxes(): Map<String, JsonObject> =
    page()["root"]!!.jsonObject.byId("volume")["props"]!!.jsonObject["boxes"]!!.jsonArray.map { it.jsonObject }.associateBy { it.text("id") }

private fun JsonObject.number(key: String) = this[key]!!.jsonPrimitive.content.toDouble()

class PageSpecTest {
    @Test
    fun the_page_is_a_spec_the_renderer_can_decode() {
        Json { ignoreUnknownKeys = false }.decodeFromString(Spec.serializer(), page().toString())
    }

    @Test
    fun a_piece_is_a_rect_of_its_footprint_and_the_lowest_are_painted_first() {
        val pieces = planShapes().filter { !it.text("id").startsWith("arrow:") }
        assertEquals(setOf("rect"), pieces.map { it.text("kind") }.toSet())
        assertEquals("b1", pieces.last().text("id"))
        assertEquals(2000.0, pieces.first { it.text("id") == "a1" }.number("width_mm"))
    }

    @Test
    fun the_system_seen_over_another_is_dashed_and_each_system_has_its_own_colour_by_order() {
        val styles = pageDrawn()["root"]!!.jsonObject.byId("plan")["props"]!!.jsonObject["styles"]!!.jsonObject
        assertEquals("true", styles["beta"]!!.jsonObject.text("dash"))
        assertEquals("false", styles["alpha"]!!.jsonObject.text("dash"))
        assertEquals(3, styles.filterKeys { it != "north arrow" }.values.map { it.jsonObject.text("stroke") }.toSet().size)
    }

    @Test
    fun north_is_drawn_as_an_arrow_not_a_glyph() {
        val labels = planShapes().filter { it.text("kind") == "label" }.associate { it.text("id") to it.text("text") }
        assertEquals("N", labels["arrow:label"])
        assertEquals(listOf("arrow:shaft", "arrow:west", "arrow:east"), planShapes().filter { it.text("id").startsWith("arrow:") && it.text("kind") == "line" }.map { it.text("id") })
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
    fun systems_that_are_drawn_never_share_a_colour_however_many_systems_the_file_names() {
        val named = (0..10).joinToString(",") { "\"s$it\": { \"settings\": {} }" }
        val file = readSystemFile("""{ "id": "w", "title": "W", "systems": { $named } }""")
        val expansion = readExpansion(
            """{ "extent_mm": [10, 10], "objects": [
                { "id": "early", "system": "s2", "type": "t", "x_mm": 0, "y_mm": 0, "width_mm": 5, "depth_mm": 5, "level": 0, "fields": {} },
                { "id": "late", "system": "s10", "type": "t", "x_mm": 0, "y_mm": 0, "width_mm": 5, "depth_mm": 5, "level": 0, "fields": {} } ],
               "instances": [], "supplied": {} }""",
        )
        val geometry = readGeometry(
            """{ "pieces": [ { "id": "early", "system": "s2", "x": 0, "y": 0, "z": 0, "dx": 5, "dy": 5, "dz": 5 }, { "id": "late", "system": "s10", "x": 0, "y": 0, "z": 0, "dx": 5, "dy": 5, "dz": 5 } ], "contacts": [], "cuts": [], "unmeasured": [] }""",
        )
        val styles = buildPageSpec(file, expansion, geometried = Geometried(geometry))["root"]!!.jsonObject.byId("plan")["props"]!!.jsonObject["styles"]!!.jsonObject
        assertEquals(2, listOf("s2", "s10").map { styles[it]!!.jsonObject.text("stroke") }.toSet().size)
    }

    @Test
    fun an_instance_row_carries_its_elevation_and_own_fields_and_two_instances_at_one_spot_have_different_row_ids() {
        val expansion = readExpansion(
            """{ "extent_mm": [10, 10], "objects": [], "supplied": {}, "instances": [
                { "id": "g@1,2", "system": "gamma", "type": "glazing", "x_mm": 1, "y_mm": 2, "rotation_deg": 0, "length_mm": 9, "elevation_mm": 1219, "fields": { "facing_degrees": 180, "sill_height_mm": 1219, "glass_height_mm": 1219 } },
                { "id": "g@1,2", "system": "gamma", "type": "glazing", "x_mm": 1, "y_mm": 2, "rotation_deg": 0, "length_mm": 9, "elevation_mm": 2438, "fields": { "facing_degrees": 180, "sill_height_mm": 2438, "glass_height_mm": 1219 } } ] }""",
        )
        val view = buildPageSpec(readSystemFile(SYSTEM_FILE), expansion)["root"]!!.jsonObject.byId("instances")["props"]!!.jsonObject["view"]!!.jsonObject
        val columnIds = view["columns"]!!.jsonArray.map { it.jsonObject.text("id") }
        listOf("length_mm", "elevation_mm", "facing_degrees", "sill_height_mm", "glass_height_mm").forEach { assertTrue(it in columnIds, it) }
        val rows = view["rows"]!!.jsonArray.map { it.jsonObject }
        assertEquals(2, rows.map { it.text("id") }.toSet().size)
        assertEquals(2438.0, rows.last()["values"]!!.jsonObject.number("sill_height_mm"))
        assertEquals(180.0, rows.last()["values"]!!.jsonObject.number("facing_degrees"))
        assertEquals(2438.0, rows.last()["values"]!!.jsonObject.number("elevation_mm"))
    }

    @Test
    fun the_table_holds_every_object_and_instance_and_the_word_tile_appears_nowhere() {
        val spec = page()["root"]!!.jsonObject
        assertEquals(5, spec.byId("objects")["props"]!!.jsonObject["view"]!!.jsonObject["rows"]!!.jsonArray.size)
        val instances = spec.byId("instances")["props"]!!.jsonObject["view"]!!.jsonObject["rows"]!!.jsonArray
        assertEquals("i1 · 90°", instances.single().jsonObject.text("label"))
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

    @Test
    fun a_known_height_is_drawn_true_and_an_unknown_one_is_a_marked_stand_in_of_the_nominal_height() {
        val boxes = volumeBoxes()
        assertEquals(3000.0, boxes["g1"]!!.number("height_mm"))
        assertEquals("false", boxes["g1"]!!.text("estimate"))
        assertEquals(200.0, boxes["a1"]!!.number("height_mm"))
        assertEquals("false", boxes["a1"]!!.text("estimate"))
        assertEquals(NOMINAL_HEIGHT_MM, boxes["b1"]!!.number("height_mm"))
        assertEquals("true", boxes["b1"]!!.text("estimate"))
        assertEquals(NOMINAL_HEIGHT_MM, boxes["g3"]!!.number("height_mm"))
        assertEquals("true", boxes["g3"]!!.text("estimate"))
    }

    @Test
    fun each_level_stands_on_the_one_below_and_an_instance_stands_on_the_highest_level_it_lies_along_lifted_by_its_elevation() {
        val boxes = volumeBoxes()
        assertEquals(0.0, boxes["g3"]!!.number("z_mm"))
        assertEquals(400.0, boxes["a1"]!!.number("z_mm"))
        assertEquals(600.0, boxes["g1"]!!.number("z_mm"))
        assertEquals(3600.0, boxes["b1"]!!.number("z_mm"))
        assertEquals(850.0, boxes["i1"]!!.number("z_mm"))
        assertEquals(90.0, boxes["i1"]!!.number("rotation_deg"))
    }

    @Test
    fun a_footprint_known_one_way_is_a_plane_and_the_legend_says_what_the_hatching_means() {
        val boxes = volumeBoxes()
        assertEquals(0.0, boxes["g1"]!!.number("depth_mm"))
        assertEquals(0.0, boxes["g2"]!!.number("width_mm"))
        assertEquals(1100.0, boxes["g1"]!!.number("x_mm"))
        val legend = page()["root"]!!.jsonObject.byId("volume")["props"]!!.jsonObject["legend"]!!.jsonArray.map { it.jsonObject }
        assertEquals("height unknown, drawn 400 mm tall", legend.last().text("label"))
        assertEquals("true", legend.last().text("hatched"))
    }

    @Test
    fun the_two_views_sit_behind_a_switch_and_neither_view_names_a_glyph() {
        val views = pageDrawn()["root"]!!.jsonObject.byId("views")
        assertEquals("tabs", views.text("type"))
        assertEquals(listOf("plan-view", "volume-view"), views["children"]!!.jsonArray.map { it.jsonObject.text("id") })
        val everyText = pageDrawn()["root"]!!.jsonObject.nodes().mapNotNull { node -> (node["props"] as? JsonObject)?.get("value").let { it as? JsonPrimitive }?.takeIf { it.isString }?.content }
        assertEquals(emptyList(), everyText.filter { value -> value.any { it.code > 0x2000 } })
    }

    @Test
    fun selecting_a_drawn_thing_shows_what_it_is_its_systems_settings_with_reasons_and_the_rules_naming_it() {
        val file = readSystemFile("""{ "id": "w", "title": "W", "binds": { "openings": ["alpha", "gamma"] }, "systems": {
            "alpha": { "solution": "plain", "settings": { "width_mm": 2000 }, "reasons": { "width_mm": "Jim measured it" } } } }""")
        val details = buildPageSpec(file, readExpansion(EXPANSION), geometried = Geometried(GEOMETRY))["root"]!!.jsonObject.byId("plan")["props"]!!.jsonObject["details"]!!.jsonObject
        val sections = details["a1"]!!.jsonObject["sections"]!!.jsonArray.map { it.jsonObject }
        val rows = sections.flatMap { it["rows"]!!.jsonArray.map { row -> row.jsonObject } }.associateBy { it.text("label") }
        assertEquals("alpha", rows.getValue("system")["value"]!!.jsonPrimitive.content)
        assertEquals("plain", rows.getValue("solution")["value"]!!.jsonPrimitive.content)
        assertEquals("200", rows.getValue("thickness")["value"]!!.jsonPrimitive.content)
        assertEquals("Jim measured it", rows.getValue("width")["note"]!!.jsonPrimitive.content)
        assertEquals("openings", rows.getValue("bound as")["value"]!!.jsonPrimitive.content)
        assertTrue("i1" in details.keys && "g3" in details.keys)
    }

    @Test
    fun the_plan_and_the_solid_view_share_one_selection_and_the_tables_wait_behind_a_switch_that_starts_off() {
        val root = pageDrawn()["root"]!!.jsonObject
        assertEquals(root.byId("plan")["props"]!!.jsonObject.text("selected"), root.byId("volume")["props"]!!.jsonObject.text("selected"))
        assertEquals("toggle", root.byId("tables-switch").text("type"))
        assertEquals(root.byId("tables-switch")["props"]!!.jsonObject.text("value"), root.byId("tables").text("visible"))
    }

    @Test
    fun two_things_the_engine_gives_one_id_are_told_apart_so_each_can_be_selected() {
        val expansion = readExpansion(
            """{ "extent_mm": [10, 10], "objects": [], "supplied": {}, "instances": [
                { "id": "g", "system": "gamma", "type": "glazing", "x_mm": 1, "y_mm": 2, "rotation_deg": 0, "length_mm": 9, "elevation_mm": 1, "fields": {} },
                { "id": "g", "system": "gamma", "type": "glazing", "x_mm": 1, "y_mm": 2, "rotation_deg": 0, "length_mm": 9, "elevation_mm": 2, "fields": {} } ] }""",
        )
        assertEquals(listOf("g", "g (2)"), expansion.instances.map { it.id })
        val geometry = readGeometry(
            """{ "pieces": [ { "id": "g", "system": "gamma", "x": 1, "y": 2, "z": 1, "dx": 9, "dy": 1, "dz": 1 }, { "id": "g (2)", "system": "gamma", "x": 1, "y": 2, "z": 2, "dx": 9, "dy": 1, "dz": 1 } ], "contacts": [], "cuts": [], "unmeasured": [] }""",
        )
        val plan = buildPageSpec(readSystemFile(SYSTEM_FILE), expansion, geometried = Geometried(geometry))["root"]!!.jsonObject.byId("plan")["props"]!!.jsonObject
        assertEquals(setOf("g", "g (2)"), plan["details"]!!.jsonObject.keys)
    }

    @Test
    fun an_opening_is_drawn_as_deep_as_the_wall_it_sits_in() {
        val expansion = readExpansion(
            """{ "extent_mm": [10, 10], "objects": [], "supplied": {}, "instances": [
                { "id": "w", "system": "gamma", "type": "glazing", "x_mm": 1, "y_mm": 2, "rotation_deg": 0, "length_mm": 9, "elevation_mm": 1, "fields": { "height_mm": 5, "thickness_mm": 114 } } ] }""",
        )
        val box = buildPageSpec(readSystemFile(SYSTEM_FILE), expansion)["root"]!!.jsonObject.byId("volume")["props"]!!.jsonObject["boxes"]!!.jsonArray.first().jsonObject
        assertEquals(114.0, box.number("depth_mm"))
    }
}
