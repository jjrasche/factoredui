package ai.factoredui.compose.render

import ai.factoredui.compose.crawl.driveScreen
import ai.factoredui.compose.schema.Spec
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val WIDTH_DP = 300

private val SPEC = Json.decodeFromString(
    Spec.serializer(),
    """
    { "spec_version": 1, "renderer_min": 1, "root": { "id": "root", "type": "column", "props": {}, "children": [
      { "id": "chips", "type": "row", "props": { "gap": 8, "data": "{pick.items}", "scroll_to": "{pick.through}",
          "itemTemplate": { "id": "chip", "type": "text", "props": { "value": "{item.label}" } } } } ] } }
    """,
)

private fun store(through: Int) = mapOf(
    "pick" to mapOf("through" to through, "items" to (0..9).map { mapOf("label" to "Piece number $it") }),
)

class RowScrollToTest {
    @Test
    fun a_row_with_scroll_to_shows_the_row_at_that_index() {
        driveScreen(SPEC, store(7), widthDp = WIDTH_DP, heightDp = 200).use { screen ->
            val shown = assertNotNull(screen.boundsOfText("Piece number 7"))
            assertTrue(shown.left.value >= 0f && shown.right.value <= WIDTH_DP + 1f, "Piece number 7 spans ${shown.left.value}..${shown.right.value} of $WIDTH_DP")
        }
    }

    @Test
    fun the_first_row_is_shown_when_scroll_to_is_zero_and_the_far_ones_are_not_drawn() {
        driveScreen(SPEC, store(0), widthDp = WIDTH_DP, heightDp = 200).use { screen ->
            assertNotNull(screen.boundsOfText("Piece number 0"))
            assertNull(screen.boundsOfText("Piece number 9"))
        }
    }
}
