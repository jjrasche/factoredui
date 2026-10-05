package ai.factoredui.worldbuilder

import ai.factoredui.worldengine.session.ScoreView
import kotlin.test.Test
import kotlin.test.assertEquals

class PanelTextTest {

    @Test
    fun wholeNumbersLoseTheirPointZeroAndFractionsKeepTwoPlaces() {
        assertEquals("625", formatNumber(625.0))
        assertEquals("0.69", formatNumber(0.688705))
        assertEquals("49.15", formatNumber(49.1536))
        assertEquals("87.4", formatNumber(87.4036))
        assertEquals("0", formatNumber(0.0))
    }

    @Test
    fun usageListsEveryUseWithItsTilesAndArea() {
        val uses = listOf(mapOf("id" to "pond", "label" to "Pond"), mapOf("id" to "path", "label" to "Path"))
        val text = usageLines(uses, counts = mapOf("pond" to 2), areas = mapOf("pond" to 1250.0))
        assertEquals("Pond: 2 tiles, 1250 sq ft\nPath: 0 tiles, 0 sq ft", text)
    }

    @Test
    fun usageShowsPlacedInstancesNextToTheTileCount() {
        val uses = listOf(mapOf("id" to "lidar_tree", "label" to "Lidar tree"), mapOf("id" to "shed", "label" to "Shed"))
        val tallies = mapOf("lidar_tree" to InstanceTally(measured = 10, proposed = 0), "shed" to InstanceTally(measured = 0, proposed = 2))
        assertEquals(
            "Lidar tree: 10 placed (10 measured), 0 tiles, 0 sq ft\nShed: 2 placed (2 proposed), 0 tiles, 0 sq ft",
            usageLines(uses, counts = emptyMap(), areas = emptyMap(), instances = tallies),
        )
    }

    @Test
    fun aTypeWithNoInstancesKeepsTheTileOnlyLine() {
        val uses = listOf(mapOf("id" to "shed", "label" to "Shed"))
        assertEquals("Shed: 1 tiles, 625 sq ft", usageLines(uses, mapOf("shed" to 1), mapOf("shed" to 625.0), mapOf("shed" to InstanceTally(0, 0))))
    }

    @Test
    fun scoresShowLabelValueUnitAndMarkTheBindingOnes() {
        val scores = listOf(
            ScoreView("pasture_yield_annual", "Pasture yield", 0.688705, "ton/year", true),
            ScoreView("neighbor_support", null, 0.5, "1", false),
        )
        assertEquals("Pasture yield: 0.69 ton/year (binding)\nneighbor_support: 0.5", scoreLines(scores))
    }

    @Test
    fun theBasePlanSaysItIsTheBase() {
        assertEquals("My plan is your base plan.", diffLine("My plan", null, emptyMap(), emptyMap()))
    }

    @Test
    fun aBranchListsOnlyTheUsesThatDifferFromItsParent() {
        val diff = mapOf("woodland_tree" to 3, "path" to -1, "pond" to 0)
        val labels = mapOf("woodland_tree" to "Woodland tree", "path" to "Path", "pond" to "Pond")
        assertEquals("Alternative 1 vs My plan: +3 Woodland tree, -1 Path", diffLine("Alternative 1", "My plan", diff, labels))
    }

    @Test
    fun largeNumbersGetThousandsSeparatorsAndKeepTheirSign() {
        assertEquals("4,375", formatGrouped(4375.0))
        assertEquals("1,234,567.89", formatGrouped(1234567.891))
        assertEquals("-12,500", formatGrouped(-12500.0))
        assertEquals("625", formatGrouped(625.0))
        assertEquals("0.4", formatGrouped(0.4))
    }

    @Test
    fun quantitiesReadInPlainUnits() {
        assertEquals("4,375 sq ft", formatQuantity(4375.0, "sq_ft"))
        assertEquals("49.15 hours/year", formatQuantity(49.1536, "hour/year"))
        assertEquals("0.4 tons/year", formatQuantity(0.4, "ton/year"))
        assertEquals("803.49 lb/year", formatQuantity(803.49, "lb/year"))
        assertEquals("$219.98", formatQuantity(219.98, "usd"))
        assertEquals("-$1,200", formatQuantity(-1200.0, "usd"))
        assertEquals("0.68", formatQuantity(0.68, "1"))
        assertEquals("not-measured", formatQuantity(null, "usd"))
    }

    @Test
    fun aChangeShowsItsSignOrSaysThereIsNone() {
        assertEquals("+49.15 hours/year", formatChange(49.15, "hour/year"))
        assertEquals("-$300", formatChange(-300.0, "usd"))
        assertEquals("no change", formatChange(0.0, "usd"))
    }

    @Test
    fun comparingTwoPlansListsEachScoreBeforeAfterAndTheChange() {
        val mine = listOf(ScoreView("labour", "Labour hours", 38.25, "hour/year", false), ScoreView("capex", "Capital floor", 0.0, "usd", false))
        val alternative = listOf(ScoreView("labour", "Labour hours", 87.4, "hour/year", false), ScoreView("capex", "Capital floor", null, "usd", false))
        assertEquals(
            "Labour hours: 38.25 hours/year to 87.4 hours/year (+49.15 hours/year)\nCapital floor: $0 to not-measured (not-measured)",
            compareLines(mine, alternative),
        )
    }

    @Test
    fun comparingListsOnlyTheFiguresThatMoved() {
        val mine = listOf(ScoreView("a", "Area", 10.0, "sq_ft", false), ScoreView("b", "Labour", 5.0, "hour/year", false))
        val alternative = listOf(ScoreView("a", "Area", 10.0, "sq_ft", false), ScoreView("b", "Labour", 6.0, "hour/year", false))
        assertEquals("Labour: 5 hours/year to 6 hours/year (+1 hours/year)", compareLines(mine, alternative))
    }

    @Test
    fun comparingTwoIdenticalPlansSaysNothingDiffers() {
        val scores = listOf(ScoreView("a", "Area", 10.0, "sq_ft", false))
        assertEquals("No figure differs yet.", compareLines(scores, scores))
    }

    @Test
    fun aBranchWithNoDifferenceSaysItMatchesItsParent() {
        assertEquals("Alternative 1 matches My plan", diffLine("Alternative 1", "My plan", mapOf("pond" to 0), mapOf("pond" to "Pond")))
    }
}
