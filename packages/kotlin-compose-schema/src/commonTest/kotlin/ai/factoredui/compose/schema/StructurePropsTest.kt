package ai.factoredui.compose.schema

import ai.factoredui.compose.structure.LevelFocus
import ai.factoredui.compose.structure.StructureDrawing
import kotlin.test.Test
import kotlin.test.assertEquals

class StructurePropsTest {

    @Test
    fun well_formed_props_read_with_no_notes() {
        val props = resolveStructureProps(
            mapOf("view" to "west", "focus" to 1.0, "show_lower" to false, "hide_top" to 2.0, "highlight" to listOf("e1"), "on_part_tap" to "tapped", "color_by" to "utilisation"),
        )
        assertEquals(StructureProps(StructureDrawing.WEST, LevelFocus.OneLevel(1), false, 2, setOf("e1"), "tapped"), props)
    }

    @Test
    fun absent_props_read_as_their_defaults_with_no_notes() {
        assertEquals(StructureProps(), resolveStructureProps(emptyMap()))
        assertEquals(StructureProps(), resolveStructureProps(mapOf("focus" to "all")))
    }

    @Test
    fun an_unknown_view_draws_the_plan_and_says_so() {
        val props = resolveStructureProps(mapOf("view" to "isometric"))
        assertEquals(StructureDrawing.PLAN, props.drawing)
        assertEquals(listOf("view 'isometric' is not plan, iso, south or west; the plan is drawn."), props.notes)
    }

    @Test
    fun a_focus_that_is_neither_all_nor_a_whole_level_shows_every_level_and_says_so() {
        assertEquals(listOf("focus 'top' is not all or a stack level number; every level is shown."), resolveStructureProps(mapOf("focus" to "top")).notes)
        val fractional = resolveStructureProps(mapOf("focus" to 1.5))
        assertEquals(LevelFocus.AllLevels, fractional.focus)
        assertEquals(listOf("focus '1.5' is not all or a stack level number; every level is shown."), fractional.notes)
    }

    @Test
    fun a_highlight_that_is_not_a_list_highlights_nothing_and_says_so() {
        val props = resolveStructureProps(mapOf("highlight" to "e1"))
        assertEquals(emptySet(), props.highlightIds)
        assertEquals(listOf("highlight must be a list of part ids, not 'e1'; nothing is highlighted."), props.notes)
    }

    @Test
    fun a_highlight_entry_that_is_not_a_part_id_is_named() {
        val props = resolveStructureProps(mapOf("highlight" to listOf("e1", 3)))
        assertEquals(setOf("e1"), props.highlightIds)
        assertEquals(listOf("highlight entries that are not part ids are not drawn: 3"), props.notes)
    }

    @Test
    fun a_show_lower_that_is_not_a_bool_hides_the_lower_levels_and_says_so() {
        val props = resolveStructureProps(mapOf("show_lower" to "yes"))
        assertEquals(false, props.showLower)
        assertEquals(listOf("show_lower must be true or false, not 'yes'; lower levels are hidden."), props.notes)
    }

    @Test
    fun a_hide_top_that_is_not_a_whole_count_hides_nothing_and_says_so() {
        assertEquals(listOf("hide_top must be a whole number of levels, not '1.5'; no level is hidden."), resolveStructureProps(mapOf("hide_top" to 1.5)).notes)
        assertEquals(listOf("hide_top must be a whole number of levels, not '-1'; no level is hidden."), resolveStructureProps(mapOf("hide_top" to -1)).notes)
        assertEquals(0, resolveStructureProps(mapOf("hide_top" to "two")).hideTopCount)
        assertEquals(listOf("hide_top must be a whole number of levels, not '1.5'; no level is hidden."), resolveStructureProps(mapOf("hide_top" to "1.5")).notes)
    }

    @Test
    fun a_hide_top_written_as_whole_number_text_by_a_select_hides_that_many_levels_as_focus_already_reads_it() {
        val fromSelect = resolveStructureProps(mapOf("hide_top" to "1", "focus" to "2"))
        assertEquals(1, fromSelect.hideTopCount)
        assertEquals(LevelFocus.OneLevel(2), fromSelect.focus)
        assertEquals(emptyList(), fromSelect.notes)
    }

    @Test
    fun an_on_part_tap_that_is_not_an_action_name_says_taps_dispatch_nothing() {
        val props = resolveStructureProps(mapOf("on_part_tap" to 3))
        assertEquals(null, props.onPartTap)
        assertEquals(listOf("on_part_tap must be an action name, not '3'; a tap dispatches nothing."), props.notes)
    }

    @Test
    fun an_unknown_color_by_colours_by_utilisation_and_says_so() {
        assertEquals(listOf("color_by 'weight' is not a colouring this renderer knows; parts are coloured by utilisation."), resolveStructureProps(mapOf("color_by" to "weight")).notes)
    }
}
