package ai.factoredui.compose.schema

import ai.factoredui.compose.ramp.RampStop
import ai.factoredui.compose.stackedscene.ColourScale
import ai.factoredui.compose.stackedscene.ColourStop
import ai.factoredui.compose.stackedscene.SceneCamera
import ai.factoredui.compose.stackedscene.LevelFocus
import ai.factoredui.compose.stackedscene.NEUTRAL_PART_COLOUR
import ai.factoredui.compose.stackedscene.StackedDrawing
import ai.factoredui.compose.stackedscene.YAxis
import kotlin.test.Test
import kotlin.test.assertEquals

private fun sceneProps(resolved: Map<String, Any?>) = resolveStackedSceneProps(resolved, "board")

private val COLOR_BY = mapOf(
    "channel" to "load",
    "stops" to listOf(mapOf("up_to" to 0.25, "color" to "#2e7d32", "label" to "light"), mapOf("up_to" to 1, "color" to "#f9a825"), mapOf("color" to "#7b1fa2", "label" to "over")),
    "unknown_color" to "#8899aa",
    "unknown_label" to "no figure",
    "format" to "percent",
)

private val SCALE = ColourScale(
    channel = "load",
    stops = listOf(ColourStop(0.25, 0xFF2E7D32, "light"), ColourStop(1.0, 0xFFF9A825), ColourStop(null, 0xFF7B1FA2, "over")),
    unknownColour = 0xFF8899AA,
    unknownLabel = "no figure",
    format = "percent",
)

private val THREAT_SCALE = mapOf("channel" to "threat", "stops" to listOf(mapOf("up_to" to 0.5, "color" to "#2e7d32", "label" to "calm"), mapOf("color" to "#c62828", "label" to "danger")))

private val HP_SCALE = mapOf("channel" to "hp", "stops" to listOf(mapOf("color" to "#1565c0", "label" to "alive")), "format" to "percent")

private val SCHEMES = mapOf("threat" to THREAT_SCALE, "hp" to HP_SCALE)

private val THREAT = ColourScale("threat", listOf(ColourStop(0.5, 0xFF2E7D32, "calm"), ColourStop(null, 0xFFC62828, "danger")))

private val HP = ColourScale("hp", listOf(ColourStop(null, 0xFF1565C0, "alive")), format = "percent")

private fun schemeProps(scheme: Any?, schemes: Any? = SCHEMES) = sceneProps(mapOf("color_by" to mapOf("scheme" to scheme, "schemes" to schemes)))

class StackedScenePropsTest {

    @Test
    fun a_color_by_with_a_ramp_instead_of_stops_reads_as_a_continuous_scale() {
        val ramp = mapOf("stops" to listOf(listOf(0, "#000000"), listOf(1, "#ffffff")), "no_figure_text" to "none")
        val scale = sceneProps(mapOf("color_by" to mapOf("channel" to "hp", "ramp" to ramp, "format" to "percent"))).colourScale!!
        assertEquals("hp", scale.channel)
        assertEquals(listOf(RampStop(0.0, 0xFF000000), RampStop(1.0, 0xFFFFFFFF)), scale.ramp!!.stops)
        assertEquals("percent", scale.format)
        assertEquals("none", scale.unknownLabel)
    }

    @Test
    fun a_ramp_the_renderer_cannot_read_draws_one_colour_and_names_the_fault() {
        val props = sceneProps(mapOf("color_by" to mapOf("channel" to "hp", "ramp" to mapOf("stops" to listOf(listOf(0, "#000000"))))))
        assertEquals(null, props.colourScale)
        assertEquals(listOf("color_by ramp needs at least 2 stops, not 1; parts are drawn in one colour."), props.notes)
    }

    @Test
    fun a_scale_with_both_stops_and_a_ramp_uses_the_ramp_and_says_so() {
        val ramp = mapOf("stops" to listOf(listOf(0, "#000000"), listOf(1, "#ffffff")))
        val props = sceneProps(mapOf("color_by" to mapOf("channel" to "hp", "ramp" to ramp, "stops" to emptyList<Any>())))
        assertEquals(2, props.colourScale!!.ramp!!.stops.size)
        assertEquals(listOf("color_by has both stops and a ramp; the ramp is used."), props.notes)
    }

    @Test
    fun a_scheme_may_carry_a_ramp_beside_a_scheme_of_stops() {
        val ramp = mapOf("stops" to listOf(listOf(0, "#000000"), listOf(1, "#ffffff")))
        val schemes = mapOf("threat" to THREAT_SCALE, "hp" to mapOf("channel" to "hp", "ramp" to ramp))
        assertEquals(THREAT, schemeProps("threat", schemes).colourScale)
        assertEquals("hp", schemeProps("hp", schemes).colourScale!!.channel)
        assertEquals(emptyList(), schemeProps("hp", schemes).notes)
    }

