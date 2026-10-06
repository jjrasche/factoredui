package ai.factoredui.worldengine.units

const val MAX_NUMBER_EXPONENT = 400
const val NUMBER_EXPONENT_RULE = "number-exponent-too-large"

private const val LONGEST_COMPARABLE_EXPONENT_DIGITS = 17
private const val LONGEST_UNABBREVIATED_NUMERAL = 24
private const val ABBREVIATED_NUMERAL_PREFIX = 12

fun isExponentTooLarge(literal: String): Boolean {
    val unsigned = literal.removePrefix("-")
    val exponentAt = unsigned.indexOfFirst { it == 'e' || it == 'E' }
    val mantissa = if (exponentAt < 0) unsigned else unsigned.substring(0, exponentAt)
    val exponentText = if (exponentAt < 0) "" else unsigned.substring(exponentAt + 1)
    if (exponentText.trimStart('+', '-').length > LONGEST_COMPARABLE_EXPONENT_DIGITS) return true
    val writtenExponent = if (exponentText.isEmpty()) 0L else exponentText.removePrefix("+").toLong()
    val fractionDigits = mantissa.substringAfter('.', "").length
    val coefficientDigits = mantissa.replace(".", "").trimStart('0').length.coerceAtLeast(1)
    val leadingDigitPower = writtenExponent - fractionDigits + coefficientDigits - 1
    return leadingDigitPower > MAX_NUMBER_EXPONENT || leadingDigitPower < -MAX_NUMBER_EXPONENT
}

fun describeExponentRefusal(literal: String): String =
    "$NUMBER_EXPONENT_RULE: ${describeNumeral(literal)} has a decimal exponent beyond ±$MAX_NUMBER_EXPONENT"

private fun describeNumeral(literal: String): String =
    if (literal.length <= LONGEST_UNABBREVIATED_NUMERAL) literal else "${literal.take(ABBREVIATED_NUMERAL_PREFIX)}... (${literal.length} characters)"
