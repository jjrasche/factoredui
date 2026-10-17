package ai.factoredui.application

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** A text colour reads only hex, so the quiet ink is a mid grey that holds on a light and a dark page. */
internal const val MUTED_INK = "#7b8089"

internal fun jsonOf(value: Any?): JsonElement = when (value) {
    null -> JsonNull
    is JsonElement -> value
    is String -> JsonPrimitive(value)
    is Boolean -> JsonPrimitive(value)
    is Number -> JsonPrimitive(value)
    is List<*> -> JsonArray(value.map(::jsonOf))
    is Map<*, *> -> JsonObject(value.entries.associate { (key, entry) -> key.toString() to jsonOf(entry) })
    else -> throw IllegalArgumentException("${value::class.simpleName} is not a JSON value")
}

internal fun specNode(id: String, type: String, props: Map<String, Any?> = emptyMap(), children: List<JsonObject> = emptyList()): JsonObject =
    JsonObject(
        mapOf(
            "id" to JsonPrimitive(id),
            "type" to JsonPrimitive(type),
            "props" to jsonOf(props),
            "children" to JsonArray(children),
        ),
    )

internal fun textNode(id: String, value: String, variant: String? = null, color: String? = null, bold: Boolean = false, flex: Number? = null): JsonObject =
    specNode(id, "text", listOfNotNull("value" to value, variant?.let { "variant" to it }, color?.let { "color" to it }, if (bold) "bold" to true else null, flex?.let { "flex" to it }).toMap())

internal fun humanize(name: String): String = name.removeSuffix("_mm").replace('_', ' ')

internal fun formatMm(value: Double): String = if (value % 1.0 == 0.0) value.toLong().toString() else "%.1f".format(value)
