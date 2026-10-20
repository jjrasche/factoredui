package ai.factoredui.application

import kotlinx.serialization.json.JsonObject

private const val STRIP_WIDTH = 1000.0
private const val STRIP_BAR_DEPTH = 40.0
private const val STRIP_BODY_DEPTH = 24.0
private const val STRIP_HEIGHT = 120.0
private const val SPREAD_INK = "#6fd0ff"
private const val MEDIAN_INK = "#ffffff"
private const val THRESHOLD_INK = "#ffd479"
private const val RANGE_INK = "#7b8089"

private fun stripStyles(): Map<String, Map<String, Any?>> = mapOf(
    "range" to mapOf("stroke" to RANGE_INK, "weight" to 1.0),
    "spread" to mapOf("stroke" to SPREAD_INK, "fill" to "${SPREAD_INK}33", "weight" to 1.0),
    "body" to mapOf("stroke" to SPREAD_INK, "fill" to "${SPREAD_INK}99", "weight" to 1.0),
    "median" to mapOf("stroke" to MEDIAN_INK, "weight" to 2.5),
    "threshold" to mapOf("stroke" to THRESHOLD_INK, "weight" to 1.5, "dash" to true),
)

private fun at(value: Double, low: Double, high: Double) = if (high == low) STRIP_WIDTH / 2 else (value - low) / (high - low) * STRIP_WIDTH

private fun label(id: String, x: Double, y: Double, text: String, align: String, style: String = "range") =
    mapOf("id" to id, "kind" to "label", "style" to style, "x_mm" to x, "y_mm" to y, "text" to text, "align" to align, "dx_dp" to 0, "dy_dp" to 0)

private fun stripShapes(path: String, spread: Spread, thresholds: List<Threshold>): List<Map<String, Any?>> {
    val low = minOf(spread.min, thresholds.minOfOrNull { it.value } ?: spread.min)
    val high = maxOf(spread.max, thresholds.maxOfOrNull { it.value } ?: spread.max)
    val mid = STRIP_HEIGHT / 2
    fun rect(id: String, style: String, from: Double, to: Double, depth: Double) = rectShape("$path/$id", style, at(from, low, high), mid - depth / 2, at(to, low, high) - at(from, low, high), depth)
    val marks = thresholds.map { lineShape("$path/below:${it.value}", "threshold", at(it.value, low, high), 0.0, at(it.value, low, high), STRIP_HEIGHT) }
    val figures = listOf(
        label("$path/min", at(spread.min, low, high), 8.0, formatFigure(spread.min), "start"),
        label("$path/p50", at(spread.p50, low, high), STRIP_HEIGHT - 8, formatFigure(spread.p50), "center", "median"),
        label("$path/max", at(spread.max, low, high), 8.0, formatFigure(spread.max), "end"),
    )
    return listOf(lineShape("$path/range", "range", at(spread.min, low, high), mid, at(spread.max, low, high), mid)) +
        rect("p5-p95", "spread", spread.p5, spread.p95, STRIP_BAR_DEPTH) + rect("p25-p75", "body", spread.p25, spread.p75, STRIP_BODY_DEPTH) +
        lineShape("$path/median", "median", at(spread.p50, low, high), mid - STRIP_BAR_DEPTH / 2, at(spread.p50, low, high), mid + STRIP_BAR_DEPTH / 2) + marks + figures
}

private fun lastWord(path: String) = humanize(path.substringAfterLast('.'))

private fun percent(fraction: Double) = "${formatFigure(fraction * 100)}%"

/** One sentence for what the thresholds drew say together, or for each alone when there is no joint. */
internal fun oddsSentences(sampling: Sampling): List<String> {
    val byPath = sampling.thresholds.associateBy { it.path }
    fun clause(path: String) = "${lastWord(path)} at most ${formatFigure(byPath.getValue(path).value)}"
    val joint = sampling.joint?.takeIf { joint -> joint.paths.all { it in byPath } }
    return if (joint != null) listOf("${percent(joint.fraction)} of runs have ${joint.paths.joinToString(" and ") { clause(it) }}.")
    else sampling.thresholds.map { "${percent(it.fraction)} of runs have ${clause(it.path)}." }
}

private fun stripNode(path: String, spread: Spread, thresholds: List<Threshold>): JsonObject =
    specNode(
        "odds.$path", "column", mapOf("gap" to 2),
        listOf(
            textNode("odds.$path.title", humanize(path.substringAfterLast('.')) + "  (" + path + ")", variant = "caption", color = MUTED_INK),
            specNode(
                "odds.$path.strip", "plan",
                mapOf("shapes" to stripShapes(path, spread, thresholds), "styles" to stripStyles(), "legend" to emptyList<Any>(), "empty_text" to "", "ground" to "#162033"),
            ),
        ),
    )

/** The odds of a sample: a strip for each output that varied (range, p5 to p95, p25 to p75, the median, and any threshold), then what the thresholds say. */
internal fun oddsNode(sampled: Sampled): JsonObject {
    val sampling = sampled.sampling
    val title = textNode("odds.title", "Odds", variant = "subheading", bold = true)
    if (sampling == null) return specNode("odds", "column", mapOf("gap" to 8), listOf(title, textNode("odds.failure", "The sample could not be run: ${sampled.failure}", variant = "caption", color = MUTED_INK)))
    val note = textNode("odds.note", "${sampling.runs} runs, seed ${sampling.seed}${if (sampling.failedRuns > 0) ", ${sampling.failedRuns} refused" else ""}. Bar: p5 to p95; thick: p25 to p75; white: median; thin line: min to max; dashed: threshold.", variant = "caption", color = MUTED_INK)
    val varied = sampling.spreads.filter { (_, spread) -> spread.min != spread.max }
    val strips = varied.map { (path, spread) -> stripNode(path, spread, sampling.thresholds.filter { it.path == path }) }
    val sentences = oddsSentences(sampling).mapIndexed { index, text -> textNode("odds.sentence.$index", text, bold = true) }
    return specNode("odds", "column", mapOf("gap" to 8), listOf(title, note) + sentences + strips)
}
