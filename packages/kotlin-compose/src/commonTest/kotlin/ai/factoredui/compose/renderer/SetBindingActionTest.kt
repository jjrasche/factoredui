package ai.factoredui.compose.renderer

import ai.factoredui.compose.adapter.ActionHandler
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
}
