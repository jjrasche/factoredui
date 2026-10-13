package ai.factoredui.compose.render

import ai.factoredui.compose.renderer.RenderContext
import ai.factoredui.compose.schema.Spec
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import ai.factoredui.compose.scene.readReports
import ai.factoredui.compose.scene.reportsState
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

private val json = Json { ignoreUnknownKeys = true }

private const val PROFILE = """{
  "units": {"usd": "usd", "mm": "feet_inches", "count": {"scale": 1, "decimals": 0},
            "kg*mm/second^2": {"scale": 0.000224809, "suffix": " lbf", "decimals": 0},
            "kg/mm/second^2": {"format": {"scale": 145.04, "suffix": " psi", "decimals": 1}, "label": "psf"}},
  "kinds": {"ratio": "percent"}
}"""

private const val VIEW = """{"id":"parts","label":"Parts list","version":3,
  "columns":[
    {"id":"qty","label":"Quantity","unit":"count"},
    {"id":"cost","label":"Unit cost","unit":"usd","null_display":"no price yet"},
    {"id":"span","label":"Span","unit":"mm"},
    {"id":"use","label":"Use","kind":"ratio"},
    {"id":"force","label":"Force","unit":"kg*mm/second^2"},
    {"id":"pressure","label":"Pressure","unit":"kg/mm/second^2"},
    {"id":"note","label":"Note","type":"text"}],
  "rows":[
    {"id":"pier","label":"Cast pier","object_ids":["e1","e2"],"values":{"qty":12,"cost":92.37,"span":3050,"use":0.31,"force":11126025.8,"pressure":0.0034,"note":"poured"}},
    {"id":"beam","label":"Edge beam","object_ids":["e13"],"values":{"qty":2,"cost":null,"span":null,"use":1.3,"force":null,"pressure":null,"note":null}}]}"""

private fun tableSpec(tableProps: String, extra: String = "") = json.decodeFromString(
    Spec.serializer(),
    """{"spec_version":1,"renderer_min":1,"root":{"id":"page","type":"column","children":[
      {"id":"parts","type":"table","props":{"flex":1,"view":"{views.parts}","display":"{display}"$tableProps}}$extra]}}""",
)

private fun plain(element: JsonElement): Any? = when (element) {
    is JsonNull -> null
    is JsonObject -> element.mapValues { plain(it.value) }
    is JsonArray -> element.map(::plain)
    is JsonPrimitive -> if (element.isString) element.content else element.booleanOrNull ?: element.longOrNull?.toDouble() ?: element.doubleOrNull
}

private fun storeWith(view: String = VIEW) = mapOf(
    "views" to mapOf("parts" to plain(json.parseToJsonElement(view))),
    "display" to plain(json.parseToJsonElement(PROFILE)),
)

private fun portCall(port: Int, route: String, vararg query: Pair<String, String>): Pair<Int, String> {
    val encoded = query.joinToString("&") { (key, value) -> "$key=${URLEncoder.encode(value, Charsets.UTF_8)}" }
    val connection = URI("http://127.0.0.1:$port/$route?$encoded").toURL().openConnection() as HttpURLConnection
    val stream = if (connection.responseCode < 400) connection.inputStream else connection.errorStream
    return connection.responseCode to stream.bufferedReader().readText()
}

private fun <T> driving(spec: Spec, store: Map<String, Any?>, width: Int = 1000, height: Int = 600, act: (port: Int, context: RenderContext) -> T): T {
    val context = RenderContext(initialData = store)
    SceneSurface(spec, context, width, height).use { surface ->
        val server = WindowControl(context, surface).start(0)
        try {
            return act(server.address.port, context)
        } finally {
            server.stop(0)
        }
    }
}

private fun nodeAt(port: Int, id: String): JsonObject {
    val (status, body) = portCall(port, "tree", "id" to id)
    assertEquals(200, status, "$id: $body")
    return json.parseToJsonElement(body).jsonObject
}

private fun textAt(port: Int, id: String): String = nodeAt(port, id).getValue("text").jsonPrimitive.content

