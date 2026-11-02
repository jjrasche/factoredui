package ai.factoredui.compose.schema

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

private val json = Json { ignoreUnknownKeys = true }

class ButtonPropsTest {
    private fun sizeOf(size: String?): ButtonSize {
        val props = if (size == null) "{}" else """{"size":"$size"}"""
        return json.decodeFromString(SpecNode.serializer(), """{"id":"b","type":"button","props":$props}""").props.asButtonProps().size
    }

    @Test
    fun xl_is_a_size_of_its_own() {
        assertEquals(ButtonSize.XL, sizeOf("xl"))
    }

    @Test
    fun the_sizes_that_were_read_before_are_read_the_same() {
        assertEquals(listOf(ButtonSize.SM, ButtonSize.LG, ButtonSize.MD, ButtonSize.MD), listOf(sizeOf("sm"), sizeOf("lg"), sizeOf("md"), sizeOf(null)))
    }
}
