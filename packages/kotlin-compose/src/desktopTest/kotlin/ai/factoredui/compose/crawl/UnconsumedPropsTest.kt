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

class UnconsumedPropsTest {

    @Test
    fun a_prop_the_renderer_reads_is_not_reported() {
        val spec = specOf("""{ "id": "line", "type": "text", "props": { "value": "read me", "bold": true } }""")
        assertEquals(emptyList(), unconsumedProps(spec).map { it.key })
    }

    @Test
    fun a_prop_nothing_reads_is_reported_by_node_and_key() {
        val spec = specOf(
            """{ "id": "line", "type": "text", "props": { "value": "read me", "elevation": 4 } }""",
        )
        val ignored = unconsumedProps(spec)
        assertEquals(listOf("elevation"), ignored.map { it.key })
        assertEquals("line", ignored.single().nodeId)
    }

    @Test
    fun the_background_that_was_dropped_this_morning_is_now_read() {
        val spec = specOf(
            """
            {
              "id": "ground", "type": "column", "props": { "background": "#0B0D0E", "padding": 8 },
              "children": [{ "id": "line", "type": "text", "props": { "value": "x" } }]
            }
            """,
        )
        assertEquals(emptyList(), unconsumedProps(spec).map { "${it.nodeId}.${it.key}" })
    }

    @Test
    fun the_assertion_names_every_unread_prop() {
        val spec = specOf(
            """{ "id": "line", "type": "text", "props": { "value": "x", "madeUpProp": 1 } }""",
        )
        val failure = runCatching { assertEveryPropWasRead(spec) }.exceptionOrNull()
            ?: fail("a spec whose prop reaches nothing must not pass")
        assertTrue("madeUpProp" in failure.message.orEmpty(), "message was: ${failure.message}")
    }

    @Test
    fun a_prop_named_as_expected_unread_does_not_fail_the_assertion() {
        val spec = specOf(
            """{ "id": "line", "type": "text", "props": { "value": "x", "madeUpProp": 1 } }""",
        )
        assertEveryPropWasRead(spec, expectedUnread = setOf("line.madeUpProp"))
    }
}
