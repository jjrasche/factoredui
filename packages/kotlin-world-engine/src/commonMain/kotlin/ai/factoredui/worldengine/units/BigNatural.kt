package ai.factoredui.worldengine.units

import kotlin.math.abs

private const val LIMB_BASE = 1_000_000_000L
internal const val LIMB_DIGITS = 9

class BigNatural private constructor(private val limbs: IntArray) : Comparable<BigNatural> {
    val isZero: Boolean get() = limbs.isEmpty()

    val limbCount: Int get() = limbs.size

    override fun compareTo(other: BigNatural): Int {
        if (limbs.size != other.limbs.size) return limbs.size.compareTo(other.limbs.size)
        val mostSignificantDifference = limbs.indices.reversed().firstOrNull { limbs[it] != other.limbs[it] } ?: return 0
        return limbs[mostSignificantDifference].compareTo(other.limbs[mostSignificantDifference])
    }

    override fun equals(other: Any?): Boolean = other is BigNatural && limbs.contentEquals(other.limbs)

    override fun hashCode(): Int = limbs.contentHashCode()

    operator fun plus(other: BigNatural): BigNatural {
        val sum = LongArray(maxOf(limbs.size, other.limbs.size) + 1)
        var carry = 0L
        for (index in 0 until sum.size - 1) {
            val cell = limbs.getOrElse(index) { 0 }.toLong() + other.limbs.getOrElse(index) { 0 } + carry
            sum[index] = cell % LIMB_BASE
            carry = cell / LIMB_BASE
        }
        sum[sum.size - 1] = carry
        return normalized(sum)
    }

    operator fun times(other: BigNatural): BigNatural {
        val product = LongArray(limbs.size + other.limbs.size)
        for (row in limbs.indices) {
            var carry = 0L
            for (column in other.limbs.indices) {
                val cell = product[row + column] + limbs[row].toLong() * other.limbs[column] + carry
                product[row + column] = cell % LIMB_BASE
                carry = cell / LIMB_BASE
            }
            product[row + other.limbs.size] = carry
        }
        return normalized(product)
    }

    operator fun times(factor: Long): BigNatural {
        require(factor >= 0) { "a natural number multiplies only by a non-negative factor, not $factor" }
        return times(magnitudeOf(factor))
    }

    fun timesPowerOfTen(exponent: Int): BigNatural {
        require(exponent >= 0) { "a power of ten for a natural number needs a non-negative exponent, not $exponent" }
        if (isZero) return this
        val scaled = timesSmall(powerOfTenBelowLimb(exponent % LIMB_DIGITS))
        return BigNatural(IntArray(exponent / LIMB_DIGITS) + scaled.limbs)
    }

    fun withoutLowLimbs(count: Int): BigNatural = BigNatural(limbs.copyOfRange(minOf(count, limbs.size), limbs.size))

    fun toDouble(): Double = limbs.foldRight(0.0) { limb, accumulated -> accumulated * LIMB_BASE + limb }

    override fun toString(): String {
        if (isZero) return "0"
        val lowerLimbs = limbs.dropLast(1).asReversed().joinToString("") { it.toString().padStart(LIMB_DIGITS, '0') }
        return limbs.last().toString() + lowerLimbs
    }

    private fun timesSmall(factor: Int): BigNatural {
        val product = LongArray(limbs.size + 1)
        var carry = 0L
        for (index in limbs.indices) {
            val cell = limbs[index].toLong() * factor + carry
            product[index] = cell % LIMB_BASE
            carry = cell / LIMB_BASE
        }
        product[limbs.size] = carry
        return normalized(product)
    }

    companion object {
        val ZERO: BigNatural = BigNatural(IntArray(0))
        val ONE: BigNatural = BigNatural(intArrayOf(1))

        fun parse(digits: String): BigNatural {
            require(digits.isNotEmpty() && digits.all { it in '0'..'9' }) { "'$digits' is not a string of decimal digits" }
            val limbCount = (digits.length + LIMB_DIGITS - 1) / LIMB_DIGITS
            val limbs = IntArray(limbCount) { index -> limbDigitsAt(digits, index).toInt() }
            return normalized(limbs)
        }

        fun magnitudeOf(value: Long): BigNatural {
            val limbs = mutableListOf<Int>()
            var remaining = value
            while (remaining != 0L) {
                limbs += abs(remaining % LIMB_BASE).toInt()
                remaining /= LIMB_BASE
            }
            return BigNatural(limbs.toIntArray())
        }

        private fun limbDigitsAt(digits: String, limbIndex: Int): String {
            val end = digits.length - limbIndex * LIMB_DIGITS
            return digits.substring(maxOf(0, end - LIMB_DIGITS), end)
        }

        private fun powerOfTenBelowLimb(exponent: Int): Int = (1..exponent).fold(1) { power, _ -> power * 10 }

        private fun normalized(limbs: IntArray): BigNatural = BigNatural(limbs.copyOf(significantLength(limbs.size) { limbs[it] != 0 }))

        private fun normalized(limbs: LongArray): BigNatural =
            BigNatural(IntArray(significantLength(limbs.size) { limbs[it] != 0L }) { limbs[it].toInt() })

        private fun significantLength(size: Int, isNonZeroAt: (Int) -> Boolean): Int =
            (size - 1 downTo 0).firstOrNull(isNonZeroAt)?.plus(1) ?: 0
    }
}
