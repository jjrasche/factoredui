package ai.factoredui.compose.schema

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GeomapBoundsRequestTest {
    private val corners = listOf(-85.9, 42.8, -85.3, 43.1)

    @Test
    fun a_request_with_only_bounds_asks_for_the_default_margin_and_no_inset() {
        val request = resolveGeomapBoundsRequest(mapOf("bounds" to corners))!!
        assertNull(request.margin)
        assertEquals(0.0, request.insetBottomDp)
    }

    @Test
    fun a_margin_and_a_bottom_inset_in_the_viewport_record_are_read() {
        val request = resolveGeomapBoundsRequest(mapOf("bounds" to corners, "margin" to 0.6, "inset_bottom" to 440))!!
        assertEquals(0.6, request.margin)
        assertEquals(440.0, request.insetBottomDp)
    }

    @Test
    fun a_margin_outside_zero_to_one_and_a_negative_inset_are_ignored() {
        val request = resolveGeomapBoundsRequest(mapOf("bounds" to corners, "margin" to 4.0, "inset_bottom" to -50))!!
        assertNull(request.margin)
        assertEquals(0.0, request.insetBottomDp)
    }
}
