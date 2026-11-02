package ai.factoredui.compose.score

import ai.factoredui.compose.ramp.RampReading
import ai.factoredui.compose.ramp.readColourRamp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val RAMP = (readColourRamp(
    "ramp",
    mapOf("stops" to listOf(listOf(0.0, "#2E86DE"), listOf(1.0, "#E67E22")), "no_figure" to "#FF00FF"),
) as RampReading.Readable).ramp

private val SPRING_HIGH = FigureScaling(GoodEnd.HIGH, best = null, worst = null)
private val DRIVE_LOW = FigureScaling(GoodEnd.LOW, best = null, worst = null)
private val SOIL_ONE_BEST_OF_EIGHT = FigureScaling(GoodEnd.LOW, best = 1.0, worst = 8.0)

private fun rawShade(
    weights: Map<String, Double>,
    scalings: Map<String, FigureScaling>,
    loaded: List<Map<String, Double?>>,
    minimum: Double? = null,
): ScoreShade = ScoreShade(weights, minimum, RAMP, scalings = scalings).withExtentsOf(loaded)

private fun bounds(shade: ScoreShade, figures: Map<String, Double?>): ScoreBounds =
    assertNotNull(scoreBoundsOf(shade, emptyMap(), figures))

class ScoreBoundsTest {
    @Test
    fun a_figure_scores_from_its_worst_loaded_value_to_its_best_at_the_good_end() {
        val loaded = listOf(mapOf("spring" to 0.0), mapOf("spring" to 5.0), mapOf("spring" to 10.0))
        val high = rawShade(mapOf("spring" to 1.0), mapOf("spring" to SPRING_HIGH), loaded)
        assertEquals(listOf(0.0, 0.5, 1.0), loaded.map { bounds(high, it).low })
        val low = rawShade(mapOf("spring" to 1.0), mapOf("spring" to DRIVE_LOW), loaded)
        assertEquals(listOf(1.0, 0.5, 0.0), loaded.map { bounds(low, it).low })
    }

    @Test
    fun a_stated_range_scores_against_its_own_best_and_worst_so_other_parcels_cannot_move_it() {
        val alone = listOf(mapOf("soil" to 1.0), mapOf("soil" to 4.5), mapOf("soil" to 8.0))
        val crowded = alone + listOf(mapOf("soil" to 100.0), mapOf("soil" to -50.0))
        val scalings = mapOf("soil" to SOIL_ONE_BEST_OF_EIGHT)
        for (loaded in listOf(alone, crowded)) {
            val shade = rawShade(mapOf("soil" to 1.0), scalings, loaded)
            assertEquals(listOf(1.0, 0.5, 0.0), alone.map { bounds(shade, it).low })
        }
        val shade = rawShade(mapOf("soil" to 1.0), scalings, crowded)
        assertEquals(0.0, bounds(shade, mapOf("soil" to 100.0)).low)
        assertEquals(1.0, bounds(shade, mapOf("soil" to -50.0)).low)
    }

    @Test
    fun a_parcel_with_a_missing_figure_is_ranked_with_bounds_not_dropped() {
        val loaded = listOf(mapOf("spring" to 0.0, "drive" to 0.0), mapOf("spring" to 10.0, "drive" to 10.0))
        val shade = rawShade(
            mapOf("spring" to 1.0, "drive" to 1.0), mapOf("spring" to SPRING_HIGH, "drive" to DRIVE_LOW), loaded,
        )
        val missingDrive = bounds(shade, mapOf("spring" to 10.0, "drive" to null))
        assertEquals(0.5, missingDrive.low)
        assertEquals(1.0, missingDrive.high)
        assertFalse(missingDrive.isSettled)
        val nothingKnown = bounds(shade, emptyMap())
        assertEquals(0.0, nothingKnown.low)
        assertEquals(1.0, nothingKnown.high)
    }

    @Test
    fun a_parcel_with_every_figure_is_settled_and_its_bounds_meet() {
        val loaded = listOf(mapOf("spring" to 0.0), mapOf("spring" to 10.0))
        val shade = rawShade(mapOf("spring" to 1.0), mapOf("spring" to SPRING_HIGH), loaded)
        val known = bounds(shade, mapOf("spring" to 7.0))
        assertTrue(known.isSettled)
        assertEquals(known.low, known.high)
    }

    @Test
    fun a_missing_figure_with_no_weight_does_not_unsettle_a_parcel() {
        val loaded = listOf(mapOf("spring" to 0.0), mapOf("spring" to 10.0))
        val shade = rawShade(
            mapOf("spring" to 1.0, "drive" to 0.0), mapOf("spring" to SPRING_HIGH, "drive" to DRIVE_LOW), loaded,
        )
        assertTrue(bounds(shade, mapOf("spring" to 10.0, "drive" to null)).isSettled)
    }

    @Test
    fun weights_are_a_ratio_so_doubling_all_of_them_changes_nothing() {
        val loaded = listOf(mapOf("spring" to 0.0, "drive" to 0.0), mapOf("spring" to 10.0, "drive" to 10.0))
        val scalings = mapOf("spring" to SPRING_HIGH, "drive" to DRIVE_LOW)
        val once = rawShade(mapOf("spring" to 1.0, "drive" to 3.0), scalings, loaded)
        val twice = rawShade(mapOf("spring" to 2.0, "drive" to 6.0), scalings, loaded)
        val figures = mapOf("spring" to 8.0, "drive" to 2.0)
        assertEquals(bounds(once, figures), bounds(twice, figures))
    }

