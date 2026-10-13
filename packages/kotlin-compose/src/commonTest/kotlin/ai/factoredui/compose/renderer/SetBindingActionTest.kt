package ai.factoredui.compose.renderer

import ai.factoredui.compose.adapter.ActionHandler
import ai.factoredui.compose.observability.Observability
import ai.factoredui.compose.schema.ActionRef
import ai.factoredui.compose.schema.Spec
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val json = Json { ignoreUnknownKeys = true }

private fun specOf(root: String) = json.decodeFromString(
    Spec.serializer(),
    """{"spec_version":1,"renderer_min":1,"root":$root}""",
)

private const val PICKABLE_ROWS = """
{
  "id": "parts", "type": "list",
  "props": {
    "data": "rows",
    "stacked": true,
    "itemTemplate": {
      "id": "row", "type": "card",
      "props": {},
      "action": { "action": "set_binding", "params": { "path": "bom.picked", "value": "{row.ids}" } },
      "children": [ { "id": "row-label", "type": "text", "props": { "value": "{row.label}" } } ]
    }
  }
}
"""

private const val PICK_BUTTON_AND_HOST_BUTTON = """
{
  "id": "root", "type": "column", "props": {},
  "children": [
    { "id": "pick", "type": "button", "props": { "label": "pick the piers" },
      "action": { "action": "set_binding", "params": { "path": "bom.picked", "value": ["e1", "e2"] } } },
    { "id": "save", "type": "button", "props": { "label": "save" },
      "action": { "action": "save_plan", "params": { "plan": "house" } } }
  ]
}
"""

private const val REFUSED_WRITES = """
{
  "id": "root", "type": "column", "props": {},
  "children": [
    { "id": "no-path", "type": "button", "props": { "label": "write nowhere" },
      "action": { "action": "set_binding", "params": { "path": "", "value": ["e1"] } } },
    { "id": "no-value", "type": "button", "props": { "label": "write nothing" },
      "action": { "action": "set_binding", "params": { "path": "bom.picked" } } },
    { "id": "error", "type": "text", "props": { "value": "{renderer.error}" } }
  ]
}
"""

private const val MANY_WRITES = """
{
  "id": "root", "type": "column", "props": {},
  "children": [
    { "id": "reset", "type": "button", "props": { "label": "reset the view" },
      "action": { "action": "set_binding", "params": { "values": { "view.turns": 0, "view.zoom": 1, "view.pan_x": 0, "view.pan_y": 0 } } } },
    { "id": "literals", "type": "button", "props": { "label": "write literals" },
      "action": { "action": "set_binding", "params": { "values": { "kept.zero": 0, "kept.none": null, "kept.on": true, "kept.list": ["a", 2, null] } } } },
    { "id": "whole-then-part", "type": "button", "props": { "label": "whole then part" },
      "action": { "action": "set_binding", "params": { "values": { "order.box": { "k": 1 }, "order.box.k": 2 } } } },
    { "id": "part-then-whole", "type": "button", "props": { "label": "part then whole" },
      "action": { "action": "set_binding", "params": { "values": { "order.box.k": 2, "order.box": { "k": 1 } } } } }
  ]
}
"""

private const val REFUSED_MANY_WRITES = """
{
  "id": "root", "type": "column", "props": {},
  "children": [
    { "id": "all-three", "type": "button", "props": { "label": "both forms" },
      "action": { "action": "set_binding", "params": { "path": "view.turns", "value": 0, "values": { "view.zoom": 1 } } } },
    { "id": "path-too", "type": "button", "props": { "label": "values and a path" },
      "action": { "action": "set_binding", "params": { "path": "view.turns", "values": { "view.zoom": 1 } } } },
    { "id": "value-too", "type": "button", "props": { "label": "values and a value" },
      "action": { "action": "set_binding", "params": { "value": 0, "values": { "view.zoom": 1 } } } },
    { "id": "none-named", "type": "button", "props": { "label": "empty values" },
      "action": { "action": "set_binding", "params": { "values": {} } } },
    { "id": "not-an-object", "type": "button", "props": { "label": "values as text" },
      "action": { "action": "set_binding", "params": { "values": "view.turns" } } },
    { "id": "blank-path", "type": "button", "props": { "label": "a blank path" },
      "action": { "action": "set_binding", "params": { "values": { "view.turns": 0, "": 1 } } } },
    { "id": "error", "type": "text", "props": { "value": "{renderer.error}" } }
  ]
}
"""

private const val SINGLE_AND_MANY_ROWS = """
{
  "id": "parts", "type": "list",
  "props": {
    "data": "rows",
    "stacked": true,
    "itemTemplate": {
      "id": "row", "type": "row", "props": {},
      "children": [
        { "id": "one", "type": "button", "props": { "label": "one {row.label}" },
          "action": { "action": "set_binding", "params": { "path": "one.ids", "value": "{row.ids}" } } },
        { "id": "many", "type": "button", "props": { "label": "many {row.label}" },
          "action": { "action": "set_binding", "params": { "values": { "many.ids": "{row.ids}", "many.source": "{source.n}" } } } }
      ]
    }
  }
}
"""

