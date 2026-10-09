package ai.factoredui.compose.renderer

import ai.factoredui.compose.adapter.ActionHandler
import ai.factoredui.compose.schema.Spec
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

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
}
