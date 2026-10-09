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
    fun a_missing_or_non_numeric_value_is_the_missing_figure_dash_unless_the_spec_names_its_own_words() {
        assertEquals(MISSING_FIGURE, formattedFigure(null, TextFormat.USD))
        assertEquals(MISSING_FIGURE, formattedFigure("twelve", TextFormat.POUNDS))
        assertEquals(MISSING_FIGURE, formattedFigure(Double.NaN, TextFormat.PERCENT))
        assertEquals("weight not known", formattedFigure(null, TextFormat.POUNDS, missingText = "weight not known"))
    }
}
