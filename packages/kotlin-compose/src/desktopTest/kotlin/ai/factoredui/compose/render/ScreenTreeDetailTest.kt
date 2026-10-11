package ai.factoredui.compose.render

import ai.factoredui.compose.renderer.RenderContext
import ai.factoredui.compose.renderer.stackedBoardText
import ai.factoredui.compose.schema.Spec
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
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

private fun specOf(root: String): Spec = json.decodeFromString(Spec.serializer(), """{"spec_version":1,"renderer_min":1,"root":$root}""")

private fun portCall(port: Int, route: String, vararg query: Pair<String, String>): Pair<Int, String> {
    val encoded = query.joinToString("&") { (key, value) -> "$key=${URLEncoder.encode(value, Charsets.UTF_8)}" }
    val connection = URI("http://127.0.0.1:$port/$route?$encoded").toURL().openConnection() as HttpURLConnection
    val stream = if (connection.responseCode < 400) connection.inputStream else connection.errorStream
    return connection.responseCode to stream.bufferedReader().readText()
}

private fun <T> drivingSpec(spec: Spec, store: Map<String, Any?>, act: (port: Int, context: RenderContext) -> T): T {
    val context = RenderContext(initialData = store)
    SceneSurface(spec, context, 800, 600).use { surface ->
        val server = WindowControl(context, surface).start(0)
        try {
            return act(server.address.port, context)
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

private fun JsonObject.text(key: String): String = getValue(key).jsonPrimitive.content

private const val RAMP = """{"stops":[[0,"#3b7dd8"],[0.5,"#e6a93a"],[1,"#b5382a"]],"over":"#5a1e8c","no_figure":"#9aa3ab"}"""

private val CELLS = specOf(
    """
    {"id":"table","type":"column","children":[{"id":"rows","type":"list","props":{"data":"{rows}","itemTemplate":
      {"id":"cell","type":"text","props":{"value":"{row.n}","background_ramp":{"value":"{row.f}","ramp":$RAMP}}}}}]}
    """.trimIndent(),
)

private val CELL_ROWS = mapOf("rows" to listOf(mapOf("n" to 1, "f" to 0.0), mapOf("n" to 2, "f" to 0.5), mapOf("n" to 3, "f" to 1.3), mapOf("n" to 4, "f" to null)))

private val PICKER = specOf(
    """
    {"id":"page","type":"column","children":[
      {"id":"picker","type":"select","props":{"value":"{form.choice}","placeholder":"Pick one","options":[{"label":"First","value":"a"},{"label":"Second","value":"b"}]}}]}
    """.trimIndent(),
)

private val DIALOG = specOf(
    """{"id":"dialog","type":"modal","props":{"title":"Asked"},"children":[{"id":"agree","type":"button","props":{"label":"Agree"},"action":{"action":"set_binding","params":{"path":"form.agreed","value":true}}}]}""",
)

private val BOARD = specOf(
    """{"id":"board","type":"scene","props":{"view_state":{"renderer":"stacked_scene"},"scene_view":"{board.scene}","view":"plan","on_part_tap":"part_tapped"}}""",
)

class ScreenTreeDetailTest {

    @Test
    fun a_ramp_cell_reports_its_drawn_fill_and_ink_and_the_fill_is_the_pixel_at_its_centre() {
        drivingSpec(CELLS, CELL_ROWS) { port, _ ->
            val shot = File(createTempDirectory("tree-fill").toFile(), "shot.png")
            assertEquals(200, portCall(port, "shot", "file" to shot.path).first)
            val image = ImageIO.read(shot)
            val expected = listOf("#3b7dd8" to "#000000","#e6a93a" to "#000000", "#5a1e8c" to "#ffffff", "#9aa3ab" to "#000000")
            expected.forEachIndexed { index, (fill, ink) ->
                val cell = treeAt(port, "rows/$index/cell")
                assertEquals(fill, cell.text("fill"), "row $index fill")
                assertEquals(ink, cell.text("ink"), "row $index ink")
                val bounds = cell.getValue("bounds").jsonObject
                val centreX = bounds.getValue("x").jsonPrimitive.int + bounds.getValue("w").jsonPrimitive.int / 2
                val centreY = bounds.getValue("y").jsonPrimitive.int + bounds.getValue("h").jsonPrimitive.int / 2
                assertEquals(fill, "#" + (image.getRGB(centreX, centreY) and 0xFFFFFF).toString(16).padStart(6, '0'), "row $index centre pixel")
            }
        }
    }

    @Test
    fun a_closed_select_shows_its_options_and_value_and_an_open_one_lists_its_options_as_nodes_that_click_by_id() {
        drivingSpec(PICKER, mapOf("form" to mapOf("choice" to "a"))) { port, context ->
            val closed = treeAt(port, "picker")
            assertEquals(JsonPrimitive("a"), closed.getValue("props").jsonObject.getValue("value"))
            assertEquals(json.parseToJsonElement("""[{"value":"a","label":"First"},{"value":"b","label":"Second"}]"""), closed.getValue("props").jsonObject.getValue("options"))
            assertEquals(JsonArray(emptyList()), closed.getValue("children"))
            assertEquals(200, portCall(port, "click", "id" to "picker").first)
            val open = treeAt(port, "picker").getValue("children").jsonArray.map { it.jsonObject }
            assertEquals(listOf("picker/option/a", "picker/option/b"), open.map { it.text("id") })
            assertEquals(listOf("First", "Second"), open.map { it.text("text") })
            assertEquals(listOf(true, false), open.map { it.getValue("selected").jsonPrimitive.content.toBoolean() })
            assertTrue(open.all { it.getValue("bounds").jsonObject.getValue("w").jsonPrimitive.int > 0 })
            assertEquals(200, portCall(port, "click", "id" to "picker/option/b").first)
            assertEquals(200 to "\"b\"", portCall(port, "get", "path" to "form.choice"))
            assertEquals(JsonArray(emptyList()), treeAt(port, "picker").getValue("children"), "the menu closes once an option is chosen")
            assertEquals("b", (context.data["form"] as Map<*, *>)["choice"])
            assertEquals(400 to "refused: select 'picker' is not open", portCall(port, "click", "id" to "picker/option/a"))
        }
    }

    @Test
    fun a_modals_children_are_nodes_in_the_tree_and_click_by_id() {
        drivingSpec(DIALOG, emptyMap()) { port, context ->
            val agree = treeAt(port, "agree")
            assertEquals("Agree", agree.text("text"))
            assertEquals(200, portCall(port, "click", "id" to "agree").first)
            assertEquals(true, (context.data["form"] as Map<*, *>)["agreed"])
        }
    }

    @Test
    fun a_click_by_part_id_taps_that_part_even_under_another_while_a_pixel_click_there_taps_the_top_one() {
        drivingSpec(BOARD, mapOf("board" to mapOf("scene" to stackedBoardText()))) { port, _ ->
            val parts = treeAt(port, "board").getValue("scene").jsonObject.getValue("parts").jsonArray.associateBy { it.jsonObject.text("id") }
            val bar = parts.getValue("r1").jsonObject
            val cover = bar.getValue("covered_by").jsonArray.single().jsonPrimitive.content
            assertTrue(cover.startsWith("p"), "a plate lies over the bar's centre: $cover")
            assertEquals(JsonArray(emptyList()), parts.getValue(cover).jsonObject.getValue("covered_by"))
            assertEquals(200, portCall(port, "click", "id" to "board/r1").first)
            val afterId = treeAt(port, "board").getValue("scene").jsonObject
            val outlines = afterId.getValue("parts").jsonArray.associate { it.jsonObject.text("id") to it.jsonObject.text("outline") }
            assertEquals("selected", outlines.getValue("r1"))
            assertEquals(JsonPrimitive("Bar / r1"), afterId.getValue("popup"))
            val centre = bar.getValue("centre").jsonObject
            portCall(port, "click", "id" to "board/r1")
            assertEquals(200, portCall(port, "click", "x" to centre.text("x"), "y" to centre.text("y")).first)
            val afterPixel = treeAt(port, "board").getValue("scene").jsonObject.getValue("parts").jsonArray.associate { it.jsonObject.text("id") to it.jsonObject.text("outline") }
            assertEquals("selected", afterPixel.getValue(cover))
            assertEquals("normal", afterPixel.getValue("r1"))
        }
    }
}
