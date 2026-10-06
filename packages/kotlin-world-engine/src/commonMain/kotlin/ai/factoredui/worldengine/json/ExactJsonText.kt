package ai.factoredui.worldengine.json

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

fun exactJsonText(element: JsonElement): String = when (element) {
    is JsonObject -> element.entries.joinToString(", ", "{", "}") { (key, value) -> "${JsonPrimitive(key)}: ${exactJsonText(value)}" }
    is JsonArray -> element.joinToString(", ", "[", "]") { exactJsonText(it) }
    is JsonNull -> "null"
    is JsonPrimitive -> element.toString()
}
