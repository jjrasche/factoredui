package ai.factoredui.compose.render

import ai.factoredui.compose.renderer.RenderContext
import ai.factoredui.compose.renderer.stackedBoardText
import ai.factoredui.compose.schema.Spec
import com.sun.net.httpserver.HttpServer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import javax.imageio.ImageIO
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val json = Json { ignoreUnknownKeys = true }

private const val STOPS = """{"channel":"crowding","stops":[{"up_to":0.25,"color":"#3a7ca5","label":"quiet"},{"up_to":1,"color":"#e0a030","label":"busy"},{"color":"#b03a2e","label":"over full"}],"unknown_color":"#9aa4ad","unknown_label":"no count","format":"percent"}"""

private const val RAMP = """{"channel":"crowding","ramp":{"stops":[[0,"#3b7dd8"],[0.5,"#e6a93a"],[1,"#b5382a"]],"over":"#5a1e8c","no_figure":"#9aa3ab"},"format":"percent"}"""

private fun screenSpec(colorBy: String = STOPS) = json.decodeFromString(
    Spec.serializer(),
    """
    {"spec_version":1,"renderer_min":1,"root":{"id":"page","type":"column","children":[
      {"id":"greeting","type":"text","props":{"value":"{greet.name}"}},
      {"id":"secret","type":"text","props":{"value":"hidden words"},"visible":"{flags.show}"},
      {"id":"go","type":"button","props":{"label":"Go"},"action":{"action":"set_binding","params":{"path":"greet.name","value":"clicked"}}},
      {"id":"rows","type":"list","props":{"data":"{rows}","itemTemplate":{"id":"row-label","type":"text","props":{"value":"{row.label}"}}}},
      {"id":"board","type":"scene","props":{"flex":1,"view_state":{"renderer":"stacked_scene"},"scene_view":"{board.scene}","view":"{board.view}","hide_top":"{board.hide_top}","color_by":$colorBy}}
    ]}}
    """.trimIndent(),
)

private fun screenStore(view: String = "plan", hideTop: Int = 0) = mapOf(
    "greet" to mapOf("name" to "Ada"),
    "flags" to mapOf("show" to false),
    "rows" to listOf("a", "b", "c").map { mapOf("label" to it) },
    "board" to mapOf("scene" to stackedBoardText(), "view" to view, "hide_top" to hideTop),
)

private fun withoutPlacement(element: JsonElement): JsonElement = when (element) {
    is JsonObject -> JsonObject(element.filterKeys { it != "bounds" && it != "centre" && it != "parts" }.mapValues { withoutPlacement(it.value) })
    is JsonArray -> JsonArray(element.map(::withoutPlacement))
    else -> element
}

private fun call(port: Int, route: String, vararg query: Pair<String, String>): Pair<Int, String> {
    val encoded = query.joinToString("&") { (key, value) -> "$key=${URLEncoder.encode(value, Charsets.UTF_8)}" }
    val connection = URI("http://127.0.0.1:$port/$route?$encoded").toURL().openConnection() as HttpURLConnection
    val stream = if (connection.responseCode < 400) connection.inputStream else connection.errorStream
    return connection.responseCode to stream.bufferedReader().readText()
}

private fun <T> driving(store: Map<String, Any?> = screenStore(), colorBy: String = STOPS, act: (port: Int, context: RenderContext) -> T): T {
    val context = RenderContext(initialData = store)
    SceneSurface(screenSpec(colorBy), context, 800, 900).use { surface ->
        val server: HttpServer = WindowControl(context, surface).start(0)
        try {
            return act(server.address.port, context)
        } finally {
            server.stop(0)
        }
    }
}

private fun tree(port: Int, vararg query: Pair<String, String>): JsonObject {
    val (status, body) = call(port, "tree", *query)
    assertEquals(200, status, body)
    return json.parseToJsonElement(body).jsonObject
}

private fun partsOf(scene: JsonObject): Map<String, JsonObject> =
    scene.getValue("scene").jsonObject.getValue("parts").jsonArray.associate { it.jsonObject.getValue("id").jsonPrimitive.content to it.jsonObject }

