package ai.factoredui.worldengine.world

import ai.factoredui.worldengine.units.describeExponentRefusal
import ai.factoredui.worldengine.units.isExponentTooLarge
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

class NumberExponentTooLarge(message: String) : WorldLoadException(message)

fun parseBoundedJson(text: String): JsonElement = Json.parseToJsonElement(text).also { document ->
    findOversizedNumber(document)?.let { throw NumberExponentTooLarge(it) }
}

fun findOversizedNumber(element: JsonElement): String? = when (element) {
    is JsonObject -> element.values.firstNotNullOfOrNull { findOversizedNumber(it) }
    is JsonArray -> element.firstNotNullOfOrNull { findOversizedNumber(it) }
    is JsonNull -> null
    is JsonPrimitive -> if (isNumber(element) && isExponentTooLarge(element.content)) describeExponentRefusal(element.content) else null
}

private fun isNumber(primitive: JsonPrimitive): Boolean = !primitive.isString && primitive.booleanOrNull == null
