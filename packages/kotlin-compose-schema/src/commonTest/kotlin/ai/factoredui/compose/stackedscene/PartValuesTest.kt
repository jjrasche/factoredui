package ai.factoredui.compose.stackedscene

import ai.factoredui.compose.scene.Annotation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun figure(refId: String? = "a", value: Double? = 0.17, kind: String = "reach", refKind: String = "FOOTPRINT", text: String = "", source: String? = null) =
    Annotation(refKind, refId, kind, text, source, value)

private fun sceneWith(vararg figures: Annotation) = stackScene(annotations = figures.toList())

class PartValuesTest {
    @Test
    fun a_parts_value_text_and_source_are_found_by_the_footprint_id_on_the_channel_the_spec_names() {
        val found = partValues(sceneWith(figure(refId = "token-3", value = 0.0794, text = "8 of 100", source = "host")), "reach")
        assertEquals(PartValue(0.0794, "8 of 100", "host"), found["token-3"])
    }

    @Test
    fun the_channel_is_any_name_the_spec_gives_and_other_channels_are_ignored() {
        val scene = sceneWith(figure(refId = "a", kind = "moisture", value = 0.4), figure(refId = "b", kind = "load", value = 0.9))
        assertEquals(mapOf("a" to 0.4), partValues(scene, "moisture").mapValues { it.value.value })
        assertEquals(mapOf("b" to 0.9), partValues(scene, "load").mapValues { it.value.value })
        assertTrue(partValues(scene, "anything else").isEmpty())
    }

    @Test
    fun an_annotation_on_something_other_than_a_footprint_is_not_a_part_value() {
        val found = partValues(sceneWith(figure(refKind = "INSTANCE", refId = "tree-1"), figure(refId = "ok")), "reach")
        assertEquals(listOf("ok"), found.keys.toList())
    }

    @Test
    fun the_reference_kind_matches_whatever_its_case() = assertEquals(listOf("lower"), partValues(sceneWith(figure(refId = "lower", refKind = "footprint")), "reach").keys.toList())

    @Test
    fun a_value_that_is_missing_or_not_finite_is_left_out_rather_than_read_as_zero() {
        val found = partValues(sceneWith(figure(refId = "a", value = null), figure(refId = "c", value = Double.NaN), figure(refId = "d", value = 0.0)), "reach")
        assertEquals(listOf("d"), found.keys.toList())
    }

    @Test
    fun a_scene_with_no_annotations_layer_has_no_values() = assertTrue(partValues(stackScene(), "reach").isEmpty())
}
