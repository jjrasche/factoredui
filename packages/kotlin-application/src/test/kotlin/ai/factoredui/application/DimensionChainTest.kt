package ai.factoredui.application

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun recorded(name: String) = checkNotNull(DimensionChainTest::class.java.getResource("/$name")).readText()

private val GEOMETRY = readGeometry(recorded("live-geometry.json"))

private val EXPANSION = readExpansion(recorded("live-expansion.json"))

private fun List<Map<String, Any?>>.texts(prefix: String) = filter { (it["id"] as String).startsWith(prefix) && it["kind"] == "label" }.map { it["text"] as String }

class DimensionChainTest {
    @Test
    fun a_chain_has_a_string_in_feet_and_inches_for_each_stretch_between_breaks() {
        val shapes = chainShapes("c", listOf(0.0, 1219.2, 2438.4, 1219.0), ChainSide.BELOW, -220.0)
        assertEquals(listOf("4'-0\"", "4'-0\""), shapes.texts("c/text"))
        assertEquals(3, shapes.count { (it["id"] as String).startsWith("c/tick") })
    }

    @Test
    fun a_stretch_too_short_to_hold_its_string_has_none() {
        assertEquals(listOf("4'-0\""), chainShapes("c", listOf(0.0, 114.0, 1333.0), ChainSide.BELOW, 0.0).texts("c/text"))
    }

    @Test
    fun the_south_elevation_chains_the_panels_along_the_wall_and_the_sill_and_opening_heights() {
        val south = elevationDrawings(GEOMETRY).first { it.id == "south" }.shapes
        assertTrue(south.texts("chain:x/text").all { it.endsWith("\"") })
        assertTrue(south.texts("chain:x/text").isNotEmpty())
        assertTrue("4'-0\"" in south.texts("chain:z/text"), south.texts("chain:z/text").toString())
    }

    @Test
    fun the_plan_chains_each_side_with_the_openings_dimensioned_to_their_edges() {
        val plan = blueprintPlanShapes(GEOMETRY)
        listOf("chain:south", "chain:north", "chain:west", "chain:east").forEach { assertTrue(plan.any { shape -> (shape["id"] as String).startsWith(it) }, it) }
        assertTrue(plan.texts("chain:south/text").isNotEmpty())
    }

    @Test
    fun the_section_labels_the_total_thickness_of_each_piece_it_passes_through() {
        val section = sectionDrawing(GEOMETRY)!!.shapes
        assertTrue(section.texts("size:thickness:").all { it.startsWith("thickness 114 mm") }, section.texts("size:thickness:").toString())
        assertTrue(section.texts("size:thickness:").isNotEmpty())
    }
}
