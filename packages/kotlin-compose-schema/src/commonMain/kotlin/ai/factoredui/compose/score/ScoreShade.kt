package ai.factoredui.compose.score

import ai.factoredui.compose.ramp.ColourRamp
import ai.factoredui.compose.ramp.RampReading
import ai.factoredui.compose.ramp.rampColourFor
import ai.factoredui.compose.ramp.readColourRamp

enum class ShadeScale(val wire: String) { VALUE("value"), RANK("rank") }

/** What a thing under the minimum looks like: still drawn, in the theme's dim colour, or not drawn at all. */
enum class BelowMinimum(val wire: String) { DIM("dim"), HIDE("hide") }

enum class GoodEnd(val wire: String) { HIGH("high"), LOW("low") }

data class FigureScaling(val good: GoodEnd, val best: Double?, val worst: Double?) {
    val hasRange: Boolean get() = best != null && worst != null
}

data class FigureExtent(val min: Double, val max: Double)

data class ScoreBounds(val low: Double, val high: Double) {
    val middle: Double get() = (low + high) / 2.0
    val isSettled: Boolean get() = high - low < SETTLED_TOLERANCE
}

private const val SETTLED_TOLERANCE = 1e-12
private const val NEUTRAL_FIGURE_SCORE = 0.5

class ScoreRanks(scores: List<Double>) {
    private val sorted = scores.sorted().toDoubleArray()

    fun percentileOf(score: Double): Double {
        val below = firstIndexAtOrAbove(score)
        val atOrBelow = firstIndexAbove(score)
        return (below + (atOrBelow - below) / 2.0) / sorted.size
    }

    private fun firstIndexAtOrAbove(score: Double): Int = partitionPoint { it < score }

    private fun firstIndexAbove(score: Double): Int = partitionPoint { it <= score }

    private fun partitionPoint(isBefore: (Double) -> Boolean): Int {
        var low = 0
        var high = sorted.size
        while (low < high) {
            val middle = (low + high) / 2
            if (isBefore(sorted[middle])) low = middle + 1 else high = middle
        }
        return low
    }
}

/** A bound on one named figure of a thing (real units, not a 0-to-1 value); a missing bound is open, and a range with neither bound is off. */
data class FigureRange(val min: Double?, val max: Double?) {
    val isActive: Boolean get() = min != null || max != null
}

/** How to say one figure to a reader: its name, unit and decimals. */
data class FigureInfo(val label: String, val unit: String, val decimals: Int, val source: String? = null, val evidence: String? = null)

/** How named fields of a thing's values become one score and then a colour: any node that draws things with values reads this one record. */
data class ScoreShade(
    val weights: Map<String, Double>,
    val minimum: Double?,
    val ramp: ColourRamp,
    val minimumKnownShare: Double = 1.0,
    val scale: ShadeScale = ShadeScale.VALUE,
    val ranks: ScoreRanks? = null,
    val labels: Map<String, String> = emptyMap(),
    val below: BelowMinimum = BelowMinimum.DIM,
    val ranges: Map<String, FigureRange> = emptyMap(),
    val figureInfo: Map<String, FigureInfo> = emptyMap(),
    val fieldFigures: Map<String, String> = emptyMap(),
    val scalings: Map<String, FigureScaling> = emptyMap(),
    val extents: Map<String, FigureExtent> = emptyMap(),
) {
    val isRaw: Boolean get() = scalings.isNotEmpty()

    fun withRanksOf(scores: List<Double>): ScoreShade = copy(ranks = ScoreRanks(scores))

    fun withExtentsOf(loaded: List<Map<String, Double?>>): ScoreShade {
        val found = LinkedHashMap<String, FigureExtent>()
        for (figures in loaded) {
            for ((figure, value) in figures) {
                if (figure !in scalings || value == null || !value.isFinite()) continue
                val seen = found[figure]
                found[figure] = if (seen == null) FigureExtent(value, value) else FigureExtent(minOf(seen.min, value), maxOf(seen.max, value))
            }
        }
        return copy(extents = found)
    }

    fun hasScoreInput(values: Map<String, Double?>, figures: Map<String, Double?>): Boolean =
        if (isRaw) figures.isNotEmpty() else values.isNotEmpty()
}

