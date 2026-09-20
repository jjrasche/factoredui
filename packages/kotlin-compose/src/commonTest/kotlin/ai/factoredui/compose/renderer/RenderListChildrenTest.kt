package ai.factoredui.compose.renderer

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import ai.factoredui.compose.schema.Spec
import kotlinx.serialization.json.Json
import kotlin.test.Test

private val json = Json { ignoreUnknownKeys = true }

private fun specOf(root: String) = json.decodeFromString(
    Spec.serializer(),
    """{"spec_version":1,"renderer_min":1,"root":$root}""",
)

private const val LIST_OF_CHILDREN = """
{
  "id": "done", "type": "list", "props": {},
  "children": [
    { "id": "r1", "type": "text", "props": { "value": "fixed the parser" } },
    { "id": "r2", "type": "text", "props": { "value": "shipped the video node" } },
    { "id": "r3", "type": "text", "props": { "value": "deleted the decisions file" } }
  ]
}
"""

private const val LIST_WITH_TEMPLATE_AND_CHILDREN = """
{
  "id": "mixed", "type": "list",
  "props": {
    "data": "rows",
    "itemTemplate": { "id": "row", "type": "text", "props": { "value": "{row.label}" } }
  },
  "children": [
    { "id": "stray", "type": "text", "props": { "value": "orphan child" } }
  ]
}
"""

@OptIn(ExperimentalTestApi::class)
class RenderListChildrenTest {

    @Test
    fun a_list_built_from_children_renders_every_child() = runComposeUiTest {
        setContent { RenderSpec(specOf(LIST_OF_CHILDREN), RenderContext()) }
        onNodeWithText("fixed the parser").assertIsDisplayed()
        onNodeWithText("shipped the video node").assertIsDisplayed()
        onNodeWithText("deleted the decisions file").assertIsDisplayed()
    }

    @Test
    fun a_list_that_cannot_draw_its_children_says_so_on_screen() = runComposeUiTest {
        val context = RenderContext(
            initialData = mapOf("rows" to listOf(mapOf("label" to "templated row"))),
        )
        setContent { RenderSpec(specOf(LIST_WITH_TEMPLATE_AND_CHILDREN), context) }
        onNodeWithText("templated row").assertIsDisplayed()
        onNodeWithText("1 child", substring = true).assertIsDisplayed()
    }
}
