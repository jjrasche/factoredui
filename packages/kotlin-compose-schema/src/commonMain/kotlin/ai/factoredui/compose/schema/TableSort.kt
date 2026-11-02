package ai.factoredui.compose.schema

data class TableSort(val column: String, val isDescending: Boolean)

const val LABEL_SORT_COLUMN = "label"

fun readTableSort(column: Any?, isDescending: Any?): TableSort? =
    (column as? String)?.takeIf { it.isNotEmpty() }?.let { TableSort(it, isDescending == true) }

/** Tapping the sorted column flips its direction; tapping any other sorts it ascending. */
fun nextTableSort(current: TableSort?, tapped: String): TableSort =
    if (current?.column == tapped) TableSort(tapped, !current.isDescending) else TableSort(tapped, false)

private fun cellOf(row: Map<String, Any?>, column: String): Any? =
    if (column == LABEL_SORT_COLUMN) row["label"] else (row["values"] as? Map<*, *>)?.get(column)

private fun compareCells(left: Any, right: Any): Int = when {
    left is Number && right is Number -> left.toDouble().compareTo(right.toDouble())
    left is Number -> -1
    right is Number -> 1
    else -> left.toString().lowercase().compareTo(right.toString().lowercase())
}

/** The row indices in drawn order: numbers by value, text without regard to case, rows with no value last whichever way it sorts. */
fun tableRowOrder(rows: List<Map<String, Any?>>, sort: TableSort?): List<Int> {
    val indices = rows.indices.toList()
    if (sort == null) return indices
    val (present, absent) = indices.partition { cellOf(rows[it], sort.column) != null }
    val ordered = present.sortedWith { a, b -> compareCells(cellOf(rows[a], sort.column)!!, cellOf(rows[b], sort.column)!!) }
    return (if (sort.isDescending) ordered.asReversed() else ordered) + absent
}
