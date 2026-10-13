package ai.factoredui.compose.render

import ai.factoredui.compose.renderer.RenderContext
import ai.factoredui.compose.schema.Spec
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import kotlin.test.Test
import kotlin.test.assertEquals

private val json = Json { ignoreUnknownKeys = true }

private val RESET_PAGE = json.decodeFromString(
    Spec.serializer(),
    """{"spec_version":1,"renderer_min":1,"root":{"id":"page","type":"column","children":[
      {"id":"zoom-text","type":"text","props":{"value":"zoom {view.zoom}"}},
      {"id":"reset","type":"button","props":{"label":"Reset"},"action":{"action":"set_binding","params":{"values":{"view.turns":0,"view.zoom":1,"view.pan_x":0,"view.pan_y":0}}}}
    ]}}""",
)

private val VIEW_BEFORE = mapOf("view" to mapOf("turns" to 2.0, "zoom" to 3.0, "pan_x" to 0.5, "pan_y" to -0.25))

private fun portCall(port: Int, route: String, vararg query: Pair<String, String>): Pair<Int, String> {
    val encoded = query.joinToString("&") { (key, value) -> "$key=${URLEncoder.encode(value, Charsets.UTF_8)}" }
    val connection = URI("http://127.0.0.1:$port/$route?$encoded").toURL().openConnection() as HttpURLConnection
    val stream = if (connection.responseCode < 400) connection.inputStream else connection.errorStream
    return connection.responseCode to stream.bufferedReader().readText()
}

private fun <T> drivingResetPage(act: (port: Int) -> T): T {
    val context = RenderContext(initialData = VIEW_BEFORE)
    SceneSurface(RESET_PAGE, context, 400, 300).use { surface ->
        val server = WindowControl(context, surface).start(0)
        try {
            return act(server.address.port)
        } finally {
            server.stop(0)
        }
    }
}

private fun boundFigure(port: Int, path: String): Double {
    val (status, body) = portCall(port, "get", "path" to path)
    assertEquals(200, status, body)
    return json.parseToJsonElement(body).jsonPrimitive.double
}

private fun nodeOf(port: Int, id: String): JsonObject {
    val (status, body) = portCall(port, "tree", "id" to id)
    assertEquals(200, status, body)
    return json.parseToJsonElement(body).jsonObject
}

class ControlPortSetBindingValuesTest {

    @Test
    fun a_click_on_a_values_button_writes_each_path_and_each_reads_back_through_get_and_the_tree() {
        drivingResetPage { port ->
            assertEquals(JsonPrimitive("zoom 3"), nodeOf(port, "zoom-text").getValue("text"))
            assertEquals(JsonPrimitive("set_binding"), nodeOf(port, "reset").getValue("action"))
            assertEquals(200, portCall(port, "click", "id" to "reset").first)
            assertEquals(0.0, boundFigure(port, "view.turns"))
            assertEquals(1.0, boundFigure(port, "view.zoom"))
            assertEquals(0.0, boundFigure(port, "view.pan_x"))
            assertEquals(0.0, boundFigure(port, "view.pan_y"))
            assertEquals(JsonPrimitive("zoom 1"), nodeOf(port, "zoom-text").getValue("text"))
            assertEquals(JsonPrimitive("set_binding"), nodeOf(port, "reset").getValue("action"))
        }
    }
}
