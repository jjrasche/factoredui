package ai.factoredui.compose.renderer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private val ROOF = PickTarget.Area("roof", 0f, 0f, 200f, 200f)
private val FLOOR = PickTarget.Area("floor", 50f, 50f, 150f, 150f)
private val WALL = PickTarget.Reach("wall", 50f, 50f, 150f, 50f)
private val POST = PickTarget.Reach("post", 50f, 150f, 50f, 150f)

class PlanPickingTest {
    @Test
    fun a_tap_inside_nested_rectangles_picks_the_smallest() {
        assertEquals("floor", pickTarget(listOf(ROOF, FLOOR), 100f, 100f, 10f))
        assertEquals("roof", pickTarget(listOf(ROOF, FLOOR), 20f, 20f, 10f))
    }

    @Test
    fun a_line_near_the_tap_beats_the_rectangles_under_it() {
        assertEquals("wall", pickTarget(listOf(ROOF, FLOOR, WALL), 100f, 56f, 10f))
        assertEquals("floor", pickTarget(listOf(ROOF, FLOOR, WALL), 100f, 80f, 10f))
    }

    @Test
    fun the_nearer_of_two_things_in_reach_is_picked() {
        assertEquals("post", pickTarget(listOf(WALL, POST), 52f, 148f, 10f))
        assertEquals("wall", pickTarget(listOf(WALL, POST), 60f, 52f, 10f))
    }

    @Test
    fun a_tap_on_nothing_picks_nothing() {
        assertNull(pickTarget(listOf(ROOF, FLOOR, WALL), 400f, 400f, 10f))
    }
}
