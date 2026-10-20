package ai.factoredui.application

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private val HOUSE = readGeometry(
    """
{ "pieces": [
    { "id": "front", "system": "walls", "x": 0, "y": 0, "z": 0, "dx": 3000, "dy": 100, "dz": 2400 },
    { "id": "back", "system": "walls", "x": 0, "y": 4900, "z": 0, "dx": 3000, "dy": 100, "dz": 2400 },
    { "id": "side", "system": "walls", "x": 0, "y": 100, "z": 0, "dx": 100, "dy": 4800, "dz": 2400 },
    { "id": "pane", "system": "glazing", "x": 500, "y": 0, "z": 900, "dx": 1000, "dy": 100, "dz": 1200 } ],
  "contacts": [],
  "cuts": [ { "host": "front", "opening": "pane", "region": { "min_mm": [500, 0, 900], "max_mm": [1500, 100, 2100] }, "offset_mm": [500, 0, 900] } ],
  "unmeasured": [] }
"""
)

private fun shapeIds(node: JsonObject): List<String> = node["children"]!!.jsonArray.last().jsonObject["props"]!!.jsonObject["shapes"]!!.jsonArray.map { it.jsonObject["id"]!!.jsonPrimitive.content }

private fun elevation(side: String): List<String> = shapeIds(blueprintElevationNodes(HOUSE).first { it["id"]!!.jsonPrimitive.content == "blueprint-$side-view" })

class ElevationTest {
    @Test
    fun an_elevation_shows_the_pieces_on_its_near_side_and_not_the_far_wall() {
        assertTrue("front" in elevation("south"))
        assertFalse("back" in elevation("south"))
        assertTrue("back" in elevation("north"))
        assertFalse("front" in elevation("north"))
    }

    @Test
    fun the_hole_cut_for_an_opening_is_drawn_on_the_side_that_has_it() {
        assertTrue("hole:front|pane" in elevation("south"))
        assertFalse("hole:front|pane" in elevation("north"))
    }

    @Test
    fun an_elevation_has_dimension_strings_for_the_whole_and_a_size_for_each_piece() {
        val ids = elevation("south")
        assertTrue(listOf("dim:width/text", "dim:height/text", "size:front").all { it in ids })
    }

    @Test
    fun a_section_through_the_middle_draws_only_what_the_plane_passes_through() {
        val ids = shapeIds(blueprintSectionNode(HOUSE)!!)
        assertTrue("side" in ids)
        assertEquals(emptyList(), listOf("front", "back", "pane").filter { it in ids })
    }
}
