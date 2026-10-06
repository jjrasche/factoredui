package ai.factoredui.compose.terrain

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class TerrainGridTest {

    @Test
    fun sampledHeightAtAVertexIsThatVertexHeight() {
        val grid = cone()
        assertEquals(1000.0, sampleHeightMm(grid, 4.0, 4.0))
        assertEquals(grid.heightAt(6, 3).toDouble(), sampleHeightMm(grid, 6.0, 3.0))
    }

    @Test
    fun sampledHeightInsideATileIsBilinearInItsFourCorners() {
        val grid = fieldOf(1, 1) { col, row -> listOf(0, 40, 100, 300)[row * 2 + col] }
        assertEquals(110.0, sampleHeightMm(grid, 0.5, 0.5))
        assertEquals(20.0, sampleHeightMm(grid, 0.5, 0.0))
        assertEquals(80.0, sampleHeightMm(grid, 0.25, 0.5))
    }

    @Test
    fun samplingOutsideTheGridClampsToItsEdge() {
        val grid = planeRisingEast()
        assertEquals(50.0, sampleHeightMm(grid, -3.0, 2.0))
        assertEquals(450.0, sampleHeightMm(grid, 9.0, -1.0))
    }

    @Test
    fun aGridRefusesAHeightCountThatIsNotColsPlusOneByRowsPlusOne() {
        assertFailsWith<IllegalArgumentException> { TerrainGrid(2, 2, IntArray(4)) }
    }

    @Test
    fun theFingerprintChangesWithTheHeightsEvenWhenTheVersionDoesNot() {
        val first = fieldOf(1, 1, version = 3) { _, _ -> 10 }
        val second = fieldOf(1, 1, version = 3) { col, _ -> 10 + col }
        assertNotEquals(first.fingerprint, second.fingerprint)
    }

    @Test
    fun theTerrainBindingResolvesToHeightsAndCutFillOfTheDeclaredSize() {
        val field = resolveTerrain(mapOf("cols" to 1, "rows" to 1, "version" to 7L, "heights_mm" to listOf(1, 2, 3, 4), "cut_fill_mm" to listOf(0, -5, 0, 6)))!!
        assertContentEquals(intArrayOf(1, 2, 3, 4), field.heights.heightsMm)
        assertContentEquals(intArrayOf(0, -5, 0, 6), field.cutFill.heightsMm)
        assertEquals(7L, field.heights.version)
    }

    @Test
    fun aTerrainBindingWithoutCutFillReadsAsNoChange() {
        val field = resolveTerrain(mapOf("cols" to 1, "rows" to 1, "heights_mm" to listOf(1.4, 2.6, 3, 4)))!!
        assertContentEquals(intArrayOf(1, 3, 3, 4), field.heights.heightsMm)
        assertContentEquals(IntArray(4), field.cutFill.heightsMm)
    }

    @Test
    fun aTerrainBindingWithTheWrongHeightCountIsIgnored() {
        assertNull(resolveTerrain(mapOf("cols" to 2, "rows" to 1, "heights_mm" to listOf(1, 2, 3, 4))))
        assertNull(resolveTerrain(mapOf("cols" to 1, "rows" to 1, "heights_mm" to listOf(1, 2, "x", 4))))
        assertNull(resolveTerrain("{terrain}"))
    }

    @Test
    fun modeIntervalAndUnitsResolveWithSafeDefaults() {
        assertEquals(TerrainMode.HEAT, resolveTerrainMode("heat"))
        assertEquals(TerrainMode.CUTFILL, resolveTerrainMode("CutFill"))
        assertEquals(TerrainMode.OFF, resolveTerrainMode("sparkle"))
        assertEquals(TerrainMode.OFF, resolveTerrainMode(null))
        assertEquals(25, resolveContourIntervalMm("25"))
        assertEquals(100, resolveContourIntervalMm(100.0))
        assertEquals(DEFAULT_CONTOUR_INTERVAL_MM, resolveContourIntervalMm(0))
        assertEquals(DEFAULT_CONTOUR_INTERVAL_MM, resolveContourIntervalMm(null))
        assertEquals(true, resolveContoursShown(true))
        assertEquals(false, resolveContoursShown(null))
        assertEquals(TerrainUnits.METRES, resolveTerrainUnits("m"))
        assertEquals(TerrainUnits.FEET, resolveTerrainUnits(null))
    }
}