sealed interface ScoreShadeReading {
    data class Readable(val shade: ScoreShade) : ScoreShadeReading
    data class Refused(val notice: String) : ScoreShadeReading
}

sealed interface ShadedColour {
    data class Coloured(val colour: Long) : ShadedColour
    data object Dimmed : ShadedColour
    data object Hidden : ShadedColour
}

private fun finiteNumberOf(raw: Any?): Double? = (raw as? Number)?.toDouble()?.takeIf { it.isFinite() }

private fun weightsReading(owner: String, raw: Any?): Pair<Map<String, Double>?, String?> {
    val fields = (raw as? Map<*, *>)?.takeIf { it.isNotEmpty() } ?: return null to "$owner needs weights: an object of field name to number, not '$raw'"
    val weights = LinkedHashMap<String, Double>()
    for ((name, weight) in fields) {
        val field = name as? String ?: return null to "$owner weights has a name that is not text: '$name'"
        val figure = finiteNumberOf(weight) ?: return null to "$owner weight '$field' must be a number, not '$weight'"
        if (figure < 0.0) return null to "$owner weight '$field' is negative ($figure); a weight is zero or more"
        weights[field] = figure
    }
    return weights to null
}

private fun scaleReading(owner: String, raw: Any?): Pair<ShadeScale?, String?> {
    if (raw == null) return ShadeScale.VALUE to null
    val scale = ShadeScale.entries.firstOrNull { it.wire == raw }
    return if (scale == null) null to "$owner scale must be 'value' or 'rank', not '$raw'" else scale to null
}

private fun knownShareReading(owner: String, raw: Any?): Pair<Double?, String?> {
    if (raw == null) return 1.0 to null
    val share = finiteNumberOf(raw)?.takeIf { it in 0.0..1.0 }
    return if (share == null) null to "$owner min_known_share must be a number from 0 to 1, not '$raw'" else share to null
}

private fun belowReading(owner: String, raw: Any?): Pair<BelowMinimum?, String?> {
    if (raw == null) return BelowMinimum.DIM to null
    val below = BelowMinimum.entries.firstOrNull { it.wire == raw }
    return if (below == null) null to "$owner below must be 'dim' or 'hide', not '$raw'" else below to null
}

private fun labelsReading(raw: Any?): Map<String, String> =
    (raw as? Map<*, *>).orEmpty().mapNotNull { (field, text) -> (field as? String)?.let { name -> (text as? String)?.let { name to it } } }.toMap()

private fun boundReading(owner: String, figure: String, side: String, raw: Any?): Pair<Double?, String?> {
    if (raw == null) return null to null
    val bound = finiteNumberOf(raw) ?: return null to "$owner range '$figure' $side must be a number, not '$raw'"
    return bound to null
}

private fun rangesReading(owner: String, raw: Any?): Pair<Map<String, FigureRange>?, String?> {
    val ranges = LinkedHashMap<String, FigureRange>()
    for ((name, entry) in (raw as? Map<*, *>).orEmpty()) {
        val figure = name as? String ?: continue
        val bounds = entry as? Map<*, *> ?: return null to "$owner range '$figure' must be an object with min and max, not '$entry'"
        val (min, minNotice) = boundReading(owner, figure, "min", bounds["min"])
        if (minNotice != null) return null to minNotice
        val (max, maxNotice) = boundReading(owner, figure, "max", bounds["max"])
        if (maxNotice != null) return null to maxNotice
        if (min != null && max != null && min > max) return null to "$owner range '$figure' has a min ($min) over its max ($max)"
        ranges[figure] = FigureRange(min, max)
    }
    return ranges to null
}

private fun figureInfoReading(raw: Any?): Map<String, FigureInfo> =
    (raw as? Map<*, *>).orEmpty().mapNotNull { (name, entry) ->
        val figure = name as? String ?: return@mapNotNull null
        val fields = entry as? Map<*, *> ?: return@mapNotNull null
        figure to FigureInfo(
            label = fields["label"] as? String ?: figure,
            unit = fields["unit"] as? String ?: "",
            decimals = finiteNumberOf(fields["decimals"])?.toInt()?.coerceAtLeast(0) ?: 0,
            source = fields["source"] as? String,
            evidence = fields["evidence"] as? String,
        )
    }.toMap()

