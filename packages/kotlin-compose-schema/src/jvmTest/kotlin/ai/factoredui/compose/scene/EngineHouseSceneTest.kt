package ai.factoredui.compose.scene

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun engineHouse(): SceneView {
    val text = EngineHouseSceneTest::class.java.getResource("/house-floor.scene.json")!!.readText()
    return readSceneView(text)
}

class EngineHouseSceneTest {
    @Test
    fun the_engine_house_reads_with_its_thirty_one_parts_on_a_21_by_24_grid() {
        val scene = engineHouse()
        assertEquals(31, scene.footprints!!.items.size)
        assertEquals(21 to 24, scene.cols to scene.rows)
        assertEquals((1..31).map { "e$it" }, scene.footprints!!.items.map { it.id })
    }

    @Test
    fun the_parts_are_twelve_piers_two_edge_beams_two_interior_beams_and_fifteen_panels() {
        val counts = engineHouse().footprints!!.items.groupingBy { it.type }.eachCount()
        assertEquals(mapOf("cast_pier" to 12, "edge_beam" to 2, "interior_beam" to 2, "sip_floor_panel" to 15), counts)
    }

    @Test
    fun piers_are_one_by_two_tiles_beams_run_all_21_tiles_and_panels_are_four_by_eight() {
        val sizes = engineHouse().footprints!!.items.groupBy({ it.type }, { it.width to it.height }).mapValues { it.value.toSet() }
        assertEquals(setOf(1 to 2), sizes["cast_pier"])
        assertEquals(setOf(21 to 1), sizes["edge_beam"])
        assertEquals(setOf(21 to 2), sizes["interior_beam"])
        assertEquals(setOf(4 to 8), sizes["sip_floor_panel"])
    }

    @Test
    fun nineteen_utilisation_figures_sit_on_the_four_beams_and_fifteen_panels_and_none_on_piers() {
        val scene = engineHouse()
        val figures = scene.annotations!!.items.filter { it.kind == "utilisation" }
        assertEquals(19, figures.size)
        assertTrue(figures.all { assertNotNull(it.value) in 0.0..1.0 })
        val typeOf = scene.footprints!!.items.associate { it.id to it.type }
        assertTrue(figures.none { typeOf[it.refId] == "cast_pier" })
    }

    @Test
    fun the_engine_house_written_and_read_back_is_the_scene_first_read() {
        val scene = engineHouse()
        assertEquals(scene, readSceneView(sceneViewJson(scene)))
    }

    @Test
    fun the_house_is_three_stack_levels_piers_on_the_ground_beams_on_the_piers_and_panels_on_the_beams() {
        val scene = engineHouse()
        assertEquals(listOf(StackLevel(0, 0, null), StackLevel(1, 361, null), StackLevel(2, 663, null)), scene.stackLevels)
        val levelOf = scene.footprints!!.items.groupBy({ it.type }, { Triple(it.stackLevel, it.zMm, it.heightMm) }).mapValues { it.value.toSet() }
        assertEquals(setOf(Triple(0, 0L, 361L)), levelOf["cast_pier"])
        assertEquals(setOf(Triple(1, 361L, 302L)), levelOf["edge_beam"])
        assertEquals(setOf(Triple(1, 361L, 302L)), levelOf["interior_beam"])
        assertEquals(setOf(Triple(2, 663L, 251L)), levelOf["sip_floor_panel"])
    }

    @Test
    fun a_pier_type_states_no_height_because_each_pier_is_cut_to_the_ground_and_carries_its_own() {
        val scene = engineHouse()
        assertNull(scene.types.first { it.id == "cast_pier" }.heightMm)
        assertTrue(scene.footprints!!.items.filter { it.type == "cast_pier" }.all { it.heightMm == 361L })
    }
}
