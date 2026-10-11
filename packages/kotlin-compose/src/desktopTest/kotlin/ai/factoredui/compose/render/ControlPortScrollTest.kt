package ai.factoredui.compose.render

import ai.factoredui.compose.renderer.RenderContext
import ai.factoredui.compose.schema.Spec
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
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

private fun portCall(port: Int, route: String, vararg query: Pair<String, String>): Pair<Int, String> {
    val encoded = query.joinToString("&") { (key, value) -> "$key=${URLEncoder.encode(value, Charsets.UTF_8)}" }
    val connection = URI("http://127.0.0.1:$port/$route?$encoded").toURL().openConnection() as HttpURLConnection
    val stream = if (connection.responseCode < 400) connection.inputStream else connection.errorStream
    return connection.responseCode to stream.bufferedReader().readText()
}

private fun <T> drivingRows(spec: Spec, act: (port: Int, context: RenderContext) -> T): T {
    val context = RenderContext(initialData = STORE)
    SceneSurface(spec, context, 400, 300).use { surface ->
        val server = WindowControl(context, surface).start(0)
        try {
            return act(server.address.port, context)
        } finally {
            server.stop(0)
        }
    }
}

private fun boundsOf(port: Int, id: String): JsonObject {
    val (status, body) = portCall(port, "tree", "id" to id)
    assertEquals(200, status, body)
    return json.parseToJsonElement(body).jsonObject.getValue("bounds").jsonObject
}

private fun JsonObject.number(key: String): Int = getValue(key).jsonPrimitive.int

class ControlPortScrollTest {

    @Test
    fun a_row_below_the_fold_of_a_scroll_is_in_the_tree_with_no_size_refuses_a_click_and_a_wheel_brings_it_into_view() {
        drivingRows(SCROLLED) { port, context ->
            val hidden = boundsOf(port, "rows/30/row-card")
            assertEquals(listOf(0, 0), listOf(hidden.number("w"), hidden.number("h")), "$hidden")
            assertEquals(400 to "refused: 'rows/30/row-card' is drawn with no size", portCall(port, "click", "id" to "rows/30/row-card"))
            repeat(40) {
                if (boundsOf(port, "rows/30/row-card").number("h") == 0) portCall(port, "wheel", "x" to "200", "y" to "150", "notches" to "3")
            }
            val shown = boundsOf(port, "rows/30/row-card")
            assertTrue(shown.number("w") > 0 && shown.number("h") > 0, "$shown")
            assertEquals(200, portCall(port, "click", "id" to "rows/30/row-card").first)
            assertEquals("row 30", context.data["picked"])
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
