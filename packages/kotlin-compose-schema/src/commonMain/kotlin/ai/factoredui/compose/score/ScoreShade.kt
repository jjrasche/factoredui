package ai.factoredui.compose.score

import ai.factoredui.compose.ramp.ColourRamp
import ai.factoredui.compose.ramp.RampReading
import ai.factoredui.compose.ramp.rampColourFor
import ai.factoredui.compose.ramp.readColourRamp

enum class ShadeScale(val wire: String) { VALUE("value"), RANK("rank") }

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

/** How named fields of a thing's values become one score and then a colour: any node that draws things with values reads this one record. */
data class ScoreShade(
    val weights: Map<String, Double>,
    val minimum: Double?,
    val ramp: ColourRamp,
    val minimumKnownShare: Double = 1.0,
    val scale: ShadeScale = ShadeScale.VALUE,
    val ranks: ScoreRanks? = null,
    val labels: Map<String, String> = emptyMap(),
) {
    fun withRanksOf(scores: List<Double>): ScoreShade = copy(ranks = ScoreRanks(scores))
}

sealed interface ScoreShadeReading {
    data class Readable(val shade: ScoreShade) : ScoreShadeReading
    data class Refused(val notice: String) : ScoreShadeReading
}

sealed interface ShadedColour {
    data class Coloured(val colour: Long) : ShadedColour
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

private fun labelsReading(raw: Any?): Map<String, String> =
    (raw as? Map<*, *>).orEmpty().mapNotNull { (field, text) -> (field as? String)?.let { name -> (text as? String)?.let { name to it } } }.toMap()

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
    if (fields["ramp"] == null) return ScoreShadeReading.Refused("$owner needs a ramp")
    return when (val ramp = readColourRamp("$owner ramp", fields["ramp"])) {
        is RampReading.Refused -> ScoreShadeReading.Refused(ramp.notice)
        is RampReading.Readable -> ScoreShadeReading.Readable(
            ScoreShade(weights, minimumFigure, ramp.ramp, knownShare, scale, labels = labelsReading(fields["labels"])),
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
data class ScoredThing(val id: String, val score: Double, val percentile: Double?, val rank: Int)

/** One field's share of a score: the shares of all fields sum to the score. */
data class ScorePart(val field: String, val label: String, val value: Double?, val weight: Double, val contribution: Double)

fun rankByScore(shade: ScoreShade, things: List<Pair<String, Map<String, Double?>>>, top: Int): List<ScoredThing> {
    val scored = things.mapNotNull { (id, values) -> scoreOf(shade, values)?.let { id to it } }
        .sortedWith(compareByDescending<Pair<String, Double>> { it.second }.thenBy { it.first })
    val valuesById = things.toMap()
    return scored.mapIndexedNotNull { index, (id, score) ->
        val isShown = shadeFor(shade, valuesById.getValue(id)) != ShadedColour.Hidden
        if (isShown) ScoredThing(id, score, shade.ranks?.percentileOf(score), rankOfScore(scored, index)) else null
    }.take(top)
}

private fun rankOfScore(sortedDescending: List<Pair<String, Double>>, index: Int): Int {
    val score = sortedDescending[index].second
    return sortedDescending.indexOfFirst { it.second == score } + 1
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

fun shadeFor(shade: ScoreShade, values: Map<String, Double?>): ShadedColour {
    val score = scoreOf(shade, values)
    val quantity = score?.let { shadedQuantityOf(shade, it) }
    val minimum = shade.minimum
    return when {
        quantity == null && minimum != null -> ShadedColour.Hidden
        quantity == null -> ShadedColour.Coloured(rampColourFor(shade.ramp, null))
        minimum != null && quantity < minimum -> ShadedColour.Hidden
        else -> ShadedColour.Coloured(rampColourFor(shade.ramp, quantity))
    }
}
