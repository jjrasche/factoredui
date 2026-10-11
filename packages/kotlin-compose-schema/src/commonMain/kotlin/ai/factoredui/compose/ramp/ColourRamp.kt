package ai.factoredui.compose.ramp

import ai.factoredui.compose.schema.plainFigureText
import kotlin.math.floor
import kotlin.math.pow

data class RampStop(val at: Double, val colour: Long)

data class RampLabel(val at: Double, val text: String)

data class ColourRamp(
    val stops: List<RampStop>,
    val over: Long,
    val noFigure: Long,
    val labels: List<RampLabel> = emptyList(),
    val overText: String? = null,
    val noFigureText: String? = null,
)

sealed interface RampReading {
    data class Readable(val ramp: ColourRamp) : RampReading
    data class Refused(val notice: String) : RampReading
}

sealed interface ValueColourReading {
    data class Coloured(val colour: Long) : ValueColourReading
    data class Refused(val notice: String) : ValueColourReading
}

const val NEUTRAL_RAMP_COLOUR: Long = 0xFF9AA4AD

private const val COLOUR_FORMS = "#rrggbb or #rrggbbaa"
private const val OPAQUE: Long = 0xFF000000L
private val CHANNEL_SHIFTS = listOf(24, 16, 8, 0)
private const val WCAG_TEXT = 4.5
private const val BLACK: Long = 0xFF000000L
private const val WHITE: Long = 0xFFFFFFFFL

fun rampColourOf(raw: Any?): Long? {
    val digits = (raw as? String)?.removePrefix("#") ?: return null
    if (digits.length != 6 && digits.length != 8) return null
    val value = digits.toLongOrNull(16) ?: return null
    return if (digits.length == 6) OPAQUE or value else ((value and 0xFF) shl 24) or (value shr 8)
}

private fun finiteNumberOf(raw: Any?): Double? = (raw as? Number)?.toDouble()?.takeIf { it.isFinite() }

private fun stopOf(raw: Any?): RampStop? {
    val pair = (raw as? List<*>)?.takeIf { it.size == 2 } ?: return null
    val at = finiteNumberOf(pair[0]) ?: return null
    val colour = rampColourOf(pair[1]) ?: return null
    return RampStop(at, colour)
}

private fun labelOf(raw: Any?): RampLabel? {
    val fields = raw as? Map<*, *> ?: return null
    val at = finiteNumberOf(fields["at"]) ?: return null
    val text = fields["text"] as? String ?: return null
    return RampLabel(at, text)
}

private fun stopsReading(owner: String, raw: Any?): Pair<List<RampStop>?, String?> {
    val entries = raw as? List<*> ?: return null to "$owner needs a list of [value, colour] stops, not '$raw'"
    if (entries.size < 2) return null to "$owner needs at least 2 stops, not ${entries.size}"
    val stops = entries.mapIndexed { index, entry ->
        stopOf(entry) ?: return null to "$owner stop $index must be [value, colour] with a number and $COLOUR_FORMS, not '$entry'"
    }
    val fall = stops.zipWithNext().firstOrNull { (before, after) -> after.at <= before.at }
    if (fall != null) return null to "$owner stop values must rise: ${plainFigureText(fall.first.at)} is followed by ${plainFigureText(fall.second.at)}"
    return stops to null
}

private fun optionalColourReading(owner: String, key: String, raw: Any?, fallback: Long): Pair<Long?, String?> {
    if (raw == null) return fallback to null
    val colour = rampColourOf(raw) ?: return null to "$owner $key must be $COLOUR_FORMS, not '$raw'"
    return colour to null
}

private fun labelsReading(owner: String, raw: Any?): Pair<List<RampLabel>?, String?> {
    val entries = raw as? List<*> ?: return emptyList<RampLabel>() to null
    val labels = entries.mapIndexed { index, entry ->
        labelOf(entry) ?: return null to "$owner legend entry $index must be {at: number, text}, not '$entry'"
    }
    return labels to null
}

