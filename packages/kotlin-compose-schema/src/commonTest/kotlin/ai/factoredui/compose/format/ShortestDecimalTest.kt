package ai.factoredui.compose.format

import kotlin.test.Test
import kotlin.test.assertEquals

class ShortestDecimalTest {

    @Test
    fun a_64_bit_whole_number_held_as_a_double_names_its_fewest_digits_in_scientific_form() =
        assertEquals("7.179409882978187E18", javaStyleDecimalText(7179409882978186897.0))

    @Test
    fun a_figure_that_needs_every_digit_keeps_them_all() = assertEquals("0.30000000000000004", javaStyleDecimalText(0.1 + 0.2))

    @Test
    fun a_figure_inside_the_plain_range_draws_with_a_point_and_no_exponent() {
        assertEquals("2438.4", javaStyleDecimalText(2438.4))
        assertEquals("6405.0", javaStyleDecimalText(6405.0))
        assertEquals("-0.001", javaStyleDecimalText(-0.001))
    }

    @Test
    fun a_figure_outside_the_plain_range_draws_one_digit_a_point_and_an_exponent() {
        assertEquals("1.0E20", javaStyleDecimalText(1.0E20))
        assertEquals("1.11260258E7", javaStyleDecimalText(11126025.8))
        assertEquals("1.0E-5", javaStyleDecimalText(1.0E-5))
    }

    @Test
    fun plain_text_writes_every_digit_of_a_whole_figure_past_the_exact_bound() {
        assertEquals("150000000000000000000", plainDecimalText(1.5E20))
        assertEquals("9500000000000000", plainDecimalText(9.5E15))
        assertEquals("-7179409882978187000", plainDecimalText(-7179409882978186897.0))
    }

    @Test
    fun plain_text_writes_a_small_figure_as_a_decimal_with_no_exponent() {
        assertEquals("0.00001", plainDecimalText(1.0E-5))
        assertEquals("11126025.8", plainDecimalText(11126025.8))
    }

    @Test
    fun the_shortest_digits_of_a_figure_read_back_as_the_same_figure() {
        listOf(7179409882978186897.0, 0.1 + 0.2, 1.5E20, 9.5E15, 123456.789, 5.0E-324, Double.MAX_VALUE).forEach { figure ->
            val shortest = shortestDigits(figure)
            assertEquals(figure, "0.${shortest.digits}E${shortest.pointAt}".toDouble())
        }
    }
}
