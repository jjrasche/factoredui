package ai.factoredui.compose.stackedscene

import ai.factoredui.compose.scene.SceneType
import ai.factoredui.compose.scene.SceneView
import ai.factoredui.compose.scene.StackLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val LEVELS = listOf(StackLevel(0, 0, "blocks"), StackLevel(1, 352, "bars"), StackLevel(2, 654, "plates"))

private fun elevationBoard() = stackScene(
    stackLevels = LEVELS,
    items = listOf(
        part("block-front", "block", col = 2, row = 2, level = 0, zMm = 0, heightMm = 276),
        part("block-back", "block", col = 2, row = 12, level = 0, zMm = 0, heightMm = 276),
        part("bar", "bar", col = 2, row = 2, width = 13, level = 1, zMm = 352, heightMm = 302),
        part("plate", "plate", col = 2, row = 2, width = 4, height = 5, level = 2, zMm = 654, heightMm = 260),
    ),
)

private fun blocksOf(scene: SceneView) = scene.footprints!!.items.filter { it.stackLevel == 0 }.map { shown(it) }

class ElevationViewsTest {
    @Test
    fun there_are_three_views_plan_from_the_south_and_from_the_west() {
        assertEquals(listOf(FlatView.PLAN, FlatView.SOUTH, FlatView.WEST), FlatView.entries)
        assertEquals(listOf("Plan", "From the south", "From the west"), FlatView.entries.map { it.label })
    }

    @Test
    fun the_plan_window_is_the_scene_window() = assertEquals(RectMm(0.0, 0.0, 9760.0, 8540.0), viewWindow(elevationBoard(), FlatView.PLAN))

    @Test
    fun an_elevation_window_runs_from_the_datum_to_a_little_above_the_highest_top() {
        val south = viewWindow(elevationBoard(), FlatView.SOUTH)
        assertEquals(0.0, south.x0)
        assertEquals(9760.0, south.x1)
        assertEquals(0.0, south.y0)
        assertTrue(south.y1 > 914 && south.y1 < 914 * 1.5, "top ${south.y1}")
    }

    @Test
    fun the_west_window_runs_along_the_scenes_south_north_extent() {
        val west = viewWindow(elevationBoard(), FlatView.WEST)
        assertEquals(0.0 to 8540.0, west.x0 to west.x1)
    }

    @Test
    fun an_elevation_window_reaches_below_the_datum_when_a_part_sits_below_it() {
        val board = elevationBoard()
        val layer = board.footprints!!
        val scene = board.copy(footprints = layer.copy(items = layer.items + part("footing", "block", col = 5, row = 5, zMm = -300, heightMm = 300)))
        assertEquals(-300.0, viewWindow(scene, FlatView.SOUTH).y0)
    }

    @Test
    fun from_the_south_a_part_spans_its_x_range_and_rises_from_its_own_z_for_its_height() =
        assertEquals(RectMm(1220.0, 352.0, 9150.0, 654.0), partRectInView(elevationBoard(), elevationBoard().footprint("bar"), FlatView.SOUTH))

    @Test
    fun a_part_with_no_height_of_its_own_takes_its_types_height() {
        val scene = stackScene(types = listOf(SceneType("block", "Block", heightMm = 400)), items = listOf(part("p", "block", zMm = 100)))
        assertEquals(400L, partHeightMm(scene, scene.footprint("p")))
        assertEquals(ElevationRange(100, 500), elevationRange(scene, scene.footprint("p")))
    }

    @Test
    fun a_parts_own_height_wins_over_its_types_height() {
        val scene = stackScene(types = listOf(SceneType("block", "Block", heightMm = 400)), items = listOf(part("p", "block", heightMm = 276)))
        assertEquals(276L, partHeightMm(scene, scene.footprint("p")))
    }

    @Test
    fun a_part_with_no_height_anywhere_is_flat_at_its_base_rather_than_given_an_invented_height() {
        val scene = stackScene(types = listOf(SceneType("block", "Block")), items = listOf(part("p", "block", zMm = 352)))
        assertNull(partHeightMm(scene, scene.footprint("p")))
        assertEquals(ElevationRange(352, 352), elevationRange(scene, scene.footprint("p")))
        assertEquals(RectMm(0.0, 352.0, 610.0, 352.0), partRectInView(scene, scene.footprint("p"), FlatView.SOUTH))
    }

    @Test
    fun from_the_west_north_is_on_the_left_and_south_on_the_right() {
        val scene = elevationBoard()
        val north = partRectInView(scene, scene.footprint("block-back"), FlatView.WEST)
        val south = partRectInView(scene, scene.footprint("block-front"), FlatView.WEST)
        assertTrue(north.x1 <= south.x0, "the northern block sits left of the southern one")
    }