fun readColourRamp(owner: String, raw: Any?): RampReading {
    val fields = raw as? Map<*, *> ?: return RampReading.Refused("$owner must be an object with stops, not '$raw'")
    val (stops, stopsNotice) = stopsReading(owner, fields["stops"])
    if (stops == null) return RampReading.Refused(stopsNotice!!)
    val (over, overNotice) = optionalColourReading(owner, "over", fields["over"], stops.last().colour)
    if (over == null) return RampReading.Refused(overNotice!!)
    val (noFigure, noFigureNotice) = optionalColourReading(owner, "no_figure", fields["no_figure"], NEUTRAL_RAMP_COLOUR)
    if (noFigure == null) return RampReading.Refused(noFigureNotice!!)
    val (labels, labelsNotice) = labelsReading(owner, fields["legend"])
    if (labels == null) return RampReading.Refused(labelsNotice!!)
    return RampReading.Readable(ColourRamp(stops, over, noFigure, labels, fields["over_text"] as? String, fields["no_figure_text"] as? String))
}

private fun channelOf(colour: Long, shift: Int): Int = ((colour shr shift) and 0xFF).toInt()

private fun mixed(from: Long, to: Long, share: Double): Long = CHANNEL_SHIFTS.fold(0L) { colour, shift ->
    val channel = floor(channelOf(from, shift) + (channelOf(to, shift) - channelOf(from, shift)) * share + 0.5).toLong()
    colour or (channel shl shift)
}

fun rampColourFor(ramp: ColourRamp, value: Double?): Long {
    if (value == null) return ramp.noFigure
    val first = ramp.stops.first()
    val last = ramp.stops.last()
    if (value <= first.at) return first.colour
    if (value > last.at) return ramp.over
    val (below, above) = ramp.stops.zipWithNext().first { (_, above) -> value <= above.at }
    return mixed(below.colour, above.colour, (value - below.at) / (above.at - below.at))
}

fun rampLabelFor(ramp: ColourRamp, value: Double?): String? = when {
    value == null -> ramp.noFigureText
    value > ramp.stops.last().at -> ramp.overText
    else -> ramp.labels.firstOrNull { it.at == value }?.text
}

fun readValueColour(owner: String, raw: Any?): ValueColourReading? {
    if (raw == null) return null
    val fields = raw as? Map<*, *> ?: return ValueColourReading.Refused("$owner must be an object with a value and a ramp, not '$raw'")
    val ramp = when (val reading = readColourRamp(owner, fields["ramp"])) {
        is RampReading.Refused -> return ValueColourReading.Refused(reading.notice)
        is RampReading.Readable -> reading.ramp
    }
    val value = fields["value"] ?: return ValueColourReading.Coloured(ramp.noFigure)
    val figure = finiteNumberOf(value) ?: return ValueColourReading.Refused("$owner needs a number, not '$value'")
    return ValueColourReading.Coloured(rampColourFor(ramp, figure))
}

private fun linearChannel(channel: Int): Double = (channel / 255.0).let { if (it <= 0.03928) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }

private fun luminanceOf(colour: Long): Double =
    0.2126 * linearChannel(channelOf(colour, 16)) + 0.7152 * linearChannel(channelOf(colour, 8)) + 0.0722 * linearChannel(channelOf(colour, 0))

fun contrastRatio(first: Long, second: Long): Double {
    val lighter = maxOf(luminanceOf(first), luminanceOf(second))
    val darker = minOf(luminanceOf(first), luminanceOf(second))
    return (lighter + 0.05) / (darker + 0.05)
}

fun legibleInk(preferred: Long?, background: Long): Long {
    if (preferred != null && contrastRatio(preferred, background) >= WCAG_TEXT) return preferred
    return if (contrastRatio(BLACK, background) >= contrastRatio(WHITE, background)) BLACK else WHITE
}
