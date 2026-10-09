package ai.factoredui.compose.structure

import ai.factoredui.compose.scene.SceneType
import ai.factoredui.compose.scene.SceneView
import ai.factoredui.compose.scene.StackLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val LEVELS = listOf(StackLevel(0, 0, "piers"), StackLevel(1, 352, "beams"), StackLevel(2, 654, "panels"))

private fun elevationHouse() = houseScene(
    stackLevels = LEVELS,
    items = listOf(
        part("pier-front", "pier", col = 2, row = 2, level = 0, zMm = 0, heightMm = 276),
        part("pier-back", "pier", col = 2, row = 12, level = 0, zMm = 0, heightMm = 276),
        part("beam", "beam", col = 2, row = 2, width = 13, level = 1, zMm = 352, heightMm = 302),
        part("panel", "panel", col = 2, row = 2, width = 4, height = 5, level = 2, zMm = 654, heightMm = 260),
    ),
)

private fun piersOf(scene: SceneView) = scene.footprints!!.items.filter { it.stackLevel == 0 }.map { shown(it) }

class ElevationViewsTest {
    @Test
    fun there_are_three_views_plan_from_the_south_and_from_the_west() {
        assertEquals(listOf(StructureView.PLAN, StructureView.SOUTH, StructureView.WEST), StructureView.entries)
        assertEquals(listOf("Plan", "From the south", "From the west"), StructureView.entries.map { it.label })
    }

    @Test
    fun the_plan_window_is_the_scene_window() = assertEquals(RectMm(0.0, 0.0, 9760.0, 8540.0), viewWindow(elevationHouse(), StructureView.PLAN))

    @Test
    fun an_elevation_window_runs_from_the_datum_to_a_little_above_the_highest_top() {
        val south = viewWindow(elevationHouse(), StructureView.SOUTH)
        assertEquals(0.0, south.x0)
        assertEquals(9760.0, south.x1)
        assertEquals(0.0, south.y0)
        assertTrue(south.y1 > 914 && south.y1 < 914 * 1.5, "top ${south.y1}")
    }

    @Test
    fun the_west_window_runs_along_the_scenes_south_north_extent() {
        val west = viewWindow(elevationHouse(), StructureView.WEST)
        assertEquals(0.0 to 8540.0, west.x0 to west.x1)
    }

    @Test
    fun an_elevation_window_reaches_below_the_datum_when_a_part_sits_below_it() {
        val house = elevationHouse()
        val layer = house.footprints!!
        val scene = house.copy(footprints = layer.copy(items = layer.items + part("footing", "pier", col = 5, row = 5, zMm = -300, heightMm = 300)))
        assertEquals(-300.0, viewWindow(scene, StructureView.SOUTH).y0)
    }

    @Test
    fun from_the_south_a_part_spans_its_x_range_and_rises_from_its_own_z_for_its_height() =
        assertEquals(RectMm(1220.0, 352.0, 9150.0, 654.0), partRectInView(elevationHouse(), elevationHouse().footprint("beam"), StructureView.SOUTH))

    @Test
    fun a_part_with_no_height_of_its_own_takes_its_types_height() {
        val scene = houseScene(types = listOf(SceneType("pier", "Pier", heightMm = 400)), items = listOf(part("p", "pier", zMm = 100)))
        assertEquals(400L, partHeightMm(scene, scene.footprint("p")))
        assertEquals(ElevationRange(100, 500), elevationRange(scene, scene.footprint("p")))
    }

    @Test
    fun a_parts_own_height_wins_over_its_types_height() {
        val scene = houseScene(types = listOf(SceneType("pier", "Pier", heightMm = 400)), items = listOf(part("p", "pier", heightMm = 276)))
        assertEquals(276L, partHeightMm(scene, scene.footprint("p")))
    }

    @Test
    fun a_part_with_no_height_anywhere_is_flat_at_its_base_rather_than_given_an_invented_height() {
        val scene = houseScene(types = listOf(SceneType("pier", "Pier")), items = listOf(part("p", "pier", zMm = 352)))
        assertNull(partHeightMm(scene, scene.footprint("p")))
        assertEquals(ElevationRange(352, 352), elevationRange(scene, scene.footprint("p")))
        assertEquals(RectMm(0.0, 352.0, 610.0, 352.0), partRectInView(scene, scene.footprint("p"), StructureView.SOUTH))
    }

