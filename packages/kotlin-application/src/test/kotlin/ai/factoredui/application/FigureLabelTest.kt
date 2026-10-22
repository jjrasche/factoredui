package ai.factoredui.application

import kotlin.test.Test
import kotlin.test.assertEquals

class FigureLabelTest {
    @Test
    fun a_snake_case_name_becomes_a_sentence_case_label() {
        assertEquals(FigureLabel("Daylight factor", null), figureLabelOf("daylight_factor"))
        assertEquals(FigureLabel("Cargo fit", null), figureLabelOf("cargo-fit"))
    }

    @Test
    fun a_unit_the_name_ends_in_becomes_the_unit_and_leaves_the_label() {
        assertEquals(
            listOf(FigureLabel("Payload left", "lb"), FigureLabel("Air temperature", "°F"), FigureLabel("Heat loss", "W"), FigureLabel("Energy drawn", "kWh"), FigureLabel("Wall height", "ft"), FigureLabel("Daylight factor", "%")),
            listOf("payload_left_lb", "air_temperature_f", "heat_loss_w", "energy_drawn_kwh", "wall_height_ft", "daylight_factor_percent").map(::figureLabelOf),
        )
    }

    @Test
    fun a_unit_of_several_words_is_read_whole() {
        assertEquals(FigureLabel("Loss", "BTU/h"), figureLabelOf("loss_btu_per_hour"))
    }

    @Test
    fun a_name_that_is_only_a_unit_is_left_as_the_name() {
        assertEquals(FigureLabel("Lb", null), figureLabelOf("lb"))
        assertEquals(FigureLabel("F", null), figureLabelOf("f"))
    }

    @Test
    fun a_last_word_that_is_not_a_known_unit_stays_in_the_label() {
        assertEquals(FigureLabel("Sheet load piece", null), figureLabelOf("sheet_load_piece"))
    }

    @Test
    fun a_figure_is_shown_with_its_unit_after_it() {
        assertEquals("1,163 lb", figureText(1163.0, "lb"))
        assertEquals("34.5", figureText(34.5, null))
    }
}
