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
    fun theMainBranchSaysItIsOnMain() {
        assertEquals("on main", diffLine("main", null, emptyMap(), emptyMap()))
    }

    @Test
    fun aBranchListsOnlyTheUsesThatDifferFromItsParent() {
        val diff = mapOf("woodland_tree" to 3, "path" to -1, "pond" to 0)
        val labels = mapOf("woodland_tree" to "Woodland tree", "path" to "Path", "pond" to "Pond")
        assertEquals("proposal-1 vs main: +3 Woodland tree, -1 Path", diffLine("proposal-1", "main", diff, labels))
    }

    @Test
    fun aBranchWithNoDifferenceSaysItMatchesItsParent() {
        assertEquals("proposal-1 matches main", diffLine("proposal-1", "main", mapOf("pond" to 0), mapOf("pond" to "Pond")))
    }
}
