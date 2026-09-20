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

private val TWO_BUTTONS = specOf(
    """
    {
      "id": "screen", "type": "column", "props": { "padding": 16, "gap": 12 },
      "children": [
        {
          "id": "send", "type": "button", "props": { "label": "send it" },
          "action": { "action": "utterance.send", "params": { "text": "{draft.text}" } }
        },
        {
          "id": "clear", "type": "button", "props": { "label": "clear" },
          "action": { "action": "draft.clear" }
        }
      ]
    }
    """,
)

class DrivenScreenTest {

    @Test
    fun tapping_a_node_yields_the_intent_the_spec_declares() {
        driveScreen(TWO_BUTTONS, mapOf("draft" to mapOf("text" to "hello jim"))).use { screen ->
            screen.tap("send")
            assertEquals(listOf("utterance.send"), screen.intents().map { it.action })
        }
    }

    @Test
    fun a_tapped_intent_carries_its_params_resolved_against_the_data() {
        driveScreen(TWO_BUTTONS, mapOf("draft" to mapOf("text" to "hello jim"))).use { screen ->
            screen.tap("send")
            assertEquals("hello jim", screen.intents().single().params["text"])
        }
    }

    @Test
    fun intents_arrive_in_the_order_they_were_tapped() {
        driveScreen(TWO_BUTTONS, mapOf("draft" to mapOf("text" to "x"))).use { screen ->
            screen.tap("clear")
            screen.tap("send")
            assertEquals(listOf("draft.clear", "utterance.send"), screen.intents().map { it.action })
        }
    }

    @Test
    fun tapping_a_node_that_declares_no_action_yields_no_intent() {
        val plain = specOf("""{ "id": "just-text", "type": "text", "props": { "value": "nothing to tap" } }""")
        driveScreen(plain).use { screen ->
            screen.tap("just-text")
            assertEquals(emptyList(), screen.intents().map { it.action })
        }
    }

    @Test
    fun tapping_an_unknown_node_says_so_rather_than_passing_quietly() {
        driveScreen(TWO_BUTTONS).use { screen ->
            val failure = runCatching { screen.tap("no-such-node") }.exceptionOrNull()
                ?: fail("tapping a node that is not on screen must not look like a successful tap")
            assertTrue("no-such-node" in failure.message.orEmpty(), "message was: ${failure.message}")
        }
    }

    @Test
    fun typing_into_a_bound_path_changes_what_the_next_tap_sends() {
        driveScreen(TWO_BUTTONS, mapOf("draft" to mapOf("text" to "first"))).use { screen ->
            screen.type("draft.text", "second")
            screen.tap("send")
            assertEquals("second", screen.intents().single().params["text"])
        }
    }

    @Test
    fun typing_redraws_the_screen_so_bound_text_is_visible_in_the_next_frame() {
        val labelled = specOf(
            """
            {
              "id": "screen", "type": "column", "props": {},
              "children": [{ "id": "echo", "type": "text", "props": { "value": "{draft.text}" } }]
            }
            """,
        )
        driveScreen(labelled, mapOf("draft" to mapOf("text" to "before"))).use { screen ->
            val firstFrame = screen.frame().png
            screen.type("draft.text", "after a much longer utterance entirely")
            assertTrue(!firstFrame.contentEquals(screen.frame().png), "typing did not change the frame")
        }
    }
}