private val FORMATS = mapOf(
    "qty" to """{"scale":1,"decimals":0}""", "cost" to "\"usd\"", "span" to "\"feet_inches\"", "use" to "\"percent\"",
    "force" to """{"scale":0.000224809,"suffix":" lbf","decimals":0}""", "pressure" to """{"scale":145.04,"suffix":" psi","decimals":1}""",
)

private val PIER = mapOf("qty" to "12", "cost" to "92.37", "span" to "3050", "use" to "0.31", "force" to "11126025.8", "pressure" to "0.0034")

/** The same values drawn by hand-built text nodes with the same formats: the existing text path the table must agree with. */
private val REFERENCE = FORMATS.entries.joinToString(",") { (column, format) ->
    """{"id":"ref-$column","type":"text","props":{"value":${PIER.getValue(column)},"format":$format}}"""
}

class TableTest {

    @Test
    fun headers_and_cells_draw_as_the_same_formats_draw_in_hand_built_text_nodes() {
        driving(tableSpec("", extra = ",$REFERENCE"), storeWith()) { port, _ ->
            assertEquals("Quantity", textAt(port, "parts/head/qty"))
            assertEquals("Force (lbf)", textAt(port, "parts/head/force"))
            assertEquals("Pressure (psf)", textAt(port, "parts/head/pressure"))
            assertEquals("Unit cost", textAt(port, "parts/head/cost"))
            FORMATS.keys.forEach { column -> assertEquals(textAt(port, "ref-$column"), textAt(port, "parts/row/0/$column"), column) }
            assertEquals("Cast pier", textAt(port, "parts/row/0/label"))
            assertEquals("poured", textAt(port, "parts/row/0/note"))
            assertEquals("Parts list", nodeAt(port, "parts").getValue("text").jsonPrimitive.content)
            assertTrue("notices" !in nodeAt(port, "parts"), "every unit has an entry")
        }
    }

