package ai.factoredui.compose.schema

import kotlin.test.Test
import kotlin.test.assertEquals

private fun layoutWith(vararg props: Pair<String, SpecValue>): LayoutProps = mapOf(*props).asLayoutProps()

class SlotSizeTest {

    @Test
    fun a_width_and_a_height_of_dp_above_0_are_read_with_no_fault() {
        val layout = layoutWith("width" to SpecValue.NumberValue(120.0), "height" to SpecValue.NumberValue(520.0))
        assertEquals(120f to 520f, layout.width to layout.height)
        assertEquals(emptyList(), layout.sizeFaults)
    }

    @Test
    fun an_absent_or_null_size_is_no_size_and_no_fault() {
        val layout = layoutWith("height" to SpecValue.NullValue)
        assertEquals(null to null, layout.width to layout.height)
        assertEquals(emptyList(), layout.sizeFaults)
    }

    @Test
    fun a_height_of_zero_less_not_a_number_or_not_finite_is_ignored_and_named() {
        listOf(
            SpecValue.NumberValue(0.0) to "0",
            SpecValue.NumberValue(-5.0) to "-5",
            SpecValue.NumberValue(Double.NaN) to "NaN",
            SpecValue.NumberValue(Double.POSITIVE_INFINITY) to "Infinity",
            SpecValue.StringValue("tall") to "tall",
            SpecValue.BooleanValue(true) to "true",
        ).forEach { (raw, shown) ->
            val layout = layoutWith("height" to raw)
            assertEquals(null, layout.height, shown)
            assertEquals(listOf("height must be a number of dp above 0, not '$shown'; it is ignored."), layout.sizeFaults)
        }
    }

    @Test
    fun a_width_of_zero_less_or_not_a_number_is_ignored_and_named_the_same_way() {
        listOf(SpecValue.NumberValue(0.0) to "0", SpecValue.NumberValue(-0.5) to "-0.5", SpecValue.NumberValue(Double.NaN) to "NaN", SpecValue.StringValue("wide") to "wide").forEach { (raw, shown) ->
            val layout = layoutWith("width" to raw)
            assertEquals(null, layout.width, shown)
            assertEquals(listOf("width must be a number of dp above 0, not '$shown'; it is ignored."), layout.sizeFaults)
        }
    }

    @Test
    fun a_bad_width_and_a_bad_height_are_both_named_width_first() =
        assertEquals(
            listOf("width must be a number of dp above 0, not '0'; it is ignored.", "height must be a number of dp above 0, not '-1'; it is ignored."),
            layoutWith("height" to SpecValue.NumberValue(-1.0), "width" to SpecValue.NumberValue(0.0)).sizeFaults,
        )
}