private val VIEW_BEFORE = mapOf("turns" to 2.0, "zoom" to 3.0, "pan_x" to 0.5, "pan_y" to -0.25, "label" to "kept")

private fun viewIn(store: Map<String, Any?>): Map<*, *> = store["view"] as Map<*, *>

private fun withoutRendererError(store: Map<String, Any?>): Map<String, Any?> = store - "renderer"

private class RecordedInteraction(val action: String, val params: Map<String, Any?>)

private class InteractionRecorder : Observability {
    val interactions = mutableListOf<RecordedInteraction>()
    override fun onRender(nodeId: String) = Unit
    override fun onInteraction(nodeId: String, action: ActionRef, resolvedParams: Map<String, Any?>) {
        interactions += RecordedInteraction(action.action, resolvedParams)
    }
}

private fun rendererErrorIn(store: Map<String, Any?>): Any? = (store["renderer"] as? Map<*, *>)?.get("error")

private val ROWS = listOf(
    mapOf("label" to "Cast pier", "ids" to listOf("e1", "e2", "e3")),
    mapOf("label" to "Edge beam", "ids" to listOf("e13", "e14")),
)

private fun pickedIn(store: Map<String, Any?>): Any? = (store["bom"] as? Map<*, *>)?.get("picked")

@OptIn(ExperimentalTestApi::class)
class SetBindingActionTest {

    @Test
    fun tapping_a_list_row_writes_that_rows_own_ids_to_the_named_path() = runComposeUiTest {
        val context = RenderContext(initialData = mapOf("rows" to ROWS))
        setContent { RenderSpec(specOf(PICKABLE_ROWS), context) }
        onNodeWithText("Edge beam").performClick()
        waitForIdle()
        assertEquals(listOf("e13", "e14"), pickedIn(context.data))
        onNodeWithText("Cast pier").performClick()
        waitForIdle()
        assertEquals(listOf("e1", "e2", "e3"), pickedIn(context.data))
    }

    @Test
    fun set_binding_writes_a_literal_value_and_never_reaches_the_host_registry() = runComposeUiTest {
        val reachedHost = mutableListOf<String>()
        val hostSetBinding: ActionHandler = { _ -> reachedHost += "set_binding" }
        val savePlan: ActionHandler = { params -> reachedHost += "save_plan:${params["plan"]}" }
        val context = RenderContext(actions = mapOf("set_binding" to hostSetBinding, "save_plan" to savePlan))
        setContent { RenderSpec(specOf(PICK_BUTTON_AND_HOST_BUTTON), context) }
        onNodeWithText("pick the piers").performClick()
        waitForIdle()
        assertEquals(listOf("e1", "e2"), pickedIn(context.data))
        assertEquals(emptyList<String>(), reachedHost)
        onNodeWithText("save").performClick()
        waitForIdle()
        assertEquals(listOf("save_plan:house"), reachedHost)
    }

    @Test
    fun set_binding_with_an_empty_path_is_refused_and_the_refusal_is_drawn_where_the_spec_binds_renderer_error() = runComposeUiTest {
        val context = RenderContext()
        setContent { RenderSpec(specOf(REFUSED_WRITES), context) }
        onNodeWithText("write nowhere").performClick()
        waitForIdle()
        val refusal = "set_binding on 'no-path' was refused: 'path' must be non-empty text, not ''"
        assertEquals(refusal, rendererErrorIn(context.data))
        assertEquals(setOf("renderer"), context.data.keys)
        onNodeWithTag("error").assertTextEquals(refusal)
    }

    @Test
    fun set_binding_with_no_value_is_refused_and_writes_nothing_to_the_path() = runComposeUiTest {
        val context = RenderContext()
        setContent { RenderSpec(specOf(REFUSED_WRITES), context) }
        onNodeWithText("write nothing").performClick()
        waitForIdle()
        assertEquals("set_binding on 'no-value' was refused: it names no 'value' to write to 'bom.picked'", rendererErrorIn(context.data))
        assertNull(context.data["bom"])
    }

    @Test
    fun one_tap_with_values_writes_every_named_path_and_leaves_the_rest_of_the_map_alone() = runComposeUiTest {
        val reachedHost = mutableListOf<String>()
        val hostSetBinding: ActionHandler = { _ -> reachedHost += "set_binding" }
        val context = RenderContext(actions = mapOf("set_binding" to hostSetBinding), initialData = mapOf("view" to VIEW_BEFORE))
        setContent { RenderSpec(specOf(MANY_WRITES), context) }
        onNodeWithText("reset the view").performClick()
        waitForIdle()
        assertEquals(0.0, viewIn(context.data)["turns"])
        assertEquals(1.0, viewIn(context.data)["zoom"])
        assertEquals(0.0, viewIn(context.data)["pan_x"])
        assertEquals(0.0, viewIn(context.data)["pan_y"])
        assertEquals("kept", viewIn(context.data)["label"])
        assertNull(rendererErrorIn(context.data))
        assertEquals(emptyList<String>(), reachedHost)
    }