private fun goodEndReading(owner: String, figure: String, raw: Any?): Pair<GoodEnd?, String?> {
    val end = GoodEnd.entries.firstOrNull { it.wire == raw }
    return if (end == null) null to "$owner figure_scaling '$figure' good must be 'high' or 'low', not '$raw'" else end to null
}

private fun scalingReading(owner: String, figure: String, raw: Any?): Pair<FigureScaling?, String?> {
    val fields = raw as? Map<*, *> ?: return null to "$owner figure_scaling '$figure' must be an object with good and an optional best and worst, not '$raw'"
    val (best, bestNotice) = boundReading(owner, figure, "best", fields["best"])
    if (bestNotice != null) return null to bestNotice
    val (worst, worstNotice) = boundReading(owner, figure, "worst", fields["worst"])
    if (worstNotice != null) return null to worstNotice
    if ((best == null) != (worst == null)) return null to "$owner figure_scaling '$figure' range needs both best and worst"
    if (best != null && best == worst) return null to "$owner figure_scaling '$figure' range has the same best and worst ($best)"
    if (best != null && worst != null && fields["good"] == null) {
        return FigureScaling(if (best > worst) GoodEnd.HIGH else GoodEnd.LOW, best, worst) to null
    }
    val (good, goodNotice) = goodEndReading(owner, figure, fields["good"])
    if (good == null) return null to goodNotice
    return FigureScaling(good, best, worst) to null
}

private fun scalingsReading(owner: String, raw: Any?, weights: Map<String, Double>): Pair<Map<String, FigureScaling>?, String?> {
    if (raw == null) return emptyMap<String, FigureScaling>() to null
    val declared = raw as? Map<*, *> ?: return null to "$owner figure_scaling must be an object of figure name to scaling, not '$raw'"
    val scalings = LinkedHashMap<String, FigureScaling>()
    for ((name, entry) in declared) {
        val figure = name as? String ?: continue
        val (scaling, notice) = scalingReading(owner, figure, entry)
        if (scaling == null) return null to notice
        scalings[figure] = scaling
    }
    val unscaled = weights.filterValues { it > 0.0 }.keys.firstOrNull { it !in scalings }
    if (scalings.isNotEmpty() && unscaled != null) return null to "$owner weighted figure '$unscaled' has no figure_scaling"
    return scalings to null
}

fun readScoreShade(owner: String, raw: Any?): ScoreShadeReading {
    val fields = raw as? Map<*, *> ?: return ScoreShadeReading.Refused("$owner must be an object with weights and a ramp, not '$raw'")
    val (weights, weightsNotice) = weightsReading(owner, fields["weights"])
    if (weights == null) return ScoreShadeReading.Refused(weightsNotice!!)
    val minimum = fields["minimum"]
    val minimumFigure = if (minimum == null) null else finiteNumberOf(minimum) ?: return ScoreShadeReading.Refused("$owner minimum must be a number, not '$minimum'")
    val (scale, scaleNotice) = scaleReading(owner, fields["scale"])
    if (scale == null) return ScoreShadeReading.Refused(scaleNotice!!)
    val (knownShare, knownShareNotice) = knownShareReading(owner, fields["min_known_share"])
    if (knownShare == null) return ScoreShadeReading.Refused(knownShareNotice!!)
    val (below, belowNotice) = belowReading(owner, fields["below"])
    if (below == null) return ScoreShadeReading.Refused(belowNotice!!)
    val (ranges, rangesNotice) = rangesReading(owner, fields["ranges"])
    if (ranges == null) return ScoreShadeReading.Refused(rangesNotice!!)
    val (scalings, scalingsNotice) = scalingsReading(owner, fields["figure_scaling"], weights)
    if (scalings == null) return ScoreShadeReading.Refused(scalingsNotice!!)
    if (fields["ramp"] == null) return ScoreShadeReading.Refused("$owner needs a ramp")
    return when (val ramp = readColourRamp("$owner ramp", fields["ramp"])) {
        is RampReading.Refused -> ScoreShadeReading.Refused(ramp.notice)
        is RampReading.Readable -> ScoreShadeReading.Readable(
            ScoreShade(
                weights, minimumFigure, ramp.ramp, knownShare, scale,
                labels = labelsReading(fields["labels"]), below = below, ranges = ranges,
                figureInfo = figureInfoReading(fields["figure_info"]), fieldFigures = labelsReading(fields["field_figures"]),
                scalings = scalings,
            ),
        )
    }
}

