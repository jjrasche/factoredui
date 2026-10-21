package ai.factoredui.compose.chart

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.round

enum class ChartSeriesKind(val wire: String) { LINE("line"), AREA("area") }

enum class ChartSide(val wire: String) { LEFT("left"), RIGHT("right") }

data class ChartSeriesDef(
    val id: String,
    val label: String,
    val path: String,
    val kind: ChartSeriesKind,
    val side: ChartSide,
    val colour: String?,
    val stack: String,
)

data class ChartAxis(val unit: String?, val label: String?)

/** A value at an x. A line has [from] and [to] equal to [value]; an area has the band it fills, where the stack puts it, below the zero line when [isBelow]. */
data class PlottedPoint(val x: Double, val value: Double, val from: Double, val to: Double, val isBelow: Boolean = false)

/** A series as unbroken runs of points: a row with no number at the series' path ends a run. */
data class PlottedSeries(val def: ChartSeriesDef, val runs: List<List<PlottedPoint>>)

enum class ChartXLabel(val wire: String) { NUMBER("number"), HOUR_OF_DAY("hour_of_day") }

data class ChartXAxis(val every: Double, val label: ChartXLabel)

data class ChartXTick(val x: Double, val label: String)

data class ChartPlot(val series: List<PlottedSeries>, val axes: Map<ChartSide, ChartAxis>, val notices: List<String>, val xAxis: ChartXAxis? = null)

data class ChartRange(val low: Double, val high: Double, val ticks: List<Double>)

data class ChartReadoutEntry(val id: String, val label: String, val value: Double, val text: String)

data class ChartReadout(val x: Double, val entries: List<ChartReadoutEntry>)

private const val DEFAULT_STACK = "stack"
private const val TARGET_TICK_STEPS = 4

private fun numberIn(fields: Map<*, *>, key: String): Double? = (fields[key] as? Number)?.toDouble()?.takeIf { it.isFinite() }

private fun numberAt(row: Map<*, *>, path: String): Double? {
    val leaf = path.split('.').fold(row as Any?) { current, key -> (current as? Map<*, *>)?.get(key) }
    return (leaf as? Number)?.toDouble()?.takeIf { it.isFinite() }
}

private fun kindOf(wire: Any?): ChartSeriesKind? = if (wire == null) ChartSeriesKind.LINE else ChartSeriesKind.entries.firstOrNull { it.wire == wire }

private fun sideOf(wire: Any?): ChartSide? = if (wire == null) ChartSide.LEFT else ChartSide.entries.firstOrNull { it.wire == wire }

private fun definitionOf(index: Int, entry: Any?): Pair<ChartSeriesDef?, String?> {
    val fields = entry as? Map<*, *> ?: return null to "chart series $index: it is not an object; it is not drawn."
    val id = fields["id"] as? String
    val path = fields["path"] as? String
    if (id == null || path == null) return null to "chart series $index: it needs an id and a path; it is not drawn."
    val kind = kindOf(fields["kind"]) ?: return null to "chart series '$id': kind must be line or area, not '${fields["kind"]}'; it is not drawn."
    val side = sideOf(fields["axis"]) ?: return null to "chart series '$id': axis must be left or right, not '${fields["axis"]}'; it is not drawn."
    return ChartSeriesDef(id, fields["label"] as? String ?: id, path, kind, side, fields["color"] as? String, fields["stack"] as? String ?: DEFAULT_STACK) to null
}

private fun axisOf(raw: Any?): ChartAxis? {
    val fields = raw as? Map<*, *> ?: return null
    return ChartAxis(fields["unit"] as? String, fields["label"] as? String)
}

private fun axesOf(raw: Any?): Map<ChartSide, ChartAxis> =
    ChartSide.entries.mapNotNull { side -> axisOf((raw as? Map<*, *>)?.get(side.wire))?.let { side to it } }.toMap()

private fun windowed(rows: List<Pair<Double, Map<*, *>>>, window: Any?): List<Pair<Double, Map<*, *>>> {
    val bounds = window as? Map<*, *> ?: return rows
    val start = numberIn(bounds, "from")
    val from = start ?: Double.NEGATIVE_INFINITY
    val to = numberIn(bounds, "to") ?: start?.let { first -> numberIn(bounds, "length")?.let { first + it } } ?: Double.POSITIVE_INFINITY
    return rows.filter { (x, _) -> x in from..to }
}

private class StackEdges(var up: Double = 0.0, var down: Double = 0.0)

private fun bandOf(value: Double, isBelow: Boolean, edges: StackEdges): Pair<Double, Double> =
    if (isBelow) (edges.down to edges.down + value).also { edges.down += value } else (edges.up to edges.up + value).also { edges.up += value }

private fun signsUpTo(values: List<Double?>): List<Boolean?> {
    var sign: Boolean? = null
    return values.map { value ->
        sign = when {
            value == null -> null
            value < 0 -> true
            value > 0 -> false
            else -> sign
        }
        sign
    }
}

