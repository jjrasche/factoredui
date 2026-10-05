package ai.factoredui.compose.layout

data class TileCell(val col: Int, val row: Int, val use: String)

const val ERASE_BRUSH = "erase"

fun applyBrush(cells: List<TileCell>, cols: Int, rows: Int, col: Int, row: Int, brush: String?): List<TileCell> {
    val settled = settleCells(cells)
    if (col !in 0 until cols || row !in 0 until rows) return settled
    val existing = settled.firstOrNull { it.col == col && it.row == row }
    val others = settled.filterNot { it.col == col && it.row == row }
    val erases = brush == null || brush == ERASE_BRUSH || existing?.use == brush
    return if (erases) others else sortedByPosition(others + TileCell(col, row, brush!!))
}

private fun settleCells(cells: List<TileCell>): List<TileCell> =
    sortedByPosition(cells.associateBy { it.col to it.row }.values.toList())

private fun sortedByPosition(cells: List<TileCell>): List<TileCell> =
    cells.sortedWith(compareBy({ it.row }, { it.col }))

fun countUses(cells: List<TileCell>): Map<String, Int> =
    cells.groupingBy { it.use }.eachCount()

fun areasOf(counts: Map<String, Int>, tileArea: Double): Map<String, Double> =
    counts.mapValues { it.value * tileArea }
