package ai.factoredui.compose.renderer

import kotlin.test.Test
import kotlin.test.assertEquals

class SliderSnapTest {
    @Test
    fun a_value_between_steps_is_written_as_the_nearest_step_with_no_float_dust() {
        assertEquals(0.8, snapSliderValue(0.8f, 0f, 0.05f))
        assertEquals(0.35, snapSliderValue(0.3499f, 0f, 0.05f))
    }

    @Test
    fun steps_count_from_the_minimum_not_from_zero() {
        assertEquals(3.0, snapSliderValue(3.2f, 1f, 2f))
    }

    @Test
    fun a_slider_with_no_step_writes_the_value_it_was_given() {
        assertEquals(0.30000001192092896, snapSliderValue(0.3f, 0f, null))
    }
}