/** The weighted mean of the known fields; unknown when their weights fall under [minimumKnownShare] of all weights. */
fun weightedScore(values: Map<String, Double?>, weights: Map<String, Double>, minimumKnownShare: Double = 1.0): Double? {
    var total = 0.0
    var knownWeight = 0.0
    var allWeight = 0.0
    for ((field, weight) in weights) {
        if (weight == 0.0) continue
        allWeight += weight
        val value = values[field] ?: continue
        total += weight * value
        knownWeight += weight
    }
    val isKnownEnough = allWeight > 0.0 && knownWeight > 0.0 && knownWeight >= allWeight * minimumKnownShare
    return if (isKnownEnough) total / knownWeight else null
}

fun scoreOf(shade: ScoreShade, values: Map<String, Double?>): Double? = weightedScore(values, shade.weights, shade.minimumKnownShare)

/** One thing in a ranking: rank 1 is the best score among every thing that has one, hidden or not. */
data class ScoredThing(
    val id: String,
    val score: Double,
    val percentile: Double?,
    val rank: Int,
    val low: Double = score,
    val high: Double = score,
    val isSettled: Boolean = true,
)

/** One field's share of a score: the shares of all fields sum to the score. */
data class ScorePart(val field: String, val label: String, val value: Double?, val weight: Double, val contribution: Double)

/** Whether a thing's figures sit inside every range that is on: a figure it has no number for is outside, since the reader asked for a number. */
private fun isInsideRanges(shade: ScoreShade, figures: Map<String, Double?>): Boolean =
    shade.ranges.filterValues { it.isActive }.all { (figure, range) ->
        val value = figures[figure]
        value != null && (range.min == null || value >= range.min) && (range.max == null || value <= range.max)
    }

/** The figures a thing has no number for among the ranges that are on: what a reader should be told the filter could not judge. */
fun unmeasuredFigures(shade: ScoreShade, figures: Map<String, Double?>): List<String> =
    shade.ranges.filterValues { it.isActive }.keys.filter { figures[it] == null }

/** A figure as a reader says it: its number to the decimals asked, then its unit; no number says so. */
fun figureText(info: FigureInfo?, value: Double?): String {
    if (value == null) return "no figure"
    if (info == null) return value.toString().removeSuffix(".0")
    val digits = info.decimals
    var scaled: Double = value
    repeat(digits) { scaled *= 10.0 }
    val rounded = kotlin.math.round(scaled).toLong()
    val sign = if (rounded < 0) "-" else ""
    val absolute = kotlin.math.abs(rounded).toString().padStart(digits + 1, '0')
    val number = if (digits == 0) absolute else absolute.dropLast(digits) + "." + absolute.takeLast(digits)
    return listOf(sign + number, info.unit).filter { it.isNotEmpty() }.joinToString(" ")
}

fun scoreBoundsOf(shade: ScoreShade, values: Map<String, Double?>, figures: Map<String, Double?>): ScoreBounds? {
    if (!shade.isRaw) return weightedScore(values, shade.weights, shade.minimumKnownShare)?.let { ScoreBounds(it, it) }
    val weighted = shade.weights.filterValues { it > 0.0 }
    val totalWeight = weighted.values.sum()
    if (totalWeight == 0.0) return null
    var low = 0.0
    var high = 0.0
    for ((figure, weight) in weighted) {
        val score = figureScoreOf(shade, figure, figures[figure])
        low += weight * (score ?: 0.0)
        high += weight * (score ?: 1.0)
    }
    return ScoreBounds(low / totalWeight, high / totalWeight)
}

