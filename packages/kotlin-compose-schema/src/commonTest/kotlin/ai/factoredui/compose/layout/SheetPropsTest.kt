package ai.factoredui.compose.layout

import kotlin.test.Test
import kotlin.test.assertEquals

class SheetPropsTest {
    @Test
    fun the_defaults_are_peeking_at_96_dp_half_at_320_on_the_raised_ground() {
        val props = readSheetProps(emptyMap())
        assertEquals(listOf(SheetStop.PEEK, 96.0, 320.0, null), listOf(props.stop, props.peekDp, props.halfDp, props.background))
        assertEquals(emptyList(), props.notices)
    }

    @Test
    fun the_stop_sizes_and_background_are_read() {
        val props = readSheetProps(mapOf("stop" to "half", "peek_height" to 120, "half_height" to 400.0, "background" to "surface"))
        assertEquals(listOf(SheetStop.HALF, 120.0, 400.0, "surface"), listOf(props.stop, props.peekDp, props.halfDp, props.background))
    }

    @Test
    fun a_stop_that_is_not_peek_half_or_full_is_named_and_peek_is_used() {
        val props = readSheetProps(mapOf("stop" to "open"))
        assertEquals(SheetStop.PEEK, props.stop)
        assertEquals(listOf("sheet stop must be peek, half or full, not 'open'; peek is used."), props.notices)
    }

    @Test
    fun a_size_that_is_not_above_zero_keeps_its_default_and_is_named() {
        val props = readSheetProps(mapOf("peek_height" to 0, "half_height" to "tall"))
        assertEquals(listOf(96.0, 320.0), listOf(props.peekDp, props.halfDp))
        assertEquals(
            listOf("sheet peek_height must be a number above zero, not '0'; 96 is used.", "sheet half_height must be a number above zero, not 'tall'; 320 is used."),
            props.notices,
        )
    }

    @Test
    fun the_stops_step_up_and_down_and_stay_at_the_ends() {
        assertEquals(listOf(SheetStop.HALF, SheetStop.FULL, SheetStop.FULL), listOf(SheetStop.PEEK, SheetStop.HALF, SheetStop.FULL).map { it.up() })
        assertEquals(listOf(SheetStop.PEEK, SheetStop.PEEK, SheetStop.HALF), listOf(SheetStop.PEEK, SheetStop.HALF, SheetStop.FULL).map { it.down() })
    }
}
