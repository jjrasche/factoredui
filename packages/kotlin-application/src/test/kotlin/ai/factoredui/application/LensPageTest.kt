package ai.factoredui.application

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val SYSTEM_FILE = """{ "id": "pier", "title": "A pier", "systems": { "walls": { "solution": "panels", "settings": {} }, "posts": { "solution": "four", "settings": {} } } }"""

private val EXPANSION = """
{ "extent_mm": [1000, 100], "supplied": {}, "instances": [],
  "objects": [ { "id": "wall-a", "system": "walls", "type": "wall", "x_mm": 0, "y_mm": 0, "width_mm": 1000, "depth_mm": 100, "level": 0, "elevation_mm": 0, "fields": { "height_mm": 3000 } } ] }
"""

private val DESCRIBED = """
{ "systems": [ { "id": "walls", "title": "Encloses", "solution": "panels", "solution_title": "panels", "parts": [], "alternatives": [], "solvers": [], "rules": [],
    "settings": [ { "id": "thickness_mm", "unit": "mm", "type": "number", "value": 114, "reason": null } ] } ] }
"""

private val GEOMETRY = """{ "pieces": [ { "id": "wall-a", "system": "walls", "x": 0, "y": 0, "z": 0, "dx": 1000, "dy": 100, "dz": 3000 } ], "contacts": [], "cuts": [], "unmeasured": [] }"""

private fun page(withGeometry: Boolean = true, describes: Boolean = true): JsonObject = buildLensPageSpec(
    readSystemFile(SYSTEM_FILE), readExpansion(EXPANSION),
    describing = if (describes) Describing(readDescribed(DESCRIBED)) else Describing(emptyList(), failure = "no describe"),
    geometried = if (withGeometry) Geometried(readGeometry(GEOMETRY)) else Geometried(null, "no geometry"),
)["root"]!!.jsonObject

private fun JsonObject.nodes(): List<JsonObject> = listOf(this) + this["children"]!!.jsonArray.flatMap { it.jsonObject.nodes() }

private fun JsonObject.byId(id: String) = nodes().first { it["id"]!!.jsonPrimitive.content == id }

private fun JsonObject.props() = this["props"]!!.jsonObject

class LensPageTest {
    @Test
    fun the_world_fills_a_dark_stack_with_a_strip_over_it_and_a_dock_under_it() {
        val root = page()
        assertEquals("stack", root["type"]!!.jsonPrimitive.content)
        assertEquals("true", root.props()["fill"]!!.jsonPrimitive.content)
        assertEquals("top", root.byId("lens").props()["anchor"]!!.jsonPrimitive.content)
        val sheet = root.byId("dock").props()["sheet"]!!.jsonObject
        assertEquals(listOf("520", "840"), listOf("closed_dp", "open_dp").map { sheet[it]!!.jsonPrimitive.content })
    }

    @Test
    fun the_blueprint_is_a_drawing_over_the_shaded_one_that_a_switch_shows() {
        val root = page()
        val switchPath = root.byId("lens.blueprint").props()["value"]!!.jsonPrimitive.content
        assertEquals(switchPath, root.byId("blueprint-3d")["visible"]!!.jsonPrimitive.content)
        assertEquals("volume", root.byId("volume")["type"]!!.jsonPrimitive.content)
        assertTrue(root.nodes().none { it["id"]!!.jsonPrimitive.content == "volume" && it.containsKey("visible") })
    }

    @Test
    fun a_system_button_chooses_its_panel_and_unchooses_the_others() {
        val root = page()
        val button = root.byId("systems.walls")
        val values = button["action"]!!.jsonObject["params"]!!.jsonObject["values"]!!.jsonObject
        assertEquals("true", values["ui.sys.walls"]!!.jsonPrimitive.content)
        assertEquals("ui.sys.walls".let { "{$it}" }, root.byId("describe.walls.card")["visible"]!!.jsonPrimitive.content)
        assertTrue("ui.sys.posts" !in values.keys || values["ui.sys.posts"]!!.jsonPrimitive.content == "false")
    }

