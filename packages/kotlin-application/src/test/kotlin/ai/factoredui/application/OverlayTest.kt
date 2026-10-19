package ai.factoredui.application

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private val SYSTEM_FILE = """{ "id": "pier", "title": "A pier", "systems": { "posts": { "solution": "four-posts", "settings": {} }, "walls": { "solution": "slanted", "settings": {} } } }"""

private val EXPANSION = """
{ "extent_mm": [2438, 2438],
  "objects": [
    { "id": "post@0,0", "system": "posts", "type": "post", "x_mm": 0, "y_mm": 0, "width_mm": 100, "depth_mm": 100, "level": 0, "elevation_mm": 0, "fields": { "height_mm": 300 } },
    { "id": "post@9,9", "system": "posts", "type": "post", "x_mm": 9, "y_mm": 9, "width_mm": 100, "depth_mm": 100, "level": 0, "elevation_mm": 0, "fields": { "height_mm": 300 } } ],
  "instances": [
    { "id": "wall@0", "system": "walls", "type": "wall", "x_mm": 0, "y_mm": 0, "rotation_deg": 0, "length_mm": 2438, "elevation_mm": 350, "fields": { "height_mm": 2400 } },
    { "id": "wall@1", "system": "walls", "type": "wall", "x_mm": 0, "y_mm": 0, "rotation_deg": 90, "length_mm": 2438, "elevation_mm": 350, "fields": { "height_mm": 2400 } } ],
  "supplied": {} }
"""

private val OVERLAY = """
{ "overlay": { "id": "utilisation", "label": "Utilisation", "unit": "ratio", "min": 0.0, "max": 1.0, "good": "low",
    "stops": [[0.0, "#2e9e4f"], [0.5, "#f2c230"], [1.0, "#d6402f"]], "unknown_color": "#9a9a9a" },
  "pieces": {
    "post@0,0": { "value": 1.25, "t": 1.0, "color": "#d6402f" },
    "post@9,9": { "value": 0.5, "t": 0.5, "color": "#f2c230" },
    "wall@0": { "value": null, "t": null, "color": null },
    "wall@1": { "value": null, "t": null, "color": "#9a9a9a", "reason": "turn-not-square" } },
  "counts": { "numbers": 2, "not_applicable": 1, "unknown": 1 }, "unknown": [ { "id": "wall@1", "color": "#9a9a9a", "reason": "turn-not-square" } ] }
"""

private fun page(): JsonObject =
    buildPageSpec(readSystemFile(SYSTEM_FILE), readExpansion(EXPANSION), overlaying = Overlaying(readOverlay(OVERLAY)))

private fun JsonObject.nodes(): List<JsonObject> = listOf(this) + this["children"]!!.jsonArray.flatMap { it.jsonObject.nodes() }

private fun JsonObject.byId(id: String): JsonObject = nodes().first { it["id"]!!.jsonPrimitive.content == id }

private fun JsonObject.text(key: String) = this[key]!!.jsonPrimitive.content

private fun planProps(): JsonObject = page()["root"]!!.jsonObject.byId("plan")["props"]!!.jsonObject

private fun styleOfShape(id: String): String = planProps()["shapes"]!!.jsonArray.map { it.jsonObject }.first { it.text("id") == id }.text("style")

class OverlayTest {
    @Test
    fun a_piece_is_drawn_in_the_colour_the_engine_gave_it() {
        val styles = planProps()["styles"]!!.jsonObject
        assertEquals("#d6402f", styles.getValue(styleOfShape("post@0,0")).jsonObject.text("stroke"))
        assertEquals("#f2c230", styles.getValue(styleOfShape("post@9,9")).jsonObject.text("stroke"))
    }

    @Test
    fun an_unknown_piece_is_the_engines_grey_and_a_figure_that_does_not_apply_is_an_outline() {
        val styles = planProps()["styles"]!!.jsonObject
        assertEquals("#9a9a9a", styles.getValue(styleOfShape("wall@1")).jsonObject.text("stroke"))
        assertEquals("#00000000", styles.getValue(styleOfShape("wall@0")).jsonObject.text("fill"))
    }

    @Test
    fun the_legend_has_a_swatch_per_stop_and_names_the_greys_that_are_in_use() {
        val labels = planProps()["legend"]!!.jsonArray.map { it.jsonObject.text("label") }
        assertEquals(listOf("0 ratio, best", "0.5 ratio", "1 ratio, worst", "unknown, tap for why", "does not apply"), labels)
    }

    @Test
    fun tapping_an_unknown_piece_shows_the_reason_and_a_known_piece_its_figure() {
        val details = planProps()["details"]!!.jsonObject
        fun rows(id: String) = details.getValue(id).jsonObject["sections"]!!.jsonArray.map { it.jsonObject }.first { it.text("heading") == "Utilisation" }["rows"]!!.jsonArray.associate { it.jsonObject.text("label") to it.jsonObject["value"]!!.jsonPrimitive.content }
        assertEquals("turn-not-square", rows("wall@1").getValue("why"))
        assertEquals("1.25 ratio", rows("post@0,0").getValue("figure"))
    }

    @Test
    fun the_solid_view_is_coloured_by_the_same_overlay() {
        val volume = page()["root"]!!.jsonObject.byId("volume")["props"]!!.jsonObject
        val boxes = volume["boxes"]!!.jsonArray.map { it.jsonObject }.associateBy { it.text("id") }
        assertEquals("#d6402f", volume["styles"]!!.jsonObject.getValue(boxes.getValue("post@0,0").text("style")).jsonObject.text("stroke"))
    }

    @Test
    fun the_page_says_what_colours_it_and_a_refused_overlay_is_said_on_screen() {
        assertTrue(page()["root"]!!.jsonObject.byId("overlay-note").toString().contains("Coloured by Utilisation"))
        val refused = buildPageSpec(readSystemFile(SYSTEM_FILE), readExpansion(EXPANSION), overlaying = Overlaying(null, failure = "say which"))
        assertTrue(refused["root"]!!.jsonObject.byId("overlay-note").toString().contains("could not be run: say which"))
    }

    @Test
    fun a_refusal_from_the_engine_is_read_as_its_message() {
        val failure = assertFailsWith<OverlayFormatException> { readOverlay("""{"refused": {"rule": "overlay-not-chosen", "message": "say which"}}""") }
        assertEquals("say which", failure.message)
    }
}
