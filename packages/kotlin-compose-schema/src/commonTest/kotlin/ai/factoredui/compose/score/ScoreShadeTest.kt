package ai.factoredui.compose.score

import ai.factoredui.compose.ramp.rampColourOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val RAMP = mapOf("stops" to listOf(listOf(0, "#000000"), listOf(1, "#ffffff")), "no_figure" to "#ff00ff")

private fun shadeOf(weights: Map<String, Any?>, minimum: Any? = null): ScoreShade {
    val reading = readScoreShade("shade_by", mapOf("weights" to weights, "minimum" to minimum, "ramp" to RAMP))
    return assertIs<ScoreShadeReading.Readable>(reading).shade
}

private fun colourOf(shade: ScoreShade, values: Map<String, Double?>): Long =
    assertIs<ShadedColour.Coloured>(shadeFor(shade, values)).colour

class ScoreShadeTest {
    @Test
    fun the_score_is_the_weighted_mean_of_the_fields_the_weights_name() {
        assertEquals(0.875, weightedScore(mapOf("slope" to 1.0, "water" to 0.5, "road" to 0.0), mapOf("slope" to 3.0, "water" to 1.0))!!, 1e-9)
    }

    @Test
    fun a_field_with_no_weight_does_not_count() {
        assertEquals(0.2, weightedScore(mapOf("a" to 0.2, "b" to 0.9), mapOf("a" to 1.0))!!, 1e-9)
    }

    @Test
    fun a_weighted_field_the_feature_lacks_leaves_the_score_unknown() {
        assertNull(weightedScore(mapOf("a" to 0.2), mapOf("a" to 1.0, "b" to 1.0)))
        assertNull(weightedScore(mapOf("a" to 0.2, "b" to null), mapOf("a" to 1.0, "b" to 1.0)))
    }

    @Test
    fun weights_that_are_all_zero_leave_the_score_unknown() {
        assertNull(weightedScore(mapOf("a" to 0.2), mapOf("a" to 0.0)))
    }

    @Test
    fun a_weight_of_zero_ignores_a_missing_field() {
        assertEquals(0.2, weightedScore(mapOf("a" to 0.2), mapOf("a" to 1.0, "b" to 0.0))!!, 1e-9)
    }

    @Test
    fun moving_a_weight_moves_the_colour() {
        val values = mapOf("a" to 1.0, "b" to 0.0)
        val fromA = colourOf(shadeOf(mapOf("a" to 1, "b" to 0)), values)
        val fromB = colourOf(shadeOf(mapOf("a" to 0, "b" to 1)), values)
        assertEquals(rampColourOf("#ffffff"), fromA)
        assertEquals(rampColourOf("#000000"), fromB)
    }

    @Test
    fun a_score_under_the_minimum_is_hidden_and_one_at_it_is_shown() {
        val shade = shadeOf(mapOf("a" to 1), minimum = 0.5)
        assertEquals(ShadedColour.Hidden, shadeFor(shade, mapOf("a" to 0.49)))
        assertIs<ShadedColour.Coloured>(shadeFor(shade, mapOf("a" to 0.5)))
    }

    @Test
    fun a_feature_whose_score_is_unknown_takes_the_no_figure_colour_when_there_is_no_minimum() {
        assertEquals(rampColourOf("#ff00ff"), colourOf(shadeOf(mapOf("a" to 1)), mapOf("b" to 1.0)))
    }

    @Test
    fun a_feature_whose_score_is_unknown_is_hidden_under_a_minimum() {
        assertEquals(ShadedColour.Hidden, shadeFor(shadeOf(mapOf("a" to 1), minimum = 0.1), mapOf("b" to 1.0)))
    }

    @Test
    fun a_record_without_weights_a_ramp_or_with_a_negative_weight_is_refused_with_the_reason() {
        val noWeights = readScoreShade("shade_by", mapOf("ramp" to RAMP))
        val noRamp = readScoreShade("shade_by", mapOf("weights" to mapOf("a" to 1)))
        val negative = readScoreShade("shade_by", mapOf("weights" to mapOf("a" to -1), "ramp" to RAMP))
        val reasons = listOf(noWeights, noRamp, negative).map { assertIs<ScoreShadeReading.Refused>(it).notice }
        assertTrue(reasons[0].contains("weights"), reasons[0])
        assertTrue(reasons[1].contains("ramp"), reasons[1])
        assertTrue(reasons[2].contains("negative") || reasons[2].contains("-1"), reasons[2])
    }

    @Test
    fun a_minimum_that_is_not_a_number_is_refused() {
        val reading = readScoreShade("shade_by", mapOf("weights" to mapOf("a" to 1), "minimum" to "high", "ramp" to RAMP))
        assertTrue(assertIs<ScoreShadeReading.Refused>(reading).notice.contains("minimum"))
    }
}
