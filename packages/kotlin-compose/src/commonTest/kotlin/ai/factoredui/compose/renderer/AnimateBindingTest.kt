package ai.factoredui.compose.renderer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun animation(extra: Map<String, Any?> = emptyMap()): BindingAnimation =
    (readBindingAnimation("play", mapOf("path" to "heat.hour", "from" to 0, "to" to 23, "interval_ms" to 400) + extra) as AnimationReading.Ready).animation

private fun refusal(params: Map<String, Any?>): String = (readBindingAnimation("play", params) as AnimationReading.Refused).notice

class AnimateBindingTest {
    @Test
    fun the_parameters_are_read_with_a_step_of_one_and_no_loop_by_default() {
        val read = animation()
        assertEquals(listOf(0.0, 23.0, 1.0, 400L), listOf(read.from, read.to, read.step, read.intervalMs))
        assertEquals(false, read.isLooping)
    }

    @Test
    fun a_run_that_does_not_loop_ends_at_to() {
        val read = animation()
        assertEquals(23.0, nextAnimationValue(read, 22.0))
        assertNull(nextAnimationValue(read, 23.0))
    }

    @Test
    fun a_loop_wraps_from_to_back_to_from() {
        assertEquals(0.0, nextAnimationValue(animation(mapOf("loop" to true)), 23.0))
    }

    @Test
    fun a_step_that_would_pass_to_wraps_or_ends_and_never_lands_beyond_it() {
        val bigStep = animation(mapOf("step" to 10))
        assertEquals(20.0, nextAnimationValue(bigStep, 10.0))
        assertNull(nextAnimationValue(bigStep, 20.0))
        assertEquals(0.0, nextAnimationValue(animation(mapOf("step" to 10, "loop" to true)), 20.0))
    }

    @Test
    fun a_value_before_from_goes_to_from() {
        assertEquals(0.0, nextAnimationValue(animation(), -5.0))
    }

    @Test
    fun a_missing_path_a_reversed_range_a_zero_step_and_a_fast_interval_are_refused_by_name() {
        assertTrue(refusal(mapOf("from" to 0, "to" to 1)).contains("'path' must be non-empty text"))
        assertTrue(refusal(mapOf("path" to "h", "from" to 5, "to" to 1)).contains("must be above 'from'"))
        assertTrue(refusal(mapOf("path" to "h", "from" to 0, "to" to 5, "step" to 0)).contains("'step' must be a number above 0"))
        assertTrue(refusal(mapOf("path" to "h", "from" to 0, "to" to 5, "interval_ms" to 10)).contains("'interval_ms' must be a number of 50 or more"))
    }
}
