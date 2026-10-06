package ai.factoredui.compose.layout

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ViewRotationTest {

    private val cols = 13
    private val rows = 26

    private fun near(expected: GroundPoint, actual: GroundPoint) =
        assertTrue(abs(expected.x - actual.x) < 1e-4f && abs(expected.y - actual.y) < 1e-4f, "$expected vs $actual")

    @Test
    fun everyTurnUndoesExactlyOnANonSquareParcel() {
        val point = GroundPoint(3.25f, 17.5f)
        for (turns in -5..5) near(point, unrotateGround(rotateGround(point, turns, cols, rows), turns, cols, rows))
    }

    @Test
    fun aQuarterTurnCarriesTheWorldEastAxisOntoTheViewSouthAxis() {
        val origin = rotateGround(GroundPoint(0f, 0f), 1, cols, rows)
        val east = rotateGround(GroundPoint(1f, 0f), 1, cols, rows)
        near(GroundPoint(0f, 1f), GroundPoint(east.x - origin.x, east.y - origin.y))
    }

    @Test
    fun theTurnedParcelFillsTheTurnedGridExactly() {
        for (turns in 0..3) {
            val (turnedCols, turnedRows) = rotatedGridSize(turns, cols, rows)
            val corners = listOf(GroundPoint(0f, 0f), GroundPoint(cols.toFloat(), 0f), GroundPoint(cols.toFloat(), rows.toFloat()), GroundPoint(0f, rows.toFloat()))
                .map { rotateGround(it, turns, cols, rows) }
            assertEquals(0f, corners.minOf { it.x })
            assertEquals(0f, corners.minOf { it.y })
            assertEquals(turnedCols.toFloat(), corners.maxOf { it.x })
            assertEquals(turnedRows.toFloat(), corners.maxOf { it.y })
        }
    }

    @Test
    fun aSpriteFacingTurnsWithTheViewAndWrapsAfterFour() {
        assertEquals(1, rotatedFacing(0, 1))
        assertEquals(0, rotatedFacing(3, 1))
        assertEquals(3, rotatedFacing(0, -1))
        assertEquals(2, rotatedFacing(2, 4))
    }
}
