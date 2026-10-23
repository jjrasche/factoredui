package ai.factoredui.compose.render

import ai.factoredui.compose.crawl.driveScreen
import ai.factoredui.compose.schema.Spec
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private fun rowSpec(template: String) = Json.decodeFromString(
    Spec.serializer(),
    """
    { "spec_version": 1, "renderer_min": 1, "root": { "id": "root", "type": "column", "props": {}, "children": [
      { "id": "strip", "type": "row", "props": { "gap": 8, "data": "{pick.items}", "itemTemplate": $template } } ] } }
    """,
)

private val ITEMS = mapOf("pick" to mapOf("items" to listOf(mapOf("label" to "Wall", "share" to 3), mapOf("label" to "Floor", "share" to 1))))

class RowOfDataTest {
    @Test
    fun a_row_with_data_draws_its_template_once_per_row_side_by_side() {
        driveScreen(rowSpec("""{ "id": "chip", "type": "text", "props": { "value": "{item.label}" } }"""), ITEMS, widthDp = 400, heightDp = 200).use { screen ->
            val wall = assertNotNull(screen.boundsOfText("Wall"))
            val floor = assertNotNull(screen.boundsOfText("Floor"))
            assertTrue(floor.left.value > wall.right.value - 1f, "Floor starts at ${floor.left.value}, Wall ends at ${wall.right.value}")
            assertEquals(wall.top, floor.top)
        }
    }

    @Test
    fun a_template_whose_flex_is_a_binding_takes_that_share_of_the_row() {
        val template = """{ "id": "cell", "type": "column", "props": { "flex": "{item.share}" }, "children": [ { "id": "cell.text", "type": "text", "props": { "value": "{item.label}" } } ] }"""
        driveScreen(rowSpec(template), ITEMS, widthDp = 400, heightDp = 200).use { screen ->
            val wall = assertNotNull(screen.boundsOfText("Wall"))
            val floor = assertNotNull(screen.boundsOfText("Floor"))
            assertTrue(floor.left.value > 400f * 0.7f, "the second cell starts at ${floor.left.value} dp of 400 with a share of 1 to 3")
            assertTrue(wall.left.value < 10f, "the first cell starts at ${wall.left.value}")
        }
    }
}