/** Whether each value stacks below zero: by its own sign, and for a zero by the nearest nonzero value before it in its run, else after it, else above. */
private fun belowOf(values: List<Double?>): List<Boolean?> {
    val before = signsUpTo(values)
    val after = signsUpTo(values.asReversed()).asReversed()
    return values.indices.map { index -> values[index]?.let { value -> if (value != 0.0) value < 0 else before[index] ?: after[index] ?: false } }
}

private fun plottedPoint(def: ChartSeriesDef, x: Double, value: Double, isBelow: Boolean, edges: StackEdges): PlottedPoint =
    if (def.kind == ChartSeriesKind.AREA) bandOf(value, isBelow, edges).let { (from, to) -> PlottedPoint(x, value, from, to, isBelow) } else PlottedPoint(x, value, value, value)

private fun pointsOf(defs: List<ChartSeriesDef>, rows: List<Pair<Double, Map<*, *>>>): Map<String, List<PlottedPoint?>> {
    val values = defs.associate { def -> def.id to rows.map { (_, row) -> numberAt(row, def.path) } }
    val below = values.mapValues { (_, series) -> belowOf(series) }
    val points = defs.associate { it.id to mutableListOf<PlottedPoint?>() }
    rows.forEachIndexed { index, (x, _) ->
        val edges = mutableMapOf<Pair<ChartSide, String>, StackEdges>()
        defs.forEach { def ->
            val value = values.getValue(def.id)[index]
            val isBelow = below.getValue(def.id)[index] ?: false
            points.getValue(def.id).add(value?.let { plottedPoint(def, x, it, isBelow, edges.getOrPut(def.side to def.stack) { StackEdges() }) })
        }
    }
    return points
}

private fun runsOf(points: List<PlottedPoint?>): List<List<PlottedPoint>> {
    val runs = mutableListOf<MutableList<PlottedPoint>>()
    var current: MutableList<PlottedPoint>? = null
    points.forEach { point ->
        if (point == null) {
            current = null
        } else {
            val run = current ?: mutableListOf<PlottedPoint>().also { runs.add(it); current = it }
            run.add(point)
        }
    }
    return runs
}

private fun crossingBetween(before: PlottedPoint, after: PlottedPoint): Pair<PlottedPoint, PlottedPoint> {
    val share = abs(before.value) / (abs(before.value) + abs(after.value))
    val x = before.x + (after.x - before.x) * share
    return PlottedPoint(x, 0.0, before.from, before.from, before.isBelow) to PlottedPoint(x, 0.0, after.from, after.from, after.isBelow)
}

/** A run of an area as the pieces to fill: where it goes from above zero to below, a piece ends at the straight-line crossing and the next begins there. */
fun chartAreaPieces(run: List<PlottedPoint>): List<List<PlottedPoint>> {
    val pieces = mutableListOf(mutableListOf<PlottedPoint>())
    run.forEachIndexed { index, point ->
        val before = run.getOrNull(index - 1)
        if (before != null && before.isBelow != point.isBelow) {
            val (ending, beginning) = crossingBetween(before, point)
            pieces.last().add(ending)
            pieces.add(mutableListOf(beginning))
        }
        pieces.last().add(point)
    }
    return pieces
}
private fun noRowsNotice(): String = "chart: rows must be a list of objects."

private fun noXNotice(): String = "chart: x must name the path of each row's x value."

private fun skippedRowsNotice(count: Int, xPath: String): String? =
    if (count == 0) null else "chart: $count row${if (count == 1) " has" else "s have"} no number at '$xPath'; ${if (count == 1) "it is" else "they are"} not drawn."

/** The chart's rows as series of points: the window applied, each value read by its path, each area stacked in series order. */
fun plotChart(props: Map<String, Any?>): ChartPlot {
    val axes = axesOf(props["axes"])
    val rawRows = (props["rows"] as? List<*>)?.filterIsInstance<Map<*, *>>() ?: return ChartPlot(emptyList(), axes, listOf(noRowsNotice()))
    val xPath = props["x"] as? String ?: return ChartPlot(emptyList(), axes, listOf(noXNotice()))
    val positioned = rawRows.mapNotNull { row -> numberAt(row, xPath)?.let { it to row } }
    val rows = windowed(positioned, props["window"])
    val entries = (props["series"] as? List<*>).orEmpty().mapIndexed { index, entry -> definitionOf(index, entry) }
    val defs = entries.mapNotNull { it.first }
    val points = pointsOf(defs, rows)
    val drawn = defs.filter { def -> points.getValue(def.id).any { it != null } }
    val absentNotices = defs.filter { it !in drawn }.associate { it.id to "chart series '${it.id}': no row has a number at '${it.path}'; it is not drawn." }
    val seriesNotices = entries.mapNotNull { entry -> entry.second ?: entry.first?.let { absentNotices[it.id] } }
    val plotted = drawn.map { PlottedSeries(it, runsOf(points.getValue(it.id))) }
    val (xAxis, xAxisNotices) = xAxisOf(props["x_axis"])
    return ChartPlot(plotted, axes, listOfNotNull(skippedRowsNotice(rawRows.size - positioned.size, xPath)) + seriesNotices + xAxisNotices, xAxis)
}

