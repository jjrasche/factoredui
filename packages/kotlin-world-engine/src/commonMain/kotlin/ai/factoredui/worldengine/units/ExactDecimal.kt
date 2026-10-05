package ai.factoredui.worldengine.units

import ai.factoredui.worldengine.json.MalformedDataException
import ai.factoredui.worldengine.text.pythonStr
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlin.math.ceil

private const val MAX_EXACT_DIGITS = 18

class ExactRatio(val numerator: Long, val denominator: Long) : Comparable<ExactRatio> {
    fun timesWhole(factor: Long): ExactRatio = ExactRatio(numerator * factor, denominator)

    fun times(other: ExactRatio): ExactRatio = ExactRatio(numerator * other.numerator, denominator * other.denominator)

    fun toDouble(): Double = numerator.toDouble() / denominator.toDouble()

    override fun compareTo(other: ExactRatio): Int = compareRatios(numerator, denominator, other.numerator, other.denominator)

    companion object {
        val ZERO: ExactRatio = ExactRatio(0, 1)
        val MM_PER_FT: ExactRatio = ExactRatio(3048, 10)
    }
}

fun exactDecimalOf(element: JsonElement): ExactRatio {
    val primitive = element as? JsonPrimitive
    val isNumber = primitive != null && primitive !is JsonNull && !primitive.isString && primitive.booleanOrNull == null
    if (!isNumber) throw MalformedDataException("invalid literal for Fraction: ${pythonStr(element)}")
    return parseDecimalLiteral(primitive.content)
}

private fun parseDecimalLiteral(literal: String): ExactRatio {
    val isNegative = literal.startsWith("-")
    val unsigned = literal.removePrefix("-")
    val exponentAt = unsigned.indexOfFirst { it == 'e' || it == 'E' }
    val mantissa = if (exponentAt < 0) unsigned else unsigned.substring(0, exponentAt)
    val exponent = if (exponentAt < 0) 0 else unsigned.substring(exponentAt + 1).removePrefix("+").toInt()
    val whole = mantissa.substringBefore('.')
    val fraction = mantissa.substringAfter('.', "").trimEnd('0')
    val digits = (whole + fraction).trimStart('0').ifEmpty { "0" }
    var scale = fraction.length - exponent
    var unscaled = digits
    if (scale < 0) {
        unscaled += "0".repeat(-scale)
        scale = 0
    }
    if (unscaled.length > MAX_EXACT_DIGITS || scale > MAX_EXACT_DIGITS) throw MalformedDataException("$literal carries more digits than an exact millimetre comparison holds")
    val magnitude = unscaled.toLong()
    return ExactRatio(if (isNegative) -magnitude else magnitude, powerOfTen(scale))
}

private fun powerOfTen(exponent: Int): Long = (1..exponent).fold(1L) { power, _ -> power * 10 }

// Compares a/b with c/d through their continued fractions, so no cross-multiplication can overflow.
private fun compareRatios(a: Long, b: Long, c: Long, d: Long): Int {
    val isLeftNegative = a < 0
    if (isLeftNegative != c < 0) return if (isLeftNegative) -1 else 1
    if (isLeftNegative) return compareNonNegativeRatios(-c, d, -a, b)
    return compareNonNegativeRatios(a, b, c, d)
}

private tailrec fun compareNonNegativeRatios(a: Long, b: Long, c: Long, d: Long): Int {
    val leftWhole = a / b
    val rightWhole = c / d
    if (leftWhole != rightWhole) return leftWhole.compareTo(rightWhole)
    val leftRest = a % b
    val rightRest = c % d
    if (leftRest == 0L || rightRest == 0L) return (leftRest > 0).compareTo(rightRest > 0)
    return compareNonNegativeRatios(d, rightRest, b, leftRest)
}

fun ceilingQuotient(length: ExactRatio, unit: ExactRatio): Long {
    var spanned = ceil(length.toDouble() / unit.toDouble()).toLong()
    while (unit.timesWhole(spanned - 1) >= length) spanned--
    while (unit.timesWhole(spanned) < length) spanned++
    return spanned
}
