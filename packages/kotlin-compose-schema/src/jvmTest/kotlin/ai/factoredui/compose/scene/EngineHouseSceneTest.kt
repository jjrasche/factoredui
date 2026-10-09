package ai.factoredui.compose.scene

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun engineHouse(): SceneView {
    val text = EngineHouseSceneTest::class.java.getResource("/house-site-sip.scene.json")!!.readText()
    return readSceneView(text)
}

class EngineHouseSceneTest {
    @Test
    fun the_engine_house_reads_with_its_twenty_six_parts_on_a_21_by_25_grid() {
        val scene = engineHouse()
        assertEquals(26, scene.footprints!!.items.size)
        assertEquals(21 to 25, scene.cols to scene.rows)
    }

    @Test
    fun the_parts_are_twelve_piers_eight_beam_segments_and_six_panels() {
        val counts = engineHouse().footprints!!.items.groupingBy { it.type }.eachCount()
        assertEquals(mapOf("cast_pier" to 12, "edge_beam" to 4, "interior_beam" to 4, "sip_floor_panel" to 6), counts)
    }

    @Test
    fun every_part_type_carries_a_price_and_none_carries_a_weight() {
        val types = engineHouse().types
        assertEquals(listOf(92.37, 62.0, 62.0, 629.68), listOf("cast_pier", "edge_beam", "interior_beam", "sip_floor_panel").map { id -> types.first { it.id == id }.unitCostUsd })
        assertTrue(types.all { it.unitWeightKg == null && it.unitWeightNullReason != null })
    }

    @Test
    fun fourteen_utilisation_figures_sit_on_beams_and_panels_and_none_on_piers() {
        val scene = engineHouse()
        val figures = scene.annotations!!.items.filter { it.kind == "utilisation" }
        assertEquals(14, figures.size)
        assertTrue(figures.all { assertNotNull(it.value) in 0.0..1.0 })
        val typeOf = scene.footprints!!.items.associate { it.id to it.type }
        assertTrue(figures.none { typeOf[it.refId] == "cast_pier" })
    }

    @Test
    fun the_house_is_one_stack_level_standing_on_the_ground() {
        val scene = engineHouse()
        assertEquals(listOf(StackLevel(0, 0, null)), scene.stackLevels)
        assertTrue(scene.footprints!!.items.all { it.stackLevel == 0 && it.zMm == 0L })
        assertNull(scene.types.first { it.id == "cast_pier" }.heightMm)
    }
}
