package ai.factoredui.compose.stackedscene

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val GREEN = 0xFF2E7D32
private const val AMBER = 0xFFF9A825
private const val PURPLE = 0xFF7B1FA2
private const val GREY = 0xFF8899AA

private val BANDED = ColourScale(
    channel = "reach",
    stops = listOf(ColourStop(0.25, GREEN, "low"), ColourStop(1.0, AMBER), ColourStop(null, PURPLE, "over")),
    unknownColour = GREY,
    unknownLabel = "no figure",
)

private fun lightness(argb: Long): Double {
    val channels = listOf((argb shr 16) and 0xFF, (argb shr 8) and 0xFF, argb and 0xFF).map { it / 255.0 }
    return (channels.max() + channels.min()) / 2
}

private fun hueOf(argb: Long): Double {
    val (red, green, blue) = listOf((argb shr 16) and 0xFF, (argb shr 8) and 0xFF, argb and 0xFF).map { it / 255.0 }
    val high = maxOf(red, green, blue)
    val spread = high - minOf(red, green, blue)
    val sector = when (high) {
        red -> ((green - blue) / spread).mod(6.0)
        green -> (blue - red) / spread + 2
        else -> (red - green) / spread + 4
    }
    return sector * 60
}

class ColourScaleTest {
    @Test
    fun a_value_takes_the_first_stop_whose_up_to_it_does_not_pass_and_up_to_is_inclusive() {
        assertEquals(GREEN, colourFor(BANDED, 0.0))
        assertEquals(GREEN, colourFor(BANDED, 0.25))
        assertEquals(AMBER, colourFor(BANDED, 0.2501))
        assertEquals(AMBER, colourFor(BANDED, 1.0))
    }

    @Test
    fun a_stop_with_no_up_to_takes_every_value_above_the_stops_before_it() = assertEquals(PURPLE, colourFor(BANDED, 40.0))

    @Test
    fun a_part_with_no_value_takes_the_scales_unknown_colour() = assertEquals(GREY, colourFor(BANDED, null))

    @Test
    fun a_value_no_stop_covers_takes_the_unknown_colour_rather_than_an_invented_band() {
        val closed = BANDED.copy(stops = listOf(ColourStop(0.5, GREEN)))
        assertEquals(GREY, colourFor(closed, 0.9))
    }

    @Test
    fun the_library_holds_no_bands_of_its_own_so_with_no_scale_every_part_is_the_neutral_colour() {
        assertEquals(NEUTRAL_PART_COLOUR, colourFor(null, 0.1))
        assertEquals(NEUTRAL_PART_COLOUR, colourFor(null, 7.0))
        assertEquals(NEUTRAL_PART_COLOUR, colourFor(null, null))
    }

    @Test
    fun the_same_values_coloured_by_two_scales_follow_each_scales_stops() {
        val inverted = ColourScale("reach", listOf(ColourStop(0.25, PURPLE), ColourStop(null, GREEN)))
        assertEquals(listOf(GREEN, AMBER), listOf(0.1, 0.9).map { colourFor(BANDED, it) })
        assertEquals(listOf(PURPLE, GREEN), listOf(0.1, 0.9).map { colourFor(inverted, it) })
    }

    @Test
    fun the_legend_lists_each_labelled_stop_in_order_then_the_unknown_colour_when_it_has_a_label() {
        assertEquals(listOf(LegendEntry(GREEN, "low"), LegendEntry(PURPLE, "over"), LegendEntry(GREY, "no figure")), legendEntries(BANDED))
        assertEquals(emptyList(), legendEntries(BANDED.copy(stops = listOf(ColourStop(1.0, GREEN)), unknownLabel = null)))
        assertEquals(emptyList(), legendEntries(null))
    }

    @Test
    fun a_value_is_named_by_the_label_of_the_stop_it_falls_in() {
        assertEquals("low", bandLabelFor(BANDED, 0.1))
        assertEquals("over", bandLabelFor(BANDED, 40.0))
    }

    @Test
    fun a_value_in_an_unlabelled_stop_has_no_band_label() = assertNull(bandLabelFor(BANDED, 0.5))

    @Test
    fun a_missing_value_or_one_no_stop_covers_is_named_by_the_unknown_label() {
        assertEquals("no figure", bandLabelFor(BANDED, null))
        assertEquals("no figure", bandLabelFor(BANDED.copy(stops = listOf(ColourStop(0.5, GREEN, "low"))), 0.9))
        assertNull(bandLabelFor(BANDED.copy(unknownLabel = null), null))
    }

    @Test
    fun with_no_scale_there_is_no_band_label() = assertNull(bandLabelFor(null, 0.1))

    @Test
    fun the_popup_value_is_the_formatted_figure_followed_by_its_band_label() {
        val percent = BANDED.copy(format = "percent")
        assertEquals("10% · low", partValueText(PartValue(0.1, "1 of 10", null), percent))
        assertEquals("4 · over", partValueText(PartValue(4.0, "4", null), BANDED))
    }

    @Test
    fun an_unlabelled_band_adds_nothing_to_the_popup_value() = assertEquals("50%", partValueText(PartValue(0.5, "5 of 10", null), BANDED.copy(format = "percent")))

    @Test
    fun a_part_with_no_value_shows_the_unknown_label_or_nothing() {
        assertEquals("no figure", partValueText(null, BANDED))
        assertNull(partValueText(null, BANDED.copy(unknownLabel = null)))
    }

    @Test
    fun with_no_scale_the_popup_shows_no_value() = assertNull(partValueText(PartValue(0.1, "1 of 10", null), null))

    @Test
    fun a_hex_colour_reads_as_opaque_argb_and_anything_else_reads_as_nothing() {
        assertEquals(GREEN, colourOfHex("#2e7d32"))
        assertEquals(GREEN, colourOfHex("2E7D32"))
        assertNull(colourOfHex("#2e7d3"))
        assertNull(colourOfHex("green"))
        assertNull(colourOfHex(3))
    }

    @Test
    fun the_top_face_is_the_lightest_shade_and_the_south_face_the_darkest() {
        val shades = faceShades(AMBER)
        assertTrue(lightness(shades.top) > lightness(shades.west))
        assertTrue(lightness(shades.west) > lightness(shades.south))
    }

    @Test
    fun every_face_keeps_the_hue_of_the_stop_colour() {
        val shades = faceShades(PURPLE)
        listOf(shades.top, shades.west, shades.south).forEach { assertEquals(hueOf(PURPLE), hueOf(it), 2.0) }
    }
}
