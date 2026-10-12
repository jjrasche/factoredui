package ai.factoredui.compose.render

import ai.factoredui.compose.renderer.RenderContext
import ai.factoredui.compose.renderer.stackedBoardText
import ai.factoredui.compose.schema.Spec
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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

private val SCREEN = json.decodeFromString(
    Spec.serializer(),
    """
    {"spec_version":1,"renderer_min":1,"root":{"id":"page","type":"column","children":[
      {"id":"items","type":"list","props":{"data":"{rows}","emptyText":"Nothing ordered yet.","itemTemplate":{"id":"item","type":"text","props":{"value":"{row.name}"}}}},
      {"id":"picker","type":"select","props":{"value":"{form.choice}","placeholder":"Pick one","options":[{"label":"First","value":"a"},{"label":"Second","value":"b"}]}},
      {"id":"board","type":"scene","props":{"flex":1,"view_state":{"renderer":"stacked_scene"},"scene_view":"{board.scene}","view":"plan"}}
    ]}}
    """.trimIndent(),
)

private val STORE = mapOf("rows" to emptyList<Any>(), "form" to mapOf("choice" to "a"), "board" to mapOf("scene" to stackedBoardText()))

private fun portCall(port: Int, route: String, vararg query: Pair<String, String>): Pair<Int, String> {
    val encoded = query.joinToString("&") { (key, value) -> "$key=${URLEncoder.encode(value, Charsets.UTF_8)}" }
    val connection = URI("http://127.0.0.1:$port/$route?$encoded").toURL().openConnection() as HttpURLConnection
    val stream = if (connection.responseCode < 400) connection.inputStream else connection.errorStream
    return connection.responseCode to stream.bufferedReader().readText()
}

private fun <T> driving(act: (port: Int) -> T): T {
    val context = RenderContext(initialData = STORE)
    SceneSurface(SCREEN, context, 800, 600).use { surface ->
        val server = WindowControl(context, surface).start(0)
        try {
            return act(server.address.port)
        } finally {
            server.stop(0)
        }
    }
}

private fun treeAt(port: Int, vararg query: Pair<String, String>): JsonObject {
    val (status, body) = portCall(port, "tree", *query)
    assertEquals(200, status, body)
    return json.parseToJsonElement(body).jsonObject
}

private fun JsonObject.text(key: String): String = getValue(key).jsonPrimitive.content

class ControlPortSyntheticIdTest {

    @Test
    fun an_empty_lists_message_reads_by_its_own_id() {
        driving { port ->
            val empty = treeAt(port, "id" to "items/empty")
            assertEquals("items/empty", empty.text("id"))
            assertEquals("Nothing ordered yet.", empty.text("text"))
        }
    }

    @Test
    fun an_open_selects_option_reads_by_its_own_id_and_a_closed_ones_is_refused() {
        driving { port ->
            assertEquals(400 to "refused: no drawn node has id 'picker/option/b'", portCall(port, "tree", "id" to "picker/option/b"))
            portCall(port, "click", "id" to "picker")
            val option = treeAt(port, "id" to "picker/option/b")
            assertEquals("Second", option.text("text"))
            assertEquals(JsonPrimitive(false), option.getValue("selected"))
        }
    }

    @Test
    fun a_scene_part_reads_by_its_own_id() {
        driving { port ->
            val part = treeAt(port, "id" to "board/p2")
            assertEquals("board/p2", part.text("id"))
            assertEquals("part", part.text("type"))
            assertEquals("plate", part.text("type_id"))
            assertTrue(part.getValue("bounds").jsonObject.getValue("w").jsonPrimitive.int > 0)
            assertEquals(400 to "refused: no drawn node has id 'board/zz'", portCall(port, "tree", "id" to "board/zz"))
        }
    }

    @Test
    fun a_text_search_answers_each_hit_with_its_id_text_and_bounds_beside_the_list_of_ids() {
        driving { port ->
            val found = treeAt(port, "text" to "Nothing ordered")
            assertEquals(listOf("items/empty"), found.getValue("ids").jsonArray.map { it.jsonPrimitive.content })
            val hit = found.getValue("hits").jsonArray.single().jsonObject
            assertEquals(listOf("items/empty", "Nothing ordered yet."), listOf(hit.text("id"), hit.text("text")))
            assertTrue(hit.getValue("bounds").jsonObject.getValue("h").jsonPrimitive.int > 0, "$hit")
        }
    }
}
