package ai.factoredui.compose.render

import ai.factoredui.compose.renderer.RenderContext
import ai.factoredui.compose.renderer.stackedBoardText
import ai.factoredui.compose.schema.Spec
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val json = Json { ignoreUnknownKeys = true }

private val SCREEN = json.decodeFromString(
    Spec.serializer(),
    """
    {"spec_version":1,"renderer_min":1,"root":{"id":"page","type":"column","children":[
      {"id":"greeting","type":"text","props":{"value":"{greet.name}"}},
      {"id":"picker","type":"select","props":{"value":"{form.choice}","placeholder":"Pick one","options":[{"label":"First","value":"a"},{"label":"Second","value":"b"}]}},
      {"id":"board","type":"scene","props":{"flex":1,"view_state":{"renderer":"stacked_scene"},"scene_view":"{board.scene}","view":"plan","selected":"{board.selected}"}}
    ]}}
    """.trimIndent(),
)

private fun store() = mapOf(
    "greet" to mapOf("name" to "Ada"),
    "form" to mapOf("choice" to "a"),
    "board" to mapOf("scene" to stackedBoardText(), "selected" to emptyList<String>()),
)

private fun portCall(port: Int, route: String, vararg query: Pair<String, String>): Pair<Int, String> {
    val encoded = query.joinToString("&") { (key, value) -> "$key=${URLEncoder.encode(value, Charsets.UTF_8)}" }
    val connection = URI("http://127.0.0.1:$port/$route?$encoded").toURL().openConnection() as HttpURLConnection
    val stream = if (connection.responseCode < 400) connection.inputStream else connection.errorStream
    return connection.responseCode to stream.bufferedReader().readText()
}

private fun <T> drivingLive(act: (port: Int, surface: SceneSurface, context: RenderContext) -> T): T {
    val context = RenderContext(initialData = store())
    SceneSurface(SCREEN, context, 800, 600, ticking = true).use { surface ->
        val server = WindowControl(context, surface).start(0)
        try {
            return act(server.address.port, surface, context)
        } finally {
            server.stop(0)
        }
    }
}

private fun treeAt(port: Int, id: String): JsonObject {
    val (status, body) = portCall(port, "tree", "id" to id)
    assertEquals(200, status, body)
    return json.parseToJsonElement(body).jsonObject
}

private fun outlinesOf(port: Int): Map<String, String> = treeAt(port, "board").getValue("scene").jsonObject.getValue("parts").jsonArray
    .associate { it.jsonObject.getValue("id").jsonPrimitive.content to it.jsonObject.getValue("outline").jsonPrimitive.content }

private val SETTLED = Regex("""settled frame=(\d+)$""")

class ControlPortSettleTest {

    @Test
    fun a_set_returns_once_the_screen_has_settled_so_the_tree_shows_the_new_text_without_polling() {
        drivingLive { port, _, _ ->
            val (status, body) = portCall(port, "set", "path" to "greet.name", "value" to "\"Bo\"")
            assertEquals(200, status)
            assertTrue(SETTLED.containsMatchIn(body), body)
            val greeting = treeAt(port, "greeting")
            assertEquals(JsonPrimitive("Bo"), greeting.getValue("text"))
            assertTrue(greeting.getValue("frame").jsonPrimitive.long >= SETTLED.find(body)!!.groupValues[1].toLong(), "$greeting")
        }
    }

    @Test
    fun a_click_on_a_part_returns_with_the_tree_showing_it_selected_and_its_popup() {
        drivingLive { port, _, _ ->
            assertTrue(SETTLED.containsMatchIn(portCall(port, "click", "id" to "board/p2").second))
            assertEquals("selected", outlinesOf(port).getValue("p2"))
            assertEquals(JsonPrimitive("Plate / p2"), treeAt(port, "board").getValue("scene").jsonObject.getValue("popup"))
        }
    }

    @Test
    fun two_quick_taps_on_one_part_select_it_and_then_clear_it() {
        drivingLive { port, _, context ->
            portCall(port, "click", "id" to "board/p3")
            assertEquals(listOf("p3"), (context.data["board"] as Map<*, *>)["selected"])
            portCall(port, "click", "id" to "board/p3")
            assertEquals(emptyList<String>(), (context.data["board"] as Map<*, *>)["selected"])
            assertEquals("normal", outlinesOf(port).getValue("p3"))
        }
    }

    @Test
    fun a_chosen_option_leaves_no_option_nodes_in_the_tree_when_the_click_returns() {
        drivingLive { port, _, _ ->
            portCall(port, "click", "id" to "picker")
            assertEquals(2, treeAt(port, "picker").getValue("children").jsonArray.size)
            assertTrue(SETTLED.containsMatchIn(portCall(port, "click", "id" to "picker/option/b").second))
            assertEquals(JsonArray(emptyList()), treeAt(port, "picker").getValue("children"))
            assertEquals(200 to "\"b\"", portCall(port, "get", "path" to "form.choice"))
        }
    }

    @Test
    fun a_screen_that_draws_no_frames_makes_the_wait_end_at_its_bound_and_say_so() {
        drivingLive { port, surface, context ->
            surface.freeze()
            val started = System.nanoTime()
            val (status, body) = portCall(port, "set", "path" to "greet.name", "value" to "\"Cy\"", "settle_ms" to "300")
            val waitedMs = (System.nanoTime() - started) / 1_000_000
            assertEquals(200, status)
            assertTrue(Regex("""unsettled after 300 ms frame=\d+$""").containsMatchIn(body), body)
            assertTrue(waitedMs in 300..2000, "waited $waitedMs ms")
            assertEquals("Cy", (context.data["greet"] as Map<*, *>)["name"])
        }
    }

    @Test
    fun settle_ms_zero_returns_at_once_without_waiting() {
        drivingLive { port, surface, _ ->
            surface.freeze()
            val (status, body) = portCall(port, "set", "path" to "greet.name", "value" to "\"Di\"", "settle_ms" to "0")
            assertEquals(200 to "set greet.name", status to body)
        }
    }
}
