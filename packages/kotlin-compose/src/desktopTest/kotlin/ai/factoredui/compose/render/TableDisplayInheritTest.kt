package ai.factoredui.compose.render

import ai.factoredui.compose.renderer.RenderContext
import ai.factoredui.compose.scene.readReports
import ai.factoredui.compose.scene.reportsState
import ai.factoredui.compose.schema.Spec
import ai.factoredui.compose.schema.decodeSpecFile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val json = Json { ignoreUnknownKeys = true }

private const val PROFILE = """{"units":{"mm":"feet_inches","count":{"scale":1,"decimals":0},"usd":"usd"},"kinds":{"ratio":"percent"}}"""

private const val VIEWS = """{"views":[
 {"id":"loads","label":"Loads","version":1,
  "columns":[{"id":"span","label":"Span","unit":"mm","kind":"length"},{"id":"use","label":"Use","unit":"1","kind":"ratio"},{"id":"parts","label":"Parts","unit":"count"},{"id":"cost","label":"Cost","unit":"usd"}],
  "rows":[{"id":"a","label":"A","object_ids":[],"values":{"span":3353,"use":0.27,"parts":12,"cost":92.37}}]}]}"""

private fun table(id: String, display: String? = null) =
    """{"id":"$id","type":"table","props":{"view":"{reports.loads}"${display?.let { ""","display":$it""" }.orEmpty()}}}"""

private fun screen(rootDisplay: String?, vararg children: String) = json.decodeFromString(
    Spec.serializer(),
    """{"spec_version":1,"renderer_min":1,"root":{"id":"page","type":"scrollview","props":{${rootDisplay?.let { """"display":$it""" }.orEmpty()}},"children":[${children.joinToString(",")}]}}""",
)

private fun portCall(port: Int, route: String, vararg query: Pair<String, String>): Pair<Int, String> {
    val encoded = query.joinToString("&") { (key, value) -> "$key=${URLEncoder.encode(value, Charsets.UTF_8)}" }
    val connection = URI("http://127.0.0.1:$port/$route?$encoded").toURL().openConnection() as HttpURLConnection
    val stream = if (connection.responseCode < 400) connection.inputStream else connection.errorStream
    return connection.responseCode to stream.bufferedReader().readText()
}

