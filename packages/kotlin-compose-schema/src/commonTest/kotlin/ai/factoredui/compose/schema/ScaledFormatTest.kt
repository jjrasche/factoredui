package ai.factoredui.compose.schema

import ai.factoredui.compose.format.MISSING_FIGURE
import kotlin.test.Test
import kotlin.test.assertEquals

private val HALF_POINTS = mapOf("scale" to 0.5, "suffix" to " pts")

private fun drawn(value: Any?, format: Map<String, Any?>, missingText: String? = null) = displayedText(value, format, missingText)

class ScaledFormatTest {

    @Test
    fun the_requesters_case_draws_the_scaled_whole_figure_grouped_with_its_suffix() =
        assertEquals("2,501 lbf", drawn(11126025.8, mapOf("scale" to 0.000224809, "suffix" to " lbf", "decimals" to 0)))

    @Test
    fun a_value_is_multiplied_by_the_scale_and_a_half_rounds_away_from_zero_as_the_named_formats_do() {
        assertEquals("4 pts", drawn(7, HALF_POINTS))
        assertEquals("-4 pts", drawn(-7, HALF_POINTS))
    }

    @Test
    fun thousands_are_grouped_as_usd_groups_them() = assertEquals("2,500,000 pts", drawn(5_000_000, HALF_POINTS))

    @Test
    fun decimals_fix_the_fraction_digits_of_a_small_value() {
        assertEquals("0.13 pts", drawn(0.256, HALF_POINTS + ("decimals" to 2)))
        assertEquals("0.50 pts", drawn(1, HALF_POINTS + ("decimals" to 2.0)))
    }

    @Test
    fun a_negative_value_keeps_its_sign_and_one_that_rounds_to_zero_drops_it() {
        assertEquals("-1,234.5 pts", drawn(-2469, HALF_POINTS + ("decimals" to 1)))
        assertEquals("0 pts", drawn(-0.4, HALF_POINTS))
    }

    @Test
    fun decimals_default_to_none_and_the_suffix_to_nothing() = assertEquals("7", drawn(3.25, mapOf("scale" to 2)))

    @Test
    fun a_scale_that_is_not_a_number_is_refused_with_a_notice_never_a_figure() {
        assertEquals("format scale needs a number, not '0.0002'", drawn(10, mapOf("scale" to "0.0002")))
        assertEquals("format scale needs a number, not 'null'", drawn(10, mapOf("scale" to null)))
        assertEquals("format scale needs a number, not 'null'", drawn(10, mapOf("suffix" to " pts")))
        assertEquals("format scale needs a number, not 'NaN'", drawn(10, mapOf("scale" to Double.NaN)))
        assertEquals("format scale needs a number, not 'Infinity'", drawn(10, mapOf("scale" to Double.POSITIVE_INFINITY)))
        assertEquals("format scale needs a number, not 'true'", drawn(10, mapOf("scale" to true)))
    }

    @Test
    fun decimals_that_are_not_a_whole_number_from_0_to_12_are_refused_with_a_notice() {
        assertEquals("format decimals needs a whole number from 0 to 12, not '1.5'", drawn(10, HALF_POINTS + ("decimals" to 1.5)))
        assertEquals("format decimals needs a whole number from 0 to 12, not '-1'", drawn(10, HALF_POINTS + ("decimals" to -1)))
        assertEquals("format decimals needs a whole number from 0 to 12, not '13'", drawn(10, HALF_POINTS + ("decimals" to 13)))
        assertEquals("format decimals needs a whole number from 0 to 12, not '2'", drawn(10, HALF_POINTS + ("decimals" to "2")))
        assertEquals("format decimals needs a whole number from 0 to 12, not '13'", drawn(10, HALF_POINTS + ("decimals" to 13.0)))
    }

    @Test
    fun a_suffix_that_is_not_text_and_a_key_the_format_does_not_know_are_refused_with_a_notice() {
        assertEquals("format suffix needs text, not '3'", drawn(10, mapOf("scale" to 1, "suffix" to 3)))
        assertEquals("format has no key 'decimal'; it reads scale, decimals and suffix", drawn(10, mapOf("scale" to 1, "decimal" to 2)))
    }

    @Test
    fun a_broken_format_is_named_even_when_the_value_is_missing() =
        assertEquals("format scale needs a number, not '0.0002'", drawn(null, mapOf("scale" to "0.0002"), missingText = "not measured"))

    @Test
    fun a_value_that_is_not_a_number_draws_the_needs_a_number_notice() {
        assertEquals("format needs a number, not 'abc'", drawn("abc", HALF_POINTS))
        assertEquals("format needs a number, not 'NaN'", drawn(Double.NaN, HALF_POINTS))
    }

    @Test
    fun a_missing_value_draws_the_missing_text_or_the_dash_as_named_formats_do() {
        assertEquals("not measured", drawn(null, HALF_POINTS, missingText = "not measured"))
        assertEquals(MISSING_FIGURE, drawn(null, HALF_POINTS))
    }

    @Test
    fun a_figure_too_large_to_hold_its_decimals_exactly_is_refused_rather_than_drawn_wrong() =
        assertEquals("format cannot draw 10000000000 to 12 decimals exactly", drawn(2.0e10, HALF_POINTS + ("decimals" to 12)))
}