    @Test
    fun y_axis_reads_as_unstated_by_default_and_up_or_down_when_named() {
        assertEquals(null, sceneProps(emptyMap()).yAxis)
        assertEquals(YAxis.UP, sceneProps(mapOf("y_axis" to "up")).yAxis)
        val down = sceneProps(mapOf("y_axis" to "down"))
        assertEquals(YAxis.DOWN, down.yAxis)
        assertEquals(emptyList(), down.notes)
    }

    @Test
    fun a_y_axis_that_is_neither_up_nor_down_draws_up_and_says_so() {
        val props = sceneProps(mapOf("y_axis" to "south"))
        assertEquals(YAxis.UP, props.yAxis)
        assertEquals(listOf("y_axis 'south' is not up or down; y is drawn up."), props.notes)
    }

    @Test
    fun a_color_by_with_schemes_colours_by_the_scheme_it_names() {
        val props = schemeProps("hp")
        assertEquals(HP, props.colourScale)
        assertEquals(emptyList(), props.notes)
    }

    @Test
    fun each_scheme_is_a_whole_scale_with_its_own_channel_stops_legend_and_format() {
        assertEquals(THREAT, schemeProps("threat").colourScale)
        assertEquals(HP, schemeProps("hp").colourScale)
    }

    @Test
    fun a_missing_scheme_colours_by_the_first_scheme_in_declared_order_with_no_note() {
        val unnamed = sceneProps(mapOf("color_by" to mapOf("schemes" to SCHEMES)))
        assertEquals(THREAT, unnamed.colourScale)
        assertEquals(emptyList(), unnamed.notes)
        val unbound = schemeProps(null)
        assertEquals(THREAT, unbound.colourScale)
        assertEquals(emptyList(), unbound.notes)
    }

    @Test
    fun a_scheme_the_schemes_do_not_hold_draws_one_colour_and_names_the_schemes() {
        val props = schemeProps("mana")
        assertEquals(null, props.colourScale)
        assertEquals(listOf("color_by scheme 'mana' is not one of the schemes threat, hp; parts are drawn in one colour."), props.notes)
    }

    @Test
    fun a_scheme_that_is_not_a_name_draws_one_colour_and_names_the_schemes() {
        val props = schemeProps(3)
        assertEquals(null, props.colourScale)
        assertEquals(listOf("color_by scheme must be one of the schemes threat, hp, not '3'; parts are drawn in one colour."), props.notes)
    }

    @Test
    fun schemes_that_are_not_an_object_of_scales_or_hold_none_draw_one_colour_and_say_so() {
        assertEquals(listOf("color_by schemes must be an object of named scales, not 'threat'; parts are drawn in one colour."), schemeProps("threat", schemes = "threat").notes)
        assertEquals(listOf("color_by schemes holds no scheme; parts are drawn in one colour."), schemeProps(null, schemes = emptyMap<String, Any>()).notes)
    }

    @Test
    fun a_fault_inside_the_chosen_scheme_is_named_with_that_scheme() {
        val broken = mapOf("threat" to THREAT_SCALE, "hp" to mapOf("stops" to emptyList<Any>()))
        assertEquals(listOf("color_by scheme 'hp' names no channel; parts are drawn in one colour."), schemeProps("hp", schemes = broken).notes)
        val badStop = mapOf("hp" to mapOf("channel" to "hp", "stops" to listOf(mapOf("color" to "blue"))))
        assertEquals(listOf("color_by scheme 'hp' stop 0 color must be #rrggbb, not 'blue'; it is left out."), schemeProps("hp", schemes = badStop).notes)
    }

    @Test
    fun a_fault_inside_a_scheme_not_chosen_is_not_reported() {
        val broken = mapOf("threat" to THREAT_SCALE, "hp" to mapOf("stops" to emptyList<Any>()))
        assertEquals(emptyList(), schemeProps("threat", schemes = broken).notes)
    }

    @Test
    fun well_formed_props_read_with_no_notes() {
        val props = sceneProps(
            mapOf("view" to "west", "focus" to 1.0, "show_lower" to false, "hide_top" to 2.0, "highlight" to listOf("e1"), "on_part_tap" to "tapped", "color_by" to COLOR_BY),
        )
        assertEquals(StackedSceneProps(StackedDrawing.WEST, LevelFocus.OneLevel(1), false, 2, setOf("e1"), "tapped", SCALE), props)
    }

    @Test
    fun color_by_reads_the_channel_the_stops_in_order_the_unknown_colour_the_labels_and_the_format() {
        val scale = sceneProps(mapOf("color_by" to COLOR_BY)).colourScale
        assertEquals(SCALE, scale)
    }

