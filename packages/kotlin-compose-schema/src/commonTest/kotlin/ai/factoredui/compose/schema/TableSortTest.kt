package ai.factoredui.compose.schema

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private fun row(label: String, value: Any?) = mapOf("label" to label, "values" to mapOf("qty" to value))

private val ROWS = listOf(row("pier", 12.0), row("beam", null), row("Anchor", 2.0), row("slab", 100.0))

class TableSortTest {

    @Test
    fun no_sort_keeps_the_view_order() {
        assertEquals(listOf(0, 1, 2, 3), tableRowOrder(ROWS, null))
    }

    @Test
    fun numbers_sort_by_value_not_by_their_digits() {
        assertEquals(listOf(2, 0, 3, 1), tableRowOrder(ROWS, TableSort("qty", false)))
    }

    @Test
    fun descending_reverses_the_numbers_but_rows_with_no_value_stay_last() {
        assertEquals(listOf(3, 0, 2, 1), tableRowOrder(ROWS, TableSort("qty", true)))
    }

    @Test
    fun the_label_column_sorts_text_without_regard_to_case() {
        assertEquals(listOf(2, 1, 0, 3), tableRowOrder(ROWS, TableSort(LABEL_SORT_COLUMN, false)))
    }

    @Test
    fun equal_values_keep_their_view_order() {
        val ties = listOf(row("a", 1.0), row("b", 1.0), row("c", 1.0))
        assertEquals(listOf(0, 1, 2), tableRowOrder(ties, TableSort("qty", false)))
    }

    @Test
    fun tapping_the_sorted_column_flips_it_and_any_other_sorts_it_ascending() {
        assertEquals(TableSort("qty", false), nextTableSort(null, "qty"))
        assertEquals(TableSort("qty", true), nextTableSort(TableSort("qty", false), "qty"))
        assertEquals(TableSort("label", false), nextTableSort(TableSort("qty", true), "label"))
    }

    @Test
    fun a_sort_is_read_only_from_a_named_column() {
        assertNull(readTableSort(null, true))
        assertNull(readTableSort("", false))
        assertEquals(TableSort("qty", true), readTableSort("qty", true))
        assertEquals(TableSort("qty", false), readTableSort("qty", "yes"))
    }
}
