package ai.factoredui.compose.render

import ai.factoredui.compose.crawl.driveScreen
import ai.factoredui.compose.schema.Spec
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private fun chipsSpec(direction: String) = Json.decodeFromString(
    Spec.serializer(),
    """
    { "spec_version": 1, "renderer_min": 1, "root": { "id": "root", "type": "column", "props": {}, "children": [
      { "id": "chips", "type": "list", "props": { "data": "{pick.items}", $direction
          "itemTemplate": { "id": "chip", "type": "text", "props": { "value": "{item.label}" } } } } ] } }
    """,
)

private val ITEMS = mapOf("pick" to mapOf("items" to listOf(mapOf("label" to "Wall"), mapOf("label" to "Floor"), mapOf("label" to "Post"))))

class HorizontalListTest {
    @Test
    fun a_horizontal_list_puts_its_rows_side_by_side() {
        driveScreen(chipsSpec(""" "direction": "horizontal", """), ITEMS, widthDp = 400, heightDp = 200).use { screen ->
            val wall = assertNotNull(screen.boundsOfText("Wall"))
            val floor = assertNotNull(screen.boundsOfText("Floor"))
            assertTrue(floor.left.value > wall.right.value - 1f, "Floor starts at ${floor.left.value}, Wall ends at ${wall.right.value}")
            assertEquals(wall.top, floor.top)
        }
    }

    @Test
    fun a_list_with_no_direction_stacks_its_rows() {
        driveScreen(chipsSpec(""), ITEMS, widthDp = 400, heightDp = 200).use { screen ->
            val wall = assertNotNull(screen.boundsOfText("Wall"))
            val floor = assertNotNull(screen.boundsOfText("Floor"))
            assertTrue(floor.top.value > wall.top.value, "Floor is at ${floor.top.value}, Wall at ${wall.top.value}")
        }
    }
}
