package ai.factoredui.compose.render

import ai.factoredui.compose.renderer.RenderContext
import ai.factoredui.compose.schema.Spec
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private val json = Json { ignoreUnknownKeys = true }

private val SCREEN = json.decodeFromString(
    Spec.serializer(),
    """
    {"spec_version":1,"renderer_min":1,"root":{"id":"page","type":"column","children":[
      {"id":"cells","type":"row","children":[
        {"id":"qty","type":"text","props":{"value":"{line.qty}"}},
        {"id":"name","type":"text","props":{"value":"{line.name}"}},
        {"id":"fixed","type":"text","props":{"value":"Quantity"}}]},
      {"id":"items","type":"list","props":{"data":"{rows}","emptyText":"Nothing ordered yet.","itemTemplate":{"id":"item","type":"text","props":{"value":"{row.name}"}}}}
    ]}}
    """.trimIndent(),
)

private fun portCall(port: Int, route: String, vararg query: Pair<String, String>): Pair<Int, String> {
    val encoded = query.joinToString("&") { (key, value) -> "$key=${URLEncoder.encode(value, Charsets.UTF_8)}" }
    val connection = URI("http://127.0.0.1:$port/$route?$encoded").toURL().openConnection() as HttpURLConnection
    val stream = if (connection.responseCode < 400) connection.inputStream else connection.errorStream
    return connection.responseCode to stream.bufferedReader().readText()
}

private fun <T> driving(store: Map<String, Any?>, act: (port: Int) -> T): T {
    val context = RenderContext(initialData = store)
    SceneSurface(SCREEN, context, 600, 400).use { surface ->
        val server = WindowControl(context, surface).start(0)
        try {
            return act(server.address.port)
        } finally {
            server.stop(0)
        }
    }
}

private fun tree(port: Int, vararg query: Pair<String, String>): JsonObject {
    val (status, body) = portCall(port, "tree", *query)
    assertEquals(200, status, body)
    return json.parseToJsonElement(body).jsonObject
}

private fun JsonObject.children(): List<JsonObject> = getValue("children").jsonArray.map { it.jsonObject }

private val LINE = mapOf("line" to mapOf("qty" to null, "name" to "bolt"))

class ControlPortTreeExtrasTest {

    @Test
    fun an_empty_list_shows_its_drawn_empty_message_as_a_node_in_the_tree() {
        driving(LINE + ("rows" to emptyList<Any>())) { port ->
            val empty = tree(port, "id" to "items").children().single()
            assertEquals("items/empty", empty.getValue("id").jsonPrimitive.content)
            assertEquals("empty", empty.getValue("type").jsonPrimitive.content)
            assertEquals(JsonPrimitive("Nothing ordered yet."), empty.getValue("text"))
            val bounds = empty.getValue("bounds").jsonObject
            assertTrue(bounds.getValue("w").jsonPrimitive.int > 0 && bounds.getValue("h").jsonPrimitive.int > 0, "$bounds")
            assertEquals(listOf("items/empty"), json.parseToJsonElement(portCall(port, "tree", "text" to "Nothing ordered").second).jsonObject.getValue("ids").jsonArray.map { it.jsonPrimitive.content })
        }
    }

    @Test
    fun a_list_with_rows_has_no_empty_node() {
        driving(LINE + ("rows" to listOf(mapOf("name" to "nut")))) { port ->
            assertEquals(listOf("items/0/item"), tree(port, "id" to "items").children().map { it.getValue("id").jsonPrimitive.content })
        }
    }

    @Test
    fun a_text_bound_to_a_null_says_so_while_a_text_with_a_value_or_no_binding_does_not() {
        driving(LINE + ("rows" to emptyList<Any>())) { port ->
            val qty = tree(port, "id" to "qty")
            assertEquals(JsonPrimitive(""), qty.getValue("text"))
            assertEquals(JsonPrimitive(true), qty.getValue("null"))
            assertFalse("null" in tree(port, "id" to "name"))
            assertFalse("null" in tree(port, "id" to "fixed"))
        }
    }

    @Test
    fun depth_limits_the_tree_and_counts_the_children_left_out() {
        driving(LINE + ("rows" to listOf(mapOf("name" to "nut"), mapOf("name" to "washer")))) { port ->
            val top = tree(port, "depth" to "1")
            val children = top.children()
            assertEquals(listOf("cells", "items"), children.map { it.getValue("id").jsonPrimitive.content })
            assertEquals(listOf(JsonArray(emptyList()), JsonArray(emptyList())), children.map { it.getValue("children") })
            assertEquals(listOf(3, 2), children.map { it.getValue("children_omitted").jsonPrimitive.int })
            assertFalse("children_omitted" in top)
            val rootOnly = tree(port, "depth" to "0")
            assertEquals(JsonArray(emptyList()), rootOnly.getValue("children"))
            assertEquals(2, rootOnly.getValue("children_omitted").jsonPrimitive.int)
            val below = tree(port, "id" to "cells", "depth" to "1")
            assertEquals(listOf("qty", "name", "fixed"), below.children().map { it.getValue("id").jsonPrimitive.content })
            assertTrue(below.children().none { "children_omitted" in it }, "leaves omit nothing")
            assertEquals(400 to "refused: depth must be a whole number of levels, not 'deep'", portCall(port, "tree", "depth" to "deep"))
        }
    }
}
