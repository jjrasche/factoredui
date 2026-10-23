package ai.factoredui.compose.schema

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StackPlacementTest {
    @Test
    fun a_child_names_where_it_sits_by_its_anchor() {
        assertEquals(StackAnchor.BOTTOM, mapOf("anchor" to SpecValue.StringValue("bottom")).asStackPlacement().anchor)
        assertEquals(StackAnchor.FILL, mapOf("anchor" to SpecValue.StringValue("fill")).asStackPlacement().anchor)
        assertEquals(StackAnchor.END, mapOf("anchor" to SpecValue.StringValue("end")).asStackPlacement().anchor)
        assertNull(mapOf("anchor" to SpecValue.StringValue("middle")).asStackPlacement().anchor)
    }

    @Test
    fun a_toggle_reads_the_colour_of_its_label() {
        assertEquals("#e6e8eb", mapOf("label" to SpecValue.StringValue("x"), "color" to SpecValue.StringValue("#e6e8eb")).asToggleProps().color)
    }
}
