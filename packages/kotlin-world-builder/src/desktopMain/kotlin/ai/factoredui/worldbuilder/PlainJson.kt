package ai.factoredui.worldbuilder

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

internal fun plainOf(element: JsonElement): Any? = when (element) {
    is JsonNull -> null
    is JsonPrimitive -> plainPrimitive(element)
    is JsonObject -> element.mapValues { plainOf(it.value) }
    is JsonArray -> element.map { plainOf(it) }
}

private fun plainPrimitive(primitive: JsonPrimitive): Any? =
    if (primitive.isString) primitive.content else primitive.booleanOrNull ?: primitive.longOrNull ?: primitive.doubleOrNull
