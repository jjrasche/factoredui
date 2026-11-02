package ai.factoredui.compose.score

import ai.factoredui.compose.ramp.RampReading
import ai.factoredui.compose.ramp.readColourRamp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

private val RAMP = (readColourRamp(
    "ramp",
    mapOf("stops" to listOf(listOf(0.0, "#2E86DE"), listOf(1.0, "#E67E22")), "no_figure" to "#FF00FF"),
) as RampReading.Readable).ramp

private val VALUES = mapOf<String, Double?>("a" to 0.9)

private fun shadeWith(ranges: Map<String, FigureRange>, below: BelowMinimum = BelowMinimum.DIM): ScoreShade =
    ScoreShade(mapOf("a" to 1.0), null, RAMP, ranges = ranges, below = below)

private val ACRES_AT_LEAST_TEN = mapOf("acres" to FigureRange(min = 10.0, max = null))
private val MINUTES_AT_MOST_TWENTY = mapOf("drive" to FigureRange(min = null, max = 20.0))

private fun recordWith(ranges: Any?, extra: Map<String, Any?> = emptyMap()): Any? =
    mapOf(
        "weights" to mapOf("a" to 1.0),
        "ramp" to mapOf("stops" to listOf(listOf(0.0, "#2E86DE"), listOf(1.0, "#E67E22")), "no_figure" to "#FF00FF"),
        "ranges" to ranges,
    ) + extra

private fun readShade(record: Any?): ScoreShade = (readScoreShade("shade_by", record) as ScoreShadeReading.Readable).shade

class ScoreRangeTest {
    @Test
    fun a_thing_inside_a_minimum_keeps_its_ramp_colour_and_one_under_it_is_dimmed() {
        val shade = shadeWith(ACRES_AT_LEAST_TEN)
        assertIs<ShadedColour.Coloured>(shadeFor(shade, VALUES, mapOf("acres" to 10.0)))
        assertEquals(ShadedColour.Dimmed, shadeFor(shade, VALUES, mapOf("acres" to 9.99)))
    }

    @Test
    fun a_thing_over_a_maximum_is_dimmed_and_one_at_it_is_not() {
        val shade = shadeWith(MINUTES_AT_MOST_TWENTY)
        assertIs<ShadedColour.Coloured>(shadeFor(shade, VALUES, mapOf("drive" to 20.0)))
        assertEquals(ShadedColour.Dimmed, shadeFor(shade, VALUES, mapOf("drive" to 20.01)))
    }

    @Test
    fun a_thing_with_no_figure_under_an_active_range_is_dimmed_and_named_as_unmeasured() {
        val shade = shadeWith(MINUTES_AT_MOST_TWENTY)
        assertEquals(ShadedColour.Dimmed, shadeFor(shade, VALUES, mapOf("drive" to null)))
        assertEquals(ShadedColour.Dimmed, shadeFor(shade, VALUES, emptyMap()))
        assertEquals(listOf("drive"), unmeasuredFigures(shade, mapOf("drive" to null)))
        assertEquals(emptyList(), unmeasuredFigures(shade, mapOf("drive" to 5.0)))
    }

    @Test
    fun a_range_with_no_bound_is_off_so_a_thing_with_no_figure_keeps_its_colour() {
        val shade = shadeWith(mapOf("drive" to FigureRange(null, null)))
        assertIs<ShadedColour.Coloured>(shadeFor(shade, VALUES, mapOf("drive" to null)))
        assertEquals(emptyList(), unmeasuredFigures(shade, mapOf("drive" to null)))
    }

    @Test
    fun a_thing_must_be_inside_every_active_range() {
        val shade = shadeWith(ACRES_AT_LEAST_TEN + MINUTES_AT_MOST_TWENTY)
        assertIs<ShadedColour.Coloured>(shadeFor(shade, VALUES, mapOf("acres" to 12.0, "drive" to 15.0)))
        assertEquals(ShadedColour.Dimmed, shadeFor(shade, VALUES, mapOf("acres" to 12.0, "drive" to 30.0)))
        assertEquals(ShadedColour.Dimmed, shadeFor(shade, VALUES, mapOf("acres" to 3.0, "drive" to 15.0)))
    }

    @Test
    fun a_figure_no_range_names_never_dims_anything() {
        val shade = shadeWith(ACRES_AT_LEAST_TEN)
        assertIs<ShadedColour.Coloured>(shadeFor(shade, VALUES, mapOf("acres" to 50.0, "drive" to 999.0)))
    }

    @Test
    fun a_thing_outside_a_range_is_hidden_when_the_record_says_hide() {
        val shade = shadeWith(ACRES_AT_LEAST_TEN, BelowMinimum.HIDE)
        assertEquals(ShadedColour.Hidden, shadeFor(shade, VALUES, mapOf("acres" to 1.0)))
    }

    @Test
    fun a_thing_outside_a_range_is_not_in_the_ranked_list() {
        val shade = shadeWith(ACRES_AT_LEAST_TEN)
        val things = listOf("small" to VALUES, "large" to VALUES)
        val figures = mapOf("small" to mapOf<String, Double?>("acres" to 2.0), "large" to mapOf<String, Double?>("acres" to 40.0))
        assertEquals(listOf("large"), rankByScore(shade, things, top = 10, figuresById = figures).map { it.id })
    }

    @Test
    fun the_record_reads_ranges_with_either_bound_missing() {
        val shade = readShade(recordWith(mapOf("acres" to mapOf("min" to 10.0), "drive" to mapOf("max" to 20.0, "min" to null))))
        assertEquals(FigureRange(10.0, null), shade.ranges["acres"])
        assertEquals(FigureRange(null, 20.0), shade.ranges["drive"])
    }

    @Test
    fun a_bound_that_is_not_a_number_or_a_min_over_its_max_is_refused_with_its_reason() {
        val notNumber = readScoreShade("shade_by", recordWith(mapOf("acres" to mapOf("min" to "ten"))))
        assertTrue(assertIs<ScoreShadeReading.Refused>(notNumber).notice.contains("acres"))
        val crossed = readScoreShade("shade_by", recordWith(mapOf("acres" to mapOf("min" to 30.0, "max" to 10.0))))
        assertTrue(assertIs<ScoreShadeReading.Refused>(crossed).notice.contains("acres"))
    }

    @Test
    fun the_record_reads_how_to_say_each_figure_and_which_figure_belongs_to_which_weighted_field() {
        val shade = readShade(
            recordWith(
                emptyMap<String, Any?>(),
                mapOf(
                    "figure_info" to mapOf("acres" to mapOf("label" to "Parcel acres", "unit" to "acres", "decimals" to 1.0)),
                    "field_figures" to mapOf("a" to "acres"),
                ),
            ),
        )
        assertEquals(FigureInfo("Parcel acres", "acres", 1), shade.figureInfo["acres"])
        assertEquals("acres", shade.fieldFigures["a"])
    }

    @Test
    fun a_figure_reads_with_its_decimals_and_unit_and_a_missing_one_says_so() {
        assertEquals("31.2 /100", figureText(FigureInfo("Spring", "/100", 1), 31.2))
        assertEquals("17 min", figureText(FigureInfo("Drive", "min", 0), 17.0))
        assertEquals("0.67 acres", figureText(FigureInfo("Trees", "acres", 2), 0.67))
        assertEquals("no figure", figureText(FigureInfo("Drive", "min", 0), null))
        assertEquals("5", figureText(null, 5.0))
    }
}
