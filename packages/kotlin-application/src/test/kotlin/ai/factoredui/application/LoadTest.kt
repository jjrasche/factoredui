package ai.factoredui.application

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private val LOAD = """
{"sections": {
  "expansion": {"ok": true, "asked": true, "extent_mm": [10, 10], "objects": [], "instances": [], "supplied": {}},
  "geometry": {"ok": false, "asked": true, "error": {"rule": "config-solution-unknown", "message": "glazing has no such solution", "setting": "glazing.solution"}},
  "overlay": {"ok": null, "asked": true, "skipped": "the domains offer no overlay"}}}
"""

class LoadTest {
    @Test
    fun a_section_that_answered_is_read_by_the_reader_of_its_command() {
        assertEquals(10.0 to 10.0, readExpansion(readLoaded(LOAD).textOf("expansion")).extentMm)
    }

    @Test
    fun a_refused_section_says_its_message_and_the_setting_at_fault() {
        val refusal = assertFailsWith<SectionRefusedException> { readLoaded(LOAD).textOf("geometry") }
        assertEquals("glazing has no such solution (setting: glazing.solution)", refusal.message)
        assertEquals("glazing.solution", refusal.setting)
    }

    @Test
    fun a_skipped_section_is_skipped_and_an_answered_one_is_not() {
        assertTrue(readLoaded(LOAD).isSkipped("overlay"))
        assertFalse(readLoaded(LOAD).isSkipped("expansion"))
    }
}
