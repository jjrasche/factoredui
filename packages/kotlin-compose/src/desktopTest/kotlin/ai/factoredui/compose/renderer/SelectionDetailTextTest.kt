package ai.factoredui.compose.renderer

import ai.factoredui.compose.detail.readSelectionDetails
import kotlin.test.Test
import kotlin.test.assertEquals

private val DETAILS = readSelectionDetails(
    mapOf("w1" to mapOf("title" to "Window", "sections" to listOf(mapOf("heading" to "Fields", "rows" to listOf(mapOf("label" to "sill", "value" to "1219", "note" to "Jim"), mapOf("label" to "open", "value" to null))))))
)

class SelectionDetailTextTest {
    @Test
    fun the_single_selected_thing_is_its_title_heading_rows_and_notes_as_lines() {
        assertEquals("Window\nFields\nsill: 1219\nJim\nopen: –", selectionDetailText(setOf("w1"), DETAILS))
    }

    @Test
    fun nothing_or_several_selected_is_no_text() {
        assertEquals("", selectionDetailText(emptySet(), DETAILS))
        assertEquals("", selectionDetailText(setOf("w1", "w2"), DETAILS))
    }
}
