package ai.factoredui.compose.format

import kotlin.math.abs

class DecimalDigits(val digits: String, val pointAt: Int)

private const val NO_DIGITS = "0"

private fun digitsOfPrinted(printed: String): DecimalDigits {
    val exponentAt = printed.indexOfFirst { it == 'E' || it == 'e' }
    val mantissa = if (exponentAt < 0) printed else printed.substring(0, exponentAt)
    val exponent = if (exponentAt < 0) 0 else printed.substring(exponentAt + 1).toInt()
    val allDigits = mantissa.replace(".", "")
    val pointInMantissa = mantissa.indexOf('.').takeIf { it >= 0 } ?: mantissa.length
    val leadingZeros = allDigits.length - allDigits.trimStart('0').length
    val significant = allDigits.trim('0').ifEmpty { NO_DIGITS }
    return DecimalDigits(significant, pointInMantissa + exponent - leadingZeros)
}

private fun roundedTo(figure: DecimalDigits, length: Int): DecimalDigits {
    val kept = figure.digits.substring(0, length).toDigitValues()
    val roundsUp = figure.digits[length] >= '5'
    val carried = if (roundsUp) incremented(kept) else kept
    val overflowed = carried.size > length
    val digits = carried.joinToString("").trimEnd('0').ifEmpty { NO_DIGITS }
    return DecimalDigits(digits, figure.pointAt + if (overflowed) 1 else 0)
}

private fun String.toDigitValues(): List<Int> = map { it - '0' }

private fun incremented(digits: List<Int>): List<Int> {
    val reversed = digits.reversed().toMutableList()
    var position = 0
    while (position < reversed.size && reversed[position] == 9) {
        reversed[position] = 0
        position++
    }
    if (position == reversed.size) reversed.add(1) else reversed[position] = reversed[position] + 1
    return reversed.reversed()
}

private fun valueOf(figure: DecimalDigits): Double = "0.${figure.digits}E${figure.pointAt}".toDouble()

/** The fewest decimal digits that read back as the same figure, whatever digits the platform's own printing produced. */
fun shortestDigits(figure: Double): DecimalDigits {
    require(figure.isFinite() && figure > 0.0) { "a shortest decimal needs a positive finite figure, not $figure" }
    var shortest = digitsOfPrinted(figure.toString())
    while (shortest.digits.length > 1) {
        val shorter = roundedTo(shortest, shortest.digits.length - 1)
        if (valueOf(shorter) != figure) break
        shortest = shorter
    }
    return shortest
}

/** Every digit of the figure written out with no exponent: 1.5E20 draws as 150000000000000000000. */
fun plainDecimalText(figure: Double): String {
    if (figure == 0.0) return "0"
    val sign = if (figure < 0) "-" else ""
    val decimal = shortestDigits(abs(figure))
    val digits = decimal.digits
    val unsigned = when {
        decimal.pointAt <= 0 -> "0." + "0".repeat(-decimal.pointAt) + digits
        decimal.pointAt >= digits.length -> digits + "0".repeat(decimal.pointAt - digits.length)
        else -> digits.substring(0, decimal.pointAt) + "." + digits.substring(decimal.pointAt)
    }
    return sign + unsigned
}

private fun scientificDecimalText(figure: Double): String {
    val sign = if (figure < 0) "-" else ""
    val decimal = shortestDigits(abs(figure))
    val rest = decimal.digits.substring(1).ifEmpty { "0" }
    return "$sign${decimal.digits[0]}.${rest}E${decimal.pointAt - 1}"
}

private fun pointedDecimalText(figure: Double): String = plainDecimalText(figure).let { if ('.' in it) it else "$it.0" }

private const val PLAIN_FROM = 1.0e-3
private const val PLAIN_BELOW = 1.0e7

fun javaStyleDecimalText(figure: Double): String = when {
    !figure.isFinite() || figure == 0.0 -> figure.toString()
    abs(figure) in PLAIN_FROM..<PLAIN_BELOW -> pointedDecimalText(figure)
    else -> scientificDecimalText(figure)
}
