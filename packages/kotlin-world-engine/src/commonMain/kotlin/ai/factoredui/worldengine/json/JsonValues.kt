package ai.factoredui.worldengine.json

import ai.factoredui.worldengine.text.pythonStrRepr
import ai.factoredui.worldengine.text.pythonStrip
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull

class MalformedDataException(override val message: String) : Exception(message)

fun missingKey(key: String): MalformedDataException = MalformedDataException(pythonStrRepr(key))

fun JsonObject.required(key: String): JsonElement = this[key] ?: throw missingKey(key)

fun JsonObject.requiredObject(key: String): JsonObject = required(key) as? JsonObject
    ?: throw MalformedDataException("'$key' is not an object")

fun JsonElement?.asTextOrNull(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content

fun JsonObject.requiredText(key: String): String = required(key).let { it.asTextOrNull() ?: pythonStrOf(it) }

fun JsonObject.optionalText(key: String): String? = this[key]?.let { if (it is JsonNull) null else it.asTextOrNull() ?: pythonStrOf(it) }

fun JsonObject.optionalList(key: String): JsonArray = when (val found = this[key]) {
    null -> JsonArray(emptyList())
    is JsonArray -> found
    else -> throw MalformedDataException("'$key' is not a list")
}

fun JsonArray.objects(): List<JsonObject> = map { it as? JsonObject ?: throw MalformedDataException("list member is not an object") }

fun JsonArray.texts(): List<String> = map { it.asTextOrNull() ?: pythonStrOf(it) }

private fun pythonStrOf(element: JsonElement): String = ai.factoredui.worldengine.text.pythonStr(element)

fun isBooleanLiteral(primitive: JsonPrimitive): Boolean = !primitive.isString && primitive.booleanOrNull != null

fun pythonInt(element: JsonElement?): Long {
    val primitive = element as? JsonPrimitive ?: throw MalformedDataException("int() argument must be a string or a number, not ${pythonTypeName(element)}")
    if (primitive is JsonNull) throw MalformedDataException("int() argument must be a string or a number, not 'NoneType'")
    if (primitive.isString) return parseIntegerText(primitive.content)
    if (isBooleanLiteral(primitive)) return if (primitive.content == "true") 1L else 0L
    return truncateToLong(primitive.content.toDouble(), primitive.content)
}

private fun truncateToLong(value: Double, literal: String): Long {
    if (literal.all { it == '-' || it in '0'..'9' }) return literal.toLong()
    if (value.isNaN()) throw MalformedDataException("cannot convert float NaN to integer")
    if (value.isInfinite()) throw MalformedDataException("cannot convert float infinity to integer")
    return value.toLong()
}

private fun parseIntegerText(text: String): Long {
    val cleaned = pythonStrip(text)
    val unsigned = cleaned.removePrefix("-").removePrefix("+")
    val isWellFormed = unsigned.isNotEmpty() && unsigned.first().isDigit() && unsigned.last().isDigit() &&
        unsigned.all { it.isDigit() || it == '_' } && "__" !in unsigned
    if (!isWellFormed) throw MalformedDataException("invalid literal for int() with base 10: ${pythonStrRepr(text)}")
    val magnitude = unsigned.filter { it != '_' }.map { it.digitToInt() }.fold(0L) { total, digit -> total * 10 + digit }
    return if (cleaned.startsWith("-")) -magnitude else magnitude
}

fun pythonFloat(element: JsonElement?): Double {
    val primitive = element as? JsonPrimitive ?: throw MalformedDataException("float() argument must be a string or a real number, not ${pythonTypeName(element)}")
    if (primitive is JsonNull) throw MalformedDataException("float() argument must be a string or a real number, not 'NoneType'")
    if (primitive.isString) return parseFloatText(primitive.content)
    if (isBooleanLiteral(primitive)) return if (primitive.content == "true") 1.0 else 0.0
    return primitive.doubleOrNull ?: throw MalformedDataException("could not convert to float: ${primitive.content}")
}

private fun parseFloatText(text: String): Double {
    val cleaned = pythonStrip(text).replace("_", "")
    val special = when (cleaned.lowercase().removePrefix("+")) {
        "inf", "infinity" -> Double.POSITIVE_INFINITY
        "-inf", "-infinity" -> Double.NEGATIVE_INFINITY
        "nan", "-nan" -> Double.NaN
        else -> null
    }
    return special ?: cleaned.toDoubleOrNull()?.takeIf { cleaned.none { it.isLetter() && it != 'e' && it != 'E' } }
        ?: throw MalformedDataException("could not convert string to float: ${pythonStrRepr(text)}")
}

private fun pythonTypeName(element: JsonElement?): String = when (element) {
    null, is JsonNull -> "'NoneType'"
    is JsonObject -> "'dict'"
    is JsonArray -> "'list'"
    is JsonPrimitive -> if (element.isString) "'str'" else "'float'"
}

fun isTruthy(element: JsonElement?): Boolean = when (element) {
    null, is JsonNull -> false
    is JsonObject -> element.isNotEmpty()
    is JsonArray -> element.isNotEmpty()
    is JsonPrimitive -> primitiveTruthiness(element)
}

private fun primitiveTruthiness(primitive: JsonPrimitive): Boolean {
    if (primitive.isString) return primitive.content.isNotEmpty()
    if (isBooleanLiteral(primitive)) return primitive.content == "true"
    return primitive.doubleOrNull?.let { it != 0.0 } ?: true
}

fun pythonEquals(left: JsonElement?, right: JsonElement?): Boolean = when {
    left == null || right == null -> left == right
    left is JsonObject && right is JsonObject -> left.keys == right.keys && left.keys.all { pythonEquals(left[it], right[it]) }
    left is JsonArray && right is JsonArray -> left.size == right.size && left.indices.all { pythonEquals(left[it], right[it]) }
    left is JsonNull || right is JsonNull -> left is JsonNull && right is JsonNull
    left is JsonPrimitive && right is JsonPrimitive -> primitivesEqual(left, right)
    else -> false
}

private fun primitivesEqual(left: JsonPrimitive, right: JsonPrimitive): Boolean {
    if (left.isString || right.isString) return left.isString && right.isString && left.content == right.content
    return numericOf(left) == numericOf(right)
}

private fun numericOf(primitive: JsonPrimitive): Double? {
    if (isBooleanLiteral(primitive)) return if (primitive.content == "true") 1.0 else 0.0
    return primitive.doubleOrNull
}
