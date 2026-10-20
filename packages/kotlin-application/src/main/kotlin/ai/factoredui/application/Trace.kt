package ai.factoredui.application

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

data class TraceSeries(val name: String, val points: List<Pair<Double, Double>>, val low: Double, val high: Double)

data class TracePanel(val title: String, val series: List<TraceSeries>)

data class Trace(val unit: String, val panels: List<TracePanel>, val notDrawn: Map<String, String>)

data class Traced(val trace: Trace?, val failure: String? = null)

private const val NO_EXTREMES = "the engine gave no extremes"

private fun lastWord(stock: String) = stock.substringAfterLast('_')

private fun incompleteOf(steps: List<JsonObject>): Map<String, String> =
    steps.flatMap { it["stocks_incomplete"].objectOrEmpty().entries }.associate { (stock, reasons) -> stock to reasons.textsOrEmpty().firstOrNull().orEmpty() }

private fun seriesOf(steps: List<JsonObject>, stock: String, extremes: JsonObject): TraceSeries? {
    val range = extremes[stock].objectOrEmpty()
    val low = range["low"].numberOrNull() ?: return null
    val high = range["high"].numberOrNull() ?: return null
    val points = steps.mapNotNull { step -> step["t"].numberOrNull()?.let { t -> step["stocks"].objectOrEmpty()[stock].numberOrNull()?.let { t to it } } }
    return TraceSeries(stock, points, low, high)
}

/** Stocks whose names end in the same word, a unit, share a panel; each other stock has its own. */
private fun panelsOf(series: List<TraceSeries>): List<TracePanel> {
    val sharing = series.groupBy { lastWord(it.name) }.filterValues { it.size > 1 }
    val shared = sharing.map { (unit, members) -> TracePanel(unit, members) }
    val alone = series.filter { lastWord(it.name) !in sharing }.map { TracePanel(humanize(it.name), listOf(it)) }
    return (shared + alone).sortedBy { panel -> series.indexOf(panel.series.first()) }
}

/** The simulation's stocks at each of its steps, for the stocks the engine gave extremes for and called complete. */
fun readTrace(text: String): Trace {
    val root = runCatching { Json.parseToJsonElement(text) }.getOrNull() as? JsonObject ?: throw BuildFormatException("the simulation is not a JSON object")
    (root["refused"] as? JsonObject)?.let { throw BuildFormatException((it["message"] as? JsonPrimitive)?.content ?: "the simulation was refused") }
    val steps = root["steps"].listOrEmpty().mapNotNull { it as? JsonObject }
    if (steps.isEmpty()) throw BuildFormatException("the simulation has no steps")
    val incomplete = incompleteOf(steps)
    val extremes = root["stock_extremes"].objectOrEmpty()
    val stocks = steps.first()["stocks"].objectOrEmpty().keys.filter { it !in incomplete }
    val drawn = stocks.associateWith { seriesOf(steps, it, extremes) }
    val missing = drawn.filterValues { it == null }.keys.associateWith { NO_EXTREMES }
    val unit = (root["clock"].objectOrEmpty()["unit"] as? JsonPrimitive)?.content ?: "step"
    return Trace(unit, panelsOf(drawn.values.filterNotNull()), incomplete + missing)
}
