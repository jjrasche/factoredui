package ai.factoredui.compose.stackedscene

import ai.factoredui.compose.scene.SceneFootprint
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val COS = cos(ISO_ELEVATION_DEG * PI / 180)
private val SIN = sin(ISO_ELEVATION_DEG * PI / 180)

private fun assertClose(expected: Double, actual: Double, label: String, tolerance: Double = 1e-6) =
    assertTrue(abs(actual - expected) < tolerance, "$label: $actual vs $expected")

private fun isoOrderOf(vararg items: SceneFootprint): List<String> =
    orderForIso(stackScene(items = items.toList()), items.map { shown(it) }).map { it.footprint.id }

private fun isBehind(a: PartBox, b: PartBox) = a.x0 >= b.x1 || a.y0 >= b.y1 || a.z1 <= b.z0

class IsoTest {
    @Test
    fun the_true_isometric_elevation_is_35_264_degrees() = assertClose(35.264, ISO_ELEVATION_DEG, "elevation", 1e-3)

    @Test
    fun the_origin_projects_to_the_origin() {
        val spot = isoProject(0.0, 0.0, 0.0)
        assertClose(0.0, spot.u, "u")
        assertClose(0.0, spot.v, "v")
        assertClose(0.0, spot.depth, "depth")
    }

    @Test
    fun a_point_to_the_east_goes_right_and_up_the_screen_and_a_point_to_the_north_goes_left_and_up() {
        val east = isoProject(1000.0, 0.0, 0.0)
        val north = isoProject(0.0, 1000.0, 0.0)
        assertClose(707.1068, east.u, "east u", 1e-3)
        assertClose(707.1068 * SIN, east.v, "east v", 1e-3)
        assertClose(-707.1068, north.u, "north u", 1e-3)
        assertClose(707.1068 * SIN, north.v, "north v", 1e-3)
    }

    @Test
    fun height_moves_a_point_straight_up_the_screen_by_the_cosine_of_the_elevation() {
        val base = isoProject(500.0, 500.0, 0.0)
        val raised = isoProject(500.0, 500.0, 1000.0)
        assertClose(base.u, raised.u, "u")
        assertClose(1000 * COS, raised.v - base.v, "v")
    }

    @Test
    fun a_parts_box_is_its_tile_rectangle_from_its_own_base_to_its_own_top() =
        assertEquals(PartBox(1220.0, 1220.0, 1830.0, 1830.0, 0.0, 276.0), partBox(stackScene(), part("p", col = 2, row = 2, heightMm = 276)))

    @Test
    fun a_part_with_no_height_has_a_flat_box() = assertEquals(PartBox(0.0, 0.0, 610.0, 610.0, 352.0, 352.0), partBox(stackScene(), part("p", zMm = 352)))

    @Test
    fun a_box_nearer_the_viewer_with_smaller_x_is_drawn_after_one_farther_away() =
        assertEquals(listOf("far", "near"), isoOrderOf(part("near", heightMm = 100), part("far", col = 5, heightMm = 100)))

    @Test
    fun a_box_with_smaller_y_is_nearer_too() =
        assertEquals(listOf("far", "near"), isoOrderOf(part("far", row = 5, heightMm = 100), part("near", heightMm = 100)))

    @Test
    fun a_higher_box_is_drawn_after_the_lower_one_it_rests_on() =
        assertEquals(listOf("pier", "beam"), isoOrderOf(part("beam", col = 2, row = 2, width = 13, level = 1, zMm = 352, heightMm = 302), part("pier", col = 2, row = 2, heightMm = 276)))

    @Test
    fun boxes_in_a_stack_of_three_bays_come_out_so_that_every_nearer_box_follows_every_farther_one_it_hides() {
        val items = mutableListOf<SceneFootprint>()
        listOf(2, 7, 12).forEachIndexed { bay, row ->
            listOf(2, 6, 10, 14).forEachIndexed { column, col -> items += part("pier-$bay-$column", col = col, row = row, heightMm = 276) }
            items += part("beam-$bay", col = 2, row = row, width = 13, level = 1, zMm = 352, heightMm = 302)
        }
        listOf(2, 7).forEachIndexed { bay, row ->
            listOf(2, 6, 10).forEachIndexed { column, col -> items += part("panel-$bay-$column", col = col, row = row, width = 4, height = 5, level = 2, zMm = 654, heightMm = 260) }
        }
        val scene = stackScene(items = items)
        val position = orderForIso(scene, items.reversed().map { shown(it) }).mapIndexed { index, part -> part.footprint.id to index }.toMap()
        assertEquals(items.size, position.size)
        items.forEach { a ->
            items.filter { it !== a }.forEach { b ->
                val boxA = partBox(scene, a)
                val boxB = partBox(scene, b)
                if (isBehind(boxA, boxB) && !isBehind(boxB, boxA)) assertTrue(position.getValue(a.id) < position.getValue(b.id), "${a.id} must be drawn before ${b.id}")
            }
        }
    }

