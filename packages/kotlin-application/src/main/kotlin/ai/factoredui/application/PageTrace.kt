package ai.factoredui.application

import kotlinx.serialization.json.JsonObject

private const val CHART_WIDTH = 1000.0
private const val CHART_HEIGHT = 300.0
private const val CHART_GROUND = "#162033"
private const val AXIS_INK = "#7b8089"

private fun chartStyles(names: List<String>): Map<String, Map<String, Any?>> =
    names.associateWith { mapOf("stroke" to colourOfSystem(names, it), "weight" to 2.0) } + ("axis" to mapOf("stroke" to AXIS_INK, "weight" to 1.0))

private fun label(id: String, x: Double, y: Double, text: String, align: String) =
    mapOf("id" to id, "kind" to "label", "style" to "axis", "x_mm" to x, "y_mm" to y, "text" to text, "align" to align, "dx_dp" to 0, "dy_dp" to 0)

private fun panelRange(panel: TracePanel): Pair<Double, Double> = panel.series.minOf { it.low } to panel.series.maxOf { it.high }

private fun withoutFlatMiddles(points: List<Pair<Double, Double>>): List<Pair<Double, Double>> =
    points.filterIndexed { index, point -> index == 0 || index == points.lastIndex || point.second != points[index - 1].second || point.second != points[index + 1].second }

private fun lineShapes(panel: TracePanel, startT: Double, endT: Double): List<Map<String, Any?>> {
    val (low, high) = panelRange(panel)
    fun x(t: Double) = if (endT == startT) 0.0 else (t - startT) / (endT - startT) * CHART_WIDTH
    fun y(value: Double) = if (high == low) CHART_HEIGHT / 2 else (value - low) / (high - low) * CHART_HEIGHT
    return panel.series.flatMap { series ->
        withoutFlatMiddles(series.points).zipWithNext().mapIndexed { index, (from, to) -> lineShape("${series.name}/$index", series.name, x(from.first), y(from.second), x(to.first), y(to.second)) }
    }
}

private fun axisShapes(position: Int, panel: TracePanel, startT: Double, endT: Double, unit: String): List<Map<String, Any?>> {
    val (low, high) = panelRange(panel)
    return listOf(
        label("$position/high", 0.0, CHART_HEIGHT, formatFigure(high), "start"), label("$position/low", 0.0, 0.0, formatFigure(low), "start"),
        label("$position/start", 0.0, -24.0, "$unit ${formatFigure(startT)}", "start"), label("$position/end", CHART_WIDTH, -24.0, "$unit ${formatFigure(endT)}", "end"),
    )
}

private fun panelNode(position: Int, panel: TracePanel, names: List<String>, unit: String): JsonObject {
    val times = panel.series.flatMap { series -> series.points.map { it.first } }
    val startT = times.min()
    val endT = times.max()
    val chart = specNode(
        "trace.$position.chart", "plan",
        mapOf("shapes" to lineShapes(panel, startT, endT) + axisShapes(position, panel, startT, endT, unit), "styles" to chartStyles(names), "legend" to emptyList<Any>(), "empty_text" to "", "ground" to CHART_GROUND),
    )
    val words = panel.series.map { series ->
        textNode("trace.${series.name}.words", "${humanize(series.name)}  ${formatFigure(series.low)} to ${formatFigure(series.high)}", variant = "caption", color = colourOfSystem(names, series.name))
    }
    return specNode("trace.$position", "column", mapOf("gap" to 2), listOf(textNode("trace.$position.title", panel.title, variant = "caption", color = MUTED_INK), chart) + words)
}

/** The simulation's stocks over its steps, a panel for each unit; what the engine would not give a series for is named below. */
internal fun traceNode(traced: Traced): JsonObject {
    val title = textNode("trace.title", "Stocks over time", variant = "subheading", bold = true)
    val trace = traced.trace ?: return specNode("trace", "column", mapOf("gap" to 8), listOf(title, textNode("trace.failure", "The simulation could not be read: ${traced.failure}", variant = "caption", color = MUTED_INK)))
    val names = trace.panels.flatMap { panel -> panel.series.map { it.name } }
    val skipped = trace.notDrawn.map { (stock, reason) -> textNode("trace.$stock.skipped", "${humanize(stock)} not drawn: $reason", variant = "caption", color = MUTED_INK) }
    return specNode("trace", "column", mapOf("gap" to 8), listOf(title) + trace.panels.mapIndexed { position, panel -> panelNode(position, panel, names, trace.unit) } + skipped)
}
