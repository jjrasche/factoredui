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

private val UNIT_WORDS = mapOf("sq_ft" to "sq ft", "hour/year" to "hours/year", "ton/year" to "tons/year", "usd" to "$")

internal fun formatGrouped(value: Double): String {
    val plain = formatNumber(value)
    val sign = if (plain.startsWith("-")) "-" else ""
    val unsigned = plain.removePrefix("-")
    val whole = unsigned.substringBefore('.')
    val fraction = unsigned.substringAfter('.', "")
    val grouped = whole.reversed().chunked(3).joinToString(",").reversed()
    return sign + grouped + if (fraction.isEmpty()) "" else ".$fraction"
}

internal fun prettyUnit(unit: String): String = UNIT_WORDS[unit] ?: unit.replace('_', ' ')

internal fun formatQuantity(value: Double?, unit: String): String {
    if (value == null) return NOT_MEASURED
    val number = formatGrouped(value)
    return when (unit) {
        UNITLESS -> number
        "usd" -> if (value < 0) "-$" + formatGrouped(-value) else "$" + number
        else -> "$number ${prettyUnit(unit)}"
    }
}

internal fun formatChange(change: Double, unit: String): String {
    val sign = if (change > 0) "+" else if (change < 0) "-" else ""
    val magnitude = formatQuantity(kotlin.math.abs(change), unit)
    return if (change == 0.0) "no change" else sign + magnitude
}

private const val NOTHING_DIFFERS = "No figure differs yet."

internal fun compareLines(base: List<ScoreView>, other: List<ScoreView>): String {
    val baseById = base.associateBy { it.id }
    val changed = other.filter { baseById[it.id]?.value != it.value }
    if (changed.isEmpty()) return NOTHING_DIFFERS
    return changed.joinToString("\n") { score ->
        val before = baseById[score.id]?.value
        val after = score.value
        val change = if (before != null && after != null) formatChange(after - before, score.unit) else NOT_MEASURED
        "${score.label ?: score.id}: ${formatQuantity(before, score.unit)} to ${formatQuantity(after, score.unit)} ($change)"
    }
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
    if (parent == null) return "$branch is your base plan."
    val changes = diff.filterValues { it != 0 }.map { (id, delta) -> "${if (delta > 0) "+" else ""}$delta ${labels[id] ?: id}" }
    return if (changes.isEmpty()) "$branch matches $parent" else "$branch vs $parent: ${changes.joinToString(", ")}"
}
