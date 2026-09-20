package ai.factoredui.compose.crawl

import ai.factoredui.compose.schema.Spec
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val json = Json { ignoreUnknownKeys = true }

private fun specOf(root: String) = json.decodeFromString(
    Spec.serializer(),
    """{"spec_version":1,"renderer_min":1,"root":$root}""",
)

private val HIDDEN_BRANCH = specOf(
    """
    {
      "id": "screen", "type": "column", "props": {},
      "children": [
        { "id": "shown", "type": "text", "props": { "value": "on screen" } },
        {
          "id": "hidden-branch", "type": "column", "props": {}, "visible": "false",
          "children": [{ "id": "buried", "type": "text", "props": { "value": "x", "madeUpProp": 1 } }]
        }
      ]
    }
    """,
)

class UnreachedNodesTest {

    @Test
    fun a_node_the_renderer_never_reached_is_not_accused_of_ignoring_its_props() {
        assertEquals(emptyList(), unconsumedProps(HIDDEN_BRANCH).map { "${it.nodeId}.${it.key}" })
    }

    @Test
    fun a_node_the_renderer_never_reached_is_reported_as_unreached() {
        val unreached = unreachedNodes(HIDDEN_BRANCH).map { it.id }
        assertTrue("buried" in unreached, "the buried node should be unreached, got $unreached")
        assertTrue("hidden-branch" in unreached, "a hidden node should be unreached, got $unreached")
        assertTrue("shown" !in unreached, "a drawn node must never be called unreached")
    }

    @Test
    fun a_visited_node_that_ignores_a_prop_is_still_caught() {
        val visible = specOf(
            """{ "id": "line", "type": "text", "props": { "value": "x", "madeUpProp": 1 } }""",
        )
        assertEquals(listOf("line.madeUpProp"), unconsumedProps(visible).map { "${it.nodeId}.${it.key}" })
    }

    @Test
    fun a_spec_whose_every_node_renders_reports_nothing_unreached() {
        val plain = specOf(
            """
            {
              "id": "screen", "type": "column", "props": {},
              "children": [{ "id": "line", "type": "text", "props": { "value": "here" } }]
            }
            """,
        )
        assertEquals(emptyList(), unreachedNodes(plain).map { it.id })
    }
}
