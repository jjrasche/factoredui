package ai.factoredui.compose.format

import kotlin.test.Test
import kotlin.test.assertEquals

class FigureTextTest {
    @Test
    fun dollars_show_a_dollar_sign_thousands_commas_and_cents_only_when_there_are_cents() {
        assertEquals("$18.50", dollarsText(18.5))
        assertEquals("$1,500", dollarsText(1500.0))
        assertEquals("$0", dollarsText(0.0))
        assertEquals("$1,234,567.89", dollarsText(1234567.885))
        assertEquals("–", dollarsText(null))
    }

    @Test
    fun weights_are_shown_in_pounds_to_the_nearest_tenth_under_100_and_whole_above() {
        assertEquals("22.0 lb", poundsText(10.0))
        assertEquals("220 lb", poundsText(100.0))
        assertEquals("1.1 lb", poundsText(0.5))
        assertEquals("2,205 lb", poundsText(1000.0))
        assertEquals("–", poundsText(null))
    }

    @Test
    fun a_share_is_shown_rounded_to_a_whole_percent() {
        assertEquals("8%", percentText(0.0794))
        assertEquals("16%", percentText(0.1588))
        assertEquals("17%", percentText(0.1726))
        assertEquals("76%", percentText(0.7576))
        assertEquals("123%", percentText(1.234))
        assertEquals("3%", percentText(0.025))
    }

    @Test
    fun a_figure_that_is_not_finite_is_the_missing_dash() {
        assertEquals(MISSING_FIGURE, dollarsText(Double.NaN))
        assertEquals(MISSING_FIGURE, poundsText(Double.POSITIVE_INFINITY))
    }
}
