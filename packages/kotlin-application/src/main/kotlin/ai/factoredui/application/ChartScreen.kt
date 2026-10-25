package ai.factoredui.application

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.floor

class ChartScreenFormatException(message: String) : IllegalArgumentException(message)

data class ChartScreen(val title: String, val rowsKey: String, val chart: JsonObject, val xPath: String, val dayLength: Double, val dayFrom: Double?, val dayStep: Double)

private const val DAY_PATH = "day.from"
private const val DAY_LABEL_PATH = "day.label"

private fun objectIn(root: JsonObject, key: String): JsonObject = root[key] as? JsonObject ?: throw ChartScreenFormatException("the screen has no '$key' object")

private fun textIn(root: JsonObject, key: String): String = (root[key] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: throw ChartScreenFormatException("the screen has no '$key' text")

private fun numberIn(root: JsonObject, key: String): Double? = (root[key] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toDoubleOrNull()

/** The screen a world gives for its rows: the chart's own props (x, series, axes, labels) and how a day is scrubbed. */
fun readChartScreen(text: String): ChartScreen {
    val root = runCatching { Json.parseToJsonElement(text) }.getOrNull() as? JsonObject ?: throw ChartScreenFormatException("the screen is not a JSON object")
    val chart = JsonObject(objectIn(root, "chart") + (root["style"] as? JsonObject).orEmpty())
    val series = chart["series"] as? JsonArray ?: throw ChartScreenFormatException("the screen's chart has no 'series' list")
    if (series.isEmpty()) throw ChartScreenFormatException("the screen's chart has an empty 'series' list")
    val scrub = root["scrub"] as? JsonObject ?: JsonObject(emptyMap())
    return ChartScreen(
        (root["title"] as? JsonPrimitive)?.content ?: "", textIn(root, "rows"), chart, textIn(chart, "x"),
        numberIn(scrub, "length") ?: 0.0, numberIn(scrub, "from"), numberIn(scrub, "step") ?: ((numberIn(scrub, "length") ?: 0.0) + 1),
    )
}

private fun valueAt(row: JsonObject, path: String): JsonElement? =
    path.split('.').fold(row as JsonElement?) { current, key -> (current as? JsonObject)?.get(key) }

private fun withValueAt(path: List<String>, value: JsonElement, into: JsonObject): JsonObject {
    val head = path.first()
    if (path.size == 1) return JsonObject(into + (head to value))
    val child = into[head] as? JsonObject ?: JsonObject(emptyMap())
    return JsonObject(into + (head to withValueAt(path.drop(1), value, child)))
}

private fun projected(row: JsonObject, paths: List<String>): JsonObject =
    paths.fold(JsonObject(emptyMap())) { kept, path -> valueAt(row, path)?.let { withValueAt(path.split('.'), it, kept) } ?: kept }

private fun pathsOf(screen: ChartScreen): List<String> =
    listOf(screen.xPath) + (screen.chart["series"] as JsonArray).mapNotNull { ((it as? JsonObject)?.get("path") as? JsonPrimitive)?.content }

private const val ROW_LABEL = "at"

private fun labelled(row: JsonObject, xPath: String, clock: SeasonClock): JsonObject {
    val step = xOf(row, xPath) ?: return row
    return JsonObject(row + (ROW_LABEL to JsonPrimitive(stepLabel(clock, step))))
}

private fun rowsOf(screen: ChartScreen, season: JsonObject): List<JsonObject> {
    val rows = season[screen.rowsKey] as? JsonArray ?: throw ChartScreenFormatException("the season has no '${screen.rowsKey}' list")
    val paths = pathsOf(screen)
    val clock = clockOf(season)
    return rows.filterIsInstance<JsonObject>().map { labelled(projected(it, paths), screen.xPath, clock) }
}

private fun xOf(row: JsonObject, xPath: String): Double? = (valueAt(row, xPath) as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toDoubleOrNull()

private fun scrubControls(low: Double, high: Double, step: Double): List<JsonObject> = listOf(
    textNode("scrub.label", "{$DAY_LABEL_PATH}", variant = "subheading", color = INK),
    specNode("scrub.slider", "slider", mapOf("value" to "{$DAY_PATH}", "min" to low, "max" to high, "step" to step)),
)
private fun chartNode(screen: ChartScreen, rows: List<JsonObject>): JsonObject {
    val props = screen.chart.toMutableMap<String, JsonElement>()
    props["rows"] = JsonArray(rows)
    props["window"] = jsonOf(mapOf("from" to "{$DAY_PATH}", "length" to screen.dayLength))
    props["row_label"] = JsonPrimitive(ROW_LABEL)
    props["window_label"] = JsonPrimitive("{$DAY_LABEL_PATH}")
    props.putIfAbsent("ink", JsonPrimitive(INK))
    props.putIfAbsent("muted", JsonPrimitive(MUTED))
    return JsonObject(mapOf("id" to JsonPrimitive("chart"), "type" to JsonPrimitive("chart"), "props" to JsonObject(props), "children" to JsonArray(emptyList())))
}

private fun page(children: List<JsonObject>, state: Map<String, Any?>): JsonObject {
    val root = scrollingRoot(children, HUD)
    return JsonObject(mapOf("spec_version" to JsonPrimitive(1), "renderer_min" to JsonPrimitive(1), "theme" to JsonPrimitive("dark"), "state" to jsonOf(state), "root" to root))
}

/** The screen as a spec: the season's rows cut to the paths the chart reads and labelled with their time, the chart windowed by the day binding, and the one slider that moves the day. */
fun chartScreenSpec(screen: ChartScreen, season: JsonObject): JsonObject {
    val title = textNode("screen.title", screen.title, variant = "subheading", bold = true, color = INK)
    val rows = rowsOf(screen, season)
    val firsts = rows.mapNotNull { xOf(it, screen.xPath) }
    if (firsts.isEmpty()) return page(listOf(title, textNode("screen.empty", "The season has no rows to draw.", variant = "caption", color = MUTED)), emptyMap())
    val low = firsts.min()
    val step = screen.dayStep.takeIf { it >= 1.0 } ?: 1.0
    val high = low + floor((maxOf(low, firsts.max() - screen.dayLength) - low) / step) * step
    return page(listOf(title, chartNode(screen, rows)) + scrubControls(low, high, step), mapOf(DAY_PATH to (screen.dayFrom ?: low)))
}
