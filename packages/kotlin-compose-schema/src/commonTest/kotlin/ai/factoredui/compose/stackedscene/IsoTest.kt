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

private val ASPECTS = listOf(0, 1, 2, 3)

private fun assertClose(expected: Double, actual: Double, label: String, tolerance: Double = 1e-6) =
    assertTrue(abs(actual - expected) < tolerance, "$label: $actual vs $expected")

private fun isoOrderOf(vararg items: SceneFootprint, quarterTurns: Int = 0): List<String> =
    orderForIso(stackScene(items = items.toList()), items.map { shown(it) }, quarterTurns).map { it.footprint.id }

private fun isBehind(a: PartBox, b: PartBox) = a.x0 >= b.x1 || a.y0 >= b.y1 || a.z1 <= b.z0

private fun projected(quarterTurns: Int, vararg corners: Triple<Double, Double, Double>): List<Pair<Double, Double>> =
    corners.map { (x, y, z) -> isoProject(x, y, z, quarterTurns).let { it.u to it.v } }

private fun threeBays(): List<SceneFootprint> {
    val items = mutableListOf<SceneFootprint>()
    listOf(2, 7, 12).forEachIndexed { bay, row ->
        listOf(2, 6, 10, 14).forEachIndexed { column, col -> items += part("block-$bay-$column", col = col, row = row, heightMm = 276) }
        items += part("bar-$bay", col = 2, row = row, width = 13, level = 1, zMm = 352, heightMm = 302)
    }
    listOf(2, 7).forEachIndexed { bay, row ->
        listOf(2, 6, 10).forEachIndexed { column, col -> items += part("plate-$bay-$column", col = col, row = row, width = 4, height = 5, level = 2, zMm = 654, heightMm = 260) }
    }
    return items
}

class IsoTest {
    @Test
    fun the_true_isometric_elevation_is_35_264_degrees_and_the_first_aspect_looks_from_45_degrees() {
        assertClose(35.264, ISO_ELEVATION_DEG, "elevation", 1e-3)
        assertEquals(45.0, isoAzimuthDeg(0))
    }

    @Test
    fun each_quarter_turn_adds_90_degrees_to_the_azimuth_and_four_come_back_round() {
        assertEquals(listOf(45.0, 135.0, 225.0, 315.0), ASPECTS.map(::isoAzimuthDeg))
        assertEquals(0, aspectOf(4))
        assertEquals(3, aspectOf(-1))
        assertEquals(2, aspectOf(-6))
        assertEquals(isoProject(100.0, 200.0, 300.0, 0), isoProject(100.0, 200.0, 300.0, 4))
        assertEquals(isoProject(100.0, 200.0, 300.0, 3), isoProject(100.0, 200.0, 300.0, -1))
    }

    @Test
    fun a_horizontal_drag_steps_one_aspect_in_its_direction_once_it_reaches_the_threshold() {
        assertEquals(48.0, QUARTER_TURN_DRAG_DP)
        assertEquals(1, aspectAfterDrag(0, 48.0))
        assertEquals(1, aspectAfterDrag(0, 400.0))
        assertEquals(3, aspectAfterDrag(0, -60.0))
        assertEquals(0, aspectAfterDrag(3, 60.0))
        assertNull(aspectAfterDrag(2, 47.0))
        assertNull(aspectAfterDrag(2, -47.0))
    }

    @Test
    fun the_origin_projects_to_the_origin() {
        val spot = isoProject(0.0, 0.0, 0.0, 0)
        assertClose(0.0, spot.u, "u")
        assertClose(0.0, spot.v, "v")
        assertClose(0.0, spot.depth, "depth")
    }

    @Test
    fun a_point_to_the_east_goes_right_and_up_the_screen_and_a_point_to_the_north_goes_left_and_up() {
        val east = isoProject(1000.0, 0.0, 0.0, 0)
        val north = isoProject(0.0, 1000.0, 0.0, 0)
        assertClose(707.1068, east.u, "east u", 1e-3)
        assertClose(707.1068 * SIN, east.v, "east v", 1e-3)
        assertClose(-707.1068, north.u, "north u", 1e-3)
        assertClose(707.1068 * SIN, north.v, "north v", 1e-3)
    }

    @Test
    fun one_quarter_turn_moves_a_point_where_axes_turned_to_135_degrees_put_it() {
        val turned = isoProject(1000.0, 0.0, 0.0, 1)
        assertClose(1000 * cos(135 * PI / 180), turned.u, "u")
        assertClose(1000 * sin(135 * PI / 180), turned.depth, "depth")
        assertClose(1000 * sin(135 * PI / 180) * SIN, turned.v, "v")
    }

