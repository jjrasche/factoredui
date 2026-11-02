package ai.factoredui.compose.renderer

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/** What a host page's script may do to a screen's state: write a value by dotted path (a numeric segment is a list index), read one back, and watch one. Values cross as JSON text. */
class StateBridge(private val context: RenderContext) {

    /** Writes [json] at [path]; null when written, else why not. */
    fun setState(path: String, json: String): String? {
        val segments = path.split('.')
        if (segments.any { it.isEmpty() }) return "setState: '$path' is not a dotted path of names and list indexes."
        val value = runCatching { valueOfJson(Json.parseToJsonElement(json)) }.getOrElse { return "setState '$path': the value is not JSON text (${it.message})." }
        context.setBinding(segments.first(), replacedIn(context.data[segments.first()], segments.drop(1), value))
        return null
    }

    fun getState(path: String): String = Json.encodeToString(JsonElement.serializer(), jsonOfValue(valueAt(context.data, path.split('.'))))

    /** The JSON text at [path] now and each time the value there is replaced. */
    fun changes(path: String): Flow<String> {
        val segments = path.split('.')
        return context.dataFlow.map { valueAt(it, segments) }.distinctUntilChanged { before, after -> before === after || (before !is Map<*, *> && before !is List<*> && before == after) }
            .map { Json.encodeToString(JsonElement.serializer(), jsonOfValue(it)) }
    }
}

private fun valueAt(root: Any?, segments: List<String>): Any? = segments.fold(root) { node, key ->
    when (node) {
        is Map<*, *> -> node[key]
        is List<*> -> key.toIntOrNull()?.let { node.getOrNull(it) }
        else -> null
    }
}

private fun replacedIn(node: Any?, segments: List<String>, value: Any?): Any? {
    if (segments.isEmpty()) return value
    val key = segments.first()
    val rest = segments.drop(1)
    val index = key.toIntOrNull()
    return when {
        node is List<*> && index != null -> node.toMutableList().also { grown ->
            while (grown.size <= index) grown.add(null)
            grown[index] = replacedIn(grown[index], rest, value)
        }
        node !is Map<*, *> && index != null -> replacedIn(emptyList<Any?>(), segments, value)
        else -> (node as? Map<*, *>).orEmpty().entries.associate { it.key.toString() to it.value }.toMutableMap().also { it[key] = replacedIn(it[key], rest, value) }
    }
}

private fun valueOfJson(element: JsonElement): Any? = when (element) {
    is JsonNull -> null
    is JsonObject -> element.mapValues { valueOfJson(it.value) }
    is JsonArray -> element.map(::valueOfJson)
    is JsonPrimitive -> if (element.isString) element.content else element.booleanOrNull ?: element.longOrNull ?: element.doubleOrNull
}

private fun jsonOfValue(value: Any?): JsonElement = when (value) {
    null -> JsonNull
    is String -> JsonPrimitive(value)
    is Boolean -> JsonPrimitive(value)
    is Number -> JsonPrimitive(value)
    is Map<*, *> -> JsonObject(value.entries.associate { it.key.toString() to jsonOfValue(it.value) })
    is List<*> -> JsonArray(value.map(::jsonOfValue))
    else -> JsonPrimitive(value.toString())
}
