package ai.factoredui.compose.schema

import kotlin.test.Test
import kotlin.test.assertEquals

private fun plain(value: Double): String = displayedText(value, rawFormat = null, missingText = null)

class PlainFigureTest {

    @Test
    fun a_large_non_whole_figure_draws_its_digits_with_no_exponent() = assertEquals("11126025.8", plain(11126025.8))

    @Test
    fun a_tiny_figure_draws_as_a_plain_decimal() = assertEquals("0.00001", plain(1.0E-5))

    @Test
    fun a_whole_figure_past_the_exact_bound_draws_all_its_digits() {
        assertEquals("150000000000000000000", plain(1.5E20))
        assertEquals("9500000000000000", plain(9.5E15))
    }

    @Test
    fun whole_figures_inside_the_bound_and_ordinary_decimals_draw_as_before() {
        assertEquals("12", plain(12.0))
        assertEquals("-3", plain(-3.0))
        assertEquals("2440.5", plain(2440.5))
        assertEquals("-0.25", plain(-0.25))
        assertEquals("-0.000012", plain(-1.2E-5))
    }

    @Test
    fun not_a_number_and_infinity_draw_as_before() {
        assertEquals("NaN", plain(Double.NaN))
        assertEquals("Infinity", plain(Double.POSITIVE_INFINITY))
    }

    @Test
    fun a_figure_interpolated_into_text_draws_with_no_exponent_too() =
        assertEquals("load 11126025.8 N", BindingResolver.resolveText("load {f} N", mapOf("f" to 11126025.8)))
}
