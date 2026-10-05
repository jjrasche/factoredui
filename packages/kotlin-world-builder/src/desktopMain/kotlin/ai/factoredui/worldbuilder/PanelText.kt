package ai.factoredui.worldbuilder

import ai.factoredui.worldengine.session.ScoreView
import kotlin.math.round

private const val ROUNDING_SCALE = 100.0
private const val UNITLESS = "1"
private const val NOT_MEASURED = "not-measured"

internal fun formatNumber(value: Double): String {
    val rounded = round(value * ROUNDING_SCALE) / ROUNDING_SCALE
    return if (rounded % 1.0 == 0.0) rounded.toLong().toString() else rounded.toString()
}

internal data class InstanceTally(val measured: Int, val proposed: Int) {
    val total: Int get() = measured + proposed
}

internal fun usageLines(
    uses: List<Map<String, Any?>>,
    counts: Map<String, Int>,
    areas: Map<String, Double>,
    instances: Map<String, InstanceTally> = emptyMap(),
): String =
    uses.joinToString("\n") { use ->
        val id = use["id"] as String
        "${use["label"]}: ${instanceClause(instances[id])}${counts[id] ?: 0} tiles, ${formatNumber(areas[id] ?: 0.0)} sq ft"
    }

private fun instanceClause(tally: InstanceTally?): String {
    if (tally == null || tally.total == 0) return ""
    val provenance = listOfNotNull(tally.measured.takeIf { it > 0 }?.let { "$it measured" }, tally.proposed.takeIf { it > 0 }?.let { "$it proposed" })
    return "${tally.total} placed (${provenance.joinToString(", ")}), "
}

internal fun scoreLines(scores: List<ScoreView>): String =
    scores.joinToString("\n") { score ->
        val unit = if (score.unit == UNITLESS) "" else " ${score.unit}"
        val binding = if (score.isBinding) " (binding)" else ""
        "${score.label ?: score.id}: ${score.value?.let { formatNumber(it) } ?: NOT_MEASURED}$unit$binding"
    }

internal fun diffLine(branch: String, parent: String?, diff: Map<String, Int>, labels: Map<String, String>): String {
    if (parent == null) return "on $branch"
    val changes = diff.filterValues { it != 0 }.map { (id, delta) -> "${if (delta > 0) "+" else ""}$delta ${labels[id] ?: id}" }
    return if (changes.isEmpty()) "$branch matches $parent" else "$branch vs $parent: ${changes.joinToString(", ")}"
}