private fun <T> driving(spec: Spec, store: Map<String, Any?> = mapOf("reports" to reportsState(readReports(VIEWS))), act: (port: Int) -> T): T {
    val context = RenderContext(initialData = store)
    SceneSurface(spec, context, 1200, 1400).use { surface ->
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
    assertEquals(200, status, "$id: $body")
    return json.parseToJsonElement(body).jsonObject
}

private val COLUMNS = listOf("span", "use", "parts", "cost")

private fun cellsOf(port: Int, tableId: String): List<String> = COLUMNS.map { nodeAt(port, "$tableId/row/0/$it").getValue("text").jsonPrimitive.content }

private fun noticesOf(port: Int, tableId: String): List<String> = nodeAt(port, tableId)["notices"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()

class TableDisplayInheritTest {

    @Test
    fun tables_under_a_screen_profile_draw_as_tables_with_the_same_profile_inline() {
        val inline = driving(screen(null, table("one", PROFILE), table("two", PROFILE))) { port -> cellsOf(port, "one") to cellsOf(port, "two") }
        driving(screen(PROFILE, table("one"), table("two"))) { port ->
            assertEquals(inline, cellsOf(port, "one") to cellsOf(port, "two"))
            assertEquals(listOf("11′ 0″", "27%", "12", "\$92.37"), cellsOf(port, "one"))
            assertEquals(emptyList(), noticesOf(port, "one"))
        }
    }

    @Test
    fun a_tables_own_entry_replaces_only_that_unit_and_keeps_the_rest_inherited() {
        driving(screen(PROFILE, table("own", """{"units":{"1":{"scale":100,"suffix":" pts","decimals":0}}}"""))) { port ->
            assertEquals(listOf("11′ 0″", "27 pts", "12", "\$92.37"), cellsOf(port, "own"))
        }
    }

    @Test
    fun a_nested_containers_profile_scopes_to_its_subtree() {
        val nested = """{"id":"inner","type":"column","props":{"display":{"units":{"usd":{"scale":100,"suffix":" cents","decimals":0}}}},"children":[${table("inside")}]}"""
        driving(screen(PROFILE, nested, table("outside"))) { port ->
            assertEquals("9,237 cents", cellsOf(port, "inside")[3])
            assertEquals("\$92.37", cellsOf(port, "outside")[3])
        }
    }

    @Test
    fun a_table_with_no_profile_anywhere_draws_plain_figures_under_one_notice() {
        driving(screen(null, table("bare"))) { port ->
            assertEquals(listOf("3353", "0.27", "12", "92.37"), cellsOf(port, "bare"))
            assertEquals(listOf("table 'loads': no display entry for unit(s) 'mm', '1', 'count', 'usd'; drawn as plain figures"), noticesOf(port, "bare"))
        }
    }

    @Test
    fun the_inherited_profile_reaches_tables_in_a_list_row_a_card_and_a_scrollview() {
        val inList = """{"id":"rows","type":"list","props":{"data":"{things}","itemTemplate":${table("in-row")}}}"""
        val inCard = """{"id":"box","type":"card","children":[${table("in-card")}]}"""
        val inScroll = """{"id":"inner-scroll","type":"scrollview","children":[${table("in-scroll")}]}"""
        val store = mapOf("reports" to reportsState(readReports(VIEWS)), "things" to listOf(mapOf("n" to 1)))
        driving(screen(PROFILE, inList, inCard, inScroll), store) { port ->
            listOf("rows/0/in-row", "in-card", "in-scroll").forEach { id -> assertEquals("27%", cellsOf(port, id)[1], id) }
        }
    }

    @Test
    fun a_profile_adds_no_node_to_the_tree() {
        driving(screen(PROFILE, table("one"))) { port ->
            assertEquals(listOf("one"), nodeAt(port, "page").getValue("children").jsonArray.map { it.jsonObject.getValue("id").jsonPrimitive.content })
        }
    }
}

private const val REAL_UI = "C:/Users/rasche_j/Documents/workspace/house-world/worlds/house-floor.ui.json"
private const val REAL_VIEWS = "C:/Users/rasche_j/Documents/workspace/house-world/scene/house-floor.views.json"

private fun tablesIn(node: JsonObject): List<String> =
    listOfNotNull(node["id"]?.jsonPrimitive?.content?.takeIf { node["type"]?.jsonPrimitive?.content == "table" }) +
        (node["children"]?.jsonArray?.flatMap { tablesIn(it.jsonObject) }.orEmpty())

class TableRealUiTest {

    @Test
    fun the_customers_screen_draws_its_tables_and_under_its_own_screen_profile_none_has_a_notice() {
        if (!File(REAL_UI).exists() || !File(REAL_VIEWS).exists()) {
            println("SKIPPED: $REAL_UI or $REAL_VIEWS does not exist on this machine; the real-ui check needs both")
            return
        }
        val text = File(REAL_UI).readText()
        val spec = decodeSpecFile(text, "screen")
        val screenRoot = json.parseToJsonElement(text).jsonObject.getValue("screen").jsonObject.getValue("root").jsonObject
        val hasScreenProfile = screenRoot["props"]?.jsonObject?.containsKey("display") == true
        val store = mapOf("reports" to reportsState(readReports(File(REAL_VIEWS).readText())))
        driving(spec, store) { port ->
            val drawnTables = tablesIn(json.parseToJsonElement(portCall(port, "tree").second).jsonObject)
            assertTrue(drawnTables.isNotEmpty(), "the screen draws at least one table")
            if (!hasScreenProfile) {
                println("SKIPPED notice check: the customer's screen root carries no display profile yet")
                return@driving
            }
            drawnTables.forEach { id -> assertEquals(emptyList(), noticesOf(port, id), id) }
        }
    }
}
