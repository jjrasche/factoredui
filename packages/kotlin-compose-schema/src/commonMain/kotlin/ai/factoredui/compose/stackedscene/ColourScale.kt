package ai.factoredui.compose.stackedscene

import ai.factoredui.compose.ramp.ColourRamp
import ai.factoredui.compose.ramp.rampColourFor
import ai.factoredui.compose.ramp.rampLabelFor

/** A stop with no [upTo] is open above: it takes every value the stops before it did not. */
data class ColourStop(val upTo: Double?, val colour: Long, val label: String? = null)

data class ColourScale(
    val channel: String,
    val stops: List<ColourStop>,
    val unknownColour: Long = NEUTRAL_PART_COLOUR,
    val unknownLabel: String? = null,
    val format: String? = null,
    val ramp: ColourRamp? = null,
)

data class FaceShades(val top: Long, val xSide: Long, val ySide: Long)

data class LegendEntry(val colour: Long, val label: String)

const val NEUTRAL_PART_COLOUR: Long = 0xFF9AA4AD

private const val OPAQUE: Long = 0xFF000000L
private const val TOP_TOWARD_WHITE = 0.22
private const val Y_SIDE_DARKENING = 0.72

private fun rgbOfHex(hex: String?): Int? {
    val digits = hex?.removePrefix("#") ?: return null
    if (digits.length != 6) return null
    return digits.toIntOrNull(16)
}

fun colourOfHex(raw: Any?): Long? = rgbOfHex(raw as? String)?.let { OPAQUE or it.toLong() }

fun stopFor(stops: List<ColourStop>, value: Double): ColourStop? = stops.firstOrNull { it.upTo == null || value <= it.upTo }

fun colourFor(scale: ColourScale?, value: Double?): Long {
    if (scale == null) return NEUTRAL_PART_COLOUR
    if (scale.ramp != null) return rampColourFor(scale.ramp, value)
    if (value == null) return scale.unknownColour
    return stopFor(scale.stops, value)?.colour ?: scale.unknownColour
}

fun bandLabelFor(scale: ColourScale?, value: Double?): String? {
    if (scale == null) return null
    if (scale.ramp != null) return rampLabelFor(scale.ramp, value)
    if (value == null) return scale.unknownLabel
    val stop = stopFor(scale.stops, value) ?: return scale.unknownLabel
    return stop.label
}

private fun channelsOf(colour: Long): List<Long> = listOf((colour shr 16) and 0xFF, (colour shr 8) and 0xFF, colour and 0xFF)

private fun colourOf(channels: List<Long>): Long = OPAQUE or (channels[0] shl 16) or (channels[1] shl 8) or channels[2]

private fun towardWhite(colour: Long, share: Double): Long = colourOf(channelsOf(colour).map { it + ((255 - it) * share).toLong() })

private fun darkened(colour: Long, factor: Double): Long = colourOf(channelsOf(colour).map { (it * factor).toLong() })

/** A face across x (west or east) is the colour itself, the top is lit and a face across y (south or north) is shaded, so every face keeps the stop's hue and its shade at any turn. */
fun faceShades(colour: Long): FaceShades = FaceShades(top = towardWhite(colour, TOP_TOWARD_WHITE), xSide = colour, ySide = darkened(colour, Y_SIDE_DARKENING))

fun legendEntries(scale: ColourScale?): List<LegendEntry> {
    if (scale == null || scale.ramp != null) return emptyList()
    val stops = scale.stops.mapNotNull { stop -> stop.label?.let { LegendEntry(stop.colour, it) } }
    val unknown = scale.unknownLabel?.let { LegendEntry(scale.unknownColour, it) }
    return stops + listOfNotNull(unknown)
}
