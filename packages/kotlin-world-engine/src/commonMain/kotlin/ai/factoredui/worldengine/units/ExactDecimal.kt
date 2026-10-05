package ai.factoredui.worldengine.units

import ai.factoredui.worldengine.json.MalformedDataException
import ai.factoredui.worldengine.text.pythonStr
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.pow

private const val LEADING_LIMBS = 3
private const val LARGEST_EXACT_DOUBLE_WHOLE = 9_007_199_254_740_992.0
private const val MOST_TILES_SPANNED = 2_000_000L

class ExactRatio private constructor(
    private val isNegative: Boolean,
    private val numerator: BigNatural,
    private val denominator: BigNatural,
) : Comparable<ExactRatio> {
    val isZero: Boolean get() = numerator.isZero

    fun timesWhole(factor: Long): ExactRatio = of(isNegative != factor < 0, numerator * BigNatural.magnitudeOf(factor), denominator)

    fun times(other: ExactRatio): ExactRatio = of(isNegative != other.isNegative, numerator * other.numerator, denominator * other.denominator)

    fun negated(): ExactRatio = of(!isNegative, numerator, denominator)

    fun toDouble(): Double {
        val numeratorShift = limbsBeyondLeading(numerator)
        val denominatorShift = limbsBeyondLeading(denominator)
        val leadingQuotient = numerator.withoutLowLimbs(numeratorShift).toDouble() / denominator.withoutLowLimbs(denominatorShift).toDouble()
        val magnitude = scaledByPowerOfTen(leadingQuotient, LIMB_DIGITS * (numeratorShift - denominatorShift))
        return if (isNegative) -magnitude else magnitude
    }

    override fun compareTo(other: ExactRatio): Int {
        if (isNegative != other.isNegative) return if (isNegative) -1 else 1
        val magnitudeOrder = (numerator * other.denominator).compareTo(other.numerator * denominator)
        return if (isNegative) -magnitudeOrder else magnitudeOrder
    }

    companion object {
        val ZERO: ExactRatio = of(false, BigNatural.ZERO, BigNatural.ONE)
        val MM_PER_FT: ExactRatio = of(false, BigNatural.magnitudeOf(3048), BigNatural.magnitudeOf(10))

        fun of(isNegative: Boolean, numerator: BigNatural, denominator: BigNatural): ExactRatio {
            require(!denominator.isZero) { "an exact ratio needs a non-zero denominator" }
            return ExactRatio(isNegative && !numerator.isZero, numerator, denominator)
        }
    }
}

private fun limbsBeyondLeading(value: BigNatural): Int = maxOf(0, value.limbCount - LEADING_LIMBS)

// Split in two so neither factor overflows while the product is still a finite double.
private fun scaledByPowerOfTen(value: Double, exponent: Int): Double = value * 10.0.pow(exponent / 2) * 10.0.pow(exponent - exponent / 2)

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
    val digits = BigNatural.parse((whole + fraction).ifEmpty { "0" })
    val scale = fraction.length - exponent
    if (scale < 0) return ExactRatio.of(isNegative, digits.timesPowerOfTen(-scale), BigNatural.ONE)
    return ExactRatio.of(isNegative, digits, BigNatural.ONE.timesPowerOfTen(scale))
}

fun ceilingQuotient(length: ExactRatio, unit: ExactRatio): Long {
    if (unit.isZero) throw ArithmeticException("division by zero")
    if (unit < ExactRatio.ZERO) return ceilingQuotient(length.negated(), unit.negated())
    fun isCovering(tiles: Long): Boolean = unit.timesWhole(tiles) >= length
    if (!isCovering(MOST_TILES_SPANNED)) return MOST_TILES_SPANNED
    if (isCovering(-MOST_TILES_SPANNED)) return -MOST_TILES_SPANNED
    val (notCovering, covering) = bracketAround(estimatedTiles(length, unit), ::isCovering)
    return bisectToFirstCovering(notCovering, covering, ::isCovering)
}

private fun estimatedTiles(length: ExactRatio, unit: ExactRatio): Long {
    val estimate = ceil(length.toDouble() / unit.toDouble())
    val isUsable = estimate.isFinite() && abs(estimate) < LARGEST_EXACT_DOUBLE_WHOLE
    return if (isUsable) estimate.toLong().coerceIn(-MOST_TILES_SPANNED, MOST_TILES_SPANNED) else 0L
}

private fun bracketAround(start: Long, isCovering: (Long) -> Boolean): Pair<Long, Long> =
    if (isCovering(start)) bracketBelow(start, isCovering) else bracketAbove(start, isCovering)

private fun bracketBelow(covering: Long, isCovering: (Long) -> Boolean): Pair<Long, Long> {
    var high = covering
    var step = 1L
    var low = (high - step).coerceAtLeast(-MOST_TILES_SPANNED)
    while (isCovering(low)) {
        high = low
        step *= 2
        low = (high - step).coerceAtLeast(-MOST_TILES_SPANNED)
    }
    return low to high
}

private fun bracketAbove(notCovering: Long, isCovering: (Long) -> Boolean): Pair<Long, Long> {
    var low = notCovering
    var step = 1L
    var high = (low + step).coerceAtMost(MOST_TILES_SPANNED)
    while (!isCovering(high)) {
        low = high
        step *= 2
        high = (low + step).coerceAtMost(MOST_TILES_SPANNED)
    }
    return low to high
}

private fun bisectToFirstCovering(notCovering: Long, covering: Long, isCovering: (Long) -> Boolean): Long {
    var low = notCovering
    var high = covering
    while (high - low > 1) {
        val middle = low + (high - low) / 2
        if (isCovering(middle)) high = middle else low = middle
    }
    return high
}
