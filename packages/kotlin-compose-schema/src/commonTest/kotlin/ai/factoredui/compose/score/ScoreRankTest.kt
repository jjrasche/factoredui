package ai.factoredui.compose.score

import ai.factoredui.compose.ramp.RampReading
import ai.factoredui.compose.ramp.readColourRamp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val RAMP = (readColourRamp(
    "ramp",
    mapOf("stops" to listOf(listOf(0.0, "#2E86DE"), listOf(1.0, "#E67E22")), "no_figure" to "#FF00FF"),
) as RampReading.Readable).ramp

private fun shade(weights: Map<String, Double>, minimum: Double? = null, scale: ShadeScale = ShadeScale.VALUE) =
    ScoreShade(weights, minimum, RAMP, scale = scale)

private val THINGS = listOf(
    "low" to mapOf<String, Double?>("a" to 0.1, "b" to 0.9),
    "high" to mapOf<String, Double?>("a" to 0.9, "b" to 0.1),
    "middle" to mapOf<String, Double?>("a" to 0.5, "b" to 0.5),
    "blank" to mapOf<String, Double?>("a" to null, "b" to null),
)

class ScoreRankTest {
    @Test
    fun things_are_ranked_best_first_by_the_weights_and_a_thing_with_no_score_is_not_ranked() {
        val ranked = rankByScore(shade(mapOf("a" to 1.0, "b" to 0.0)), THINGS, top = 10)
        assertEquals(listOf("high", "middle", "low"), ranked.map { it.id })
        assertEquals(listOf(1, 2, 3), ranked.map { it.rank })
    }

    @Test
    fun moving_a_weight_changes_who_is_first() {
        val ranked = rankByScore(shade(mapOf("a" to 0.0, "b" to 1.0)), THINGS, top = 10)
        assertEquals("low", ranked.first().id)
    }

    @Test
    fun the_list_stops_at_the_top_count() {
        assertEquals(2, rankByScore(shade(mapOf("a" to 1.0)), THINGS, top = 2).size)
    }

    @Test
    fun a_minimum_keeps_the_hidden_things_out_of_the_list_but_the_rank_still_counts_them() {
        val ranked = rankByScore(shade(mapOf("a" to 1.0), minimum = 0.6), THINGS, top = 10)
        assertEquals(listOf("high"), ranked.map { it.id })
        assertEquals(1, ranked.single().rank)
    }

    @Test
    fun a_thing_ranks_below_everything_that_scores_higher_even_when_they_are_hidden_by_the_minimum() {
        val ranked = rankByScore(shade(mapOf("a" to 1.0), minimum = 0.0), THINGS, top = 10)
        assertEquals(3, ranked.first { it.id == "low" }.rank)
    }

    @Test
    fun in_rank_scale_the_percentile_comes_with_each_row() {
        val ranks = ScoreRanks(listOf(0.1, 0.5, 0.9))
        val ranked = rankByScore(shade(mapOf("a" to 1.0), scale = ShadeScale.RANK).copy(ranks = ranks), THINGS, top = 10)
        assertTrue(ranked.first().percentile!! > ranked.last().percentile!!)
    }

    @Test
    fun the_parts_of_a_score_are_each_fields_value_weight_and_share_of_the_score_and_the_shares_sum_to_it() {
        val weights = mapOf("a" to 3.0, "b" to 1.0)
        val parts = scoreParts(shade(weights), mapOf("a" to 0.8, "b" to 0.4))
        assertEquals(listOf("a", "b"), parts.map { it.field })
        assertEquals(0.6, parts.first().contribution, 1e-9)
        assertEquals(scoreOf(shade(weights), mapOf("a" to 0.8, "b" to 0.4))!!, parts.sumOf { it.contribution }, 1e-9)
    }

    @Test
    fun a_field_with_no_figure_is_listed_with_no_value_and_adds_nothing() {
        val parts = scoreParts(shade(mapOf("a" to 1.0, "b" to 1.0)), mapOf("a" to 0.5, "b" to null))
        assertNull(parts.last().value)
        assertEquals(0.0, parts.last().contribution, 1e-9)
        assertEquals(0.5, parts.first().contribution, 1e-9)
    }

    @Test
    fun a_part_carries_the_label_the_record_gives_its_field_and_the_field_name_when_it_gives_none() {
        val named = shade(mapOf("a" to 1.0, "b" to 1.0)).copy(labels = mapOf("a" to "Spring likelihood"))
        val parts = scoreParts(named, mapOf("a" to 0.5, "b" to 0.5))
        assertEquals(listOf("Spring likelihood", "b"), parts.map { it.label })
    }

    @Test
    fun a_record_read_from_a_spec_keeps_its_labels() {
        val reading = readScoreShade(
            "shade_by",
            mapOf("weights" to mapOf("a" to 1.0), "labels" to mapOf("a" to "Spring"),
                "ramp" to mapOf("stops" to listOf(listOf(0.0, "#2E86DE"), listOf(1.0, "#E67E22")), "no_figure" to "#FF00FF")),
        )
        assertEquals("Spring", (reading as ScoreShadeReading.Readable).shade.labels["a"])
    }

    @Test
    fun a_field_with_zero_weight_is_left_out_of_the_parts() {
        val parts = scoreParts(shade(mapOf("a" to 1.0, "b" to 0.0)), mapOf("a" to 0.5, "b" to 0.5))
        assertEquals(listOf("a"), parts.map { it.field })
    }
}
