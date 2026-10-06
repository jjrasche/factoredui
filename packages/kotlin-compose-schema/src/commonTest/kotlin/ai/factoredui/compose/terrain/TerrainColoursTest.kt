package ai.factoredui.compose.terrain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TerrainColoursTest {

    private fun red(argb: Int) = (argb ushr 16) and 0xFF

    @Test
    fun theLowestGroundIsTheFirstHeatStopAndTheHighestIsTheLast() {
        assertEquals(HEAT_RAMP.first(), heatColourFor(200.0, 200.0, 600.0))
        assertEquals(HEAT_RAMP.last(), heatColourFor(600.0, 200.0, 600.0))
        assertEquals(HEAT_RAMP[2], heatColourFor(400.0, 200.0, 600.0))
        assertEquals(HEAT_RAMP[1], heatColourFor(300.0, 200.0, 600.0))
    }

    @Test
    fun heightsOutsideTheRangeClampToTheEndStops() {
        assertEquals(HEAT_RAMP.first(), heatColourFor(-50.0, 200.0, 600.0))
        assertEquals(HEAT_RAMP.last(), heatColourFor(900.0, 200.0, 600.0))
    }

    @Test
    fun flatGroundTakesTheMiddleHeatStop() {
        assertEquals(HEAT_RAMP[2], heatColourFor(500.0, 500.0, 500.0))
    }

    @Test
    fun betweenTwoStopsTheHeatColourMixesTheirChannels() {
        val eighth = heatColourFor(250.0, 200.0, 600.0)
        assertEquals(mixArgb(HEAT_RAMP[0], HEAT_RAMP[1], 0.5), eighth)
        assertEquals(0xFF56A1B3.toInt(), eighth)
    }

    @Test
    fun noChangeIsNeutralTheDeepestCutIsTheCutColourAndTheHighestFillTheFillColour() {
        assertEquals(NEUTRAL_COLOUR, cutFillColourFor(0.0, 137.0))
        assertEquals(CUT_COLOUR, cutFillColourFor(-137.0, 137.0))
        assertEquals(FILL_COLOUR, cutFillColourFor(137.0, 137.0))
        assertEquals(CUT_COLOUR, cutFillColourFor(-400.0, 137.0))
        assertEquals(NEUTRAL_COLOUR, cutFillColourFor(-10.0, 0.0))
    }

    @Test
    fun aShallowCutIsPartWayFromNeutralToTheCutColour() {
        val halfCut = cutFillColourFor(-50.0, 100.0)
        assertEquals(mixArgb(NEUTRAL_COLOUR, CUT_COLOUR, 0.5), halfCut)
        assertTrue(red(halfCut) in red(CUT_COLOUR)..red(NEUTRAL_COLOUR))
    }

    @Test
    fun theHeatRasterRunsFromTheLowStopOnTheLowSideToTheHighStopOnTheHighSide() {
        val raster = heatRaster(fieldOf(2, 1) { col, _ -> 100 * col })
        assertEquals(8, raster.width)
        assertEquals(4, raster.height)
        assertEquals(rampColourAt(HEAT_RAMP, 1.0 / 16), raster.at(0, 0))
        assertEquals(rampColourAt(HEAT_RAMP, 15.0 / 16), raster.at(7, 3))
    }

    @Test
    fun aDugTileIsTheCutColourAtItsCentreAndNeutralFarAway() {
        val dug = fieldOf(4, 4) { col, row -> if (col in 1..2 && row in 1..2) -120 else 0 }
        val raster = cutFillRaster(dug)
        assertEquals(CUT_COLOUR, raster.at(6, 6))
        assertEquals(NEUTRAL_COLOUR, raster.at(15, 15))
    }

    @Test
    fun theHillshadeRasterIsGreyAtTheSampledBrightness() {
        val raster = hillshadeRaster(flatField(), 100.0, 1.0)
        assertEquals(setOf(argbOf(180, 180, 180)), raster.values.toSet())
    }

    @Test
    fun modeOffBakesNoRaster() {
        val field = TerrainField(flatField(), flatField())
        assertNull(terrainRaster(TerrainMode.OFF, field, 100.0))
        assertEquals(HEAT_RAMP[2], terrainRaster(TerrainMode.HEAT, field, 100.0)!!.values.first())
    }
}
