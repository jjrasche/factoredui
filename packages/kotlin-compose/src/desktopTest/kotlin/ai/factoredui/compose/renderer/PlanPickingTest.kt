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
    fun a_tap_inside_nested_rectangles_lists_the_smallest_first() {
        assertEquals(listOf("floor", "roof"), pickTargets(listOf(ROOF, FLOOR), 100f, 100f, 10f))
        assertEquals(listOf("roof"), pickTargets(listOf(ROOF, FLOOR), 20f, 20f, 10f))
    }

    @Test
    fun a_line_near_the_tap_is_listed_before_the_rectangles_under_it() {
        assertEquals(listOf("wall", "floor", "roof"), pickTargets(listOf(ROOF, FLOOR, WALL), 100f, 56f, 10f))
        assertEquals(listOf("floor", "roof"), pickTargets(listOf(ROOF, FLOOR, WALL), 100f, 80f, 10f))
    }

    @Test
    fun things_in_reach_are_listed_nearest_first_and_coincident_ones_keep_their_order() {
        assertEquals(listOf("post", "wall"), pickTargets(listOf(WALL, POST), 60f, 120f, 80f))
        val lower = PickTarget.Reach("lower", 50f, 50f, 150f, 50f)
        val upper = PickTarget.Reach("upper", 50f, 50f, 150f, 50f)
        assertEquals(listOf("wall", "lower", "upper"), pickTargets(listOf(WALL, lower, upper), 100f, 50f, 10f))
    }

    @Test
    fun a_tap_on_nothing_lists_nothing() {
        assertEquals(emptyList(), pickTargets(listOf(ROOF, FLOOR, WALL), 400f, 400f, 10f))
    }

    @Test
    fun repeated_taps_walk_the_candidates_and_wrap_while_one_candidate_toggles_and_none_clears() {
        val under = listOf("wall", "lower", "upper")
        assertEquals(listOf("wall"), selectionAfterTapAmong(emptySet(), under))
        assertEquals(listOf("lower"), selectionAfterTapAmong(setOf("wall"), under))
        assertEquals(listOf("wall"), selectionAfterTapAmong(setOf("upper"), under))
        assertEquals(listOf("wall"), selectionAfterTapAmong(setOf("elsewhere"), under))
        assertEquals(emptyList(), selectionAfterTapAmong(setOf("wall"), listOf("wall")))
        assertEquals(emptyList(), selectionAfterTapAmong(setOf("wall"), emptyList()))
    }
}