private val EXPECTED_TREE = """
{"id":"page","type":"column","text":null,"props":{},"visible":true,"enabled":true,"action":null,"children":[
 {"id":"greeting","type":"text","text":"Ada","props":{"value":"Ada"},"ink":"#1a1a1a","visible":true,"enabled":true,"action":null,"children":[]},
 {"id":"secret","type":"text","text":null,"props":{},"visible":false,"enabled":true,"action":null,"children":[]},
 {"id":"go","type":"button","text":"Go","props":{},"visible":true,"enabled":true,"action":"set_binding","children":[]},
 {"id":"rows","type":"list","text":null,"props":{"data":"<list: 3 items>"},"visible":true,"enabled":true,"action":null,"children":[
  {"id":"rows/0/row-label","type":"text","text":"a","props":{"value":"a"},"ink":"#1a1a1a","visible":true,"enabled":true,"action":null,"children":[]},
  {"id":"rows/1/row-label","type":"text","text":"b","props":{"value":"b"},"ink":"#1a1a1a","visible":true,"enabled":true,"action":null,"children":[]},
  {"id":"rows/2/row-label","type":"text","text":"c","props":{"value":"c"},"ink":"#1a1a1a","visible":true,"enabled":true,"action":null,"children":[]}]},
 {"id":"board","type":"scene","text":null,"props":{"hide_top":0,"scene_view":"<text: SCENE_CHARS chars>","view":"plan"},"visible":true,"enabled":true,"action":null,
  "scene":{"view":"plan","focus":"all","hide_top":0,"show_lower":false,"y_axis":"up","active_scheme":null,
   "legend":[{"text":"quiet","color":"#3a7ca5"},{"text":"busy","color":"#e0a030"},{"text":"over full","color":"#b03a2e"},{"text":"no count","color":"#9aa4ad"}],
   "notices":[],"popup":null},
  "children":[]}]}
""".trimIndent()

private val EXPECTED_PARTS = mapOf(
    "p1" to Triple("plate", 2, "#3a7ca5"), "p2" to Triple("plate", 2, "#e0a030"), "p3" to Triple("plate", 2, "#b03a2e"), "p4" to Triple("plate", 2, "#9aa4ad"),
    "r1" to Triple("bar", 1, "#3a7ca5"), "r2" to Triple("wide_bar", 1, "#e0a030"),
    "b1" to Triple("block", 0, "#9aa4ad"), "b2" to Triple("block", 0, "#9aa4ad"), "b3" to Triple("block", 0, "#9aa4ad"),
    "b4" to Triple("block", 0, "#9aa4ad"), "b5" to Triple("block", 0, "#9aa4ad"), "b6" to Triple("block", 0, "#9aa4ad"),
)

class ScreenTreeTest {

    @Test
    fun the_tree_names_every_drawn_node_with_its_resolved_text_bound_props_rows_and_scene() {
        driving { port, _ ->
            val drawn = tree(port)
            val expected = json.parseToJsonElement(EXPECTED_TREE.replace("SCENE_CHARS", stackedBoardText().length.toString()))
            assertEquals(expected, withoutPlacement(drawn))
            val board = drawn.getValue("children").jsonArray.last().jsonObject
            val parts = partsOf(board)
            assertEquals(EXPECTED_PARTS.keys, parts.keys)
            EXPECTED_PARTS.forEach { (id, expectation) ->
                val part = parts.getValue(id)
                assertEquals(expectation, Triple(part.getValue("type_id").jsonPrimitive.content, part.getValue("level").jsonPrimitive.int, part.getValue("fill").jsonPrimitive.content), id)
                assertEquals("normal", part.getValue("outline").jsonPrimitive.content, id)
            }
            assertEquals(JsonPrimitive(0.5), parts.getValue("p2").getValue("value"))
            val greeting = drawn.getValue("children").jsonArray.first().jsonObject.getValue("bounds").jsonObject
            assertTrue(greeting.getValue("w").jsonPrimitive.int > 0 && greeting.getValue("h").jsonPrimitive.int > 0, "$greeting")
        }
    }

