package ai.factoredui.compose.schema

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

private val json = Json { ignoreUnknownKeys = true }

private fun specWith(extra: String): Spec = json.decodeFromString(Spec.serializer(), """{"spec_version":1,"renderer_min":1,$extra"root":{"id":"n","type":"text"}}""")

class SpecThemeFieldTest {
    @Test
    fun a_spec_may_name_the_theme_it_is_drawn_in() {
        assertEquals("dark", specWith(""""theme":"dark",""").theme)
    }

    @Test
    fun a_spec_that_names_no_theme_leaves_the_choice_to_the_host() {
        assertEquals(null, specWith("").theme)
    }
}