    @Test
    fun with_no_color_by_there_is_no_scale_and_no_note() {
        val props = sceneProps(emptyMap())
        assertEquals(null, props.colourScale)
        assertEquals(emptyList(), props.notes)
    }

    @Test
    fun a_color_by_that_is_not_an_object_draws_one_colour_and_says_so() {
        val props = sceneProps(mapOf("color_by" to "load"))
        assertEquals(null, props.colourScale)
        assertEquals(listOf("color_by must be an object naming a channel and its stops, not 'load'; parts are drawn in one colour."), props.notes)
    }

    @Test
    fun a_color_by_with_no_channel_or_no_stop_list_draws_one_colour_and_says_which() {
        assertEquals(listOf("color_by names no channel; parts are drawn in one colour."), sceneProps(mapOf("color_by" to mapOf("stops" to emptyList<Any>()))).notes)
        assertEquals(listOf("color_by stops must be a list, not 'null'; parts are drawn in one colour."), sceneProps(mapOf("color_by" to mapOf("channel" to "load"))).notes)
    }

    @Test
    fun a_stop_the_renderer_cannot_read_is_left_out_by_number_and_the_rest_still_colour() {
        val props = sceneProps(
            mapOf("color_by" to mapOf("channel" to "load", "stops" to listOf(mapOf("up_to" to "half", "color" to "#2e7d32"), mapOf("color" to "red"), "x", mapOf("color" to "#7b1fa2")))),
        )
        assertEquals(listOf(ColourStop(null, 0xFF7B1FA2)), props.colourScale!!.stops)
        assertEquals(
            listOf(
                "color_by stop 0 up_to must be a number, not 'half'; it is left out. " +
                    "color_by stop 1 color must be #rrggbb, not 'red'; it is left out. " +
                    "color_by stop 2 must be an object with a color, not 'x'; it is left out.",
            ),
            props.notes,
        )
    }

    @Test
    fun an_unreadable_unknown_colour_falls_back_to_the_neutral_grey_and_says_so() {
        val props = sceneProps(mapOf("color_by" to mapOf("channel" to "load", "stops" to emptyList<Any>(), "unknown_color" to "grey")))
        assertEquals(NEUTRAL_PART_COLOUR, props.colourScale!!.unknownColour)
        assertEquals(listOf("color_by unknown_color must be #rrggbb, not 'grey'; the neutral grey is used."), props.notes)
    }

    @Test
    fun absent_props_read_as_their_defaults_with_no_notes() {
        assertEquals(StackedSceneProps(), sceneProps(emptyMap()))
        assertEquals(StackedSceneProps(), sceneProps(mapOf("focus" to "all")))
    }

    @Test
    fun an_unknown_view_draws_the_plan_and_says_so() {
        val props = sceneProps(mapOf("view" to "isometric"))
        assertEquals(StackedDrawing.PLAN, props.drawing)
        assertEquals(listOf("view 'isometric' is not plan, iso, south or west; the plan is drawn."), props.notes)
    }

    @Test
    fun a_focus_that_is_neither_all_nor_a_whole_level_shows_every_level_and_says_so() {
        assertEquals(listOf("focus 'top' is not all or a stack level number; every level is shown."), sceneProps(mapOf("focus" to "top")).notes)
        val fractional = sceneProps(mapOf("focus" to 1.5))
        assertEquals(LevelFocus.AllLevels, fractional.focus)
        assertEquals(listOf("focus '1.5' is not all or a stack level number; every level is shown."), fractional.notes)
    }

    @Test
    fun a_highlight_that_is_not_a_list_highlights_nothing_and_says_so() {
        val props = sceneProps(mapOf("highlight" to "e1"))
        assertEquals(emptySet(), props.highlightIds)
        assertEquals(listOf("highlight must be a list of part ids, not 'e1'; nothing is highlighted."), props.notes)
    }

    @Test
    fun a_highlight_entry_that_is_not_a_part_id_is_named() {
        val props = sceneProps(mapOf("highlight" to listOf("e1", 3)))
        assertEquals(setOf("e1"), props.highlightIds)
        assertEquals(listOf("highlight entries that are not part ids are not drawn: 3"), props.notes)
    }

    @Test
    fun a_show_lower_that_is_not_a_bool_hides_the_lower_levels_and_says_so() {
        val props = sceneProps(mapOf("show_lower" to "yes"))
        assertEquals(false, props.showLower)
        assertEquals(listOf("show_lower must be true or false, not 'yes'; lower levels are hidden."), props.notes)
    }