    @Test
    fun under_the_minimum_means_even_the_best_case_is_under_it() {
        val loaded = listOf(mapOf("spring" to 0.0, "drive" to 0.0), mapOf("spring" to 10.0, "drive" to 10.0))
        val shade = rawShade(
            mapOf("spring" to 1.0, "drive" to 1.0), mapOf("spring" to SPRING_HIGH, "drive" to DRIVE_LOW), loaded, minimum = 0.8,
        )
        val couldYetReachIt = mapOf("spring" to 10.0, "drive" to null)
        assertIs<ShadedColour.Coloured>(shadeFor(shade, emptyMap(), couldYetReachIt))
        val cannotReachIt = mapOf("spring" to 0.0, "drive" to null)
        assertEquals(ShadedColour.Dimmed, shadeFor(shade, emptyMap(), cannotReachIt))
    }

    @Test
    fun the_list_orders_by_the_low_score_then_the_high_and_gives_ties_one_rank() {
        val loaded = listOf(mapOf("spring" to 0.0, "drive" to 0.0), mapOf("spring" to 10.0, "drive" to 10.0))
        val shade = rawShade(
            mapOf("spring" to 1.0, "drive" to 1.0), mapOf("spring" to SPRING_HIGH, "drive" to DRIVE_LOW), loaded,
        )
        val things = listOf("best", "tie-a", "tie-b", "unsure", "worst").map { it to emptyMap<String, Double?>() }
        val figures = mapOf(
            "best" to mapOf("spring" to 10.0, "drive" to 0.0),
            "tie-a" to mapOf("spring" to 5.0, "drive" to 5.0),
            "tie-b" to mapOf("spring" to 5.0, "drive" to 5.0),
            "unsure" to mapOf("spring" to 10.0, "drive" to null),
            "worst" to mapOf("spring" to 0.0, "drive" to 10.0),
        )
        val ranked = rankByScore(shade, things, top = 10, figuresById = figures)
        assertEquals(listOf("best", "unsure", "tie-a", "tie-b", "worst"), ranked.map { it.id })
        assertEquals(listOf(1, 2, 3, 3, 5), ranked.map { it.rank })
        assertEquals(listOf(true, false, true, true, true), ranked.map { it.isSettled })
        assertEquals(0.5, ranked[1].low)
        assertEquals(1.0, ranked[1].high)
    }

    @Test
    fun a_part_of_the_score_names_each_figure_with_its_share_and_a_missing_one_contributes_nothing() {
        val loaded = listOf(mapOf("spring" to 0.0, "drive" to 0.0), mapOf("spring" to 10.0, "drive" to 10.0))
        val shade = rawShade(
            mapOf("spring" to 1.0, "drive" to 1.0), mapOf("spring" to SPRING_HIGH, "drive" to DRIVE_LOW), loaded,
        )
        val parts = scorePartsOf(shade, emptyMap(), mapOf("spring" to 10.0, "drive" to null)).associateBy { it.field }
        assertEquals(0.5, parts.getValue("spring").contribution)
        assertEquals(1.0, parts.getValue("spring").value)
        assertNull(parts.getValue("drive").value)
        assertEquals(0.0, parts.getValue("drive").contribution)
    }

    @Test
    fun a_record_names_each_weighted_figure_its_good_end_and_an_optional_range() {
        val reading = readScoreShade("shade_by", record(scaling = mapOf(
            "spring" to mapOf("good" to "high"),
            "soil" to mapOf("good" to "low", "best" to 1.0, "worst" to 8.0),
        ), weights = mapOf("spring" to 1.0, "soil" to 1.0)))
        val shade = assertIs<ScoreShadeReading.Readable>(reading).shade
        assertEquals(GoodEnd.HIGH, shade.scalings.getValue("spring").good)
        assertEquals(SOIL_ONE_BEST_OF_EIGHT, shade.scalings.getValue("soil"))
        assertTrue(shade.isRaw)
    }

    @Test
    fun a_record_with_a_weighted_figure_that_has_no_scaling_is_refused_by_name() {
        val reading = readScoreShade("shade_by", record(scaling = mapOf("spring" to mapOf("good" to "high")), weights = mapOf("spring" to 1.0, "drive" to 1.0)))
        assertTrue(assertIs<ScoreShadeReading.Refused>(reading).notice.contains("drive"))
    }

    @Test
    fun a_scaling_with_a_bad_good_end_or_a_range_of_no_width_is_refused() {
        val badEnd = readScoreShade("shade_by", record(scaling = mapOf("spring" to mapOf("good" to "up")), weights = mapOf("spring" to 1.0)))
        assertTrue(assertIs<ScoreShadeReading.Refused>(badEnd).notice.contains("good"))
        val flat = readScoreShade("shade_by", record(scaling = mapOf("spring" to mapOf("best" to 3.0, "worst" to 3.0)), weights = mapOf("spring" to 1.0)))
        assertTrue(assertIs<ScoreShadeReading.Refused>(flat).notice.contains("range"))
        val neither = readScoreShade("shade_by", record(scaling = mapOf("spring" to emptyMap<String, Any?>()), weights = mapOf("spring" to 1.0)))
        assertIs<ScoreShadeReading.Refused>(neither)
    }

    private fun record(scaling: Map<String, Any?>, weights: Map<String, Double>): Map<String, Any?> = mapOf(
        "weights" to weights,
        "figure_scaling" to scaling,
        "ramp" to mapOf("stops" to listOf(listOf(0.0, "#2E86DE"), listOf(1.0, "#E67E22")), "no_figure" to "#FF00FF"),
    )
}
