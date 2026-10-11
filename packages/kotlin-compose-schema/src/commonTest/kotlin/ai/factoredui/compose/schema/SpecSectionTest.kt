package ai.factoredui.compose.schema

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private const val SCREEN = """{"spec_version":1,"renderer_min":1,"root":{"id":"page","type":"column","children":[]}}"""

private const val UI_FILE = """{"projection":{"x":"east"},"screen":$SCREEN,"sprites":[],"types":{}}"""

class SpecSectionTest {

    @Test
    fun a_section_names_the_top_level_key_whose_spec_is_drawn_and_its_siblings_are_ignored() =
        assertEquals("page", decodeSpecFile(UI_FILE, section = "screen").root.id)

    @Test
    fun without_a_section_the_whole_file_is_the_spec() =
        assertEquals("page", decodeSpecFile(SCREEN, section = null).root.id)

    @Test
    fun a_section_the_file_lacks_is_refused_naming_the_keys_it_holds() {
        val failure = assertFailsWith<SpecSectionException> { decodeSpecFile(UI_FILE, section = "scene") }
        assertEquals("the file has no section 'scene'; its top-level keys are projection, screen, sprites, types", failure.message)
    }

    @Test
    fun a_section_that_is_not_a_spec_object_is_refused() {
        val failure = assertFailsWith<SpecSectionException> { decodeSpecFile(UI_FILE, section = "sprites") }
        assertEquals("section 'sprites' must be a spec object with a root, not []", failure.message)
    }
}
