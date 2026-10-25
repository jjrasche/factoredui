package ai.factoredui.application

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import java.nio.file.Files
import java.nio.file.Path

class BuyFormatException(message: String) : IllegalArgumentException(message)

data class BuyScreen(val title: String, val world: String)

private const val BOM_PATH = "bom"

/** The buy view a world gives: the world whose bill of materials it lists. */
fun readBuyScreen(text: String): BuyScreen {
    val root = runCatching { Json.parseToJsonElement(text) }.getOrNull() as? JsonObject ?: throw BuyFormatException("the buy view is not a JSON object")
    val world = (root["world"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: throw BuyFormatException("the buy view has no 'world' text")
    return BuyScreen((root["title"] as? JsonPrimitive)?.content ?: "What to buy", world)
}

private fun numberOf(line: JsonObject, key: String): Double = (line[key] as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull ?: throw BuyFormatException("a bill line has no number '$key'")

private fun textOf(line: JsonObject, key: String): String = (line[key] as? JsonPrimitive)?.content ?: throw BuyFormatException("a bill line has no '$key'")

private fun rowOf(line: JsonObject): Map<String, Any?> = mapOf(
    "id" to textOf(line, "stock_item"), "label" to textOf(line, "stock_item"), "object_ids" to emptyList<String>(),
    "values" to mapOf("count" to numberOf(line, "count"), "unit_price" to numberOf(line, "unit_price_usd"), "total" to numberOf(line, "total_usd"), "source" to (line["price_source"] as? JsonPrimitive)?.content),
)

private val COLUMNS = listOf(
    mapOf("id" to "count", "label" to "Count", "unit" to "count", "kind" to "count"),
    mapOf("id" to "unit_price", "label" to "Each", "unit" to "usd", "kind" to "usd"),
    mapOf("id" to "total", "label" to "Total", "unit" to "usd", "kind" to "usd"),
    mapOf("id" to "source", "label" to "Priced from", "type" to "text", "kind" to "text"),
)

private val COLUMN_OVERRIDES = mapOf(
    "source" to mapOf("hidden" to true), "count" to mapOf("width" to 52), "unit_price" to mapOf("width" to 84), "total" to mapOf("width" to 100),
)

private const val NOTHING_PICKED = "Tap a line to see where its price came from."

private val PICK_LINE = mapOf("action" to "set_binding", "params" to mapOf("values" to mapOf("bom.picked_item" to "{row.label}", "bom.picked_source" to "{row.values.source}")))

private val DISPLAY = mapOf("units" to mapOf("count" to mapOf("scale" to 1, "decimals" to 0), "usd" to "usd"))

/** What to buy: the engine's bill of materials as one sortable table, its own total on top. */
fun buySpec(screen: BuyScreen, expanded: JsonObject): JsonObject {
    val bill = expanded["bill_of_materials"] as? JsonObject ?: throw BuyFormatException("the engine's expand answer has no bill_of_materials")
    val lines = (bill["lines"] as? JsonArray).orEmpty().map { rowOf(it as? JsonObject ?: throw BuyFormatException("a bill line is not an object")) }
    val total = numberOf(bill, "total_usd")
    val view = mapOf("id" to BOM_PATH, "label" to screen.title, "columns" to COLUMNS, "rows" to lines)
    val children = listOf(
        textNode("buy.title", screen.title, variant = "subheading", bold = true, color = INK),
        specNode("buy.total", "text", mapOf("value" to total, "format" to "usd", "variant" to "heading")),
        textNode("buy.note", "From the engine's bill of materials, ${lines.size} lines; each price names where it came from. Tap a heading to sort.", variant = "caption", color = MUTED),
        textNode("buy.picked_item", "{bom.picked_item}", variant = "subheading", bold = true, color = INK),
        textNode("buy.picked_source", "{bom.picked_source}", variant = "caption", color = MUTED),
        specNode("buy.table", "table", mapOf("view" to "{$BOM_PATH.view}", "display" to DISPLAY, "label_header" to "Item", "columns" to COLUMN_OVERRIDES, "on_row_tap" to PICK_LINE, "sort_by" to "{$BOM_PATH.sort_by}", "sort_desc" to "{$BOM_PATH.sort_desc}", "title" to false)),
    )
    return JsonObject(
        mapOf(
            "spec_version" to JsonPrimitive(1), "renderer_min" to JsonPrimitive(1), "theme" to JsonPrimitive("dark"),
            "state" to jsonOf(mapOf("$BOM_PATH.view" to view, "$BOM_PATH.sort_by" to "total", "$BOM_PATH.sort_desc" to true, "$BOM_PATH.picked_item" to "", "$BOM_PATH.picked_source" to NOTHING_PICKED)),
            "root" to scrollingRoot(children, HUD),
        ),
    )
}

internal fun buyOfView(engine: EngineRuntime, worlds: List<EngineWorld>, viewFile: Path): JsonObject {
    val screen = readBuyScreen(Files.readString(viewFile))
    val world = worlds.firstOrNull { it.id == screen.world } ?: throw BuyFormatException("the engine serves no world '${screen.world}'")
    val text = expandSystem(engine, world.path)
    val expanded = Json.parseToJsonElement(text.substring(text.indexOf('{').coerceAtLeast(0))) as? JsonObject ?: throw BuyFormatException("the expand answer is not a JSON object")
    return buySpec(screen, expanded)
}