    @Test
    fun a_hide_top_that_is_not_a_whole_count_hides_nothing_and_says_so() {
        assertEquals(listOf("hide_top must be a whole number of levels, not '1.5'; no level is hidden."), sceneProps(mapOf("hide_top" to 1.5)).notes)
        assertEquals(listOf("hide_top must be a whole number of levels, not '-1'; no level is hidden."), sceneProps(mapOf("hide_top" to -1)).notes)
        assertEquals(0, sceneProps(mapOf("hide_top" to "two")).hideTopCount)
        assertEquals(listOf("hide_top must be a whole number of levels, not '1.5'; no level is hidden."), sceneProps(mapOf("hide_top" to "1.5")).notes)
    }

    @Test
    fun a_hide_top_written_as_whole_number_text_by_a_select_hides_that_many_levels_as_focus_already_reads_it() {
        val fromSelect = sceneProps(mapOf("hide_top" to "1", "focus" to "2"))
        assertEquals(1, fromSelect.hideTopCount)
        assertEquals(LevelFocus.OneLevel(2), fromSelect.focus)
        assertEquals(emptyList(), fromSelect.notes)
    }

    @Test
    fun an_on_part_tap_that_is_not_an_action_name_says_taps_dispatch_nothing() {
        val props = sceneProps(mapOf("on_part_tap" to 3))
        assertEquals(null, props.onPartTap)
        assertEquals(listOf("on_part_tap must be an action name, not '3'; a tap dispatches nothing."), props.notes)
    }

    @Test
    fun with_no_quarter_turns_zoom_or_pan_the_first_aspect_is_drawn_at_zoom_1_unshifted() {
        val props = sceneProps(emptyMap())
        assertEquals(0, props.quarterTurns)
        assertEquals(SceneCamera(1.0, 0.0, 0.0), props.camera)
    }

    @Test
    fun quarter_turns_zoom_and_pan_read_as_numbers_or_as_number_text_a_select_writes() {
        val numbers = sceneProps(mapOf("quarter_turns" to 1, "zoom" to 2.5, "pan_x" to 0.25, "pan_y" to -0.5))
        assertEquals(1 to SceneCamera(2.5, 0.25, -0.5), numbers.quarterTurns to numbers.camera)
        val fromSelect = sceneProps(mapOf("quarter_turns" to "3", "zoom" to "2", "pan_x" to "0.1", "pan_y" to "0"))
        assertEquals(3 to SceneCamera(2.0, 0.1, 0.0), fromSelect.quarterTurns to fromSelect.camera)
        assertEquals(emptyList(), fromSelect.notes)
    }

    @Test
    fun quarter_turns_are_read_round_the_four_aspects_so_4_is_0_and_minus_1_is_3() {
        assertEquals(0, sceneProps(mapOf("quarter_turns" to 4)).quarterTurns)
        assertEquals(3, sceneProps(mapOf("quarter_turns" to -1)).quarterTurns)
        assertEquals(2, sceneProps(mapOf("quarter_turns" to 2.0)).quarterTurns)
        assertEquals(emptyList(), sceneProps(mapOf("quarter_turns" to -1)).notes)
    }

    @Test
    fun a_quarter_turns_that_is_not_a_whole_number_draws_the_first_aspect_and_names_the_scene() {
        listOf(1.5, "1.5", "left", true).forEach { raw ->
            val props = sceneProps(mapOf("quarter_turns" to raw))
            assertEquals(0, props.quarterTurns, "$raw")
            assertEquals(listOf("scene 'board': quarter_turns must be a whole number, not '$raw'; the first aspect is drawn."), props.notes)
        }
    }

    @Test
    fun a_zoom_beyond_its_bounds_is_held_to_a_quarter_or_sixteen() {
        assertEquals(16.0, sceneProps(mapOf("zoom" to 40)).camera.zoom)
        assertEquals(0.25, sceneProps(mapOf("zoom" to 0.01)).camera.zoom)
    }

    @Test
    fun a_zoom_or_pan_that_is_not_a_finite_number_draws_the_default_and_says_so() {
        val props = sceneProps(mapOf("zoom" to 0, "pan_x" to true, "pan_y" to "down"))
        assertEquals(SceneCamera(), props.camera)
        assertEquals(
            listOf(
                "zoom must be a number above 0, not '0'; the view is drawn at zoom 1.",
                "pan_x must be a number, not 'true'; the view is not shifted.",
                "pan_y must be a number, not 'down'; the view is not shifted.",
            ),
            props.notes,
        )
        assertEquals(listOf("pan_x must be a number, not 'NaN'; the view is not shifted."), sceneProps(mapOf("pan_x" to Double.NaN)).notes)
        assertEquals(listOf("zoom must be a number above 0, not 'Infinity'; the view is drawn at zoom 1."), sceneProps(mapOf("zoom" to Double.POSITIVE_INFINITY)).notes)
    }
}
