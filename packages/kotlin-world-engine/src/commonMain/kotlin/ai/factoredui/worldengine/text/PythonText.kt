package ai.factoredui.worldengine.text

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

private const val PYTHON_SPACES = "\t\n\u000B\u000C\r\u001C\u001D\u001E\u001F \u0085      　"

fun isPythonSpace(character: Char): Boolean = character in PYTHON_SPACES || character in ' '..' '

fun pythonStrip(text: String): String = text.trim { isPythonSpace(it) }

fun pythonFloatRepr(value: Double): String {
    if (value.isNaN()) return "nan"
    if (value.isInfinite()) return if (value > 0) "inf" else "-inf"
    val decimal = decomposeDecimal(value.toString())
    val sign = if (value < 0 || (value == 0.0 && 1.0 / value < 0)) "-" else ""
    if (decimal.digits.isEmpty()) return "${sign}0.0"
    val isExponentForm = decimal.pointPosition <= -4 || decimal.pointPosition > 16
    return sign + if (isExponentForm) exponentForm(decimal) else fixedForm(decimal)
}

private data class DecimalDigits(val digits: String, val pointPosition: Int)

private fun decomposeDecimal(printed: String): DecimalDigits {
    val unsigned = printed.removePrefix("-")
    val exponentAt = unsigned.indexOfFirst { it == 'e' || it == 'E' }
    val mantissa = if (exponentAt < 0) unsigned else unsigned.substring(0, exponentAt)
    val exponent = if (exponentAt < 0) 0 else unsigned.substring(exponentAt + 1).removePrefix("+").toInt()
    val pointAt = mantissa.indexOf('.').let { if (it < 0) mantissa.length else it }
    val rawDigits = mantissa.replace(".", "")
    val leadingZeros = rawDigits.takeWhile { it == '0' }.length
    val digits = rawDigits.substring(leadingZeros).trimEnd('0')
    return DecimalDigits(digits, pointAt + exponent - leadingZeros)
}

private fun exponentForm(decimal: DecimalDigits): String {
    val mantissa = decimal.digits.take(1) + decimal.digits.drop(1).let { if (it.isEmpty()) "" else ".$it" }
    val exponent = decimal.pointPosition - 1
    val exponentSign = if (exponent < 0) "-" else "+"
    return "${mantissa}e$exponentSign${kotlin.math.abs(exponent).toString().padStart(2, '0')}"
}

private fun fixedForm(decimal: DecimalDigits): String {
    val digits = decimal.digits
    val point = decimal.pointPosition
    if (point <= 0) return "0." + "0".repeat(-point) + digits
    if (point >= digits.length) return digits + "0".repeat(point - digits.length) + ".0"
    return digits.substring(0, point) + "." + digits.substring(point)
}

fun formatGeneral(value: Double, precision: Int): String {
    if (value.isNaN()) return "nan"
    if (value.isInfinite()) return if (value > 0) "inf" else "-inf"
    val sign = if (value < 0 || (value == 0.0 && 1.0 / value < 0)) "-" else ""
    val decimal = decomposeDecimal(value.toString())
    if (decimal.digits.isEmpty()) return "${sign}0"
    val rounded = roundToSignificant(decimal, maxOf(precision, 1))
    val exponent = rounded.pointPosition - 1
    val body = if (exponent < -4 || exponent >= precision) generalExponentForm(rounded, exponent) else fixedTrimmed(rounded)
    return sign + body
}

fun formatFixedTrimmed(value: Double, places: Int): String {
    if (!value.isFinite()) return pythonFloatRepr(value)
    val sign = if (value < 0 || (value == 0.0 && 1.0 / value < 0)) "-" else ""
    val decimal = decomposeDecimal(value.toString())
    val significant = decimal.pointPosition + places
    val rounded = when {
        decimal.digits.isEmpty() || significant < 0 -> DecimalDigits("", 0)
        significant == 0 -> roundBelowFirstDigit(decimal, places)
        else -> roundToSignificant(decimal, significant)
    }
    if (rounded.digits.isEmpty()) return "${sign}0"
    return sign + fixedForm(rounded).trimEnd('0').trimEnd('.')
}

private fun roundBelowFirstDigit(decimal: DecimalDigits, places: Int): DecimalDigits {
    val digits = decimal.digits
    val roundsUp = digits[0] > '5' || (digits[0] == '5' && digits.length > 1)
    return if (roundsUp) DecimalDigits("1", 1 - places) else DecimalDigits("", 0)
}

private fun roundToSignificant(decimal: DecimalDigits, precision: Int): DecimalDigits {
    val digits = decimal.digits
    if (digits.length <= precision) return decimal
    val next = digits[precision]
    val isTie = next == '5' && digits.length == precision + 1
    val isOddKept = (digits[precision - 1] - '0') % 2 == 1
    val roundsUp = next > '5' || (next == '5' && !isTie) || (isTie && isOddKept)
    val kept = digits.substring(0, precision)
    if (!roundsUp) return DecimalDigits(kept.trimEnd('0'), decimal.pointPosition)
    val carried = incrementDigits(kept)
    val grew = carried.length > kept.length
    return DecimalDigits(carried.trimEnd('0'), decimal.pointPosition + if (grew) 1 else 0)
}