    @Test
    fun the_words_are_light_on_the_dark_dock() {
        val root = page()
        assertEquals("#e6e8eb", root.byId("describe.walls.name").props()["color"]!!.jsonPrimitive.content)
        assertEquals("#162033", root.byId("describe.walls.card").props()["background"]!!.jsonPrimitive.content)
    }

    @Test
    fun without_geometry_there_is_no_switch_and_without_a_description_the_old_cards_stay() {
        val root = page(withGeometry = false, describes = false)
        assertTrue(root.nodes().none { it["id"]!!.jsonPrimitive.content == "lens.blueprint" })
        assertTrue(root.nodes().any { it["id"]!!.jsonPrimitive.content == "describe-failure" })
        assertTrue(root.toString().contains("The geometry could not be read: no geometry"))
    }

    @Test
    fun the_dock_shows_the_card_of_the_piece_tapped_in_either_world() {
        val root = page()
        val path = root.byId("volume").props()["detail_text"]!!.jsonPrimitive.content
        assertEquals(path, root.byId("blueprint-3d").props()["detail_text"]!!.jsonPrimitive.content)
        val tapped = root.byId("dock.tapped")
        assertEquals(path, tapped.props()["value"]!!.jsonPrimitive.content)
        assertEquals(path, tapped["visible"]!!.jsonPrimitive.content)
    }

    @Test
    fun the_shaded_world_is_lit_by_the_sun() {
        assertEquals("sun", page().byId("volume").props()["lighting"]!!.jsonPrimitive.content)
    }

    @Test
    fun a_piece_is_styled_by_the_material_the_engine_gives_it_and_by_its_system_when_it_gives_none() {
        val geometry = readGeometry(
            """{ "pieces": [
                { "id": "a", "system": "walls", "x": 0, "y": 0, "z": 0, "dx": 1000, "dy": 100, "dz": 3000, "material": "SIP", "material_source": "research row r" },
                { "id": "b", "system": "walls", "x": 1000, "y": 0, "z": 0, "dx": 1000, "dy": 100, "dz": 3000, "material": "SIP", "material_source": "research row r" },
                { "id": "c", "system": "roof", "x": 0, "y": 0, "z": 3000, "dx": 2000, "dy": 100, "dz": 200, "material": null, "material_source": null } ],
              "contacts": [], "cuts": [], "unmeasured": [] }""",
        )
        val volume = volumeNode(readSystemFile(SYSTEM_FILE), readExpansion(EXPANSION), geometry = geometry)!!["props"]!!.jsonObject
        assertEquals(listOf("SIP", "SIP", "roof"), volume["boxes"]!!.jsonArray.map { it.jsonObject["style"]!!.jsonPrimitive.content })
        assertEquals(setOf("SIP", "roof"), volume["styles"]!!.jsonObject.keys)
    }

    @Test
    fun a_built_simulation_steps_the_shaded_world_and_its_button_starts_at_the_first_moment() {
        val root = buildLensPageSpec(
            readSystemFile(SYSTEM_FILE), readExpansion(EXPANSION),
            built = Built(Build("hour", listOf(Keyframe(0.0, emptyList(), emptyMap(), emptyMap()), Keyframe(1.0, emptyList(), emptyMap(), emptyMap())), mapOf("wall-a" to 1))),
        )["root"]!!.jsonObject
        val volume = root.byId("volume").props()
        assertEquals("{ui.step}", volume["step"]!!.jsonPrimitive.content)
        assertEquals("1", volume["boxes"]!!.jsonArray.first().jsonObject["stage"]!!.jsonPrimitive.content)
        val values = root.byId("systems.build")["action"]!!.jsonObject["params"]!!.jsonObject["values"]!!.jsonObject
        assertEquals("0", values["ui.step"]!!.jsonPrimitive.content)
        val others = root.byId("systems.walls")["action"]!!.jsonObject["params"]!!.jsonObject["values"]!!.jsonObject
        assertEquals("null", others["ui.step"]!!.jsonPrimitive.content)
    }
}
