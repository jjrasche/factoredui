package ai.factoredui.compose.render

import ai.factoredui.compose.renderer.RenderContext
import ai.factoredui.compose.schema.Spec
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val json = Json { ignoreUnknownKeys = true }

private fun rowsSpec(root: String): Spec = json.decodeFromString(Spec.serializer(), """{"spec_version":1,"renderer_min":1,"root":$root}""")

private const val TEMPLATE = """{"id":"row-card","type":"card","props":{"padding":12},"action":{"action":"set_binding","params":{"path":"picked","value":"{row.name}"}},"children":[{"id":"row-text","type":"text","props":{"value":"{row.name}"}}]}"""

private val SCROLLED = rowsSpec("""{"id":"page","type":"scrollview","children":[{"id":"rows","type":"list","props":{"data":"{rows}","itemTemplate":$TEMPLATE}}]}""")

private val LAZY = rowsSpec("""{"id":"page","type":"column","children":[{"id":"rows","type":"list","props":{"flex":1,"data":"{rows}","itemTemplate":$TEMPLATE}}]}""")

private val STORE = mapOf("rows" to (0 until 40).map { mapOf("name" to "row $it") })

private fun countView(id: String, rows: Int) = mapOf(
    "id" to id, "label" to id, "version" to 1,
    "columns" to listOf(mapOf("id" to "n", "label" to "N", "unit" to "count")),
    "rows" to (0 until rows).map { mapOf("id" to "r$it", "label" to "Row $it", "object_ids" to emptyList<String>(), "values" to mapOf("n" to it)) },
)

private val TABLES = rowsSpec("""{"id":"page","type":"scrollview","children":[
  {"id":"top","type":"table","props":{"view":"{views.top}"}},
  {"id":"below","type":"table","props":{"view":"{views.below}"}}]}""")

private val TABLE_STORE = mapOf("views" to mapOf("top" to countView("top", 40), "below" to countView("below", 3)))

private val UNSIZED = rowsSpec("""{"id":"page","type":"column","children":[
  {"id":"title","type":"text","props":{"value":"A title"}},
  {"id":"nothing","type":"column"},
  {"id":"none","type":"list","props":{"data":"{empty}","emptyText":"Nothing yet","itemTemplate":{"id":"x","type":"text","props":{"value":"{row}"}}}}]}""")

private fun portCall(port: Int, route: String, vararg query: Pair<String, String>): Pair<Int, String> {
    val encoded = query.joinToString("&") { (key, value) -> "$key=${URLEncoder.encode(value, Charsets.UTF_8)}" }
    val connection = URI("http://127.0.0.1:$port/$route?$encoded").toURL().openConnection() as HttpURLConnection
    val stream = if (connection.responseCode < 400) connection.inputStream else connection.errorStream
    return connection.responseCode to stream.bufferedReader().readText()
}

private fun <T> drivingRows(spec: Spec, store: Map<String, Any?> = STORE, act: (port: Int, context: RenderContext) -> T): T {
    val context = RenderContext(initialData = store)
    SceneSurface(spec, context, 400, 300).use { surface ->
        val server = WindowControl(context, surface).start(0)
        try {
            return act(server.address.port, context)
        } finally {
            server.stop(0)
        }
    }
}

private fun nodeOf(port: Int, id: String): JsonObject {
    val (status, body) = portCall(port, "tree", "id" to id)
    assertEquals(200, status, body)
    return json.parseToJsonElement(body).jsonObject
}

private fun boundsOf(port: Int, id: String): JsonObject = nodeOf(port, id).getValue("bounds").jsonObject

private fun isComposed(port: Int, id: String): Boolean = nodeOf(port, id).getValue("composed").jsonPrimitive.boolean

private fun assertOutOfView(port: Int, id: String) {
    val node = nodeOf(port, id)
    assertEquals(false, node.getValue("composed").jsonPrimitive.boolean, "$node")
    assertEquals(JsonNull, node.getValue("bounds"), "$node")
    assertEquals(true, node.getValue("visible").jsonPrimitive.boolean, "$node")
}

private fun assertPlacedWithSize(port: Int, id: String) {
    assertTrue(isComposed(port, id), id)
    val bounds = boundsOf(port, id)
    assertTrue(bounds.number("w") > 0 && bounds.number("h") > 0, "$id: $bounds")
}

private fun JsonObject.number(key: String): Int = getValue(key).jsonPrimitive.int

class ControlPortScrollTest {

    @Test
    fun a_row_below_the_fold_of_a_scroll_is_in_the_tree_not_composed_with_no_bounds_refuses_a_click_and_a_wheel_brings_it_into_view() {
        drivingRows(SCROLLED) { port, context ->
            assertOutOfView(port, "rows/30/row-card")
            assertEquals(400 to "refused: 'rows/30/row-card' is drawn with no bounds", portCall(port, "click", "id" to "rows/30/row-card"))
            repeat(40) {
                if (!isComposed(port, "rows/30/row-card")) portCall(port, "wheel", "x" to "200", "y" to "150", "notches" to "3")
            }
            assertPlacedWithSize(port, "rows/30/row-card")
            assertEquals(200, portCall(port, "click", "id" to "rows/30/row-card").first)
            assertEquals("row 30", context.data["picked"])
        }
    }

    @Test
    fun a_tables_rows_and_head_below_the_fold_are_not_composed_while_the_ones_in_view_are() {
        drivingRows(TABLES, TABLE_STORE) { port, _ ->
            listOf("top/head", "top/head/n", "top/row/0", "top/row/0/n").forEach { assertPlacedWithSize(port, it) }
            listOf("top/row/30", "top/row/30/n", "below/head", "below/head/n", "below/row/0", "below/row/2/n").forEach { assertOutOfView(port, it) }
            assertEquals(400 to "refused: 'below/row/0' is drawn with no bounds", portCall(port, "click", "id" to "below/row/0"))
        }
    }

    @Test
    fun a_node_drawn_in_view_with_no_size_is_composed_at_its_place_and_refuses_a_click_for_its_size() {
        drivingRows(UNSIZED, mapOf("empty" to emptyList<Any>())) { port, _ ->
            val title = boundsOf(port, "title")
            val nothing = nodeOf(port, "nothing")
            assertEquals(true, nothing.getValue("composed").jsonPrimitive.boolean, "$nothing")
            val bounds = nothing.getValue("bounds").jsonObject
            assertEquals(listOf(0, 0), listOf(bounds.number("w"), bounds.number("h")), "$bounds")
            assertTrue(bounds.number("y") >= title.number("y") + title.number("h"), "$bounds under $title")
            assertEquals(400 to "refused: 'nothing' is drawn with no size", portCall(port, "click", "id" to "nothing"))
            assertTrue(isComposed(port, "page"))
            assertPlacedWithSize(port, "none/empty")
        }
    }

    @Test
    fun a_row_beyond_a_lazy_lists_view_is_not_in_the_tree_until_a_wheel_brings_it_in() {
        drivingRows(LAZY) { port, _ ->
            assertEquals(400 to "refused: no drawn node has id 'rows/30/row-card'", portCall(port, "tree", "id" to "rows/30/row-card"))
            repeat(40) {
                if (portCall(port, "tree", "id" to "rows/30/row-card").first != 200) portCall(port, "wheel", "x" to "200", "y" to "150", "notches" to "3")
            }
            assertEquals(200, portCall(port, "tree", "id" to "rows/30/row-card").first)
        }
    }
}