    @Test
    fun the_plan_rectangle_is_the_tile_rectangle() =
        assertEquals(RectMm(1220.0, 1220.0, 1830.0, 1830.0), partRectInView(elevationBoard(), elevationBoard().footprint("block-front"), FlatView.PLAN))

    @Test
    fun from_the_south_the_far_parts_are_drawn_first_so_a_near_one_covers_it() {
        val scene = elevationBoard()
        assertEquals(listOf("block-back", "block-front"), orderForView(scene, blocksOf(scene), FlatView.SOUTH).map { it.footprint.id })
    }

    @Test
    fun within_one_depth_the_lower_level_is_drawn_before_the_higher() {
        val scene = elevationBoard()
        val parts = listOf("bar", "block-front").map { shown(scene.footprint(it)) }
        assertEquals(listOf("block-front", "bar"), orderForView(scene, parts, FlatView.SOUTH).map { it.footprint.id })
    }

    @Test
    fun the_plan_keeps_the_order_it_was_given() {
        val scene = elevationBoard()
        val parts = listOf("plate", "block-back").map { shown(scene.footprint(it)) }
        assertEquals(parts, orderForView(scene, parts, FlatView.PLAN))
    }

    @Test
    fun a_part_whose_centre_lies_under_a_later_part_is_flagged_covered() {
        val scene = elevationBoard()
        val parts = listOf("block-front", "bar").map { shown(scene.footprint(it)) }
        assertEquals(listOf(true, false), coveredFlags(scene, parts, FlatView.PLAN))
    }

    @Test
    fun from_the_south_a_block_behind_another_in_the_same_column_is_covered_by_it() {
        val scene = elevationBoard()
        val ordered = orderForView(scene, blocksOf(scene), FlatView.SOUTH)
        assertEquals(listOf("block-back", "block-front"), ordered.map { it.footprint.id })
        assertEquals(listOf(true, false), coveredFlags(scene, ordered, FlatView.SOUTH))
    }

    @Test
    fun from_the_west_the_same_two_blocks_are_side_by_side_and_neither_covers_the_other() {
        val scene = elevationBoard()
        assertEquals(listOf(false, false), coveredFlags(scene, orderForView(scene, blocksOf(scene), FlatView.WEST), FlatView.WEST))
    }

    @Test
    fun from_the_south_the_nearer_of_two_stacked_parts_is_hit() {
        val scene = elevationBoard()
        val ordered = orderForView(scene, blocksOf(scene), FlatView.SOUTH)
        assertEquals("block-front", hitPartInView(scene, ordered, 1500.0, 100.0, FlatView.SOUTH)?.footprint?.id)
    }

    @Test
    fun every_part_a_point_crosses_is_listed_nearest_first_and_the_nearest_is_the_one_a_tap_hits() {
        val scene = elevationBoard()
        val ordered = orderForView(scene, blocksOf(scene), FlatView.SOUTH)
        val crossed = hitPartsInView(scene, ordered, 1500.0, 100.0, FlatView.SOUTH).map { it.footprint.id }
        assertEquals("block-front", crossed.first())
        assertEquals(ordered.map { it.footprint.id }.filter { it in crossed }.asReversed(), crossed)
    }

    @Test
    fun a_point_that_crosses_nothing_lists_no_part() {
        val scene = elevationBoard()
        assertEquals(emptyList(), hitPartsInView(scene, orderForView(scene, blocksOf(scene), FlatView.SOUTH), -99999.0, -99999.0, FlatView.SOUTH))
    }

    @Test
    fun the_plan_is_not_exaggerated_and_an_elevation_is_stretched_so_its_height_can_be_read() {
        val scene = elevationBoard()
        assertEquals(1, exaggerationFor(scene, FlatView.PLAN, 800.0, 600.0, 20.0))
        val stretch = exaggerationFor(scene, FlatView.SOUTH, 800.0, 600.0, 20.0)
        assertTrue(stretch in 3..10, "stretch $stretch")
    }

    @Test
    fun a_scene_of_flat_parts_is_stretched_to_the_most_allowed_rather_than_dividing_by_zero() {
        val scene = stackScene(items = listOf(part("flat")))
        assertEquals(10, exaggerationFor(scene, FlatView.SOUTH, 800.0, 600.0, 20.0))
    }

    @Test
    fun floor_levels_list_each_stack_levels_floor_height_and_label_for_the_lines_on_an_elevation() =
        assertEquals(listOf(FloorLevel(0, "blocks", 0), FloorLevel(1, "bars", 352), FloorLevel(2, "plates", 654)), floorLevels(elevationBoard()))

    @Test
    fun a_scene_with_no_stack_levels_has_no_floor_lines() = assertEquals(emptyList(), floorLevels(stackScene()))
}
