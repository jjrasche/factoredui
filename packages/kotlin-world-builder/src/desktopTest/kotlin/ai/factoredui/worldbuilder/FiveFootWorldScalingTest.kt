package ai.factoredui.worldbuilder

import ai.factoredui.worldengine.session.DispatchResult
import ai.factoredui.worldengine.session.WorldSession
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

private const val TILE_RATIO = 5
private const val TOLERANCE = 1e-9

private fun quarterAcreSession(): WorldSession = openSession(File(designDirectory(), "worlds/parcel-five-acre.world.json").path)

private fun fiveFootSession(): WorldSession = openSession(File("examples/parcel-five-acre-5ft.world.json").path)

private fun WorldSession.placeAt(use: String, col: Int, row: Int) {
    assertIs<DispatchResult.Accepted>(tap(col, row, use), "$use at $col,$row")
}

private fun WorldSession.scoreValues(): Map<String, Double?> = scores().associate { it.id to it.value }

class FiveFootWorldScalingTest {

    private val quarterAcreFarm = quarterAcreSession().apply {
        placeAt("hoop_house", 2, 2)
        placeAt("paddock", 6, 10)
    }

    private val fiveFootFarm = fiveFootSession().apply {
        placeAt("hoop_house", 2 * TILE_RATIO, 2 * TILE_RATIO)
        placeAt("paddock", 6 * TILE_RATIO, 10 * TILE_RATIO)
    }

    @Test
    fun theSameAcreageGivesTheSameFigureForEveryScoreInBothWorlds() {
        val coarse = quarterAcreFarm.scoreValues()
        val fine = fiveFootFarm.scoreValues()
        assertEquals(coarse.keys, fine.keys)
        val disagreements = coarse.keys.filter { id ->
            val expected = coarse.getValue(id)
            val actual = fine.getValue(id)
            expected == null || actual == null || abs(expected - actual) > TOLERANCE * maxOf(1.0, abs(expected))
        }
        assertTrue(disagreements.isEmpty(), "scores that differ for the same acreage: ${disagreements.associateWith { coarse[it] to fine[it] }}")
    }

    @Test
    fun aHoopHouseOfTwoQuarterAcreTilesCostsFortyNineHoursWhicheverGridDrawsIt() {
        val hoopHouseHours = { session: WorldSession -> session.scoreValues().getValue("hoop_house_labor") }
        assertEquals(49.153645833333336, hoopHouseHours(quarterAcreFarm)!!, TOLERANCE)
        assertEquals(49.153645833333336, hoopHouseHours(fiveFootFarm)!!, TOLERANCE)
    }

    @Test
    fun theAreaScoresCountTheSameSquareFeetInBothWorlds() {
        listOf("area_paddock" to 625.0, "area_hoop_house" to 1250.0).forEach { (id, squareFeet) ->
            assertEquals(squareFeet, fiveFootFarm.scoreValues().getValue(id)!!, TOLERANCE, id)
            assertEquals(squareFeet, quarterAcreFarm.scoreValues().getValue(id)!!, TOLERANCE, id)
        }
    }
}
