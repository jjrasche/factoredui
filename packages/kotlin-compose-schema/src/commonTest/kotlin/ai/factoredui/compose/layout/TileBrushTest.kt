package ai.factoredui.compose.layout

import kotlin.test.Test
import kotlin.test.assertEquals

class TileBrushTest {

    private fun cell(col: Int, row: Int, use: String) = TileCell(col, row, use)

    @Test
    fun aBrushOnAnEmptyTilePlacesItsUse() {
        assertEquals(listOf(cell(2, 3, "pond")), applyBrush(emptyList(), 13, 26, 2, 3, "pond"))
    }

    @Test
    fun aDifferentBrushOnAnOccupiedTileReplacesTheUse() {
        val after = applyBrush(listOf(cell(2, 3, "pond")), 13, 26, 2, 3, "path")
        assertEquals(listOf(cell(2, 3, "path")), after)
    }

    @Test
    fun theSameBrushOnAnOccupiedTileRemovesIt() {
        assertEquals(emptyList(), applyBrush(listOf(cell(2, 3, "pond")), 13, 26, 2, 3, "pond"))
    }

    @Test
    fun theEraseBrushRemovesWhateverIsThere() {
        assertEquals(emptyList(), applyBrush(listOf(cell(2, 3, "pond")), 13, 26, 2, 3, "erase"))
    }

    @Test
    fun noBrushRemovesWhateverIsThere() {
        assertEquals(emptyList(), applyBrush(listOf(cell(2, 3, "pond")), 13, 26, 2, 3, null))
    }

    @Test
    fun erasingAnEmptyTileChangesNothing() {
        val cells = listOf(cell(0, 0, "path"))
        assertEquals(cells, applyBrush(cells, 13, 26, 5, 5, "erase"))
    }

    @Test
    fun placingOutsideTheParcelChangesNothing() {
        val cells = listOf(cell(0, 0, "path"))
        assertEquals(cells, applyBrush(cells, 13, 26, 13, 0, "pond"))
        assertEquals(cells, applyBrush(cells, 13, 26, 0, 26, "pond"))
        assertEquals(cells, applyBrush(cells, 13, 26, -1, 0, "pond"))
    }

    @Test
    fun otherTilesAreUntouchedAndTheResultIsOrderedByRowThenColumn() {
        val after = applyBrush(listOf(cell(5, 1, "path"), cell(0, 0, "tree")), 13, 26, 2, 1, "pond")
        assertEquals(listOf(cell(0, 0, "tree"), cell(2, 1, "pond"), cell(5, 1, "path")), after)
    }

    @Test
    fun aDuplicatedCellKeepsTheLastUse() {
        val after = applyBrush(listOf(cell(1, 1, "path"), cell(1, 1, "pond")), 13, 26, 4, 4, "tree")
        assertEquals(listOf(cell(1, 1, "pond"), cell(4, 4, "tree")), after)
    }

    @Test
    fun countingTalliesTilesPerUse() {
        val counts = countUses(listOf(cell(0, 0, "tree"), cell(1, 0, "tree"), cell(2, 0, "pond")))
        assertEquals(mapOf("tree" to 2, "pond" to 1), counts)
    }

    @Test
    fun areasMultiplyEachCountByTheTileArea() {
        assertEquals(mapOf("tree" to 1250.0, "pond" to 625.0), areasOf(mapOf("tree" to 2, "pond" to 1), tileArea = 625.0))
    }

    @Test
    fun anAreaOfOneMakesAreasEqualCounts() {
        assertEquals(mapOf("tree" to 2.0), areasOf(mapOf("tree" to 2), tileArea = 1.0))
    }
}
