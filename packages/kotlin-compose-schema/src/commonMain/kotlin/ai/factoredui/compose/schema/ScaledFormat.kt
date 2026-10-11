package ai.factoredui.compose.schema

import ai.factoredui.compose.format.MISSING_FIGURE
import ai.factoredui.compose.format.canDrawExactly
import ai.factoredui.compose.format.groupedFigureText

/** A format written as data: the value times [scale], to [decimals] places, then [suffix]. */
data class ScaledFormat(val scale: Double, val decimals: Int, val suffix: String)

sealed interface ScaledFormatReading {
    data class Readable(val format: ScaledFormat) : ScaledFormatReading
    data class Refused(val notice: String) : ScaledFormatReading
}

private val SCALED_FORMAT_KEYS = setOf("scale", "decimals", "suffix")
private const val MOST_DECIMALS = 12

private fun noticeFigure(raw: Any?): String = (raw as? Number)?.toDouble()?.let(::plainFigureText) ?: "$raw"

private fun finiteNumberOf(raw: Any?): Double? = (raw as? Number)?.toDouble()?.takeIf { it.isFinite() }

private fun decimalsOf(raw: Any?): Int? {
    if (raw == null) return 0
    val figure = finiteNumberOf(raw) ?: return null
    return figure.takeIf { it % 1.0 == 0.0 && it in 0.0..MOST_DECIMALS.toDouble() }?.toInt()
}

fun readScaledFormat(fields: Map<*, *>): ScaledFormatReading {
    val strayKey = fields.keys.firstOrNull { it !in SCALED_FORMAT_KEYS }
    if (strayKey != null) return ScaledFormatReading.Refused("format has no key '$strayKey'; it reads scale, decimals and suffix")
    val rawScale = fields["scale"]
    val scale = finiteNumberOf(rawScale) ?: return ScaledFormatReading.Refused("format scale needs a number, not '${noticeFigure(rawScale)}'")
    val rawDecimals = fields["decimals"]
    val decimals = decimalsOf(rawDecimals)
        ?: return ScaledFormatReading.Refused("format decimals needs a whole number from 0 to $MOST_DECIMALS, not '${noticeFigure(rawDecimals)}'")
    val rawSuffix = fields["suffix"] ?: ""
    val suffix = rawSuffix as? String ?: return ScaledFormatReading.Refused("format suffix needs text, not '$rawSuffix'")
    return ScaledFormatReading.Readable(ScaledFormat(scale, decimals, suffix))
}

fun scaledFigureText(value: Any?, format: ScaledFormat, missingText: String?): String {
    if (value == null) return missingText ?: MISSING_FIGURE
    val figure = figureOf(value) ?: return "format needs a number, not '$value'"
    val scaled = figure * format.scale
    if (!canDrawExactly(scaled, format.decimals)) return "format cannot draw ${plainFigureText(scaled)} to ${format.decimals} decimals exactly"
    return groupedFigureText(scaled, format.decimals) + format.suffix
}

fun objectFormattedText(value: Any?, fields: Map<*, *>, missingText: String?): String = when (val reading = readScaledFormat(fields)) {
    is ScaledFormatReading.Refused -> reading.notice
    is ScaledFormatReading.Readable -> scaledFigureText(value, reading.format, missingText)
}
