package ai.factoredui.compose.terrain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TerrainLegendTest {

    private val sideMm = 3048.0

    @Test
    fun fixedDecimalsRoundHalfAwayAndNeverPrintMinusZero() {
        assertEquals("734.55", formatFixed(734.548, 2))
        assertEquals("0.00", formatFixed(-0.004, 2))
        assertEquals("-1.5", formatFixed(-1.5, 1))
        assertEquals("12", formatFixed(12.0, 0))
        assertEquals("0.05", formatFixed(0.05, 2))
    }

    @Test
    fun aLengthIsShownInMetresAndFeet() {
        assertEquals("223.89 m / 734.55 ft", lengthLabel(223891.0))
    }

    @Test
    fun contourLabelsUseTheChosenUnit() {
        assertEquals("0.82 ft", contourLabel(250, TerrainUnits.FEET))
        assertEquals("0.25 m", contourLabel(250, TerrainUnits.METRES))
    }

    @Test
    fun theHeatLegendShowsTheRampAndTheLowestAndHighestGround() {
        val field = TerrainField(planeRisingEast(), flatField())
        val legend = terrainLegendFor(TerrainMode.HEAT, field, isContoursShown = false, intervalMm = 50, tileSideMm = sideMm)!!
        assertEquals("Height", legend.title)
        assertEquals(HEAT_RAMP, legend.ramp)
        assertEquals("low 0.05 m / 0.16 ft", legend.lowLabel)
        assertEquals("high 0.45 m / 1.48 ft", legend.highLabel)
        assertEquals(emptyList(), legend.notes)
    }

    @Test
    fun withTerrainOffAndNoContoursThereIsNoLegend() {
        assertNull(terrainLegendFor(TerrainMode.OFF, TerrainField(flatField(), flatField()), isContoursShown = false, intervalMm = 50, tileSideMm = sideMm))
    }

    @Test
    fun contoursAloneGetALegendNamingTheInterval() {
        val legend = terrainLegendFor(TerrainMode.OFF, TerrainField(cone(), flatField()), isContoursShown = true, intervalMm = 50, tileSideMm = sideMm)!!
        assertEquals("Contours", legend.title)
        assertEquals(listOf("Contours every 50 mm (0.16 ft), heavier every 250 mm"), legend.notes)
    }

    @Test
    fun theCutFillLegendNamesTheDeepestCutAndTheHighestFill() {
        val dug = fieldOf(2, 2) { col, row -> if (col == 1 && row == 1) -137 else 0 }
        val legend = terrainLegendFor(TerrainMode.CUTFILL, TerrainField(flatField(), dug), isContoursShown = false, intervalMm = 50, tileSideMm = sideMm)!!
        assertEquals(listOf(CUT_COLOUR, NEUTRAL_COLOUR, FILL_COLOUR), legend.ramp)
        assertEquals("cut 0.14 m / 0.45 ft", legend.lowLabel)
        assertEquals("fill 0.00 m / 0.00 ft", legend.highLabel)
    }

    @Test
    fun anUntouchedPlanSaysThereIsNoCutOrFill() {
        val legend = terrainLegendFor(TerrainMode.CUTFILL, TerrainField(flatField(), fieldOf(1, 1) { _, _ -> 0 }), isContoursShown = false, intervalMm = 50, tileSideMm = sideMm)!!
        assertEquals(listOf("No cut or fill on this plan yet"), legend.notes)
    }

    @Test
    fun theHillshadeLegendSaysHowMuchTheReliefIsExaggerated() {
        val gentle = fieldOf(2, 2) { col, _ -> 152 * col }
        val legend = terrainLegendFor(TerrainMode.HILLSHADE, TerrainField(gentle, gentle), isContoursShown = false, intervalMm = 50, tileSideMm = sideMm)!!
        assertEquals(HILLSHADE_RAMP, legend.ramp)
        assertEquals(listOf("Relief exaggerated 20 times"), legend.notes)
    }

    @Test
    fun viewScalesShareABucketUntilTheyDifferByAQuarterDoubling() {
        assertEquals(0, viewScaleBucket(1f))
        assertEquals(0, viewScaleBucket(1.05f))
        assertEquals(4, viewScaleBucket(2f))
        assertEquals(-4, viewScaleBucket(0.5f))
        assertEquals(2f, bucketScale(4))
    }
}
