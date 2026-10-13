package ai.factoredui.compose.render

import ai.factoredui.compose.renderer.RenderContext
import ai.factoredui.compose.renderer.stackedBoardText
import ai.factoredui.compose.schema.Spec
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

private val json = Json { ignoreUnknownKeys = true }

private const val WIDTH = 400
private const val HEIGHT = 600

private fun specOf(root: String): Spec = json.decodeFromString(Spec.serializer(), """{"spec_version":1,"renderer_min":1,"root":$root}""")

private fun scene(size: String) = """{"id":"board","type":"scene","props":{$size"view_state":{"renderer":"stacked_scene"},"scene_view":"{board.scene}","view":"plan"}}"""

private const val AFTER = """{"id":"after","type":"text","props":{"value":"after"}}"""

private const val ROW_TEMPLATE = """{"id":"row-text","type":"text","props":{"value":"{row.name}"}}"""

private fun store(): Map<String, Any?> = mapOf("board" to mapOf("scene" to stackedBoardText()), "rows" to (0 until 60).map { mapOf("name" to "row $it") })

private fun portCall(port: Int, route: String, vararg query: Pair<String, String>): Pair<Int, String> {
    val encoded = query.joinToString("&") { (key, value) -> "$key=${URLEncoder.encode(value, Charsets.UTF_8)}" }
    val connection = URI("http://127.0.0.1:$port/$route?$encoded").toURL().openConnection() as HttpURLConnection
    val stream = if (connection.responseCode < 400) connection.inputStream else connection.errorStream
    return connection.responseCode to stream.bufferedReader().readText()
}

private fun <T> driving(spec: Spec, act: (port: Int) -> T): T {
    val context = RenderContext(initialData = store())
    SceneSurface(spec, context, WIDTH, HEIGHT).use { surface ->
        val server = WindowControl(context, surface).start(0)
        try {
            return act(server.address.port)
        } finally {
            server.stop(0)
        }
    }
}

private fun nodeAt(port: Int, id: String): JsonObject {
    val (status, body) = portCall(port, "tree", "id" to id)
    assertEquals(200, status, body)
    return json.parseToJsonElement(body).jsonObject
}

private fun isInTree(port: Int, id: String): Boolean = portCall(port, "tree", "id" to id).first == 200

private fun JsonObject.box(): Map<String, Int> = getValue("bounds").jsonObject.mapValues { it.value.jsonPrimitive.int }

private fun JsonObject.noticeTexts(): List<String> = get("notices")?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()

private fun boxOf(port: Int, id: String): Map<String, Int> = nodeAt(port, id).box()

private fun text(id: String, value: String, layout: String = "") = """{"id":"$id","type":"text","props":{$layout"value":"$value"}}"""

class LayoutSizeTest {

    @Test
    fun a_scene_with_a_height_in_a_scroll_is_that_tall_and_the_next_sibling_starts_below_it() {
        driving(specOf("""{"id":"page","type":"scrollview","children":[${scene(""""height":520,""")},$AFTER]}""")) { port ->
            val board = boxOf(port, "board")
            assertEquals(520, board.getValue("h"), "$board")
            assertTrue(boxOf(port, "after").getValue("y") >= board.getValue("y") + 520, "${boxOf(port, "after")} below $board")
        }
    }

    @Test
    fun a_scene_with_no_height_in_a_scroll_keeps_the_height_its_window_shape_asks_for() {
        driving(specOf("""{"id":"page","type":"scrollview","children":[${scene("")},$AFTER]}""")) { port ->
            assertNotEquals(520, boxOf(port, "board").getValue("h"))
        }
    }

    @Test
    fun a_child_with_no_flex_in_a_scroll_takes_its_height() {
        driving(specOf("""{"id":"page","type":"scrollview","children":[{"id":"gap","type":"column","props":{"height":150}},$AFTER]}""")) { port ->
            assertEquals(150, boxOf(port, "gap").getValue("h"))
            assertTrue(boxOf(port, "after").getValue("y") >= 150)
        }
    }

    @Test
    fun a_fixed_height_child_in_a_column_leaves_its_flex_siblings_exactly_that_much_less() {
        val shared = driving(specOf("""{"id":"page","type":"column","children":[{"id":"a","type":"column","props":{"flex":1}},{"id":"b","type":"column","props":{"flex":1}}]}""")) { port ->
            boxOf(port, "a").getValue("h") + boxOf(port, "b").getValue("h")
        }
        driving(specOf("""{"id":"page","type":"column","children":[{"id":"fixed","type":"column","props":{"height":100}},{"id":"a","type":"column","props":{"flex":1}},{"id":"b","type":"column","props":{"flex":1}}]}""")) { port ->
            val a = boxOf(port, "a").getValue("h")
            val b = boxOf(port, "b").getValue("h")
            assertEquals(100, boxOf(port, "fixed").getValue("h"))
            assertEquals(shared - 100, a + b)
            assertTrue(abs(a - b) <= 1, "$a and $b share alike")
        }
    }

