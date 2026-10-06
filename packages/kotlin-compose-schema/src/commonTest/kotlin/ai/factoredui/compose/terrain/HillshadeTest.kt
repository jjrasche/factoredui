package ai.factoredui.compose.terrain

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HillshadeTest {

    private val sideMm = 100.0

    private fun TerrainRaster.atGround(x: Double, y: Double): Int = at((x * SAMPLES_PER_TILE).toInt(), (y * SAMPLES_PER_TILE).toInt())

    @Test
    fun theRasterHasFourSamplesPerTileEdge() {
        val raster = hillshadeBrightness(cone(), sideMm)
        assertEquals(32, raster.width)
        assertEquals(32, raster.height)
        assertEquals(32 * 32, raster.values.size)
    }

    @Test
    fun flatGroundIsLitBySineOfTheSunAltitudeEverywhere() {
        val raster = hillshadeBrightness(flatField(), sideMm)
        assertEquals(setOf(180), raster.values.toSet())
    }

    @Test
    fun aPlaneIsOneBrightnessAndASlopeFacingTheSunIsBrighterThanFlat() {
        val facingWest = hillshadeBrightness(fieldOf(4, 4) { col, _ -> 100 * col }, sideMm)
        assertEquals(setOf(218), facingWest.values.toSet())
        val facingNorthWest = hillshadeBrightness(fieldOf(4, 4) { col, row -> 100 * col + 100 * row }, sideMm)
        assertEquals(setOf(251), facingNorthWest.values.toSet())
    }

    @Test
    fun aSlopeFacingAwayFromTheSunIsInFullShade() {
        val facingSouthEast = hillshadeBrightness(fieldOf(4, 4) { col, row -> 100 * (4 - col) + 100 * (4 - row) }, sideMm)
        assertEquals(setOf(0), facingSouthEast.values.toSet())
    }

    @Test
    fun aConeIsLitOnItsNorthWestFlankAndShadedOnItsSouthEastFlank() {
        val raster = hillshadeBrightness(cone(), sideMm, reliefExaggeration = 1.0)
        val northWest = raster.atGround(2.6, 2.6)
        val southEast = raster.atGround(5.4, 5.4)
        assertTrue(northWest > 180 && southEast < 180, "north-west flank $northWest, south-east flank $southEast")
    }

    @Test
    fun aConeShadesTheSameEitherSideOfTheSunsLineOfApproach() {
        val raster = hillshadeBrightness(cone(), sideMm)
        for (y in 0 until raster.height) {
            for (x in 0 until raster.width) {
                assertTrue(abs(raster.at(x, y) - raster.at(y, x)) <= 1, "sample $x,$y is ${raster.at(x, y)} but its mirror is ${raster.at(y, x)}")
            }
        }
    }

    @Test
    fun aBowlIsLitOnItsSouthEastInnerWallAndShadedOnItsNorthWestInnerWall() {
        val raster = hillshadeBrightness(bowl(), sideMm)
        val northWestWall = raster.atGround(2.6, 2.6)
        val southEastWall = raster.atGround(5.4, 5.4)
        assertTrue(southEastWall > 180 && northWestWall < 180, "south-east wall $southEastWall, north-west wall $northWestWall")
    }

    @Test
    fun aOneTileFieldGivesSixteenSamplesOfOneBrightness() {
        val raster = hillshadeBrightness(oneTileRisingSouth(), sideMm)
        assertEquals(16, raster.values.size)
        assertEquals(1, raster.values.toSet().size)
    }

    @Test
    fun reliefIsExaggeratedSoTheSteepestStepReadsAsFortyFiveDegrees() {
        assertEquals(10.0, reliefExaggeration(fieldOf(2, 2) { col, _ -> 10 * col }, sideMm))
        assertEquals(1.0, reliefExaggeration(flatField(), sideMm))
        assertEquals(1.0, reliefExaggeration(planeRisingEast(), sideMm))
        assertEquals(MAX_RELIEF_EXAGGERATION, reliefExaggeration(fieldOf(2, 2) { col, _ -> col }, sideMm))
    }

    @Test
    fun exaggeratingAGentleSlopeDeepensItsShade() {
        val gentle = fieldOf(4, 4) { col, row -> 10 * (4 - col) + 10 * (4 - row) }
        val plain = hillshadeBrightness(gentle, sideMm).values.first()
        val exaggerated = hillshadeBrightness(gentle, sideMm, reliefExaggeration = 10.0).values.first()
        assertTrue(exaggerated < plain, "exaggerated $exaggerated against plain $plain")
        assertEquals(0, exaggerated)
    }
}