    @Test
    fun height_moves_a_point_straight_up_the_screen_by_the_cosine_of_the_elevation_at_every_aspect() = ASPECTS.forEach { turns ->
        val base = isoProject(500.0, 500.0, 0.0, turns)
        val raised = isoProject(500.0, 500.0, 1000.0, turns)
        assertClose(base.u, raised.u, "u at $turns")
        assertClose(1000 * COS, raised.v - base.v, "v at $turns")
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
    fun at_each_aspect_the_box_on_the_cameras_side_of_x_is_drawn_last() {
        val expected = mapOf(0 to listOf("high_x", "low_x"), 1 to listOf("high_x", "low_x"), 2 to listOf("low_x", "high_x"), 3 to listOf("low_x", "high_x"))
        ASPECTS.forEach { turns ->
            assertEquals(expected.getValue(turns), isoOrderOf(part("low_x", heightMm = 100), part("high_x", col = 5, heightMm = 100), quarterTurns = turns), "quarter turns $turns")
        }
    }

    @Test
    fun at_each_aspect_the_box_on_the_cameras_side_of_y_is_drawn_last() {
        val expected = mapOf(0 to listOf("high_y", "low_y"), 1 to listOf("low_y", "high_y"), 2 to listOf("low_y", "high_y"), 3 to listOf("high_y", "low_y"))
        ASPECTS.forEach { turns ->
            assertEquals(expected.getValue(turns), isoOrderOf(part("low_y", heightMm = 100), part("high_y", row = 5, heightMm = 100), quarterTurns = turns), "quarter turns $turns")
        }
    }

    @Test
    fun a_higher_box_is_drawn_after_the_lower_one_it_rests_on_at_every_aspect() = ASPECTS.forEach { turns ->
        assertEquals(listOf("block", "bar"), isoOrderOf(part("bar", col = 2, row = 2, width = 13, level = 1, zMm = 352, heightMm = 302), part("block", col = 2, row = 2, heightMm = 276), quarterTurns = turns), "quarter turns $turns")
    }

    @Test
    fun boxes_in_a_stack_of_three_bays_come_out_so_that_every_nearer_box_follows_every_farther_one_it_hides() {
        val items = threeBays()
        val scene = stackScene(items = items)
        val position = orderForIso(scene, items.reversed().map { shown(it) }, 0).mapIndexed { index, part -> part.footprint.id to index }.toMap()
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
    fun two_quarter_turns_round_the_three_bays_come_out_with_every_box_nearer_the_turned_camera_following_the_one_it_hides() {
        val items = threeBays()
        val scene = stackScene(items = items)
        val mirrored = { box: PartBox -> PartBox(-box.x1, -box.y1, -box.x0, -box.y0, box.z0, box.z1) }
        val position = orderForIso(scene, items.map { shown(it) }, 2).mapIndexed { index, part -> part.footprint.id to index }.toMap()
        items.forEach { a ->
            items.filter { it !== a }.forEach { b ->
                val boxA = mirrored(partBox(scene, a))
                val boxB = mirrored(partBox(scene, b))
                if (isBehind(boxA, boxB) && !isBehind(boxB, boxA)) assertTrue(position.getValue(a.id) < position.getValue(b.id), "${a.id} must be drawn before ${b.id} two quarter turns round")
            }
        }
    }

    @Test
    fun three_boxes_that_each_must_be_drawn_before_the_next_fall_back_to_the_order_they_were_given_and_are_each_drawn_once() {
        val far = part("far", col = 16, row = 6, width = 2, height = 2, zMm = 500, heightMm = 1000)
        val middle = part("middle", col = 6, row = 6, width = 2, height = 2, heightMm = 1000)
        val long = part("long", col = 0, row = 0, width = 17, height = 7, heightMm = 1000)
        assertEquals(listOf("far", "middle", "long"), isoOrderOf(far, middle, long))
        assertEquals(listOf("long", "far", "middle"), isoOrderOf(long, far, middle))
        ASPECTS.forEach { turns ->
            val order = isoOrderOf(far, middle, long, quarterTurns = turns)
            assertEquals(3, order.size, "quarter turns $turns")
            assertEquals(setOf("far", "middle", "long"), order.toSet(), "quarter turns $turns")
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
    fun a_box_shows_three_faces_top_and_one_across_x_and_one_across_y_each_a_four_cornered_polygon() {
        val faces = projectFaces(PartBox(0.0, 0.0, 1000.0, 1000.0, 0.0, 500.0), 0)
        listOf(faces.top, faces.xSide, faces.ySide).forEach { assertEquals(4, it.size) }
    }

    @Test
    fun each_pair_of_faces_shares_exactly_one_edge_two_corners_at_every_aspect() = ASPECTS.forEach { turns ->
        val faces = projectFaces(PartBox(0.0, 0.0, 1000.0, 1000.0, 0.0, 500.0), turns)
        fun shared(a: List<Pair<Double, Double>>, b: List<Pair<Double, Double>>) =
            a.count { point -> b.any { abs(it.first - point.first) < 1e-9 && abs(it.second - point.second) < 1e-9 } }
        assertEquals(2, shared(faces.top, faces.ySide), "quarter turns $turns")
        assertEquals(2, shared(faces.top, faces.xSide), "quarter turns $turns")
        assertEquals(2, shared(faces.xSide, faces.ySide), "quarter turns $turns")
    }

    @Test
    fun the_side_faces_drawn_are_the_two_that_face_the_camera_at_each_aspect() {
        val box = PartBox(0.0, 0.0, 1000.0, 2000.0, 0.0, 500.0)
        val facedX = mapOf(0 to 0.0, 1 to 0.0, 2 to 1000.0, 3 to 1000.0)
        val facedY = mapOf(0 to 0.0, 1 to 2000.0, 2 to 2000.0, 3 to 0.0)
        ASPECTS.forEach { turns ->
            val faces = projectFaces(box, turns)
            val x = facedX.getValue(turns)
            val y = facedY.getValue(turns)
            assertEquals(projected(turns, Triple(x, 0.0, 0.0), Triple(x, 2000.0, 0.0), Triple(x, 2000.0, 500.0), Triple(x, 0.0, 500.0)), faces.xSide, "x face at $turns")
            assertEquals(projected(turns, Triple(0.0, y, 0.0), Triple(1000.0, y, 0.0), Triple(1000.0, y, 500.0), Triple(0.0, y, 500.0)), faces.ySide, "y face at $turns")
        }
    }

    @Test
    fun the_side_faces_hang_below_the_top_faces_near_edge_at_every_aspect() = ASPECTS.forEach { turns ->
        val faces = projectFaces(PartBox(0.0, 0.0, 1000.0, 1000.0, 0.0, 500.0), turns)
        assertTrue(faces.ySide.minOf { it.second } < faces.top.minOf { it.second }, "y face at $turns")
        assertTrue(faces.xSide.minOf { it.second } < faces.top.minOf { it.second }, "x face at $turns")
    }

    @Test
    fun the_window_covers_every_corner_of_every_part_whatever_is_currently_shown_at_every_aspect() {
        val items = listOf(part("a", heightMm = 100), part("b", col = 10, row = 10, width = 2, height = 2, level = 1, zMm = 400, heightMm = 300))
        val scene = stackScene(items = items)
        ASPECTS.forEach { turns ->
            val bounds = isoWindow(scene, turns)
            items.map { partBox(scene, it) }.forEach { box ->
                listOf(box.x0, box.x1).forEach { x ->
                    listOf(box.y0, box.y1).forEach { y ->
                        listOf(box.z0, box.z1).forEach { z ->
                            val spot = isoProject(x, y, z, turns)
                            assertTrue(spot.u >= bounds.x0 - 1e-6 && spot.u <= bounds.x1 + 1e-6 && spot.v >= bounds.y0 - 1e-6 && spot.v <= bounds.y1 + 1e-6, "quarter turns $turns")
                        }
                    }
                }
            }
        }
    }

    @Test
    fun a_scene_with_no_parts_frames_its_own_window() {
        val bounds = isoWindow(stackScene(), 0)
        val farCorner = isoProject(9760.0, 0.0, 0.0, 0)
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
        val ordered = orderForIso(scene, listOf(shown(near), shown(far)), 0)
        val nearTop = projectFaces(partBox(scene, near), 0).top
        val centre = nearTop.sumOf { it.first } / 4 to nearTop.sumOf { it.second } / 4
        assertEquals("near", hitIso(scene, ordered, centre.first, centre.second, 0)?.footprint?.id)
    }

    @Test
    fun two_quarter_turns_round_the_same_tap_picks_the_box_that_is_now_nearer() {
        val low = part("low", width = 2, height = 2, heightMm = 1000)
        val high = part("high", col = 1, row = 1, width = 2, height = 2, heightMm = 1000)
        val scene = stackScene(items = listOf(low, high))
        val ordered = orderForIso(scene, listOf(shown(low), shown(high)), 2)
        val highTop = projectFaces(partBox(scene, high), 2).top
        val centre = highTop.sumOf { it.first } / 4 to highTop.sumOf { it.second } / 4
        assertEquals(listOf("low", "high"), ordered.map { it.footprint.id })
        assertEquals("high", hitIso(scene, ordered, centre.first, centre.second, 2)?.footprint?.id)
    }

    @Test
    fun tapping_empty_screen_picks_nothing() {
        val only = part("only", heightMm = 100)
        assertNull(hitIso(stackScene(items = listOf(only)), listOf(shown(only)), 99999.0, 99999.0, 0))
    }
}