    @Test
    fun from_the_west_north_is_on_the_left_and_south_on_the_right() {
        val scene = elevationHouse()
        val north = partRectInView(scene, scene.footprint("pier-back"), StructureView.WEST)
        val south = partRectInView(scene, scene.footprint("pier-front"), StructureView.WEST)
        assertTrue(north.x1 <= south.x0, "the northern pier sits left of the southern one")
    }

    @Test
    fun the_plan_rectangle_is_the_tile_rectangle() =
        assertEquals(RectMm(1220.0, 1220.0, 1830.0, 1830.0), partRectInView(elevationHouse(), elevationHouse().footprint("pier-front"), StructureView.PLAN))

    @Test
    fun from_the_south_the_far_parts_are_drawn_first_so_a_near_one_covers_it() {
        val scene = elevationHouse()
        assertEquals(listOf("pier-back", "pier-front"), orderForView(scene, piersOf(scene), StructureView.SOUTH).map { it.footprint.id })
    }

    @Test
    fun within_one_depth_the_lower_level_is_drawn_before_the_higher() {
        val scene = elevationHouse()
        val parts = listOf("beam", "pier-front").map { shown(scene.footprint(it)) }
        assertEquals(listOf("pier-front", "beam"), orderForView(scene, parts, StructureView.SOUTH).map { it.footprint.id })
    }

    @Test
    fun the_plan_keeps_the_order_it_was_given() {
        val scene = elevationHouse()
        val parts = listOf("panel", "pier-back").map { shown(scene.footprint(it)) }
        assertEquals(parts, orderForView(scene, parts, StructureView.PLAN))
    }

    @Test
    fun a_part_whose_centre_lies_under_a_later_part_is_flagged_covered() {
        val scene = elevationHouse()
        val parts = listOf("pier-front", "beam").map { shown(scene.footprint(it)) }
        assertEquals(listOf(true, false), coveredFlags(scene, parts, StructureView.PLAN))
    }

    @Test
    fun from_the_south_a_pier_behind_another_in_the_same_column_is_covered_by_it() {
        val scene = elevationHouse()
        val ordered = orderForView(scene, piersOf(scene), StructureView.SOUTH)
        assertEquals(listOf("pier-back", "pier-front"), ordered.map { it.footprint.id })
        assertEquals(listOf(true, false), coveredFlags(scene, ordered, StructureView.SOUTH))
    }

    @Test
    fun from_the_west_the_same_two_piers_are_side_by_side_and_neither_covers_the_other() {
        val scene = elevationHouse()
        assertEquals(listOf(false, false), coveredFlags(scene, orderForView(scene, piersOf(scene), StructureView.WEST), StructureView.WEST))
    }

    @Test
    fun from_the_south_the_nearer_of_two_stacked_parts_is_hit() {
        val scene = elevationHouse()
        val ordered = orderForView(scene, piersOf(scene), StructureView.SOUTH)
        assertEquals("pier-front", hitPartInView(scene, ordered, 1500.0, 100.0, StructureView.SOUTH)?.footprint?.id)
    }

    @Test
    fun the_plan_is_not_exaggerated_and_an_elevation_is_stretched_so_its_height_can_be_read() {
        val scene = elevationHouse()
        assertEquals(1, exaggerationFor(scene, StructureView.PLAN, 800.0, 600.0, 20.0))
        val stretch = exaggerationFor(scene, StructureView.SOUTH, 800.0, 600.0, 20.0)
        assertTrue(stretch in 3..10, "stretch $stretch")
    }

    @Test
    fun a_scene_of_flat_parts_is_stretched_to_the_most_allowed_rather_than_dividing_by_zero() {
        val scene = houseScene(items = listOf(part("flat")))
        assertEquals(10, exaggerationFor(scene, StructureView.SOUTH, 800.0, 600.0, 20.0))
    }

    @Test
    fun floor_levels_list_each_stack_levels_floor_height_and_label_for_the_lines_on_an_elevation() =
        assertEquals(listOf(FloorLevel(0, "piers", 0), FloorLevel(1, "beams", 352), FloorLevel(2, "panels", 654)), floorLevels(elevationHouse()))

    @Test
    fun a_scene_with_no_stack_levels_has_no_floor_lines() = assertEquals(emptyList(), floorLevels(houseScene()))
}
