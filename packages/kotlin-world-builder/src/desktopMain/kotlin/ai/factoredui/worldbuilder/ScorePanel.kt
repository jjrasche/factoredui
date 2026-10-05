package ai.factoredui.worldbuilder

import ai.factoredui.worldengine.session.ScoreView

private const val UNITLESS = "1"
private const val NOT_STATED = "not stated in the world file"
private val GROUP_ORDER = listOf("Cost", "Labour", "Yield", "Neighbours", "Other")
private val GROUP_BY_UNIT = mapOf(
    "usd" to "Cost",
    "hour/year" to "Labour",
    "ton/year" to "Yield",
    "lb/year" to "Yield",
    UNITLESS to "Neighbours",
)

internal data class ScoreRow(val id: String, val line: String)

internal data class ScoreGroup(val title: String, val rows: List<ScoreRow>)

internal fun scoreGroupOf(unit: String): String = GROUP_BY_UNIT[unit] ?: "Other"

internal fun scoreGroups(scores: List<ScoreView>, useLabels: List<String>): List<ScoreGroup> {
    val duplicated = useLabels.map { "$it area" }.toSet()
    val shown = scores.filterNot { it.unit == "sq_ft" && it.label in duplicated }
    val byGroup = shown.groupBy { scoreGroupOf(it.unit) }
    return GROUP_ORDER.mapNotNull { title -> byGroup[title]?.let { ScoreGroup(title, it.map(::scoreRow)) } }
}

private fun scoreRow(score: ScoreView): ScoreRow {
    val binding = if (score.isBinding) " (binding)" else ""
    return ScoreRow(score.id, "${score.label ?: score.id}: ${formatQuantity(score.value, score.unit)}$binding")
}

internal fun scoreDetailTitle(score: ScoreView): String = score.label ?: score.id

internal fun scoreDetailLines(score: ScoreView, unpricedLabels: List<String>): List<String> =
    listOfNotNull(
        score.note?.let { "What it means: $it" },
        "Where the figure comes from: ${score.source ?: NOT_STATED}",
        pricingLine(score.unit, unpricedLabels),
    )

private fun pricingLine(unit: String, unpricedLabels: List<String>): String? =
    if (unit == "usd" && unpricedLabels.isNotEmpty()) "Sourced prices only. No price yet for: ${unpricedLabels.joinToString(", ")}." else null
