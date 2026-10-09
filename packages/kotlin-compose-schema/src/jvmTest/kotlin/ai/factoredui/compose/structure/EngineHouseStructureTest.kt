package ai.factoredui.compose.structure

import ai.factoredui.compose.scene.SceneView
import ai.factoredui.compose.scene.readSceneView
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun engineHouse(): SceneView =
    readSceneView(EngineHouseStructureTest::class.java.getResource("/house-floor.scene.json")!!.readText())

private fun everyPart(scene: SceneView) = visibleParts(scene, LevelFocus.AllLevels, showLower = true)

class EngineHouseStructureTest {
    @Test
    fun the_engine_house_bill_of_materials_costs_5630_dollars_49_before_hardware() {
        val scene = engineHouse()
        val totals = bomTotals(bomLines(scene, everyPart(scene)))
        assertTrue(abs(totals.costUsd!! - 5630.49) < 1e-6, "total ${totals.costUsd}")
        assertEquals("$5,630.49", bomTotalsText(totals).cost)
        assertEquals(35, totals.quantity)
        assertEquals(0, totals.unpricedLines)
    }

    @Test
    fun the_engine_house_lines_are_twelve_piers_four_edge_beams_four_interior_beams_and_fifteen_panels() {
        val scene = engineHouse()
        val lines = bomLines(scene, everyPart(scene)).associate { it.typeId to it.quantity }
        assertEquals(mapOf("cast_pier" to 12, "edge_beam" to 4, "interior_beam" to 4, "sip_floor_panel" to 15), lines)
    }

    @Test
    fun no_engine_house_part_is_weighed_so_every_line_and_the_total_say_the_weight_is_not_known() {
        val scene = engineHouse()
        val lines = bomLines(scene, everyPart(scene))
        lines.forEach { assertEquals("weight not known: no weight for this part is banked in the world or the structural rows", bomLineText(it).weightLine) }
        assertEquals("weight not known", bomTotalsText(bomTotals(lines)).weight)
    }

    @Test
    fun twenty_three_engine_house_parts_carry_a_utilisation_figure_and_no_pier_does() {
        val scene = engineHouse()
        val figures = utilisationByPart(scene)
        assertEquals(23, figures.size)
        val typeOf = scene.footprints!!.items.associate { it.id to it.type }
        assertTrue(figures.keys.none { typeOf[it] == "cast_pier" })
    }

    @Test
    fun the_engine_house_is_one_level_named_for_its_panels() {
        val scene = engineHouse()
        assertEquals(listOf(0), levelsPresent(scene))
        assertEquals("Site-built SIP floor panel", levelName(scene, 0))
    }

    @Test
    fun the_engine_house_states_no_heights_so_every_part_is_drawn_flat_on_the_ground() {
        val scene = engineHouse()
        val parts = scene.footprints!!.items
        assertTrue(parts.all { partHeightMm(scene, it) == null })
        assertTrue(parts.map { partBox(scene, it) }.all { it.z0 == 0.0 && it.z1 == 0.0 })
    }

    @Test
    fun the_iso_order_of_the_engine_house_draws_every_part_exactly_once() {
        val scene = engineHouse()
        val ordered = orderForIso(scene, everyPart(scene)).map { it.footprint.id }
        assertEquals(scene.footprints!!.items.map { it.id }.sorted(), ordered.sorted())
    }
}