private fun incrementDigits(digits: String): String {
    val characters = digits.toCharArray()
    var index = characters.lastIndex
    while (index >= 0 && characters[index] == '9') {
        characters[index] = '0'
        index--
    }
    if (index < 0) return "1" + characters.concatToString()
    characters[index] = characters[index] + 1
    return characters.concatToString()
}

private fun generalExponentForm(decimal: DecimalDigits, exponent: Int): String {
    val mantissa = decimal.digits.take(1) + decimal.digits.drop(1).let { if (it.isEmpty()) "" else ".$it" }
    val exponentSign = if (exponent < 0) "-" else "+"
    return "${mantissa}e$exponentSign${kotlin.math.abs(exponent).toString().padStart(2, '0')}"
}

private fun fixedTrimmed(decimal: DecimalDigits): String {
    val fixed = fixedForm(decimal)
    return fixed.removeSuffix(".0")
}

fun pythonStrRepr(text: String): String {
    val quote = if ('\'' in text && '"' !in text) '"' else '\''
    return buildString {
        append(quote)
        text.forEach { append(escapeReprCharacter(it, quote)) }
        append(quote)
    }
}

private fun escapeReprCharacter(character: Char, quote: Char): String = when {
    character == '\\' -> "\\\\"
    character == quote -> "\\$quote"
    character == '\t' -> "\\t"
    character == '\n' -> "\\n"
    character == '\r' -> "\\r"
    character < ' ' || character in '\u007F'..' ' -> "\\x" + character.code.toString(16).padStart(2, '0')
    character == ' ' || character == ' ' -> "\\u" + character.code.toString(16)
    else -> character.toString()
}

fun pythonNumberText(primitive: JsonPrimitive): String {
    val literal = primitive.content
    if (isIntegerLiteral(literal)) return normalizeIntegerLiteral(literal)
    return pythonFloatRepr(literal.toDouble())
}

private fun isIntegerLiteral(literal: String): Boolean = literal.removePrefix("-").let { it.isNotEmpty() && it.all { c -> c in '0'..'9' } }

private fun normalizeIntegerLiteral(literal: String): String {
    val magnitude = literal.removePrefix("-").trimStart('0').ifEmpty { "0" }
    return if (literal.startsWith("-") && magnitude != "0") "-$magnitude" else magnitude
}

fun pythonRepr(element: JsonElement): String = when (element) {
    is JsonNull -> "None"
    is JsonObject -> element.entries.joinToString(", ", "{", "}") { (key, value) -> "${pythonStrRepr(key)}: ${pythonRepr(value)}" }
    is JsonArray -> element.joinToString(", ", "[", "]") { pythonRepr(it) }
    is JsonPrimitive -> primitiveRepr(element)
}

private fun primitiveRepr(primitive: JsonPrimitive): String {
    if (primitive.isString) return pythonStrRepr(primitive.content)
    val flag = primitive.booleanOrNull
    if (flag != null) return if (flag) "True" else "False"
    return pythonNumberText(primitive)
}

fun pythonStr(element: JsonElement): String =
    if (element is JsonPrimitive && element.isString) element.content else pythonRepr(element)

fun pythonListRepr(items: List<String>): String = items.joinToString(", ", "[", "]") { pythonStrRepr(it) }

fun pythonTupleRepr(items: List<String>): String = items.joinToString(", ", "(", ")") { pythonStrRepr(it) }

fun pythonJsonDumps(element: JsonElement, sortKeys: Boolean): String = when (element) {
    is JsonNull -> "null"
    is JsonObject -> dumpObject(element, sortKeys)
    is JsonArray -> element.joinToString(", ", "[", "]") { pythonJsonDumps(it, sortKeys) }
    is JsonPrimitive -> dumpPrimitive(element)
}

private fun dumpObject(element: JsonObject, sortKeys: Boolean): String {
    val keys = if (sortKeys) element.keys.sorted() else element.keys.toList()
    return keys.joinToString(", ", "{", "}") { "${jsonQuote(it)}: ${pythonJsonDumps(element.getValue(it), sortKeys)}" }
}

private fun dumpPrimitive(primitive: JsonPrimitive): String {
    if (primitive.isString) return jsonQuote(primitive.content)
    if (primitive.booleanOrNull != null) return primitive.content
    val text = pythonNumberText(primitive)
    return when (text) {
        "inf" -> "Infinity"
        "-inf" -> "-Infinity"
        "nan" -> "NaN"
        else -> text
    }
}

fun jsonQuote(text: String): String = buildString {
    append('"')
    text.forEach { append(escapeJsonCharacter(it)) }
    append('"')
}

private fun escapeJsonCharacter(character: Char): String = when {
    character == '"' -> "\\\""
    character == '\\' -> "\\\\"
    character == '\n' -> "\\n"
    character == '\r' -> "\\r"
    character == '\t' -> "\\t"
    character == '\b' -> "\\b"
    character == '\u000C' -> "\\f"
    character < ' ' || character.code > 126 -> "\\u" + character.code.toString(16).padStart(4, '0')
    else -> character.toString()
}
