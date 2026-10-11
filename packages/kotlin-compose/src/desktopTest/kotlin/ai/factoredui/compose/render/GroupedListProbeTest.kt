package ai.factoredui.compose.render

import ai.factoredui.compose.renderer.RenderContext
import ai.factoredui.compose.schema.Spec
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val json = Json { ignoreUnknownKeys = true }

private const val GROUPS = 20
private const val LINES_PER_GROUP = 19

private const val TEMPLATE = """
{"id":"item","type":"column","children":[
  {"id":"header","type":"card","visible":"{row.is_header}","props":{"padding":8},
   "action":{"action":"view.toggle_group","params":{"group":"{row.group}"}},
   "children":[{"id":"header-row","type":"row","children":[
     {"id":"header-label","type":"text","props":{"value":"{row.label}","bold":true,"flex":3}},
     {"id":"header-rows","type":"text","props":{"value":"{row.rows}","flex":1,"align":"right"}},
     {"id":"header-unpriced","type":"text","props":{"value":"{row.unpriced}","flex":1,"align":"right"}},
     {"id":"header-total","type":"text","props":{"value":"{row.priced_total}","format":"usd","flex":2,"align":"right"}}]}]},
  {"id":"line","type":"card","visible":"{row.is_line}","props":{"padding":8},
   "children":[{"id":"line-row","type":"row","children":[
     {"id":"line-label","type":"text","props":{"value":"{row.label}","flex":3}},
     {"id":"line-qty","type":"text","props":{"value":"{row.qty}","flex":1,"align":"right"}},
     {"id":"line-price","type":"text","props":{"value":"{row.price}","format":"usd","missing":"no price yet","flex":2,"align":"right"}}]}]}
]}"""

private fun listSpec(listProps: String, root: String) = json.decodeFromString(
    Spec.serializer(),
    """{"spec_version":1,"renderer_min":1,"root":{"id":"page","type":"$root","children":[{"id":"items","type":"list","props":{$listProps"data":"{bom.rows}","itemTemplate":$TEMPLATE}}]}}""",
)

private val BOUNDED = listSpec(""""flex":1,""", "column")

private val SCROLLED = listSpec(""""stacked":true,""", "scrollview")

private fun groupRows(group: Int): List<Map<String, Any?>> {
    val lines = (0 until LINES_PER_GROUP).map { line ->
        val price = if (line % 4 == 0) null else 10.0 + line
        mapOf("kind" to "line", "is_header" to false, "is_line" to true, "group" to "g$group", "label" to "part $group-$line", "qty" to (line + 1).toDouble(), "price" to price)
    }
    val priced = lines.mapNotNull { it["price"] as Double? }
    val header = mapOf(
        "kind" to "header", "is_header" to true, "is_line" to false, "group" to "g$group", "label" to "group $group",
        "rows" to LINES_PER_GROUP.toDouble(), "unpriced" to (LINES_PER_GROUP - priced.size).toDouble(), "priced_total" to priced.sum(),
    )
    return listOf(header) + lines
}

private val ROWS = (0 until GROUPS).flatMap(::groupRows)

private fun portCall(port: Int, route: String, vararg query: Pair<String, String>): Pair<Int, String> {
    val encoded = query.joinToString("&") { (key, value) -> "$key=${URLEncoder.encode(value, Charsets.UTF_8)}" }
    val connection = URI("http://127.0.0.1:$port/$route?$encoded").toURL().openConnection() as HttpURLConnection
    val stream = if (connection.responseCode < 400) connection.inputStream else connection.errorStream
    return connection.responseCode to stream.bufferedReader().readText()
}

private class Probe(val port: Int, val toggled: List<Any?>, val openMs: Long)

private fun <T> probing(spec: Spec, act: (Probe) -> T): T {
    val toggled = mutableListOf<Any?>()
    val context = RenderContext(
        actions = mapOf("view.toggle_group" to { params -> synchronized(toggled) { toggled += params["group"] } }),
        initialData = mapOf("bom" to mapOf("rows" to ROWS)),
    )
    val started = System.nanoTime()
    SceneSurface(spec, context, 800, 600).use { surface ->
        val openMs = (System.nanoTime() - started) / 1_000_000
        val server = WindowControl(context, surface).start(0)
        try {
            return act(Probe(server.address.port, toggled, openMs))
        } finally {
            server.stop(0)
        }
    }
}

private fun nodeAt(port: Int, id: String): JsonObject = json.parseToJsonElement(portCall(port, "tree", "id" to id).second).jsonObject

private fun JsonObject.flag(key: String): Boolean = getValue(key).jsonPrimitive.content.toBoolean()

private fun measure(label: String, probe: Probe) {
    val whole = portCall(probe.port, "tree").second.length
    val shown = portCall(probe.port, "tree", "visible" to "1").second.length
    println("GROUPEDPROBE $label open_ms=${probe.openMs} tree_bytes=$whole visible_tree_bytes=$shown")
}

class GroupedListProbeTest {

    @Test
    fun a_row_bound_visible_shows_only_the_header_card_on_header_rows_and_only_the_line_card_on_line_rows() {
        probing(BOUNDED) { probe ->
            assertEquals(listOf(true, false), listOf(nodeAt(probe.port, "items/0/header").flag("visible"), nodeAt(probe.port, "items/0/line").flag("visible")))
            assertEquals(listOf(false, true), listOf(nodeAt(probe.port, "items/1/header").flag("visible"), nodeAt(probe.port, "items/1/line").flag("visible")))
            assertEquals("group 0", nodeAt(probe.port, "items/0/header-label").getValue("text").jsonPrimitive.content)
            assertEquals("no price yet", nodeAt(probe.port, "items/1/line-price").getValue("text").jsonPrimitive.content)
            assertEquals("$11", nodeAt(probe.port, "items/2/line-price").getValue("text").jsonPrimitive.content)
            val shown = portCall(probe.port, "tree", "visible" to "1").second
            assertTrue("items/0/line\"" !in shown && "items/1/header\"" !in shown, "hidden cards are left out of /tree?visible=1")
        }
    }

    @Test
    fun a_header_tap_dispatches_the_toggle_action_with_its_rows_group() {
        probing(BOUNDED) { probe ->
            assertEquals(200, portCall(probe.port, "click", "id" to "items/0/header").first)
            assertEquals(listOf<Any?>("g0"), probe.toggled)
        }
    }

    @Test
    fun four_hundred_rows_in_a_bounded_list_draw_lazily_and_scroll() {
        probing(BOUNDED) { }
        probing(BOUNDED) { probe ->
            measure("bounded", probe)
            assertEquals(400 to "refused: no drawn node has id 'items/380/item'", portCall(probe.port, "tree", "id" to "items/380/item"))
            repeat(200) {
                if (portCall(probe.port, "tree", "id" to "items/380/item").first != 200) portCall(probe.port, "wheel", "x" to "400", "y" to "300", "notches" to "10")
            }
            assertEquals(200, portCall(probe.port, "tree", "id" to "items/380/item").first)
        }
    }

    @Test
    fun four_hundred_rows_stacked_in_a_scrollview_draw_every_row() {
        probing(SCROLLED) { }
        probing(SCROLLED) { probe ->
            measure("stacked", probe)
            assertEquals(200, portCall(probe.port, "tree", "id" to "items/399/item").first)
        }
    }
}
