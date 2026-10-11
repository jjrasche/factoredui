package ai.factoredui.compose.render

import ai.factoredui.compose.renderer.RenderContext
import ai.factoredui.compose.schema.Spec
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val json = Json { ignoreUnknownKeys = true }

private val OPTIONS = listOf("a" to "First", "b" to "Second", "c" to "Third")

private val PICKER = json.decodeFromString(
    Spec.serializer(),
    """
    {"spec_version":1,"renderer_min":1,"root":{"id":"page","type":"column","children":[
      {"id":"picker","type":"select","props":{"value":"{form.choice}","placeholder":"Pick one","options":[${OPTIONS.joinToString(",") { (value, label) -> """{"label":"$label","value":"$value"}""" }}]}}]}}
    """.trimIndent(),
)

private fun portCall(port: Int, route: String, vararg query: Pair<String, String>): Pair<Int, String> {
    val encoded = query.joinToString("&") { (key, value) -> "$key=${URLEncoder.encode(value, Charsets.UTF_8)}" }
    val connection = URI("http://127.0.0.1:$port/$route?$encoded").toURL().openConnection() as HttpURLConnection
    val stream = if (connection.responseCode < 400) connection.inputStream else connection.errorStream
    return connection.responseCode to stream.bufferedReader().readText()
}

class ControlPortPopupSettleTest {

    @Test
    fun a_click_that_opens_a_select_returns_with_every_option_in_the_tree_sized_and_an_option_then_clicks_at_once() {
        val context = RenderContext(initialData = mapOf("form" to mapOf("choice" to "a")))
        SceneSurface(PICKER, context, 600, 400).use { surface ->
            val server = WindowControl(context, surface).start(0)
            try {
                val port = server.address.port
                repeat(20) { round ->
                    assertEquals(200, portCall(port, "click", "id" to "picker").first, "round $round open")
                    val (_, body) = portCall(port, "tree", "id" to "picker")
                    val options = json.parseToJsonElement(body).jsonObject.getValue("children").jsonArray.map { it.jsonObject }
                    assertEquals(OPTIONS.map { "picker/option/${it.first}" }, options.map { it.getValue("id").jsonPrimitive.content }, "round $round options")
                    options.forEach { option ->
                        val bounds = option.getValue("bounds").jsonObject
                        assertTrue(bounds.getValue("w").jsonPrimitive.int > 0 && bounds.getValue("h").jsonPrimitive.int > 0, "round $round $option")
                    }
                    val chosen = OPTIONS[round % OPTIONS.size].first
                    assertEquals(200, portCall(port, "click", "id" to "picker/option/$chosen").first, "round $round choose")
                    assertEquals(200 to "\"$chosen\"", portCall(port, "get", "path" to "form.choice"), "round $round value")
                    val closed = json.parseToJsonElement(portCall(port, "tree", "id" to "picker").second).jsonObject.getValue("children").jsonArray
                    assertTrue(closed.isEmpty(), "round $round menu closed")
                }
            } finally {
                server.stop(0)
            }
        }
    }
}
