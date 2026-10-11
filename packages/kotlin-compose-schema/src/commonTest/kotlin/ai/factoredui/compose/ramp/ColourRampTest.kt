package ai.factoredui.compose.ramp

import kotlin.math.abs
import kotlin.math.floor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val RAMP_SPEC = mapOf(
    "stops" to listOf(listOf(0, "#3b7dd8"), listOf(0.5, "#e6a93a"), listOf(1, "#b5382a")),
    "over" to "#5a1e8c",
    "no_figure" to "#9aa3ab",
    "legend" to listOf(mapOf("at" to 0, "text" to "0"), mapOf("at" to 1, "text" to "full")),
    "over_text" to "over",
    "no_figure_text" to "no figure",
)

private fun ramp(raw: Any? = RAMP_SPEC): ColourRamp = (readColourRamp("ramp", raw) as RampReading.Readable).ramp

private fun refusal(raw: Any?): String = (readColourRamp("ramp", raw) as RampReading.Refused).notice

private fun channels(argb: Long): List<Int> = listOf(24, 16, 8, 0).map { ((argb shr it) and 0xFF).toInt() }

private fun halfwayChannel(from: Int, to: Int, share: Double): Int = floor(from + (to - from) * share + 0.5).toInt()

private fun expectedBetween(from: Long, to: Long, share: Double): Long {
    val mixed = channels(from).zip(channels(to)).map { (a, b) -> halfwayChannel(a, b, share).toLong() }
    return (mixed[0] shl 24) or (mixed[1] shl 16) or (mixed[2] shl 8) or mixed[3]
}

private fun assertColourWithinOneLevel(expected: Long, actual: Long, what: String) {
    channels(expected).zip(channels(actual)).forEach { (e, a) -> assertTrue(abs(e - a) <= 1, "$what: expected ${expected.toString(16)}, drew ${actual.toString(16)}") }
}

class ColourRampTest {

    @Test
    fun the_stop_values_take_their_own_colours_exactly() {
        assertEquals(0xFF3B7DD8, rampColourFor(ramp(), 0.0))
        assertEquals(0xFFE6A93A, rampColourFor(ramp(), 0.5))
        assertEquals(0xFFB5382A, rampColourFor(ramp(), 1.0))
    }

    @Test
    fun between_two_stops_each_channel_is_interpolated_in_srgb() {
        assertColourWithinOneLevel(expectedBetween(0xFF3B7DD8, 0xFFE6A93A, 0.5), rampColourFor(ramp(), 0.25), "0.25")
        assertColourWithinOneLevel(expectedBetween(0xFFE6A93A, 0xFFB5382A, 0.5), rampColourFor(ramp(), 0.75), "0.75")
        assertColourWithinOneLevel(expectedBetween(0xFF3B7DD8, 0xFFE6A93A, 0.2), rampColourFor(ramp(), 0.1), "0.1")
    }

    @Test
    fun below_the_first_stop_takes_the_first_colour_and_above_the_last_takes_over() {
        assertEquals(0xFF3B7DD8, rampColourFor(ramp(), -0.2))
        assertEquals(0xFF5A1E8C, rampColourFor(ramp(), 1.3))
    }

    @Test
    fun a_missing_value_takes_no_figure() = assertEquals(0xFF9AA3AB, rampColourFor(ramp(), null))

    @Test
    fun over_defaults_to_the_last_colour_and_no_figure_to_the_neutral_grey() {
        val bare = ramp(mapOf("stops" to listOf(listOf(0, "#000000"), listOf(10, "#ffffff"))))
        assertEquals(0xFFFFFFFF, rampColourFor(bare, 11.0))
        assertEquals(NEUTRAL_RAMP_COLOUR, rampColourFor(bare, null))
        assertEquals(emptyList(), bare.labels)
        assertNull(bare.overText)
    }

    @Test
    fun a_colour_with_an_alpha_pair_keeps_its_alpha_and_alpha_is_interpolated_too() {
        val fading = ramp(mapOf("stops" to listOf(listOf(0, "#ff000000"), listOf(1, "#ff0000ff"))))
        assertEquals(0x00FF0000, rampColourFor(fading, 0.0))
        assertColourWithinOneLevel(0x80FF0000, rampColourFor(fading, 0.5), "half alpha")
    }

    @Test
    fun the_legend_and_its_texts_are_read() {
        assertEquals(listOf(RampLabel(0.0, "0"), RampLabel(1.0, "full")), ramp().labels)
        assertEquals("over", ramp().overText)
        assertEquals("no figure", ramp().noFigureText)
    }