fun figureScoreOf(shade: ScoreShade, figure: String, value: Double?): Double? {
    val scaling = shade.scalings[figure] ?: return null
    if (value == null || !value.isFinite()) return null
    if (scaling.best != null && scaling.worst != null) return ((value - scaling.worst) / (scaling.best - scaling.worst)).coerceIn(0.0, 1.0)
    val extent = shade.extents[figure] ?: return NEUTRAL_FIGURE_SCORE
    val span = extent.max - extent.min
    if (span == 0.0) return NEUTRAL_FIGURE_SCORE
    return when (scaling.good) {
        GoodEnd.HIGH -> (value - extent.min) / span
        GoodEnd.LOW -> (extent.max - value) / span
    }
}

private val BY_LOW_THEN_HIGH_BEST_FIRST = compareByDescending<Pair<String, ScoreBounds>> { it.second.low }
    .thenByDescending { it.second.high }
    .thenBy { it.first }

fun rankByScore(
    shade: ScoreShade,
    things: List<Pair<String, Map<String, Double?>>>,
    top: Int,
    figuresById: Map<String, Map<String, Double?>> = emptyMap(),
): List<ScoredThing> {
    val scored = things.mapNotNull { (id, values) -> scoreBoundsOf(shade, values, figuresById[id].orEmpty())?.let { id to it } }
        .sortedWith(BY_LOW_THEN_HIGH_BEST_FIRST)
    val valuesById = things.toMap()
    return scored.mapIndexedNotNull { index, (id, bounds) ->
        val isShown = shadeFor(shade, valuesById.getValue(id), figuresById[id].orEmpty()) is ShadedColour.Coloured
        if (isShown) {
            ScoredThing(id, bounds.middle, shade.ranks?.percentileOf(bounds.middle), rankOfBounds(scored, index), bounds.low, bounds.high, bounds.isSettled)
        } else {
            null
        }
    }.take(top)
}

private fun rankOfBounds(sortedBest: List<Pair<String, ScoreBounds>>, index: Int): Int {
    val own = sortedBest[index].second
    return sortedBest.indexOfFirst { it.second.low == own.low && it.second.high == own.high } + 1
}

fun rankAmong(own: ScoreBounds, others: List<ScoreBounds>): Int =
    others.count { it.low > own.low || (it.low == own.low && it.high > own.high) } + 1

fun scorePartsOf(shade: ScoreShade, values: Map<String, Double?>, figures: Map<String, Double?>): List<ScorePart> {
    if (!shade.isRaw) return scoreParts(shade, values)
    val weighted = shade.weights.filterValues { it > 0.0 }
    val totalWeight = weighted.values.sum()
    return weighted.map { (figure, weight) ->
        val score = figureScoreOf(shade, figure, figures[figure])
        ScorePart(figure, shade.labels[figure] ?: figure, score, weight, if (score == null || totalWeight == 0.0) 0.0 else weight * score / totalWeight)
    }
}

fun scoreParts(shade: ScoreShade, values: Map<String, Double?>): List<ScorePart> {
    val weighted = shade.weights.filterValues { it > 0.0 }
    val knownWeight = weighted.filterKeys { values[it] != null }.values.sum()
    return weighted.map { (field, weight) ->
        val value = values[field]
        ScorePart(field, shade.labels[field] ?: field, value, weight, if (value == null || knownWeight == 0.0) 0.0 else weight * value / knownWeight)
    }
}

private fun shadedQuantityOf(shade: ScoreShade, score: Double): Double? = when (shade.scale) {
    ShadeScale.VALUE -> score
    ShadeScale.RANK -> shade.ranks?.percentileOf(score)
}

fun shadeFor(shade: ScoreShade, values: Map<String, Double?>, figures: Map<String, Double?> = emptyMap()): ShadedColour {
    val bounds = scoreBoundsOf(shade, values, figures)
    val quantity = bounds?.let { shadedQuantityOf(shade, it.middle) }
    val bestCase = bounds?.let { shadedQuantityOf(shade, it.high) }
    val minimum = shade.minimum
    val outOfRange = if (shade.below == BelowMinimum.HIDE) ShadedColour.Hidden else ShadedColour.Dimmed
    return when {
        !isInsideRanges(shade, figures) -> outOfRange
        quantity == null && minimum != null -> outOfRange
        quantity == null -> ShadedColour.Coloured(rampColourFor(shade.ramp, null))
        minimum != null && bestCase != null && bestCase < minimum -> outOfRange
        else -> ShadedColour.Coloured(rampColourFor(shade.ramp, quantity))
    }
}