    @Test
    fun the_same_screen_reads_as_the_same_bytes_and_the_query_options_narrow_it() {
        driving { port, _ ->
            assertEquals(call(port, "tree"), call(port, "tree"))
            assertEquals("rows/1/row-label", tree(port, "id" to "rows/1/row-label").getValue("id").jsonPrimitive.content)
            val visibleIds = tree(port, "visible" to "1").getValue("children").jsonArray.map { it.jsonObject.getValue("id").jsonPrimitive.content }
            assertEquals(listOf("greeting", "go", "rows", "board"), visibleIds)
            assertEquals(200 to """["rows/1/row-label"]""", call(port, "tree", "text" to "b"))
        }
    }

    @Test
    fun a_set_binding_shows_in_the_tree_and_a_click_by_id_runs_the_buttons_action() {
        driving { port, context ->
            call(port, "set", "path" to "greet.name", "value" to "\"Bo\"")
            assertEquals(JsonPrimitive("Bo"), tree(port, "id" to "greeting").getValue("text"))
            assertEquals(200, call(port, "click", "id" to "go").first)
            assertEquals("clicked", (context.data["greet"] as Map<*, *>)["name"])
            assertEquals(200 to """["greeting"]""", call(port, "tree", "text" to "clicked"))
        }
    }

    @Test
    fun a_click_on_a_scene_part_selects_it_and_the_tree_shows_it_selected_with_its_popup() {
        driving { port, _ ->
            assertEquals(200, call(port, "click", "id" to "board/p2").first)
            val board = tree(port, "id" to "board")
            assertEquals("selected", partsOf(board).getValue("p2").getValue("outline").jsonPrimitive.content)
            assertEquals(JsonPrimitive("Plate / p2 · 50% · busy"), board.getValue("scene").jsonObject.getValue("popup"))
        }
    }

    @Test
    fun each_top_parts_fill_in_the_tree_is_the_pixel_at_its_centre_for_stops_and_a_ramp_in_plan_and_iso() {
        val folder = createTempDirectory("screen-tree").toFile()
        listOf(STOPS, RAMP).forEach { colorBy ->
            listOf(Triple("plan", 0, 2), Triple("plan", 1, 1), Triple("plan", 2, 0), Triple("iso", 0, 2)).forEach { (view, hideTop, topLevel) ->
                driving(screenStore(view, hideTop), colorBy) { port, _ ->
                    val shot = File(folder, "shot.png")
                    assertEquals(200, call(port, "shot", "file" to shot.path).first)
                    val image = ImageIO.read(shot)
                    val parts = partsOf(tree(port, "id" to "board")).values.filter { it.getValue("level").jsonPrimitive.int == topLevel }
                    assertTrue(parts.isNotEmpty(), "$view hide_top $hideTop")
                    parts.forEach { part ->
                        val centre = part.getValue("centre").jsonObject
                        val pixel = image.getRGB(centre.getValue("x").jsonPrimitive.int, centre.getValue("y").jsonPrimitive.int) and 0xFFFFFF
                        val fill = part.getValue("fill").jsonPrimitive.content
                        assertEquals(fill, "#" + pixel.toString(16).padStart(6, '0'), "${part.getValue("id")} in $view hide_top $hideTop under ${colorBy.take(40)}")
                    }
                }
            }
        }
    }

    @Test
    fun a_click_on_an_unknown_id_an_unknown_part_or_a_node_with_no_size_is_refused() {
        driving { port, _ ->
            assertEquals(400 to "refused: no node or scene part has id 'nope'", call(port, "click", "id" to "nope"))
            assertEquals(400 to "refused: scene 'board' draws no part 'zz'", call(port, "click", "id" to "board/zz"))
            val (status, body) = call(port, "click", "id" to "secret")
            assertEquals(400, status)
            assertTrue(body.startsWith("refused: 'secret' is drawn with no"), body)
            assertEquals(400 to "refused: no drawn node has id 'nope'", call(port, "tree", "id" to "nope"))
        }
    }
}