    @Test
    fun a_null_draws_the_columns_null_display_else_the_tables_missing_text_and_carries_the_null_flag() {
        driving(tableSpec(""","missing":"not measured""""), storeWith()) { port, _ ->
            assertEquals("no price yet", textAt(port, "parts/row/1/cost"))
            assertEquals("not measured", textAt(port, "parts/row/1/span"))
            assertEquals(JsonPrimitive(true), nodeAt(port, "parts/row/1/span").getValue("null"))
            assertEquals("130%", textAt(port, "parts/row/1/use"), "a fraction past 1 is drawn as it is")
        }
        driving(tableSpec(""), storeWith()) { port, _ ->
            assertEquals("–", textAt(port, "parts/row/1/span"))
        }
    }

    @Test
    fun a_column_override_beats_the_unit_entry() {
        driving(tableSpec(""","columns":{"cost":{"format":{"scale":100,"suffix":" cents","decimals":0},"label":"Cost"}}"""), storeWith()) { port, _ ->
            assertEquals("9,237 cents", textAt(port, "parts/row/0/cost"))
            assertEquals("Cost", textAt(port, "parts/head/cost"))
        }
    }

    @Test
    fun units_with_no_entry_draw_plain_figures_under_one_notice_naming_them() {
        val unmapped = VIEW.replace("\"unit\":\"kg*mm/second^2\"", "\"unit\":\"joule\"").replace("\"unit\":\"mm\"", "\"unit\":\"furlong\"")
        val plainReference = """,{"id":"ref-plain","type":"text","props":{"value":11126025.8}}"""
        driving(tableSpec("", extra = plainReference), storeWith(unmapped)) { port, _ ->
            assertEquals(textAt(port, "ref-plain"), textAt(port, "parts/row/0/force"), "an unmapped unit draws the plain figure a text node draws")
            assertEquals("11126025.8", textAt(port, "ref-plain"), "a plain figure draws with no exponent")
            assertEquals("11126025.8", textAt(port, "parts/row/0/force"))
            assertEquals(
                listOf("table 'parts': no display entry for unit(s) 'furlong', 'joule'; drawn as plain figures"),
                nodeAt(port, "parts").getValue("notices").jsonArray.map { it.jsonPrimitive.content },
            )
        }
    }

    @Test
    fun a_row_tap_runs_on_row_tap_with_the_rows_values_in_scope() {
        val tap = ""","on_row_tap":{"action":"set_binding","params":{"path":"picked","value":"{row.object_ids}"}}"""
        driving(tableSpec(tap), storeWith()) { port, context ->
            assertEquals(200, portCall(port, "click", "id" to "parts/row/0").first)
            assertEquals(listOf("e1", "e2"), context.data["picked"])
            assertEquals("set_binding", nodeAt(port, "parts/row/1").getValue("action").jsonPrimitive.content)
        }
    }

    @Test
    fun a_view_with_no_rows_shows_the_empty_text_node() {
        val empty = VIEW.substringBefore("\"rows\":[") + "\"rows\":[]}"
        driving(tableSpec(""","empty_text":"Nothing to list.""""), storeWith(empty)) { port, _ ->
            assertEquals("Nothing to list.", textAt(port, "parts/empty"))
            assertEquals(listOf("parts/head"), nodeAt(port, "parts").getValue("children").jsonArray.map { it.jsonObject.getValue("id").jsonPrimitive.content }.filter { it != "parts/empty" })
        }
    }

    @Test
    fun the_tree_addresses_the_table_its_header_its_rows_and_each_cell() {
        driving(tableSpec(""","show_label":true,"label_header":"Item""""), storeWith()) { port, _ ->
            val table = nodeAt(port, "parts")
            assertEquals("table", table.getValue("type").jsonPrimitive.content)
            assertEquals(listOf("parts/head", "parts/row/0", "parts/row/1"), table.getValue("children").jsonArray.map { it.jsonObject.getValue("id").jsonPrimitive.content })
            assertEquals("Item", textAt(port, "parts/head/label"))
            val row = nodeAt(port, "parts/row/0").getValue("children").jsonArray.map { it.jsonObject.getValue("id").jsonPrimitive.content }
            assertEquals(listOf("label", "qty", "cost", "span", "use", "force", "pressure", "note").map { "parts/row/0/$it" }, row)
            assertTrue(nodeAt(port, "parts/row/1/cost").getValue("bounds").jsonObject.getValue("w").jsonPrimitive.int > 0)
        }
        driving(tableSpec(""","show_label":false,"title":false"""), storeWith()) { port, _ ->
            assertEquals(400, portCall(port, "tree", "id" to "parts/row/0/label").first)
            assertEquals(JsonNull, nodeAt(port, "parts").getValue("text"))
        }
    }

    @Test
    fun four_hundred_rows_in_a_bounded_pane_draw_and_read_by_id() {
        val rows = (0 until 400).joinToString(",") { """{"id":"r$it","label":"row $it","object_ids":[],"values":{"qty":$it,"cost":${it * 1.5}}}""" }
        val big = VIEW.substringBefore("\"rows\":[") + "\"rows\":[$rows]}"
        driving(tableSpec(""), storeWith(big)) { port, _ ->
            assertEquals("row 0", textAt(port, "parts/row/0/label"))
            assertEquals("\$1.50", textAt(port, "parts/row/1/cost"))
            repeat(200) {
                if (portCall(port, "tree", "id" to "parts/row/380/label").first != 200) portCall(port, "wheel", "x" to "400", "y" to "300", "notches" to "10")
            }
            assertEquals("row 380", textAt(port, "parts/row/380/label"))
        }
    }

    @Test
    fun a_column_with_a_width_draws_that_wide_in_its_head_and_every_row_while_the_others_still_share_the_rest() {
        val widths = driving(tableSpec(""","columns":{"span":{"width":220}}"""), storeWith()) { port, _ -> cellWidths(port) }
        assertEquals(listOf(220, 220, 220), widths.getValue("span"), "$widths")
        val shared = SHARED_COLUMNS.map { widths.getValue(it) }
        assertTrue(isOneShare(shared.flatten()), "$widths")
        assertNotEquals(220, shared.first().first(), "$widths")
    }

    @Test
    fun a_column_override_with_no_width_draws_as_a_table_with_no_override() {
        val plainWidths = driving(tableSpec(""), storeWith()) { port, _ -> cellWidths(port) }
        val labelledWidths = driving(tableSpec(""","columns":{"span":{"label":"Reach"}}"""), storeWith()) { port, _ -> cellWidths(port) }
        assertEquals(plainWidths, labelledWidths)
        assertTrue(isOneShare(plainWidths.values.flatten()), "$plainWidths")
    }
}

/** Shares of one width differ by at most the pixel a weighted layout rounds. */
private fun isOneShare(widths: List<Int>): Boolean = widths.max() - widths.min() <= 1

private val SHARED_COLUMNS = listOf("qty", "cost", "use", "force", "pressure")

/** Each right-aligned column's drawn width in the head and in each row, which fill their cells. */
private fun cellWidths(port: Int): Map<String, List<Int>> = (SHARED_COLUMNS + "span").associateWith { column ->
    listOf("parts/head/$column", "parts/row/0/$column", "parts/row/1/$column").map { nodeAt(port, it).getValue("bounds").jsonObject.getValue("w").jsonPrimitive.int }
}

private const val REAL_VIEWS = "C:/Users/rasche_j/Documents/workspace/house-world/scene/house-floor.views.json"

private fun nodesById(tree: JsonObject): Map<String, JsonObject> =
    mapOf(tree.getValue("id").jsonPrimitive.content to tree) + tree.getValue("children").jsonArray.flatMap { nodesById(it.jsonObject).entries }.associate { it.key to it.value }

private val EXPONENT = Regex("[0-9][eE][-+]?[0-9]")

/** A profile drawn from the file itself: every unit it uses maps to a plain three-decimal figure, so a new unit cannot fail the check. */
private fun passThroughProfile(views: List<Map<*, *>>): Map<String, Any?> {
    val units = views.flatMap { view -> (view["columns"] as List<*>).mapNotNull { (it as Map<*, *>)["unit"] as? String } }.toSet()
    return mapOf("units" to units.associateWith { mapOf("scale" to 1.0, "decimals" to 3.0) })
}

class TableRealViewsTest {

    @Test
    fun every_view_of_the_real_house_floor_file_draws_through_table_with_no_notice_no_exponent_and_no_unexpected_empty_cell() {
        val file = File(REAL_VIEWS)
        if (!file.exists()) {
            println("SKIPPED: $REAL_VIEWS does not exist on this machine; the real-file check needs it")
            return
        }
        val reports = reportsState(readReports(file.readText()))
        val views = reports.values.toList()
        assertTrue(views.isNotEmpty(), "the file holds at least one view")
        val tables = reports.keys.joinToString(",") { id -> """{"id":"t-$id","type":"table","props":{"view":"{reports.$id}","display":"{display}"}}""" }
        val spec = json.decodeFromString(Spec.serializer(), """{"spec_version":1,"renderer_min":1,"root":{"id":"page","type":"scrollview","children":[$tables]}}""")
        val store = mapOf("reports" to reports, "display" to passThroughProfile(views))
        driving(spec, store, width = 1400, height = 900) { port, _ ->
            val drawn = nodesById(json.parseToJsonElement(portCall(port, "tree").second).jsonObject)
            reports.forEach { (id, view) ->
                val table = drawn.getValue("t-$id")
                assertTrue("notices" !in table, "view $id: ${table["notices"]}")
                val columns = (view["columns"] as List<*>).map { it as Map<*, *> }
                (view["rows"] as List<*>).forEachIndexed { rowIndex, rawRow ->
                    val values = (rawRow as Map<*, *>)["values"] as Map<*, *>
                    columns.forEach { column ->
                        val cellId = "t-$id/row/$rowIndex/${column["id"]}"
                        val cell = drawn[cellId] ?: error("$cellId is not in the tree")
                        val text = cell.getValue("text").jsonPrimitive.content
                        val value = values[column["id"]]
                        assertTrue(text.isNotEmpty() || value == null, "$cellId drew nothing for $value")
                        assertTrue(!EXPONENT.containsMatchIn(text) || value is String, "$cellId drew an exponent: $text")
                        assertTrue(!text.contains("needs a number") || value is String, "$cellId: $text")
                    }
                }
            }
        }
    }
}
