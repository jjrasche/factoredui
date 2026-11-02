package ai.factoredui.application

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private val GEOMETRY = """
{ "pieces": [
    { "id": "wall-a", "system": "walls", "x": 0, "y": 0, "z": 0, "dx": 1000, "dy": 100, "dz": 3000 },
    { "id": "wall-b", "system": "walls", "x": 1000, "y": 0, "z": 0, "dx": 1000, "dy": 100, "dz": 3000 },
    { "id": "window", "system": "glazing", "x": 0, "y": 0, "z": 1000, "dx": 2000, "dy": 100, "dz": 1000 } ],
  "contacts": [
    { "a": "wall-a", "b": "wall-b", "normal": "side", "on_top": null, "area_mm2": 300000, "region": { "min_mm": [1000, 0, 0], "max_mm": [1000, 100, 3000] } },
    { "a": "wall-a", "b": "window", "normal": "side", "on_top": null, "area_mm2": 1, "region": { "min_mm": [0, 0, 1000], "max_mm": [0, 100, 2000] } } ],
  "cuts": [ { "host": "wall-a", "opening": "window", "region": { "min_mm": [0, 0, 1000], "max_mm": [1000, 100, 2000] }, "offset_mm": [0, 0, 1000] } ],
  "unmeasured": [ { "id": "post-1", "field": "width_mm", "why": "size-unknown" } ] }
"""

private val EXPANSION = """
{ "extent_mm": [2000, 100], "supplied": {}, "instances": [],
  "objects": [ { "id": "post-1", "system": "posts", "type": "post", "x_mm": 500, "y_mm": 50, "width_mm": null, "depth_mm": null, "level": 0, "elevation_mm": -1219, "fields": { "height_mm": 1105 } } ] }
"""

private val SYSTEM_FILE = """{ "id": "pier", "title": "A pier", "systems": { "walls": { "settings": {} } } }"""

private fun volume(): JsonObject = blueprintVolumeNode(readGeometry(GEOMETRY), readExpansion(EXPANSION))["props"]!!.jsonObject

private fun plan(): JsonObject = blueprintPlanNode(readGeometry(GEOMETRY))["props"]!!.jsonObject

private fun JsonObject.entries(key: String) = this[key]!!.jsonArray.map { it.jsonObject }

private fun JsonObject.text(key: String) = this[key]!!.jsonPrimitive.content

class BlueprintTest {
    @Test
    fun a_length_is_given_in_feet_and_inches_to_the_sixteenth() {
        assertEquals("8'-0\"", feetInches(2438.4))
        assertEquals("12'-0\"", feetInches(3657.6))
        assertEquals("4'-0\"", feetInches(1219.2))
        assertEquals("0'-1 1/2\"", feetInches(38.1))
        assertEquals("2438 mm · 8'-0\"", dimensionText(2438.0))
    }

    @Test
    fun each_piece_is_a_box_at_its_geometry_in_edges_light_on_dark_blue() {
        val props = volume()
        assertEquals("blueprint", props.text("mode"))
        val wall = props.entries("boxes").first { it.text("id") == "wall-b" }
        assertEquals(listOf(1500.0, 50.0, 0.0, 1000.0, 100.0, 3000.0), listOf("x_mm", "y_mm", "z_mm", "width_mm", "depth_mm", "height_mm").map { wall.text(it).toDouble() })
    }

    @Test
    fun a_seam_is_a_side_contact_between_pieces_of_one_system_and_a_contact_with_another_system_is_not() {
        val seams = volume().entries("lines").filter { it.text("id").startsWith("seam:") }
        assertTrue(seams.isNotEmpty())
        assertTrue(seams.all { it.text("id").startsWith("seam:wall-a|wall-b") })
    }

    @Test
    fun a_cut_is_drawn_as_the_outline_of_its_region_and_a_hole_in_the_plan() {
        val holes = volume().entries("lines").filter { it.text("id").startsWith("hole:wall-a|window") }
        assertEquals(12, holes.size)
        assertEquals("hole", holes.first().text("style"))
        val planHole = plan().entries("shapes").single { it.text("id") == "hole:wall-a|window" }
        assertEquals(listOf(0.0, 0.0, 1000.0, 100.0), listOf("x_mm", "y_mm", "width_mm", "depth_mm").map { planHole.text(it).toDouble() })
    }

    @Test
    fun a_piece_with_no_measured_size_is_dashed_and_says_why_when_tapped() {
        val post = volume().entries("lines").single { it.text("id") == "unknown:post-1" }
        assertEquals("unknown", post.text("style"))
        assertEquals(listOf(-1219.0, -114.0), listOf(post["from"]!!.jsonArray[2], post["to"]!!.jsonArray[2]).map { it.jsonPrimitive.content.toDouble() })
        val reason = plan()["details"]!!.jsonObject["post-1"]!!.jsonObject["sections"]!!.jsonArray.single().jsonObject["rows"]!!.jsonArray.single().jsonObject
        assertEquals("unknown: size-unknown", reason.text("value"))
    }

    @Test
    fun the_whole_is_dimensioned_in_millimetres_and_feet_and_inches_from_the_piece_sizes() {
        val labels = volume().entries("labels").map { it.text("text") }
        assertTrue("2000 mm · 6'-6 3/4\"" in labels)
        assertTrue("100 mm · 0'-3 15/16\"" in labels)
        assertTrue("3000 mm · 9'-10 1/8\"" in labels)
    }

    @Test
    fun the_page_carries_a_blueprint_section_and_says_when_the_geometry_could_not_be_read() {
        val ok = buildPageSpec(readSystemFile(SYSTEM_FILE), readExpansion(EXPANSION), geometried = Geometried(readGeometry(GEOMETRY)))["root"]!!.jsonObject
        assertTrue(ok.toString().contains("blueprint-3d") && ok.toString().contains("blueprint-plan"))
        val failed = buildPageSpec(readSystemFile(SYSTEM_FILE), readExpansion(EXPANSION), geometried = Geometried(null, "no geometry"))["root"]!!.jsonObject
        assertTrue(failed.toString().contains("The geometry could not be read: no geometry"))
        assertEquals("no geometry", assertFailsWith<GeometryFormatException> { readGeometry("""{"refused": {"rule": "x", "message": "no geometry"}}""") }.message)
    }

    @Test
    fun an_opening_is_a_hole_in_the_solid_wall_view_and_not_a_box_laid_over_it() {
        val expansion = readExpansion(
            """{ "extent_mm": [2000, 100], "supplied": {}, "instances": [], "objects": [
                { "id": "wall-a", "system": "walls", "type": "wall", "x_mm": 0, "y_mm": 0, "width_mm": 1000, "depth_mm": 100, "level": 0, "elevation_mm": 0, "fields": { "height_mm": 3000 } } ] }""",
        )
        val boxes = volumeNode(readSystemFile(SYSTEM_FILE), expansion, geometry = readGeometry(GEOMETRY))!!["props"]!!.jsonObject.entries("boxes")
        val hostBoxes = boxes.filter { it.text("id").startsWith("wall-a") }
        assertEquals(listOf("wall-a", "wall-a#2"), hostBoxes.map { it.text("id") })
        val area = hostBoxes.sumOf { it.text("width_mm").toDouble() * it.text("height_mm").toDouble() }
        assertEquals(1000.0 * 3000.0 - 1000.0 * 1000.0, area)
    }
}
