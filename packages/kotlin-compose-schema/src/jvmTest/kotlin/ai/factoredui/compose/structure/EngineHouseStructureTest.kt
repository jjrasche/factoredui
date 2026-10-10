package ai.factoredui.compose.structure

import ai.factoredui.compose.scene.SceneView
import ai.factoredui.compose.scene.readSceneView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private fun engineHouse(): SceneView =
    readSceneView(EngineHouseStructureTest::class.java.getResource("/house-floor.scene.json")!!.readText())

private fun everyPart(scene: SceneView) = visibleParts(scene, LevelFocus.AllLevels, showLower = true)

class EngineHouseStructureTest {
    @Test
    fun nineteen_engine_house_parts_carry_a_utilisation_figure_and_no_pier_does() {
        val scene = engineHouse()
        val figures = utilisationByPart(scene)
        assertEquals(19, figures.size)
        val typeOf = scene.footprints!!.items.associate { it.id to it.type }
        assertTrue(figures.keys.none { typeOf[it] == "cast_pier" })
    }

    @Test
    fun the_engine_house_has_three_levels() {
        assertEquals(listOf(0, 1, 2), levelsPresent(engineHouse()))
    }

    @Test
    fun the_beam_level_holds_two_edge_and_two_interior_beams_so_it_is_named_for_both() {
        val scene = engineHouse()
        assertEquals(
            listOf("Cast pier in a form tube", "Edge LVL beam and Interior 2-ply LVL beam", "Site-built SIP floor panel"),
            listOf(0, 1, 2).map { levelName(scene, it) },
        )
    }

    @Test
    fun hiding_top_levels_peels_the_panels_then_the_beams_and_a_focus_shows_one_level_or_it_and_those_below() {
        val scene = engineHouse()
        fun count(focus: LevelFocus, showLower: Boolean, hideTop: Int) = visibleParts(scene, focus, showLower, hideTop).size
        assertEquals(31, count(LevelFocus.AllLevels, true, 0))
        assertEquals(16, count(LevelFocus.AllLevels, true, 1))
        assertEquals(12, count(LevelFocus.AllLevels, true, 2))
        assertEquals(4, count(LevelFocus.OneLevel(1), false, 0))
        assertEquals(16, count(LevelFocus.OneLevel(1), true, 0))
        assertEquals(12, visibleParts(scene, LevelFocus.OneLevel(1), true, 0).count { it.isDimmed })
    }

    @Test
    fun every_part_stands_at_its_own_z_with_its_own_height_and_the_floor_tops_out_at_914_mm() {
        val scene = engineHouse()
        val boxes = scene.footprints!!.items.associate { it.id to partBox(scene, it) }
        assertEquals(0.0 to 361.0, boxes.getValue("e1").let { it.z0 to it.z1 })
        assertEquals(361.0 to 663.0, boxes.getValue("e13").let { it.z0 to it.z1 })
        assertEquals(361.0 to 663.0, boxes.getValue("e14").let { it.z0 to it.z1 })
        assertEquals(663.0 to 914.0, boxes.getValue("e17").let { it.z0 to it.z1 })
        assertEquals(914.0, boxes.values.maxOf { it.z1 })
        assertFalse(isEveryPartFlat(scene, everyPart(scene)))
    }

    @Test
    fun a_pier_is_one_tile_by_two_and_a_beam_runs_the_whole_6405_mm_width() {
        val scene = engineHouse()
        val pier = partRectMm(scene, scene.footprints!!.items.first { it.id == "e5" })
        assertEquals(RectMm(3050.0, 2135.0, 3355.0, 2745.0), pier)
        val beam = partRectMm(scene, scene.footprints!!.items.first { it.id == "e14" })
        assertEquals(RectMm(0.0, 2135.0, 6405.0, 2745.0), beam)
    }

    @Test
    fun the_iso_order_of_the_engine_house_draws_every_part_exactly_once() {
        val scene = engineHouse()
        val ordered = orderForIso(scene, everyPart(scene)).map { it.footprint.id }
        assertEquals(scene.footprints!!.items.map { it.id }.sorted(), ordered.sorted())
    }

    @Test
    fun in_iso_every_part_is_painted_after_each_part_it_stands_on() {
        val scene = engineHouse()
        val ordered = orderForIso(scene, everyPart(scene)).map { it.footprint }
        val position = ordered.withIndex().associate { it.value.id to it.index }
        val boxes = ordered.associate { it.id to partBox(scene, it) }
        val standsOn = ordered.flatMap { upper ->
            ordered.filter { lower ->
                val a = boxes.getValue(lower.id)
                val b = boxes.getValue(upper.id)
                a.z1 == b.z0 && a.x0 < b.x1 && b.x0 < a.x1 && a.y0 < b.y1 && b.y0 < a.y1
            }.map { it.id to upper.id }
        }
        val beams = setOf("e13", "e14", "e15", "e16")
        assertEquals(12, standsOn.count { (_, upper) -> upper in beams }, "piers under beams: $standsOn")
        assertEquals(30, standsOn.count { (lower, _) -> lower in beams }, "beams under panels: $standsOn")
        standsOn.forEach { (lower, upper) -> assertTrue(position.getValue(lower) < position.getValue(upper), "$lower is painted after $upper, which stands on it") }
    }
}
