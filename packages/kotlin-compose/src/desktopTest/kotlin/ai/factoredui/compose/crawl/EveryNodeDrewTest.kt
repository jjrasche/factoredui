package ai.factoredui.compose.crawl

import ai.factoredui.compose.schema.Spec
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

private val json = Json { ignoreUnknownKeys = true }

private fun specOf(root: String) = json.decodeFromString(
    Spec.serializer(),
    """{"spec_version":1,"renderer_min":1,"root":$root}""",
)

class EveryNodeDrewTest {

    private val listOfChildren = specOf(
        """
        {
          "id": "done", "type": "list", "props": {},
          "children": [
            { "id": "r1", "type": "text", "props": { "value": "fixed the parser" } },
            { "id": "r2", "type": "text", "props": { "value": "shipped the video node" } }
          ]
        }
        """,
    )

    @Test
    fun a_spec_whose_every_node_paints_pixels_reports_nothing_undrawn() {
        assertEquals(emptyList(), undrawnNodes(listOfChildren, emptyMap(), 400, 800).map { it.id })
    }

    @Test
    fun a_node_that_paints_no_pixels_is_named() {
        val blankRow = specOf(
            """
            {
              "id": "col", "type": "column", "props": {},
              "children": [
                { "id": "visible-row", "type": "text", "props": { "value": "drawn" } },
                { "id": "blank-row", "type": "text", "props": { "value": "" } }
              ]
            }
            """,
        )
        val undrawn = undrawnNodes(blankRow, emptyMap(), 400, 800).map { it.id }
        assertEquals(listOf("blank-row"), undrawn)
    }

    @Test
    fun a_node_the_spec_declares_invisible_is_not_reported() {
        val hidden = specOf(
            """
            {
              "id": "col", "type": "column", "props": {},
              "children": [
                { "id": "shown", "type": "text", "props": { "value": "drawn" } },
                { "id": "hidden", "type": "text", "props": { "value": "never asked for" }, "visible": "false" }
              ]
            }
            """,
        )
        assertEquals(emptyList(), undrawnNodes(hidden, emptyMap(), 400, 800).map { it.id })
    }

    @Test
    fun a_node_pushed_outside_the_viewport_is_named() {
        val offscreen = specOf(
            """
            {
              "id": "col", "type": "column", "props": {},
              "children": [
                { "id": "tall-spacer", "type": "spacer", "props": { "size": 900 } },
                { "id": "pushed-off", "type": "text", "props": { "value": "below the fold" } }
              ]
            }
            """,
        )
        val undrawn = undrawnNodes(offscreen, emptyMap(), 400, 200).map { it.id }
        assertTrue("pushed-off" in undrawn, "a node laid out past the viewport must be named, got $undrawn")
    }

    @Test
    fun the_assertion_names_every_undrawn_node_in_its_message() {
        val failure = runCatching {
            assertEverySpecNodeDrew(
                specOf(
                    """
                    {
                      "id": "col", "type": "column", "props": {},
                      "children": [{ "id": "blank-row", "type": "text", "props": { "value": "" } }]
                    }
                    """,
                ),
                emptyMap(),
                400,
                800,
            )
        }.exceptionOrNull() ?: fail("a spec with an undrawn node must not pass")
        assertTrue("blank-row" in failure.message.orEmpty(), "message must name the node: ${failure.message}")
    }

    @Test
    fun a_node_named_as_expected_undrawn_does_not_fail_the_assertion() {
        assertEverySpecNodeDrew(
            specOf(
                """
                {
                  "id": "col", "type": "column", "props": {},
                  "children": [
                    { "id": "shown", "type": "text", "props": { "value": "drawn" } },
                    { "id": "blank-row", "type": "text", "props": { "value": "" } }
                  ]
                }
                """,
            ),
            emptyMap(),
            400,
            800,
            expectedUndrawn = setOf("blank-row"),
        )
    }
}
