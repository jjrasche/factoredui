package ai.factoredui.worldbuilder

import ai.factoredui.worldengine.session.ScoreView
import kotlin.test.Test
import kotlin.test.assertEquals

private fun score(id: String, label: String, value: Double?, unit: String, note: String? = null, source: String? = null) =
    ScoreView(id, label, value, unit, isBinding = false, note = note, source = source)

class ScorePanelTest {

    @Test
    fun unitsFallIntoPlainGroups() {
        assertEquals("Cost", scoreGroupOf("usd"))
        assertEquals("Labour", scoreGroupOf("hour/year"))
        assertEquals("Yield", scoreGroupOf("ton/year"))
        assertEquals("Yield", scoreGroupOf("lb/year"))
        assertEquals("Neighbours", scoreGroupOf("1"))
        assertEquals("Other", scoreGroupOf("furlong"))
    }

    @Test
    fun groupsComeInAFixedOrderWithFormattedLines() {
        val scores = listOf(
            score("n", "Projected neighbour support", 0.68, "1"),
            score("c", "Sourced capital floor", 1219.98, "usd"),
            score("l", "Labour hours", 87.4, "hour/year"),
        )
        val groups = scoreGroups(scores, emptyList())
        assertEquals(listOf("Cost", "Labour", "Neighbours"), groups.map { it.title })
        assertEquals("Sourced capital floor: $1,219.98", groups.first().rows.single().line)
        assertEquals("c", groups.first().rows.single().id)
    }

    @Test
    fun anAreaScoreThatRepeatsAUsageLineIsLeftOut() {
        val scores = listOf(score("a", "Paddock area", 4375.0, "sq_ft"), score("t", "Total frontage", 120.0, "sq_ft"))
        val rows = scoreGroups(scores, useLabels = listOf("Paddock")).flatMap { it.rows }
        assertEquals(listOf("t"), rows.map { it.id })
    }

    @Test
    fun aScoreWithNoValueReadsNotMeasured() {
        val line = scoreGroups(listOf(score("n", "Neighbour support", null, "1")), emptyList()).single().rows.single().line
        assertEquals("Neighbour support: not-measured", line)
    }

    @Test
    fun theDetailGivesTheWorldsOwnNoteAndSource() {
        val capital = score("c", "Sourced capital floor", 220.0, "usd", note = "a floor: banked up-front prices only", source = "twin capex-per-use; row r-12")
        assertEquals(
            listOf(
                "What it means: a floor: banked up-front prices only",
                "Where the figure comes from: twin capex-per-use; row r-12",
                "Sourced prices only. No price yet for: Hoop house, Pond.",
            ),
            scoreDetailLines(capital, listOf("Hoop house", "Pond")),
        )
    }

    @Test
    fun aScoreWithNoSourceSaysSoAndNonCostScoresSayNothingAboutPrices() {
        val labour = score("l", "Labour hours", 87.4, "hour/year")
        assertEquals(listOf("Where the figure comes from: not stated in the world file"), scoreDetailLines(labour, listOf("Pond")))
    }

    @Test
    fun theDetailTitleIsTheLabelOrTheIdWhenThereIsNone() {
        assertEquals("Labour hours", scoreDetailTitle(score("l", "Labour hours", 1.0, "hour/year")))
        assertEquals("raw_id", scoreDetailTitle(ScoreView("raw_id", null, 1.0, "1", false)))
    }
}
