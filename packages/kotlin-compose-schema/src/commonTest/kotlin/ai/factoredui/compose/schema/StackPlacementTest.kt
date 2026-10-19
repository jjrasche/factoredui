package ai.factoredui.compose.schema

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private fun sheet(closed: Double, open: Double) =
    SpecValue.ObjectValue(mapOf("closed_dp" to SpecValue.NumberValue(closed), "open_dp" to SpecValue.NumberValue(open)))

class StackPlacementTest {
    @Test
    fun a_child_names_where_it_sits_by_its_anchor() {
        assertEquals(StackAnchor.BOTTOM, mapOf("anchor" to SpecValue.StringValue("bottom")).asStackPlacement().anchor)
        assertEquals(StackAnchor.FILL, mapOf("anchor" to SpecValue.StringValue("fill")).asStackPlacement().anchor)
        assertNull(mapOf("anchor" to SpecValue.StringValue("middle")).asStackPlacement().anchor)
    }

    @Test
    fun a_sheet_gives_a_closed_and_an_open_height_and_the_open_one_is_not_less_than_the_closed() {
        assertEquals(SheetProps(300f, 700f), mapOf("sheet" to sheet(300.0, 700.0)).asStackPlacement().sheet)
        assertNull(mapOf("sheet" to sheet(700.0, 300.0)).asStackPlacement().sheet)
        assertNull(mapOf("sheet" to sheet(0.0, 300.0)).asStackPlacement().sheet)
    }

    @Test
    fun a_toggle_reads_the_colour_of_its_label() {
        assertEquals("#e6e8eb", mapOf("label" to SpecValue.StringValue("x"), "color" to SpecValue.StringValue("#e6e8eb")).asToggleProps().color)
    }
}
