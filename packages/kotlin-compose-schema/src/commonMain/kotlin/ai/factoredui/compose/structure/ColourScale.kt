package ai.factoredui.compose.structure

import ai.factoredui.compose.pixel.rgbOfHex

/** A stop with no [upTo] is open above: it takes every value the stops before it did not. */
data class ColourStop(val upTo: Double?, val colour: Long, val label: String? = null)

data class ColourScale(
    val channel: String,
    val stops: List<ColourStop>,
    val unknownColour: Long = NEUTRAL_PART_COLOUR,
    val unknownLabel: String? = null,
    val format: String? = null,
)

data class FaceShades(val top: Long, val west: Long, val south: Long)

data class LegendEntry(val colour: Long, val label: String)

const val NEUTRAL_PART_COLOUR: Long = 0xFF9AA4AD

private const val OPAQUE: Long = 0xFF000000L
private const val TOP_TOWARD_WHITE = 0.22
private const val SOUTH_DARKENING = 0.72

fun colourOfHex(raw: Any?): Long? = rgbOfHex(raw as? String)?.let { OPAQUE or it.toLong() }

fun stopFor(stops: List<ColourStop>, value: Double): ColourStop? = stops.firstOrNull { it.upTo == null || value <= it.upTo }

fun colourFor(scale: ColourScale?, value: Double?): Long {
    if (scale == null) return NEUTRAL_PART_COLOUR
    if (value == null) return scale.unknownColour
    return stopFor(scale.stops, value)?.colour ?: scale.unknownColour
}

private fun channelsOf(colour: Long): List<Long> = listOf((colour shr 16) and 0xFF, (colour shr 8) and 0xFF, colour and 0xFF)

private fun colourOf(channels: List<Long>): Long = OPAQUE or (channels[0] shl 16) or (channels[1] shl 8) or channels[2]

private fun towardWhite(colour: Long, share: Double): Long = colourOf(channelsOf(colour).map { it + ((255 - it) * share).toLong() })

private fun darkened(colour: Long, factor: Double): Long = colourOf(channelsOf(colour).map { (it * factor).toLong() })

/** The west face is the colour itself, lit from above and shaded to the south, so every face keeps the stop's hue. */
fun faceShades(colour: Long): FaceShades = FaceShades(top = towardWhite(colour, TOP_TOWARD_WHITE), west = colour, south = darkened(colour, SOUTH_DARKENING))

fun legendEntries(scale: ColourScale?): List<LegendEntry> {
    if (scale == null) return emptyList()
    val stops = scale.stops.mapNotNull { stop -> stop.label?.let { LegendEntry(stop.colour, it) } }
    val unknown = scale.unknownLabel?.let { LegendEntry(scale.unknownColour, it) }
    return stops + listOfNotNull(unknown)
}
