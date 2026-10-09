package ai.factoredui.compose.structure

import ai.factoredui.compose.scene.SceneView
import kotlin.math.abs

data class BomLine(
    val typeId: String,
    val label: String,
    val quantity: Int,
    val ids: List<String>,
    val unitCostUsd: Double?,
    val costUsd: Double?,
    val unitWeightKg: Double?,
    val weightKg: Double?,
    val weightUnknownReason: String?,
)

data class BomTotals(val quantity: Int, val costUsd: Double?, val unpricedLines: Int, val weightKg: Double?, val unweighedLines: Int)

data class BomLineText(val title: String, val priceLine: String, val weightLine: String)

data class BomTotalsText(val cost: String, val weight: String)

private const val POUNDS_PER_KILOGRAM = 2.2046226218
private const val WHOLE_POUNDS_FROM = 100.0
private const val WEIGHT_NOT_KNOWN = "weight not known"

private fun Double?.figureOrNull(): Double? = this?.takeIf { it.isFinite() }

private fun times(unit: Double?, quantity: Int): Double? = unit?.let { it * quantity }

private fun lineFor(scene: SceneView, typeId: String, ids: List<String>): BomLine {
    val type = scene.types.firstOrNull { it.id == typeId }
    val unitCostUsd = type?.unitCostUsd.figureOrNull()
    val unitWeightKg = type?.unitWeightKg.figureOrNull()
    return BomLine(
        typeId = typeId,
        label = type?.label ?: typeId,
        quantity = ids.size,
        ids = ids,
        unitCostUsd = unitCostUsd,
        costUsd = times(unitCostUsd, ids.size),
        unitWeightKg = unitWeightKg,
        weightKg = times(unitWeightKg, ids.size),
        weightUnknownReason = type?.unitWeightNullReason,
    )
}

fun bomLines(scene: SceneView, parts: List<VisiblePart>): List<BomLine> {
    val idsByType = LinkedHashMap<String, MutableList<String>>()
    parts.forEach { idsByType.getOrPut(it.footprint.type) { mutableListOf() } += it.footprint.id }
    return idsByType.map { (typeId, ids) -> lineFor(scene, typeId, ids) }
}

private fun sumOf(figures: List<Double?>): Double? {
    val present = figures.filterNotNull()
    return if (present.isEmpty()) null else present.fold(0.0) { sum, figure -> sum + figure }
}

fun bomTotals(lines: List<BomLine>): BomTotals = BomTotals(
    quantity = lines.sumOf { it.quantity },
    costUsd = sumOf(lines.map { it.costUsd }),
    unpricedLines = lines.count { it.costUsd == null },
    weightKg = sumOf(lines.map { it.weightKg }),
    unweighedLines = lines.count { it.weightKg == null },
)

private fun groupedThousands(whole: Long): String = whole.toString().reversed().chunked(3).joinToString(",").reversed()

/** en-US toLocaleString with a fixed number of fraction digits, rounding halves away from zero as Intl does. */
private fun localeNumber(value: Double, fractionDigits: Int): String {
    val divisor = (1..fractionDigits).fold(1L) { power, _ -> power * 10 }
    val scaled = roundHalfUp(abs(value) * divisor)
    val sign = if (value < 0 && scaled != 0L) "-" else ""
    val whole = groupedThousands(scaled / divisor)
    if (fractionDigits == 0) return "$sign$whole"
    val fraction = (scaled % divisor).toString().padStart(fractionDigits, '0')
    return "$sign$whole.$fraction"
}

fun dollarsText(dollars: Double?): String {
    val figure = dollars.figureOrNull() ?: return MISSING_FIGURE
    val hasCents = roundHalfUp(figure * 100) % 100 != 0L
    return "$" + localeNumber(figure, if (hasCents) 2 else 0)
}

fun poundsText(kilograms: Double?): String {
    val figure = kilograms.figureOrNull() ?: return MISSING_FIGURE
    val pounds = figure * POUNDS_PER_KILOGRAM
    return "${localeNumber(pounds, if (pounds >= WHOLE_POUNDS_FROM) 0 else 1)} lb"
}

private fun weightUnknownText(reason: String?): String = if (reason.isNullOrBlank()) WEIGHT_NOT_KNOWN else "$WEIGHT_NOT_KNOWN: $reason"

fun bomLineText(line: BomLine): BomLineText {
    val priceLine = if (line.unitCostUsd == null) "no price in the scene file" else "${dollarsText(line.unitCostUsd)} each · ${dollarsText(line.costUsd)} for ${line.quantity}"
    val weightLine = if (line.unitWeightKg == null) weightUnknownText(line.weightUnknownReason) else "${poundsText(line.unitWeightKg)} each · ${poundsText(line.weightKg)} for ${line.quantity}"
    return BomLineText("${line.quantity} × ${line.label}", priceLine, weightLine)
}

private fun leftOutText(count: Int, word: String): String = if (count > 0) " ($count $word)" else ""

fun bomTotalsText(totals: BomTotals): BomTotalsText {
    val cost = totals.costUsd?.let { dollarsText(it) + leftOutText(totals.unpricedLines, "unpriced") } ?: "no prices in the scene file"
    val weight = totals.weightKg?.let { poundsText(it) + leftOutText(totals.unweighedLines, "unweighed") } ?: WEIGHT_NOT_KNOWN
    return BomTotalsText(cost, weight)
}
