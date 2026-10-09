package ai.factoredui.compose.structure

import kotlin.test.Test
import kotlin.test.assertEquals

private const val FT = FOOT_MARK
private const val IN = INCH_MARK

class FeetInchesTest {
    @Test
    fun zero_is_zero_inches() = assertEquals("0$IN", feetInches(0.0))

    @Test
    fun an_inch_is_25_point_4_millimetres() = assertEquals("1$IN", feetInches(25.4))

    @Test
    fun under_a_foot_is_inches_only_to_the_nearest_sixteenth_with_the_fraction_reduced() {
        assertEquals("10 7/8$IN", feetInches(276.0))
        assertEquals("3/4$IN", feetInches(19.05))
        assertEquals("1/2$IN", feetInches(12.7))
    }

    @Test
    fun a_foot_or_more_gives_feet_then_inches() {
        assertEquals("2$FT 0$IN", feetInches(610.0))
        assertEquals("3$FT 0$IN", feetInches(914.4))
        assertEquals("25$FT 0$IN", feetInches(7620.0))
    }

    @Test
    fun a_span_the_table_lists_as_8_ft_7_in_reads_as_8_ft_7_in() = assertEquals("8$FT 7$IN", feetInches(2616.0))

    @Test
    fun an_8_ft_span_reads_as_8_ft() = assertEquals("8$FT 0$IN", feetInches(2438.0))

    @Test
    fun the_house_is_32_feet_and_a_quarter_inch_wide() = assertEquals("32$FT 0 1/4$IN", feetInches(9760.0))

    @Test
    fun rounding_up_to_twelve_inches_carries_into_the_next_foot() = assertEquals("1$FT 0$IN", feetInches(304.8 - 0.4))

    @Test
    fun a_fraction_after_feet_is_shown_beside_the_whole_inches() = assertEquals("3$FT 3 3/8$IN", feetInches(1000.0))

    @Test
    fun a_negative_height_keeps_its_sign() = assertEquals("-11 13/16$IN", feetInches(-300.0))

    @Test
    fun exactly_half_a_sixteenth_rounds_up_as_javascript_math_round_does_not_to_the_even_neighbour() {
        assertEquals("1/16$IN", feetInches(0.79375))
        assertEquals("1/8$IN", feetInches(2.38125))
    }

    @Test
    fun a_missing_or_non_finite_length_is_shown_as_a_dash_rather_than_nan() {
        assertEquals("–", feetInches(null))
        assertEquals("–", feetInches(Double.NaN))
        assertEquals("–", feetInches(Double.POSITIVE_INFINITY))
    }
}