    @Test
    fun values_writes_zero_null_a_bool_and_a_list_exactly_as_the_single_form_reads_them() = runComposeUiTest {
        val context = RenderContext(initialData = mapOf("kept" to mapOf("none" to "was set")))
        setContent { RenderSpec(specOf(MANY_WRITES), context) }
        onNodeWithText("write literals").performClick()
        waitForIdle()
        val kept = context.data["kept"] as Map<*, *>
        assertEquals(mapOf("zero" to 0.0, "none" to null, "on" to true, "list" to listOf("a", 2.0, null)), kept)
        assertTrue(kept.containsKey("none"))
    }

    @Test
    fun values_are_written_in_the_objects_key_order_so_a_later_key_wins() = runComposeUiTest {
        val context = RenderContext()
        setContent { RenderSpec(specOf(MANY_WRITES), context) }
        onNodeWithText("whole then part").performClick()
        waitForIdle()
        assertEquals(mapOf("k" to 2.0), (context.data["order"] as Map<*, *>)["box"])
        onNodeWithText("part then whole").performClick()
        waitForIdle()
        assertEquals(mapOf("k" to 1.0), (context.data["order"] as Map<*, *>)["box"])
    }

    @Test
    fun values_beside_path_or_value_is_refused_naming_the_forms_it_saw_and_writes_nothing() = runComposeUiTest {
        val context = RenderContext(initialData = mapOf("view" to VIEW_BEFORE))
        setContent { RenderSpec(specOf(REFUSED_MANY_WRITES), context) }
        mapOf(
            "both forms" to "set_binding on 'all-three' was refused: it gives 'values' and also 'path' and 'value'; give either 'path' with 'value' or 'values', not both",
            "values and a path" to "set_binding on 'path-too' was refused: it gives 'values' and also 'path'; give either 'path' with 'value' or 'values', not both",
            "values and a value" to "set_binding on 'value-too' was refused: it gives 'values' and also 'value'; give either 'path' with 'value' or 'values', not both",
        ).forEach { (label, refusal) ->
            onNodeWithText(label).performClick()
            waitForIdle()
            assertEquals(refusal, rendererErrorIn(context.data), label)
            onNodeWithTag("error").assertTextEquals(refusal)
            assertEquals(mapOf("view" to VIEW_BEFORE), withoutRendererError(context.data), label)
        }
    }

    @Test
    fun an_empty_values_a_values_that_is_not_an_object_or_a_blank_path_is_refused_and_writes_nothing() = runComposeUiTest {
        val context = RenderContext(initialData = mapOf("view" to VIEW_BEFORE))
        setContent { RenderSpec(specOf(REFUSED_MANY_WRITES), context) }
        mapOf(
            "empty values" to "set_binding on 'none-named' was refused: 'values' must name at least one path, not {}",
            "values as text" to "set_binding on 'not-an-object' was refused: 'values' must be an object of paths to values, not 'view.turns'",
            "a blank path" to "set_binding on 'blank-path' was refused: every path in 'values' must be non-empty text, not ''",
        ).forEach { (label, refusal) ->
            onNodeWithText(label).performClick()
            waitForIdle()
            assertEquals(refusal, rendererErrorIn(context.data), label)
            onNodeWithTag("error").assertTextEquals(refusal)
            assertEquals(mapOf("view" to VIEW_BEFORE), withoutRendererError(context.data), label)
        }
    }

    @Test
    fun a_binding_string_in_values_resolves_from_the_row_and_the_state_as_the_single_forms_value_does() = runComposeUiTest {
        val context = RenderContext(initialData = mapOf("rows" to ROWS, "source" to mapOf("n" to 7.0)))
        setContent { RenderSpec(specOf(SINGLE_AND_MANY_ROWS), context) }
        onNodeWithText("one Edge beam").performClick()
        onNodeWithText("many Edge beam").performClick()
        waitForIdle()
        val single = (context.data["one"] as Map<*, *>)["ids"]
        assertEquals(listOf("e13", "e14"), single)
        assertEquals(single, (context.data["many"] as Map<*, *>)["ids"])
        assertEquals(7.0, (context.data["many"] as Map<*, *>)["source"])
    }

    @Test
    fun values_reports_one_interaction_for_the_tap_with_its_params_resolved_as_any_actions_are() = runComposeUiTest {
        val recorder = InteractionRecorder()
        val context = RenderContext(initialData = mapOf("rows" to ROWS, "source" to mapOf("n" to 7.0)), observability = recorder)
        setContent { RenderSpec(specOf(SINGLE_AND_MANY_ROWS), context) }
        onNodeWithText("many Cast pier").performClick()
        waitForIdle()
        assertEquals(1, recorder.interactions.size)
        val tapped = recorder.interactions.single()
        assertEquals("set_binding", tapped.action)
        assertEquals(mapOf("values" to mapOf("many.ids" to listOf("e1", "e2", "e3"), "many.source" to 7.0)), tapped.params)
    }
}