    @Test
    fun a_ramp_with_fewer_than_two_stops_is_refused() {
        assertEquals("ramp needs at least 2 stops, not 1", refusal(mapOf("stops" to listOf(listOf(0, "#000000")))))
        assertEquals("ramp needs a list of [value, colour] stops, not 'none'", refusal(mapOf("stops" to "none")))
        assertEquals("ramp must be an object with stops, not 'warm'", refusal("warm"))
    }

    @Test
    fun stop_values_that_do_not_rise_are_refused() {
        assertEquals("ramp stop values must rise: 0.5 is followed by 0.25", refusal(mapOf("stops" to listOf(listOf(0, "#000000"), listOf(0.5, "#ffffff"), listOf(0.25, "#ff0000")))))
        assertEquals("ramp stop values must rise: 1 is followed by 1", refusal(mapOf("stops" to listOf(listOf(1, "#000000"), listOf(1, "#ffffff")))))
    }

    @Test
    fun a_stop_that_is_not_a_number_and_a_colour_is_refused() {
        assertEquals("ramp stop 1 must be [value, colour] with a number and #rrggbb or #rrggbbaa, not '[0.5, red]'", refusal(mapOf("stops" to listOf(listOf(0, "#000000"), listOf(0.5, "red")))))
        assertEquals("ramp stop 0 must be [value, colour] with a number and #rrggbb or #rrggbbaa, not '[half, #000000]'", refusal(mapOf("stops" to listOf(listOf("half", "#000000"), listOf(1, "#ffffff")))))
    }

    @Test
    fun a_bad_over_or_no_figure_colour_and_a_bad_legend_entry_are_refused() {
        val stops = listOf(listOf(0, "#000000"), listOf(1, "#ffffff"))
        assertEquals("ramp over must be #rrggbb or #rrggbbaa, not 'purple'", refusal(mapOf("stops" to stops, "over" to "purple")))
        assertEquals("ramp no_figure must be #rrggbb or #rrggbbaa, not '3'", refusal(mapOf("stops" to stops, "no_figure" to 3)))
        assertEquals("ramp legend entry 0 must be {at: number, text}, not 'top'", refusal(mapOf("stops" to stops, "legend" to listOf("top"))))
    }

    @Test
    fun a_value_ramp_colours_a_number_takes_no_figure_for_null_and_refuses_anything_else() {
        assertEquals(ValueColourReading.Coloured(0xFFE6A93A), readValueColour("color_ramp", mapOf("value" to 0.5, "ramp" to RAMP_SPEC)))
        assertEquals(ValueColourReading.Coloured(0xFF9AA3AB), readValueColour("color_ramp", mapOf("value" to null, "ramp" to RAMP_SPEC)))
        assertEquals(ValueColourReading.Refused("color_ramp needs a number, not 'abc'"), readValueColour("color_ramp", mapOf("value" to "abc", "ramp" to RAMP_SPEC)))
        assertEquals(ValueColourReading.Refused("color_ramp needs at least 2 stops, not 1"), readValueColour("color_ramp", mapOf("value" to 1, "ramp" to mapOf("stops" to listOf(listOf(0, "#000000"))))))
        assertEquals(ValueColourReading.Refused("color_ramp must be an object with a value and a ramp, not '0.5'"), readValueColour("color_ramp", 0.5))
        assertNull(readValueColour("color_ramp", null))
    }

    @Test
    fun the_ink_over_a_background_is_black_or_white_whichever_contrasts_more_and_always_at_least_four_and_a_half() {
        assertEquals(0xFF000000, legibleInk(null, 0xFFE6A93A))
        assertEquals(0xFFFFFFFF, legibleInk(null, 0xFF3B2A7D))
        listOf(0xFF777777, 0xFF3B7DD8, 0xFFB5382A, 0xFF9AA3AB, 0xFF00FF00).forEach { background ->
            assertTrue(contrastRatio(legibleInk(null, background), background) >= 4.5, background.toString(16))
        }
    }

    @Test
    fun a_preferred_ink_is_kept_over_a_background_only_when_it_contrasts_four_and_a_half() {
        assertEquals(0xFF111111, legibleInk(0xFF111111, 0xFFFFFFFF))
        assertEquals(0xFF000000, legibleInk(0xFFE6A93A, 0xFFE6A93A))
    }
}
