package ai.factoredui.compose.terrain

import ai.factoredui.compose.layout.GroundPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ContoursTest {

    @Test
    fun levelsAreTheIntervalMultiplesStrictlyInsideTheHeightRange() {
        assertEquals(listOf(-100L, -50L, 0L, 50L, 100L), contourLevels(-120, 130, 50))
        assertEquals(listOf(50L), contourLevels(0, 100, 50))
        assertEquals(emptyList(), contourLevels(500, 500, 50))
    }

    @Test
    fun everyFifthLevelIsAnIndexContour() {
        assertTrue(isIndexContour(250, 50))
        assertTrue(isIndexContour(0, 50))
        assertTrue(isIndexContour(-250, 50))
        assertFalse(isIndexContour(300, 50))
    }

    @Test
    fun aPlaneGivesStraightOpenLinesOnePerLevelRunningNorthToSouth() {
        val lines = contourPolylines(planeRisingEast(), 100)
        assertEquals(listOf(100L, 200L, 300L, 400L), lines.map { it.levelMm })
        lines.forEach { line ->
            assertFalse(line.isClosed)
            assertEquals(5, line.points.size)
            assertEquals(setOf(line.levelMm / 100f - 0.5f), line.points.map { it.x }.toSet())
            assertEquals(0f, line.points.first().y)
            assertEquals(4f, line.points.last().y)
        }
    }

    @Test
    fun aConeGivesNineClosedConcentricRings() {
        val lines = contourPolylines(cone(), 100)
        assertEquals((1..9).map { it * 100L }, lines.map { it.levelMm })
        assertTrue(lines.all { it.isClosed })
        val eastReach = lines.map { line -> line.points.maxOf { it.x } }
        assertEquals(eastReach.sortedDescending(), eastReach, "higher rings sit inside lower ones: $eastReach")
        assertTrue(lines.all { line -> line.points.minOf { it.x } < 4f && line.points.maxOf { it.x } > 4f })
    }

    @Test
    fun aBowlGivesNineClosedRingsTooWithTheLowestInnermost() {
        val lines = contourPolylines(bowl(), 100)
        assertEquals(9, lines.size)
        assertTrue(lines.all { it.isClosed })
        val eastReach = lines.map { line -> line.points.maxOf { it.x } }
        assertEquals(eastReach.sorted(), eastReach)
    }

    @Test
    fun aSaddleIsResolvedByItsCentreSoTheHighCornersJoinAcrossIt() {
        val lines = contourPolylines(saddle(), 50)
        assertEquals(2, lines.size)
        assertTrue(lines.none { it.isClosed })
        val pieces = lines.map { it.points.toSet() }.toSet()
        assertEquals(setOf(setOf(GroundPoint(0.5f, 0f), GroundPoint(1f, 0.5f)), setOf(GroundPoint(0f, 0.5f), GroundPoint(0.5f, 1f))), pieces)
    }

    @Test
    fun aSaddleWhoseCentreIsLowCutsOffTheHighCornersInstead() {
        val lowCentre = fieldOf(1, 1) { col, row -> if (col == row) 100 else 0 }
        val lines = contourPolylines(lowCentre, 60)
        val pieces = lines.map { it.points.toSet() }.toSet()
        assertEquals(setOf(setOf(GroundPoint(0f, 0.4f), GroundPoint(0.4f, 0f)), setOf(GroundPoint(1f, 0.6f), GroundPoint(0.6f, 1f))), pieces)
    }

    @Test
    fun flatGroundHasNoContours() {
        assertEquals(emptyList(), contourPolylines(flatField(), 50))
    }

    @Test
    fun aOneTileFieldRisingSouthHasOneLineAcrossItsMiddle() {
        val lines = contourPolylines(oneTileRisingSouth(), 50)
        assertEquals(1, lines.size)
        assertEquals(setOf(GroundPoint(0f, 0.5f), GroundPoint(1f, 0.5f)), lines.single().points.toSet())
        assertFalse(lines.single().isClosed)
    }

    @Test
    fun aLabelSitsAtTheNorthernmostPointOfItsLine() {
        val ring = contourPolylines(cone(), 100).first { it.levelMm == 500L }
        val anchor = labelAnchor(ring)
        assertEquals(ring.points.minOf { it.y }, anchor.y)
        assertTrue(anchor.y < 4f)
    }

    @Test
    fun anOpenLineIsLabelledAtItsMiddleNotWhereItMeetsTheEdge() {
        val line = contourPolylines(planeRisingEast(), 100).first()
        assertEquals(GroundPoint(0.5f, 2f), labelAnchor(line))
    }

    @Test
    fun onlyIndexLinesAreLabelledAndALabelTooCloseToAnEarlierOneIsDropped() {
        val steep = fieldOf(4, 4) { col, _ -> 100 * col }
        val placements = contourLabelPlacements(contourPolylines(steep, 20), 20, minPoints = 2, minSpacingTiles = 1.5f)
        assertEquals(listOf(100L, 300L), placements.map { it.line.levelMm })
        assertEquals(listOf(GroundPoint(1f, 2f), GroundPoint(3f, 2f)), placements.map { it.anchor })
    }

    @Test
    fun aLineWithTooFewPointsGoesUnlabelled() {
        val steep = fieldOf(4, 4) { col, _ -> 100 * col }
        assertEquals(emptyList(), contourLabelPlacements(contourPolylines(steep, 20), 20, minPoints = 6, minSpacingTiles = 0f))
    }

    @Test
    fun tracingIsDeterministic() {
        val first = contourPolylines(bowl(), 50).map { it.points }
        val second = contourPolylines(bowl(), 50).map { it.points }
        assertEquals(first, second)
    }
}
