package ai.factoredui.compose.score

import ai.factoredui.compose.ramp.ColourRamp
import ai.factoredui.compose.ramp.RampReading
import ai.factoredui.compose.ramp.rampColourFor
import ai.factoredui.compose.ramp.readColourRamp

/** How named fields of a thing's values become one score and then a colour: any node that draws things with values reads this one record. */
data class ScoreShade(val weights: Map<String, Double>, val minimum: Double?, val ramp: ColourRamp)

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

fun readScoreShade(owner: String, raw: Any?): ScoreShadeReading {
    val fields = raw as? Map<*, *> ?: return ScoreShadeReading.Refused("$owner must be an object with weights and a ramp, not '$raw'")
    val (weights, weightsNotice) = weightsReading(owner, fields["weights"])
    if (weights == null) return ScoreShadeReading.Refused(weightsNotice!!)
    val minimum = fields["minimum"]
    val minimumFigure = if (minimum == null) null else finiteNumberOf(minimum) ?: return ScoreShadeReading.Refused("$owner minimum must be a number, not '$minimum'")
    if (fields["ramp"] == null) return ScoreShadeReading.Refused("$owner needs a ramp")
    return when (val ramp = readColourRamp("$owner ramp", fields["ramp"])) {
        is RampReading.Refused -> ScoreShadeReading.Refused(ramp.notice)
        is RampReading.Readable -> ScoreShadeReading.Readable(ScoreShade(weights, minimumFigure, ramp.ramp))
    }
}

/** The mean of the weighted fields, each weighted by its weight; unknown when a weighted field is missing or no weight is above zero. */
fun weightedScore(values: Map<String, Double?>, weights: Map<String, Double>): Double? {
    var total = 0.0
    var weightSum = 0.0
    for ((field, weight) in weights) {
        if (weight == 0.0) continue
        val value = values[field] ?: return null
        total += weight * value
        weightSum += weight
    }
    return if (weightSum > 0.0) total / weightSum else null
}

fun shadeFor(shade: ScoreShade, values: Map<String, Double?>): ShadedColour {
    val score = weightedScore(values, shade.weights)
    val minimum = shade.minimum
    return when {
        score == null && minimum != null -> ShadedColour.Hidden
        score == null -> ShadedColour.Coloured(rampColourFor(shade.ramp, null))
        minimum != null && score < minimum -> ShadedColour.Hidden
        else -> ShadedColour.Coloured(rampColourFor(shade.ramp, score))
    }
}
