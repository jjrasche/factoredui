package ai.factoredui.compose.renderer

import ai.factoredui.compose.detail.readSelectionDetails
import kotlin.test.Test
import kotlin.test.assertEquals

private val DETAILS = readSelectionDetails(
    mapOf("w1" to mapOf("title" to "Window", "sections" to listOf(mapOf("heading" to "Fields", "rows" to listOf(mapOf("label" to "sill", "value" to "1219", "note" to "Jim"), mapOf("label" to "open", "value" to null))))))
)

class SelectionDetailTextTest {
    @Test
    fun the_single_selected_thing_is_its_title_then_each_section_with_its_rows_indented() {
        assertEquals("Window\n\nFields\n  sill: 1219\n    Jim\n  open: –", selectionDetailText(setOf("w1"), DETAILS))
    }

    @Test
    fun nothing_or_several_selected_is_no_text() {
        assertEquals("", selectionDetailText(emptySet(), DETAILS))
        assertEquals("", selectionDetailText(setOf("w1", "w2"), DETAILS))
    }
}