    @Test
    fun the_same_input_in_the_same_order_always_gives_the_same_drawing_order() {
        val items = arrayOf(part("a", heightMm = 10), part("b", col = 5, row = 5, heightMm = 10), part("c", col = 9, heightMm = 10))
        assertEquals(isoOrderOf(*items), isoOrderOf(*items))
    }

    @Test
    fun two_boxes_that_overlap_in_space_have_no_separating_axis_and_still_come_out_lower_first() =
        assertEquals(listOf("low", "high"), isoOrderOf(part("high", col = 2, row = 2, width = 2, height = 2, level = 1, zMm = 300, heightMm = 500), part("low", col = 2, row = 2, width = 2, height = 2, heightMm = 500)))

    @Test
    fun parts_that_cannot_be_ordered_keep_the_order_they_were_given() =
        assertEquals(listOf("second", "first"), isoOrderOf(part("second", col = 4), part("first", col = 0)))

    @Test
    fun a_box_shows_three_faces_top_west_and_south_each_a_four_cornered_polygon() {
        val faces = projectFaces(PartBox(0.0, 0.0, 1000.0, 1000.0, 0.0, 500.0))
        listOf(faces.top, faces.west, faces.south).forEach { assertEquals(4, it.size) }
    }

    @Test
    fun each_pair_of_faces_shares_exactly_one_edge_two_corners() {
        val faces = projectFaces(PartBox(0.0, 0.0, 1000.0, 1000.0, 0.0, 500.0))
        fun shared(a: List<Pair<Double, Double>>, b: List<Pair<Double, Double>>) =
            a.count { point -> b.any { abs(it.first - point.first) < 1e-9 && abs(it.second - point.second) < 1e-9 } }
        assertEquals(2, shared(faces.top, faces.south))
        assertEquals(2, shared(faces.top, faces.west))
        assertEquals(2, shared(faces.west, faces.south))
    }

    @Test
    fun the_south_face_hangs_below_the_top_faces_near_edge() {
        val faces = projectFaces(PartBox(0.0, 0.0, 1000.0, 1000.0, 0.0, 500.0))
        assertTrue(faces.south.minOf { it.second } < faces.top.minOf { it.second })
    }

    @Test
    fun the_window_covers_every_corner_of_every_part_whatever_is_currently_shown() {
        val items = listOf(part("a", heightMm = 100), part("b", col = 10, row = 10, width = 2, height = 2, level = 1, zMm = 400, heightMm = 300))
        val scene = stackScene(items = items)
        val bounds = isoWindow(scene)
        items.map { partBox(scene, it) }.forEach { box ->
            listOf(box.x0, box.x1).forEach { x ->
                listOf(box.y0, box.y1).forEach { y ->
                    listOf(box.z0, box.z1).forEach { z ->
                        val spot = isoProject(x, y, z)
                        assertTrue(spot.u >= bounds.x0 - 1e-6 && spot.u <= bounds.x1 + 1e-6 && spot.v >= bounds.y0 - 1e-6 && spot.v <= bounds.y1 + 1e-6)
                    }
                }
            }
        }
    }

    @Test
    fun a_scene_with_no_parts_frames_its_own_window() {
        val bounds = isoWindow(stackScene())
        val farCorner = isoProject(9760.0, 0.0, 0.0)
        assertTrue(bounds.x1 > farCorner.u)
    }

    @Test
    fun a_point_inside_a_polygon_is_found_and_a_point_outside_is_not() {
        val square = listOf(0.0 to 0.0, 10.0 to 0.0, 10.0 to 10.0, 0.0 to 10.0)
        assertTrue(isPointInPolygon(5.0, 5.0, square))
        assertFalse(isPointInPolygon(15.0, 5.0, square))
        assertFalse(isPointInPolygon(5.0, -1.0, square))
    }

    @Test
    fun tapping_where_two_boxes_overlap_on_the_screen_picks_the_one_drawn_last_the_nearer() {
        val near = part("near", width = 2, height = 2, heightMm = 1000)
        val far = part("far", col = 1, row = 1, width = 2, height = 2, heightMm = 1000)
        val scene = stackScene(items = listOf(near, far))
        val ordered = orderForIso(scene, listOf(shown(near), shown(far)))
        val nearTop = projectFaces(partBox(scene, near)).top
        val centre = nearTop.sumOf { it.first } / 4 to nearTop.sumOf { it.second } / 4
        assertEquals("near", hitIso(scene, ordered, centre.first, centre.second)?.footprint?.id)
    }

    @Test
    fun tapping_empty_screen_picks_nothing() {
        val only = part("only", heightMm = 100)
        assertNull(hitIso(stackScene(items = listOf(only)), listOf(shown(only)), 99999.0, 99999.0))
    }
}
