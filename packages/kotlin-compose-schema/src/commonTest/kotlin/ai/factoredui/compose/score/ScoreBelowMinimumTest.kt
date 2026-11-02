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

private fun shade(below: BelowMinimum? = null, minimum: Double? = 0.5): ScoreShade =
    if (below == null) ScoreShade(mapOf("a" to 1.0), minimum, RAMP) else ScoreShade(mapOf("a" to 1.0), minimum, RAMP, below = below)

private fun recordWith(below: Any?): Any? =
    mapOf(
        "weights" to mapOf("a" to 1.0), "minimum" to 0.5, "below" to below,
        "ramp" to mapOf("stops" to listOf(listOf(0.0, "#2E86DE"), listOf(1.0, "#E67E22")), "no_figure" to "#FF00FF"),
    )

class ScoreBelowMinimumTest {
    @Test
    fun a_thing_under_the_minimum_is_dimmed_by_default_not_hidden() {
        assertEquals(ShadedColour.Dimmed, shadeFor(shade(), mapOf("a" to 0.1)))
    }

    @Test
    fun a_thing_under_the_minimum_is_hidden_when_the_record_says_hide() {
        assertEquals(ShadedColour.Hidden, shadeFor(shade(BelowMinimum.HIDE), mapOf("a" to 0.1)))
    }

    @Test
    fun a_thing_at_or_over_the_minimum_keeps_its_ramp_colour_whatever_below_says() {
        assertIs<ShadedColour.Coloured>(shadeFor(shade(), mapOf("a" to 0.5)))
        assertIs<ShadedColour.Coloured>(shadeFor(shade(BelowMinimum.HIDE), mapOf("a" to 0.9)))
    }

    @Test
    fun a_thing_with_no_score_under_a_minimum_is_dimmed_too() {
        assertEquals(ShadedColour.Dimmed, shadeFor(shade(), mapOf("a" to null)))
    }

    @Test
    fun with_no_minimum_nothing_is_dimmed() {
        assertIs<ShadedColour.Coloured>(shadeFor(shade(minimum = null), mapOf("a" to 0.0)))
    }

    @Test
    fun a_dimmed_thing_is_not_in_the_ranked_list() {
        val ranked = rankByScore(shade(), listOf("low" to mapOf<String, Double?>("a" to 0.1), "high" to mapOf<String, Double?>("a" to 0.9)), top = 10)
        assertEquals(listOf("high"), ranked.map { it.id })
    }

    @Test
    fun the_record_reads_below_as_dim_or_hide_and_dim_is_the_default() {
        assertEquals(BelowMinimum.HIDE, ((readScoreShade("shade_by", recordWith("hide"))) as ScoreShadeReading.Readable).shade.below)
        assertEquals(BelowMinimum.DIM, ((readScoreShade("shade_by", recordWith("dim"))) as ScoreShadeReading.Readable).shade.below)
        assertEquals(BelowMinimum.DIM, ((readScoreShade("shade_by", recordWith(null))) as ScoreShadeReading.Readable).shade.below)
    }

    @Test
    fun a_below_that_is_neither_dim_nor_hide_is_refused_with_its_reason() {
        val reading = readScoreShade("shade_by", recordWith("fade"))
        assertTrue(assertIs<ScoreShadeReading.Refused>(reading).notice.contains("below"))
    }
}
