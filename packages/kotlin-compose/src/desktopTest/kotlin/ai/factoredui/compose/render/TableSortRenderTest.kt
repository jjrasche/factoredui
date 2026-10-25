package ai.factoredui.compose.render

import ai.factoredui.compose.renderer.RenderContext
import ai.factoredui.compose.schema.Spec
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val json = Json { ignoreUnknownKeys = true }

private const val LAYERS = """{"id":"layers","label":"Air by layer",
  "columns":[
    {"id":"low","label":"Low air","kind":"temperature"},
    {"id":"mid","label":"Middle air","kind":"temperature"},
    {"id":"high","label":"High air","kind":"temperature"},
    {"id":"note","label":"Note","type":"text"}],
  "rows":[
    {"id":"a","label":"Bench","object_ids":[],"values":{"low":18.5,"mid":21.0,"high":25.5,"note":"north"}},
    {"id":"b","label":"Tray","object_ids":[],"values":{"low":9.0,"mid":null,"high":30.25,"note":"south"}},
    {"id":"c","label":"Rafter","object_ids":[],"values":{"low":100.0,"mid":3.0,"high":2.0,"note":"east"}}]}"""

private fun plain(element: JsonElement): Any? = when (element) {
    is JsonNull -> null
    is JsonObject -> element.mapValues { plain(it.value) }
    is JsonArray -> element.map(::plain)
    is JsonPrimitive -> if (element.isString) element.content else element.booleanOrNull ?: element.doubleOrNull
}

private val LAYERS_STORE = plain(json.parseToJsonElement(LAYERS))

private fun sortableSpec(sortProps: String) = json.decodeFromString(
    Spec.serializer(),
    """{"spec_version":1,"renderer_min":1,"root":{"id":"page","type":"column","children":[
      {"id":"air","type":"table","props":{"flex":1,"view":"{layers}"$sortProps}}]}}""",
)

private fun call(port: Int, route: String, id: String): Pair<Int, String> {
    val connection = URI("http://127.0.0.1:$port/$route?id=${URLEncoder.encode(id, Charsets.UTF_8)}").toURL().openConnection() as HttpURLConnection
    val stream = if (connection.responseCode < 400) connection.inputStream else connection.errorStream
    return connection.responseCode to stream.bufferedReader().readText()
}

private fun textAt(port: Int, id: String) = json.parseToJsonElement(call(port, "tree", id).second).jsonObject.getValue("text").jsonPrimitive.content

private fun labelsInOrder(port: Int) = (0..2).map { textAt(port, "air/row/$it/label") }

private fun <T> driving(spec: Spec, width: Int = 390, act: (port: Int, context: RenderContext) -> T): T {
    val store = mapOf("layers" to LAYERS_STORE)
    val context = RenderContext(initialData = store)
    SceneSurface(spec, context, width, 600).use { surface ->
        val server = WindowControl(context, surface).start(0)
        try {
            return act(server.address.port, context)
        } finally {
            server.stop(0)
        }
    }
}

class TableSortRenderTest {

    @Test
    fun a_table_with_no_sort_binding_keeps_the_engines_row_order() {
        driving(sortableSpec("")) { port, _ -> assertEquals(listOf("Bench", "Tray", "Rafter"), labelsInOrder(port)) }
    }

    @Test
    fun tapping_a_header_sorts_ascending_then_descending_and_marks_the_column() {
        val sorting = ""","sort_by":"{air_sort.by}","sort_desc":"{air_sort.desc}""""
        driving(sortableSpec(sorting)) { port, context ->
            assertEquals(200, call(port, "click", "air/head/low").first)
            assertEquals(listOf("Tray", "Bench", "Rafter"), labelsInOrder(port))
            assertEquals("Low air ▲", textAt(port, "air/head/low"))
            assertEquals(200, call(port, "click", "air/head/low").first)
            assertEquals(listOf("Rafter", "Bench", "Tray"), labelsInOrder(port))
            assertEquals("Low air ▼", textAt(port, "air/head/low"))
            assertEquals(mapOf("by" to "low", "desc" to true), context.data["air_sort"])
        }
    }

    @Test
    fun a_value_less_row_sorts_last_in_either_direction() {
        val sorting = ""","sort_by":"{air_sort.by}","sort_desc":"{air_sort.desc}""""
        driving(sortableSpec(sorting)) { port, _ ->
            call(port, "click", "air/head/mid")
            assertEquals("Tray", textAt(port, "air/row/2/label"))
            call(port, "click", "air/head/mid")
            assertEquals("Tray", textAt(port, "air/row/2/label"))
        }
    }

    @Test
    fun a_sort_bound_from_elsewhere_orders_the_rows_at_the_start() {
        val sorting = ""","sort_by":"{air_sort.by}""""
        val spec = sortableSpec(sorting)
        val store = mapOf("layers" to LAYERS_STORE, "air_sort" to mapOf("by" to "high"))
        val context = RenderContext(initialData = store)
        SceneSurface(spec, context, 390, 600).use { surface ->
            val server = WindowControl(context, surface).start(0)
            try {
                assertEquals(listOf("Rafter", "Bench", "Tray"), labelsInOrder(server.address.port))
            } finally {
                server.stop(0)
            }
        }
    }

    @Test
    fun at_390_dp_every_cell_of_a_four_column_table_stays_inside_the_pane_and_is_readable() {
        driving(sortableSpec("")) { port, _ ->
            val cells = listOf("label", "low", "mid", "high", "note").map { column ->
                json.parseToJsonElement(call(port, "tree", "air/row/0/$column").second).jsonObject.getValue("bounds").jsonObject
            }
            cells.forEach { bounds ->
                assertTrue(bounds.getValue("x").jsonPrimitive.int >= 0, "$bounds starts inside the pane")
                assertTrue(bounds.getValue("x").jsonPrimitive.int + bounds.getValue("w").jsonPrimitive.int <= 390, "$bounds ends inside the pane")
                assertTrue(bounds.getValue("h").jsonPrimitive.int <= 28, "$bounds draws on one line")
            }
        }
    }
}