    @Test
    fun a_height_in_a_column_takes_the_place_of_the_childs_flex() {
        driving(specOf("""{"id":"page","type":"column","children":[{"id":"fixed","type":"column","props":{"flex":3,"height":80}},{"id":"a","type":"column","props":{"flex":1}}]}""")) { port ->
            assertEquals(80, boxOf(port, "fixed").getValue("h"))
            assertEquals(HEIGHT - 80, boxOf(port, "a").getValue("h"))
        }
    }

    @Test
    fun a_child_with_a_height_in_a_row_and_in_a_stack_is_that_tall() {
        driving(specOf("""{"id":"page","type":"column","children":[{"id":"strip","type":"row","children":[{"id":"cell","type":"column","props":{"height":70}}]},{"id":"layer","type":"stack","children":[{"id":"inner","type":"column","props":{"height":90}}]}]}""")) { port ->
            assertEquals(70, boxOf(port, "cell").getValue("h"))
            assertEquals(90, boxOf(port, "inner").getValue("h"))
        }
    }

    @Test
    fun a_child_with_a_width_in_a_column_is_that_wide() {
        driving(specOf("""{"id":"page","type":"column","children":[${text("narrow", "narrow", """"width":120,""")},${text("plain", "plain")}]}""")) { port ->
            assertEquals(120, boxOf(port, "narrow").getValue("w"))
            assertNotEquals(120, boxOf(port, "plain").getValue("w"))
        }
    }

    @Test
    fun a_child_with_a_width_in_a_row_keeps_it_and_its_flex_siblings_share_the_rest() {
        val spec = specOf("""{"id":"page","type":"row","children":[${text("fixed", "fixed", """"width":100,""")},{"id":"a","type":"column","props":{"flex":1}},{"id":"b","type":"column","props":{"flex":1}}]}""")
        driving(spec) { port ->
            assertEquals(100, boxOf(port, "fixed").getValue("w"))
            val a = boxOf(port, "a").getValue("w")
            val b = boxOf(port, "b").getValue("w")
            assertTrue(abs(a + b - (WIDTH - 100)) <= 1 && abs(a - b) <= 1, "$a and $b share the ${WIDTH - 100} left")
        }
    }

    @Test
    fun a_list_with_a_height_in_a_scroll_is_a_fixed_pane_that_scrolls_its_own_rows() {
        val spec = specOf("""{"id":"page","type":"scrollview","children":[{"id":"rows","type":"list","props":{"height":200,"data":"{rows}","itemTemplate":$ROW_TEMPLATE}},$AFTER]}""")
        driving(spec) { port ->
            assertEquals(200, boxOf(port, "rows").getValue("h"))
            assertTrue(isInTree(port, "rows/0/row-text"))
            assertTrue(!isInTree(port, "rows/59/row-text"), "a lazy pane draws only the rows in view")
            assertTrue(boxOf(port, "after").getValue("y") >= 200)
        }
    }

    @Test
    fun a_list_with_no_height_in_a_scroll_stacks_every_row() {
        val spec = specOf("""{"id":"page","type":"scrollview","children":[{"id":"rows","type":"list","props":{"data":"{rows}","itemTemplate":$ROW_TEMPLATE}},$AFTER]}""")
        driving(spec) { port ->
            assertTrue(isInTree(port, "rows/59/row-text"))
            assertEquals(false, nodeAt(port, "rows/59/row-text").getValue("composed").jsonPrimitive.boolean)
        }
    }

    @Test
    fun a_height_of_zero_less_or_not_a_number_is_ignored_and_its_parent_names_the_child() {
        listOf("0" to "0", "-5" to "-5", "\"tall\"" to "tall").forEach { (raw, shown) ->
            driving(specOf("""{"id":"page","type":"column","children":[{"id":"bad","type":"column","props":{"height":$raw},"children":[${text("inside", "inside")}]}]}""")) { port ->
                assertEquals(listOf("'bad' height must be a number of dp above 0, not '$shown'; it is ignored."), nodeAt(port, "page").noticeTexts(), raw)
                assertEquals(boxOf(port, "inside").getValue("h"), boxOf(port, "bad").getValue("h"), "$raw: drawn as if it had no height")
            }
        }
    }

    @Test
    fun a_width_of_zero_less_or_not_a_number_is_ignored_and_its_parent_names_the_child() {
        listOf("0" to "0", "-40" to "-40", "\"wide\"" to "wide").forEach { (raw, shown) ->
            driving(specOf("""{"id":"page","type":"row","children":[${text("bad", "bad", """"width":$raw,""")}]}""")) { port ->
                assertEquals(listOf("'bad' width must be a number of dp above 0, not '$shown'; it is ignored."), nodeAt(port, "page").noticeTexts(), raw)
                assertTrue(boxOf(port, "bad").getValue("w") > 0, raw)
            }
        }
    }

    @Test
    fun a_bad_size_in_a_scroll_or_a_stack_is_named_by_that_parent() {
        driving(specOf("""{"id":"page","type":"scrollview","children":[{"id":"layer","type":"stack","props":{"height":-1},"children":[${text("t", "t", """"width":0,""")}]}]}""")) { port ->
            assertEquals(listOf("'layer' height must be a number of dp above 0, not '-1'; it is ignored."), nodeAt(port, "page").noticeTexts())
            assertEquals(listOf("'t' width must be a number of dp above 0, not '0'; it is ignored."), nodeAt(port, "layer").noticeTexts())
        }
    }
}
