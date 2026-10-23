package ai.factoredui.compose.schema

import kotlin.test.Test
import kotlin.test.assertEquals

class ListDirectionTest {
    @Test
    fun a_list_is_horizontal_only_when_its_direction_says_so() {
        val read = listOf(mapOf("direction" to SpecValue.StringValue("horizontal")), mapOf("direction" to SpecValue.StringValue("vertical")), emptyMap()).map { it.asListProps().isHorizontal }
        assertEquals(listOf(true, false, false), read)
    }
}
