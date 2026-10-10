package ai.factoredui.compose.structure

import ai.factoredui.compose.scene.Annotation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

private fun note(refId: String? = "beam-0", value: Double? = 0.17, kind: String = "utilisation", refKind: String = "FOOTPRINT", text: String = "beam", source: String? = "glu.util_shear") =
    Annotation(refKind, refId, kind, text, source, value)

private fun sceneWith(vararg notes: Annotation) = houseScene(annotations = notes.toList())

private fun lightness(argb: Long): Double {
    val channels = listOf((argb shr 16) and 0xFF, (argb shr 8) and 0xFF, argb and 0xFF).map { it / 255.0 }
    return (channels.max() + channels.min()) / 2
}

private fun hueOf(argb: Long): Double {
    val (red, green, blue) = listOf((argb shr 16) and 0xFF, (argb shr 8) and 0xFF, argb and 0xFF).map { it / 255.0 }
    val high = maxOf(red, green, blue)
    val spread = high - minOf(red, green, blue)
    val sector = when (high) {
        red -> ((green - blue) / spread).mod(6.0)
        green -> (blue - red) / spread + 2
        else -> (red - green) / spread + 4
    }
    return sector * 60
}

class UtilisationTest {
    @Test
    fun each_parts_utilisation_text_and_source_are_found_by_the_footprint_id_they_refer_to() {
        val found = utilisationByPart(sceneWith(note(refId = "pier-0-0", value = 0.0794, text = "pier uses 8%", source = "pier.util_edge")))
        assertEquals(UtilisationFigure(0.0794, "pier uses 8%", "pier.util_edge"), found["pier-0-0"])
    }

    @Test
    fun annotations_of_another_kind_or_another_reference_kind_are_not_utilisation() {
        val found = utilisationByPart(sceneWith(note(kind = "note"), note(refKind = "INSTANCE", refId = "tree-1"), note(refId = "ok")))
        assertEquals(listOf("ok"), found.keys.toList())
    }

    @Test
    fun the_reference_kind_matches_whatever_its_case() = assertEquals(listOf("lower"), utilisationByPart(sceneWith(note(refId = "lower", refKind = "footprint"))).keys.toList())

    @Test
    fun an_annotation_whose_value_is_missing_or_not_finite_is_left_out_rather_than_drawn_as_zero() {
        val found = utilisationByPart(sceneWith(note(refId = "a", value = null), note(refId = "c", value = Double.NaN), note(refId = "d", value = 0.0)))
        assertEquals(listOf("d"), found.keys.toList())
    }

    @Test
    fun a_scene_with_no_annotations_layer_has_no_utilisation() = assertTrue(utilisationByPart(houseScene()).isEmpty())

    @Test
    fun under_a_quarter_of_capacity_is_little_up_to_and_including_all_of_it_is_carrying_over_it_fails() {
        assertEquals(UtilisationClass.LITTLE, classifyUtilisation(0.0794))
        assertEquals(UtilisationClass.LITTLE, classifyUtilisation(0.2499))
        assertEquals(UtilisationClass.CARRYING, classifyUtilisation(0.25))
        assertEquals(UtilisationClass.CARRYING, classifyUtilisation(0.7576))
        assertEquals(UtilisationClass.CARRYING, classifyUtilisation(1.0))
        assertEquals(UtilisationClass.FAILS, classifyUtilisation(1.0001))
    }

    @Test
    fun the_colour_runs_from_green_at_none_through_yellow_to_red_at_all_of_capacity() {
        assertEquals(0xFF20B620, utilisationColour(0.0))
        assertEquals(0xFFB6B620, utilisationColour(0.5))
        assertEquals(0xFFB62020, utilisationColour(1.0))
    }

    @Test
    fun a_part_over_capacity_gets_the_failure_colour_never_a_red_that_looks_like_a_part_at_exactly_capacity() {
        assertEquals(FAIL_COLOUR, utilisationColour(1.2))
        assertNotEquals(FAIL_COLOUR, utilisationColour(1.0))
        assertEquals(300.0, hueOf(FAIL_COLOUR), 1.0)
    }

    @Test
    fun a_negative_value_is_clamped_to_the_none_end_not_wrapped() = assertEquals(utilisationColour(0.0), utilisationColour(-0.5))

    @Test
    fun a_part_with_no_figure_is_a_grey_distinct_from_every_colour_on_the_scale() {
        assertEquals(NO_FIGURE_COLOUR, utilisationColour(null))
        val scale = (0..100).map { utilisationColour(it / 100.0) } + FAIL_COLOUR
        assertTrue(NO_FIGURE_COLOUR !in scale)
    }

    @Test
    fun the_legend_names_the_green_end_the_quarter_line_the_red_end_and_the_failure_case() {
        val stops = legendStops()
        assertEquals(listOf("0%", "25%", "50%", "75%", "100%", "over 100% fails"), stops.map { it.label })
        assertEquals(utilisationColour(0.0), stops[0].colour)
        assertEquals(utilisationColour(1.0), stops[4].colour)
        assertEquals(FAIL_COLOUR, stops[5].colour)
    }

    @Test
    fun the_top_face_is_the_lightest_shade_and_the_south_face_the_darkest() {
        val shades = utilisationShades(0.5)
        assertTrue(lightness(shades.top) > lightness(shades.west))
        assertTrue(lightness(shades.west) > lightness(shades.south))
    }

    @Test
    fun a_part_over_capacity_keeps_the_failure_hue_in_every_shade_and_a_part_with_no_figure_is_grey() {
        val failing = utilisationShades(1.4)
        listOf(failing.top, failing.west, failing.south).forEach { assertEquals(300.0, hueOf(it), 1.5) }
        val none = utilisationShades(null)
        listOf(none.top, none.west, none.south).forEach { assertEquals(210.0, hueOf(it), 8.0) }
    }
}
