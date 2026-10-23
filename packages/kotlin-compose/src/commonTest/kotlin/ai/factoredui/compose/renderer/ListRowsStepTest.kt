package ai.factoredui.compose.renderer

import kotlin.test.Test
import kotlin.test.assertEquals

class ListRowsStepTest {
    @Test
    fun a_swipe_left_of_a_step_goes_to_the_next_row_and_a_swipe_right_to_the_one_before() {
        assertEquals(2, stepAfterSwipe(1, -60f, 48f, 4))
        assertEquals(0, stepAfterSwipe(1, 60f, 48f, 4))
    }

    @Test
    fun a_short_swipe_leaves_the_step_where_it_is() {
        assertEquals(1, stepAfterSwipe(1, -20f, 48f, 4))
        assertEquals(1, stepAfterSwipe(1, 47f, 48f, 4))
    }

    @Test
    fun the_step_stays_inside_the_rows() {
        assertEquals(3, stepAfterSwipe(3, -100f, 48f, 4))
        assertEquals(0, stepAfterSwipe(0, 100f, 48f, 4))
    }

    @Test
    fun with_no_rows_the_step_is_unchanged() {
        assertEquals(0, stepAfterSwipe(0, -100f, 48f, 0))
    }
}
