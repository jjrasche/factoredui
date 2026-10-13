package ai.factoredui.compose.render

import ai.factoredui.compose.renderer.RenderContext
import ai.factoredui.compose.schema.Spec
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

private val json = Json { ignoreUnknownKeys = true }

private val PICKERS = json.decodeFromString(
    Spec.serializer(),
    """{"spec_version":1,"renderer_min":1,"root":{"id":"page","type":"column","children":[
      {"id":"zoom-pick","type":"select","props":{"value":"{view.zoom}","placeholder":"Pick a zoom","options":[{"label":"Zoom 1 time","value":1},{"label":"Zoom 2 times","value":2}]}},
      {"id":"flag-pick","type":"select","props":{"value":"{view.on}","options":[{"label":"On","value":true},{"label":"Off","value":false}]}},
      {"id":"mixed-pick","type":"select","props":{"value":"{view.mode}","options":[{"label":"Fine","value":"fine"},{"label":"No value"},{"label":"Null","value":null},{"label":"Object","value":{"a":1}},{"label":"List","value":[1]},{"value":"unlabelled"},"bare"]}}
    ]}}""",
)

private const val DROPPED_NOTICE = "select 'mixed-pick' dropped options 1, 2, 3, 4, 5, 6: each needs a text 'label' and a 'value' that is text, a number or true/false"

private fun portCall(port: Int, route: String, vararg query: Pair<String, String>): Pair<Int, String> {
    val encoded = query.joinToString("&") { (key, value) -> "$key=${URLEncoder.encode(value, Charsets.UTF_8)}" }
    val connection = URI("http://127.0.0.1:$port/$route?$encoded").toURL().openConnection() as HttpURLConnection
    val stream = if (connection.responseCode < 400) connection.inputStream else connection.errorStream
    return connection.responseCode to stream.bufferedReader().readText()
}

private fun <T> drivingPickers(view: Map<String, Any?>, act: (port: Int) -> T): T {
    val context = RenderContext(initialData = mapOf("view" to view))
    SceneSurface(PICKERS, context, 800, 600).use { surface ->
        val server = WindowControl(context, surface).start(0)
        try {
            return act(server.address.port)
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

private fun boundValue(port: Int, path: String): JsonElement {
    val (status, body) = portCall(port, "get", "path" to path)
    assertEquals(200, status, body)
    return json.parseToJsonElement(body)
}

private fun openOptions(port: Int, selectId: String): List<JsonObject> {
    assertEquals(200, portCall(port, "click", "id" to selectId).first)
    return nodeOf(port, selectId).getValue("children").jsonArray.map { it.jsonObject }
}

private fun JsonObject.isSelected(): Boolean = getValue("selected").jsonPrimitive.content.toBoolean()

class ControlPortSelectOptionValuesTest {

    @Test
    fun a_select_with_number_options_draws_each_option_and_the_tree_counts_them() {
        drivingPickers(mapOf("zoom" to 1.0)) { port ->
            val closed = nodeOf(port, "zoom-pick")
            assertEquals(json.parseToJsonElement("""[{"value":1.0,"label":"Zoom 1 time"},{"value":2.0,"label":"Zoom 2 times"}]"""), closed.getValue("props").jsonObject.getValue("options"))
            val options = openOptions(port, "zoom-pick")
            assertEquals(listOf("zoom-pick/option/1", "zoom-pick/option/2"), options.map { it.getValue("id").jsonPrimitive.content })
            assertEquals(listOf("Zoom 1 time", "Zoom 2 times"), options.map { it.getValue("text").jsonPrimitive.content })
        }
    }

    @Test
    fun clicking_a_number_option_writes_the_number_and_get_reads_back_a_number() {
        drivingPickers(mapOf("zoom" to 1.0)) { port ->
            openOptions(port, "zoom-pick")
            assertEquals(200, portCall(port, "click", "id" to "zoom-pick/option/2").first)
            val written = boundValue(port, "view.zoom").jsonPrimitive
            assertFalse(written.isString, "$written")
            assertEquals(2.0, written.content.toDouble())
            assertEquals(JsonPrimitive("Zoom 2 times"), nodeOf(port, "zoom-pick").getValue("text"))
        }
    }

    @Test
    fun a_bound_2_point_0_or_the_text_2_shows_option_2_chosen() {
        drivingPickers(mapOf("zoom" to 2.0)) { port ->
            assertEquals(JsonPrimitive("Zoom 2 times"), nodeOf(port, "zoom-pick").getValue("text"))
            assertEquals(listOf(false, true), openOptions(port, "zoom-pick").map { it.isSelected() })
            assertEquals(200, portCall(port, "click", "id" to "zoom-pick/option/2").first)
            assertEquals(200, portCall(port, "set", "path" to "view.zoom", "value" to "\"2\"").first)
            assertEquals(JsonPrimitive("Zoom 2 times"), nodeOf(port, "zoom-pick").getValue("text"))
        }
    }

    @Test
    fun a_select_with_true_and_false_options_shows_the_bound_bool_and_writes_a_bool() {
        drivingPickers(mapOf("on" to true)) { port ->
            assertEquals(JsonPrimitive("On"), nodeOf(port, "flag-pick").getValue("text"))
            val options = openOptions(port, "flag-pick")
            assertEquals(listOf("flag-pick/option/true", "flag-pick/option/false"), options.map { it.getValue("id").jsonPrimitive.content })
            assertEquals(listOf(true, false), options.map { it.isSelected() })
            assertEquals(200, portCall(port, "click", "id" to "flag-pick/option/false").first)
            val written = boundValue(port, "view.on").jsonPrimitive
            assertFalse(written.isString, "$written")
            assertEquals("false", written.content)
        }
    }

    @Test
    fun options_with_no_usable_value_or_label_are_named_by_index_in_one_notice_on_the_select() {
        drivingPickers(mapOf("mode" to "fine")) { port ->
            val select = nodeOf(port, "mixed-pick")
            assertEquals(JsonArray(listOf(JsonPrimitive(DROPPED_NOTICE))), select.getValue("notices"))
            assertEquals(json.parseToJsonElement("""[{"value":"fine","label":"Fine"}]"""), select.getValue("props").jsonObject.getValue("options"))
            assertEquals(JsonPrimitive("Fine"), select.getValue("text"))
            assertEquals(listOf("mixed-pick/option/fine"), openOptions(port, "mixed-pick").map { it.getValue("id").jsonPrimitive.content })
        }
    }
}
