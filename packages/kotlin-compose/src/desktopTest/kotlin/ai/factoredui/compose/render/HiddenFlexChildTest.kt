package ai.factoredui.compose.render

import ai.factoredui.compose.crawl.driveScreen
import ai.factoredui.compose.schema.Spec
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val HEIGHT_DP = 600

private fun spec(container: String) = Json.decodeFromString(
    Spec.serializer(),
    """
    { "spec_version": 1, "renderer_min": 1, "root": { "id": "root", "type": "$container", "props": {}, "children": [
      { "id": "gone", "type": "column", "props": { "flex": 1 }, "visible": "{show.gone}", "children": [ { "id": "gone.text", "type": "text", "props": { "value": "hidden" } } ] },
      { "id": "gone2", "type": "column", "props": { "flex": 1 }, "visible": "{show.gone}", "children": [ { "id": "gone2.text", "type": "text", "props": { "value": "hidden too" } } ] },
      { "id": "shown", "type": "column", "props": { "flex": 1 }, "children": [ { "id": "shown.text", "type": "text", "props": { "value": "shown" } } ] } ] } }
    """,
)

private val STORE = mapOf("show" to mapOf("gone" to false))

class HiddenFlexChildTest {
    @Test
    fun a_hidden_child_with_flex_takes_no_share_of_a_column() {
        driveScreen(spec("column"), STORE, widthDp = 400, heightDp = HEIGHT_DP).use { screen ->
            val shown = assertNotNull(screen.boundsOf("shown"))
            assertTrue(shown.bottom.value - shown.top.value > HEIGHT_DP * 0.9f, "the shown child is ${shown.bottom.value - shown.top.value} dp tall of $HEIGHT_DP")
        }
    }

    @Test
    fun a_hidden_child_with_flex_takes_no_share_of_a_row() {
        driveScreen(spec("row"), STORE, widthDp = 400, heightDp = HEIGHT_DP).use { screen ->
            val shown = assertNotNull(screen.boundsOf("shown"))
            assertTrue(shown.right.value - shown.left.value > 400 * 0.9f, "the shown child is ${shown.right.value - shown.left.value} dp wide of 400")
        }
    }
}
