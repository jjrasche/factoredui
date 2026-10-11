package ai.factoredui.compose.format

import kotlin.math.abs
import kotlin.math.pow

private const val POUNDS_PER_KILOGRAM = 2.2046226218
private const val WHOLE_POUNDS_FROM = 100.0
private const val PERCENT = 100

private fun Double?.finiteOrNull(): Double? = this?.takeIf { it.isFinite() }

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

private const val LARGEST_EXACT_SCALED = 9.0e15

fun canDrawExactly(value: Double, fractionDigits: Int): Boolean = abs(value) * 10.0.pow(fractionDigits) < LARGEST_EXACT_SCALED

fun groupedFigureText(value: Double, fractionDigits: Int): String = localeNumber(value, fractionDigits)

fun dollarsText(dollars: Double?): String {
    val figure = dollars.finiteOrNull() ?: return MISSING_FIGURE
    val hasCents = roundHalfUp(figure * 100) % 100 != 0L
    return "$" + localeNumber(figure, if (hasCents) 2 else 0)
}

fun poundsText(kilograms: Double?): String {
    val figure = kilograms.finiteOrNull() ?: return MISSING_FIGURE
    val pounds = figure * POUNDS_PER_KILOGRAM
    return "${localeNumber(pounds, if (pounds >= WHOLE_POUNDS_FROM) 0 else 1)} lb"
}

fun percentText(share: Double): String = "${roundHalfUp(share * PERCENT)}%"
