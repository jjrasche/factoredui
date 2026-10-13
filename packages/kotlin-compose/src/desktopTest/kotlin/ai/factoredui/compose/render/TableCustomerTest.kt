package ai.factoredui.compose.render

import ai.factoredui.compose.renderer.RenderContext
import ai.factoredui.compose.scene.readReports
import ai.factoredui.compose.scene.reportsState
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
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

private val json = Json { ignoreUnknownKeys = true }

/** The display profile as the customer wrote it inline on each table. */
private const val CUSTOMER_PROFILE = """{"units": {"mm": "feet_inches",
  "kg*mm/second^2": {"scale": 0.000224809, "suffix": " lbf", "decimals": 0},
  "kg/mm/second^2": {"scale": 20.8854, "suffix": " psf", "decimals": 1},
  "kg/second^2": {"scale": 0.0685218, "suffix": " lbf/ft", "decimals": 0},
  "joule": {"scale": 0.737562, "suffix": " lbf-ft", "decimals": 0},
  "count": {"scale": 1, "decimals": 0}, "usd": "usd", "usd/count": "usd"},
 "kinds": {"ratio": "percent"}}"""

/** Reduced from the engine's views response: a capacity view and a cut list, in the shape the host receives them. */
private const val VIEWS = """{"views":[
 {"id":"wall_panel_capacity","label":"Wall panel capacity","version":1,
  "columns":[{"id":"span_mm","label":"Span","unit":"mm","kind":"length"},
             {"id":"use_positive","label":"Use, toward the wall","unit":"1","kind":"ratio","bad":"above","null_display":"not measured"},
             {"id":"axial","label":"Axial load","type":"text"}],
  "rows":[{"id":"at_wall_height","label":"At the wall height","object_ids":["w1"],"values":{"span_mm":3353,"use_positive":0.27022459945198063,"axial":"none"}},
          {"id":"at_twelve","label":"At 12 ft","object_ids":["w2"],"values":{"span_mm":3658,"use_positive":null,"axial":"none"}},
          {"id":"at_fourteen","label":"At 14 ft","object_ids":["w3"],"values":{"span_mm":4267,"use_positive":0.5,"axial":"some"}}]},
 {"id":"wall_cut_list","label":"Wall cut list","version":1,
  "columns":[{"id":"whole_sheets","label":"Whole sheets a layer","unit":"1"},
             {"id":"use","label":"Use","unit":"1","kind":"ratio"},
             {"id":"reach","label":"Reach","unit":"furlong"}],
  "rows":[{"id":"as_set","label":"As set","object_ids":[],"values":{"whole_sheets":1,"use":0.75,"reach":2.5}}]}]}"""

private fun plain(element: JsonElement): Any? = when (element) {
    is JsonNull -> null
    is JsonObject -> element.mapValues { plain(it.value) }
    is JsonArray -> element.map(::plain)
    is JsonPrimitive -> if (element.isString) element.content else element.booleanOrNull ?: element.longOrNull?.toDouble() ?: element.doubleOrNull
}

private const val TAP = """"on_row_tap":{"action":"set_binding","params":{"path":"house.picked","value":"{row.object_ids}"}}"""

private fun screenIn(rootType: String) = json.decodeFromString(
    Spec.serializer(),
    """{"spec_version":1,"renderer_min":1,"root":{"id":"page","type":"$rootType","children":[
      {"id":"wall-panel-capacity","type":"table","props":{"view":"{reports.wall_panel_capacity}","label_header":"Item","display":$CUSTOMER_PROFILE,$TAP}},
      {"id":"wall-cut-list","type":"table","props":{"view":"{reports.wall_cut_list}","label_header":"Item","display":$CUSTOMER_PROFILE,
        "columns":{"whole_sheets":{"format":{"scale":1,"decimals":0}}}}},
      {"id":"ref-percent","type":"text","props":{"value":0.27022459945198063,"format":"percent"}}]}}""",
)

private fun portCall(port: Int, route: String, vararg query: Pair<String, String>): Pair<Int, String> {
    val encoded = query.joinToString("&") { (key, value) -> "$key=${URLEncoder.encode(value, Charsets.UTF_8)}" }
    val connection = URI("http://127.0.0.1:$port/$route?$encoded").toURL().openConnection() as HttpURLConnection
    val stream = if (connection.responseCode < 400) connection.inputStream else connection.errorStream
    return connection.responseCode to stream.bufferedReader().readText()
}

private val SCREEN = screenIn("column")

private fun <T> driving(screen: Spec = SCREEN, act: (port: Int, context: RenderContext) -> T): T {
    val context = RenderContext(initialData = mapOf("reports" to reportsState(readReports(VIEWS))))
    SceneSurface(screen, context, 1200, 800).use { surface ->
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

private fun noticesOf(port: Int, id: String): List<String> = nodeAt(port, id)["notices"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()

class TableCustomerTest {

    @Test
    fun a_unit_1_ratio_column_takes_the_profiles_kind_entry_through_the_hosts_views_state() {
        driving { port, _ ->
            assertEquals(textAt(port, "ref-percent"), textAt(port, "wall-panel-capacity/row/0/use_positive"))
            assertEquals("not measured", textAt(port, "wall-panel-capacity/row/1/use_positive"), "the column's null_display reaches the table too")
            assertEquals("none", textAt(port, "wall-panel-capacity/row/0/axial"))
        }
    }

    @Test
    fun the_notice_names_only_units_nothing_covers() {
        driving { port, _ ->
            assertEquals(emptyList(), noticesOf(port, "wall-panel-capacity"))
            assertEquals(listOf("table 'wall_cut_list': no display entry for unit(s) 'furlong'; drawn as plain figures"), noticesOf(port, "wall-cut-list"))
            assertEquals("1", textAt(port, "wall-cut-list/row/0/whole_sheets"), "the override covers a unit-1 column")
            assertEquals("75%", textAt(port, "wall-cut-list/row/0/use"), "the kind covers a unit-1 ratio column")
            assertEquals("2.5", textAt(port, "wall-cut-list/row/0/reach"))
        }
    }

    @Test
    fun each_row_reports_its_own_bounds_and_a_click_on_a_row_by_id_taps_that_row() {
        listOf(SCREEN, screenIn("scrollview")).forEach { screen -> rowsHaveTheirOwnBounds(screen) }
    }

    private fun rowsHaveTheirOwnBounds(screen: Spec) {
        driving(screen) { port, context ->
            val bounds = (0..2).map { nodeAt(port, "wall-panel-capacity/row/$it").getValue("bounds").jsonObject }
            assertEquals(3, bounds.map { it.getValue("y").jsonPrimitive.int }.toSet().size, "rows sit at different heights: $bounds")
            val table = nodeAt(port, "wall-panel-capacity").getValue("bounds").jsonObject
            assertNotEquals(table.getValue("h"), bounds[1].getValue("h"), "a row is not the whole table")
            val cell = nodeAt(port, "wall-panel-capacity/row/1/span_mm").getValue("bounds").jsonObject
            assertTrue(cell.getValue("y").jsonPrimitive.int >= bounds[1].getValue("y").jsonPrimitive.int, "a cell sits inside its row")
            assertEquals(200, portCall(port, "click", "id" to "wall-panel-capacity/row/1").first)
            assertEquals(listOf("w2"), (context.data["house"] as Map<*, *>)["picked"])
        }
    }
}