private fun xAxisOf(raw: Any?): Pair<ChartXAxis?, List<String>> {
    val fields = raw as? Map<*, *> ?: return null to emptyList()
    val every = numberIn(fields, "every")?.takeIf { it > 0 } ?: return null to listOf("chart x_axis: every must be a number above zero; no ticks are drawn.")
    val label = if (fields["as"] == null) ChartXLabel.NUMBER else ChartXLabel.entries.firstOrNull { it.wire == fields["as"] }
    if (label == null) return ChartXAxis(every, ChartXLabel.NUMBER) to listOf("chart x_axis: as must be number or hour_of_day, not '${fields["as"]}'; ticks are labelled as numbers.")
    return ChartXAxis(every, label) to emptyList()
}

private fun twoDigits(value: Int): String = if (value < 10) "0$value" else value.toString()

private fun clockOf(x: Double): String {
    val hours = ((x % 24) + 24) % 24
    val whole = floor(hours).toInt()
    return "${twoDigits(whole)}:${twoDigits(round((hours - whole) * 60).toInt() % 60)}"
}

/** The labelled marks along the x axis: one at each multiple of the axis step inside the rows drawn. */
fun chartXTicks(plot: ChartPlot): List<ChartXTick> {
    val axis = plot.xAxis ?: return emptyList()
    val (low, high) = chartXRange(plot) ?: return emptyList()
    val first = ceil(low / axis.every).toLong()
    val last = floor(high / axis.every).toLong()
    return (first..last).map { step ->
        val x = step * axis.every
        ChartXTick(x, if (axis.label == ChartXLabel.HOUR_OF_DAY) clockOf(x) else formatChartValue(x))
    }
}

private fun valuesOnSide(plot: ChartPlot, side: ChartSide): List<Double> =
    plot.series.filter { it.def.side == side }.flatMap { series ->
        val points = series.runs.flatten()
        if (series.def.kind == ChartSeriesKind.AREA) points.flatMap { listOf(it.from, it.to) } + 0.0 else points.map { it.value }
    }

private fun stepFor(span: Double): Double {
    val raw = span / TARGET_TICK_STEPS
    val magnitude = 10.0.pow(floor(log10(raw)))
    val normalised = raw / magnitude
    val factor = when {
        normalised <= 1.0 -> 1.0
        normalised <= 2.0 -> 2.0
        normalised <= 5.0 -> 5.0
        else -> 10.0
    }
    return factor * magnitude
}

/** The axis on a side: the round numbers that enclose its values, and always zero when it holds an area. */
fun chartRange(plot: ChartPlot, side: ChartSide): ChartRange? {
    val values = valuesOnSide(plot, side).ifEmpty { return null }
    val low = values.min()
    val high = values.max()
    val (spanLow, spanHigh) = if (low == high) (low - 1.0) to (high + 1.0) else low to high
    val step = stepFor(spanHigh - spanLow)
    val first = floor(spanLow / step)
    val count = (ceil(spanHigh / step) - first).toInt()
    val ticks = (0..count).map { (first + it) * step }
    return ChartRange(ticks.first(), ticks.last(), ticks)
}

fun chartXRange(plot: ChartPlot): Pair<Double, Double>? {
    val xs = plot.series.flatMap { series -> series.runs.flatten().map { it.x } }.ifEmpty { return null }
    return xs.min() to xs.max()
}

fun formatChartValue(value: Double): String {
    val rounded = round(value * 100) / 100
    return if (rounded == floor(rounded)) rounded.toLong().toString() else rounded.toString()
}

private fun textOf(plot: ChartPlot, def: ChartSeriesDef, value: Double): String =
    listOfNotNull(formatChartValue(value), plot.axes[def.side]?.unit).joinToString(" ")

/** What every series holds at the x of the row nearest [x]: each series' own value, not the edge it is stacked to. */
fun chartReadout(plot: ChartPlot, x: Double): ChartReadout? {
    val xs = plot.series.flatMap { series -> series.runs.flatten().map { it.x } }.ifEmpty { return null }
    val nearest = xs.distinct().minWith(compareBy({ abs(it - x) }, { it }))
    val entries = plot.series.mapNotNull { series ->
        series.runs.flatten().firstOrNull { it.x == nearest }?.let { ChartReadoutEntry(series.def.id, series.def.label, it.value, textOf(plot, series.def, it.value)) }
    }
    return ChartReadout(nearest, entries)
}
