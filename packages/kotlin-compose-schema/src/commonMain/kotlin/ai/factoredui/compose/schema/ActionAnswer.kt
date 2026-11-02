package ai.factoredui.compose.schema

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

data class ActionAnswer(val writes: Map<String, Any?>, val error: String?)

private const val SHOWN_BODY_CHARS = 80
private const val SHOWN_REASON_CHARS = 200

private fun plainOf(element: JsonElement): Any? = when (element) {
    is JsonNull -> null
    is JsonPrimitive -> if (element.isString) element.content else element.content.toBooleanStrictOrNull() ?: element.content.toDouble()
    is JsonArray -> element.map(::plainOf)
    is JsonObject -> element.mapValues { (_, inner) -> plainOf(inner) }
}

private fun failure(error: String) = ActionAnswer(emptyMap(), error)

private fun reasonOf(body: String): String {
    val given = (runCatching { Json.parseToJsonElement(body) }.getOrNull() as? JsonObject)?.get("reason") as? JsonPrimitive
    return (given?.content ?: body.trim()).take(SHOWN_REASON_CHARS).ifEmpty { "no reason given" }
}

fun readActionAnswer(action: String, status: Int, body: String): ActionAnswer {
    if (status !in 200..299) return failure("action '$action' was refused with status $status: ${reasonOf(body)}")
    if (body.isBlank()) return ActionAnswer(emptyMap(), null)
    val sent = runCatching { Json.parseToJsonElement(body) }.getOrNull() as? JsonObject
        ?: return failure("action '$action' answered something that is not a JSON object: '${body.trim().take(SHOWN_BODY_CHARS)}'")
    val set = sent["set"] ?: return ActionAnswer(emptyMap(), null)
    val paths = set as? JsonObject ?: return failure("action '$action' answered a 'set' that is not an object of paths to values")
    if (paths.keys.any { it.isEmpty() }) return failure("action '$action' answered a 'set' with an empty path; nothing was written")
    return ActionAnswer(paths.mapValues { (_, value) -> plainOf(value) }, null)
}
