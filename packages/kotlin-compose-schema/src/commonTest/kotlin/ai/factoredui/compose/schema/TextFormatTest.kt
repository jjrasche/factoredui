package ai.factoredui.compose.schema

import ai.factoredui.compose.structure.MISSING_FIGURE
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TextFormatTest {

    @Test
    fun each_wire_name_reads_as_its_format_and_anything_else_reads_as_no_format() {
        assertEquals(TextFormat.USD, textFormatOf("usd"))
        assertEquals(TextFormat.FEET_INCHES, textFormatOf("feet_inches"))
        assertEquals(TextFormat.PERCENT, textFormatOf("percent"))
        assertEquals(TextFormat.POUNDS, textFormatOf("pounds"))
        assertNull(textFormatOf("dollars"))
        assertNull(textFormatOf(null))
    }

    @Test
    fun usd_prints_cents_only_when_there_are_cents() {
        assertEquals("$92.37", formattedFigure(92.37, TextFormat.USD))
        assertEquals("$5,630.49", formattedFigure(5630.49, TextFormat.USD))
        assertEquals("$248", formattedFigure(248, TextFormat.USD))
    }

    @Test
    fun feet_inches_reads_millimetres() {
        assertEquals("10′ 0″", formattedFigure(3048.0, TextFormat.FEET_INCHES))
    }

    @Test
    fun percent_reads_a_fraction() {
        assertEquals("76%", formattedFigure(0.7576350143203, TextFormat.PERCENT))
    }

    @Test
    fun pounds_reads_kilograms() {
        assertEquals("22.0 lb", formattedFigure(10.0, TextFormat.POUNDS))
        assertEquals("220 lb", formattedFigure(100, TextFormat.POUNDS))
    }

    @Test
    fun a_null_value_is_the_missing_figure_dash_unless_the_spec_names_its_own_words() {
        assertEquals(MISSING_FIGURE, formattedFigure(null, TextFormat.USD))
        assertEquals("weight not known", formattedFigure(null, TextFormat.POUNDS, missingText = "weight not known"))
    }

    @Test
    fun a_non_numeric_value_under_a_numeric_format_draws_a_notice_never_the_dash() {
        assertEquals("usd needs a number, not '92.37'", formattedFigure("92.37", TextFormat.USD))
        assertEquals("pounds needs a number, not 'true'", formattedFigure(true, TextFormat.POUNDS, missingText = "weight not known"))
        assertEquals("percent needs a number, not 'NaN'", formattedFigure(Double.NaN, TextFormat.PERCENT))
    }

    @Test
    fun text_with_no_format_prints_its_value_and_an_unknown_format_names_itself() {
        assertEquals("Cast pier", displayedText("Cast pier", rawFormat = null, missingText = null))
        assertEquals("unknown format 'usdd'", displayedText(92.37, rawFormat = "usdd", missingText = null))
        assertEquals("$92.37", displayedText(92.37, rawFormat = "usd", missingText = null))
    }

    @Test
    fun a_number_bound_with_no_format_prints_as_a_plain_figure_and_a_whole_one_without_a_decimal() {
        assertEquals("12", displayedText(12.0, rawFormat = null, missingText = null))
        assertEquals("12", displayedText(12, rawFormat = null, missingText = null))
        assertEquals("2440.5", displayedText(2440.5, rawFormat = null, missingText = null))
    }

    @Test
    fun a_missing_value_with_no_format_draws_its_missing_text_or_nothing() {
        assertEquals("", displayedText(null, rawFormat = null, missingText = null))
        assertEquals("not counted", displayedText(null, rawFormat = null, missingText = "not counted"))
    }
}
