package ai.factoredui.compose.detail

import kotlin.test.Test
import kotlin.test.assertEquals

class SelectionDetailsTest {
    @Test
    fun a_detail_is_read_with_its_sections_rows_values_and_notes() {
        val read = readSelectionDetails(
            mapOf("w1" to mapOf("title" to "Window", "sections" to listOf(mapOf("heading" to "Fields", "rows" to listOf(mapOf("label" to "sill", "value" to 1219, "note" to "Jim"), mapOf("label" to "open", "value" to null))))))
        )
        val detail = read.getValue("w1")
        assertEquals("Window", detail.title)
        assertEquals(listOf(DetailRow("sill", "1219", "Jim"), DetailRow("open", null, null)), detail.sections.single().rows)
    }

    @Test
    fun an_entry_without_a_title_or_a_row_without_a_label_is_left_out() {
        val read = readSelectionDetails(mapOf("a" to mapOf("sections" to emptyList<Any>()), "b" to mapOf("title" to "B", "sections" to listOf(mapOf("rows" to listOf(mapOf("value" to 1)))))))
        assertEquals(setOf("b"), read.keys)
        assertEquals(emptyList(), read.getValue("b").sections.single().rows)
    }
}
