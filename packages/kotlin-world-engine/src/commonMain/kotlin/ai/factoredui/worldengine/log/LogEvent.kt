package ai.factoredui.worldengine.log

import ai.factoredui.worldengine.events.AppliedEvent
import ai.factoredui.worldengine.events.Refusal
import ai.factoredui.worldengine.json.asTextOrNull
import ai.factoredui.worldengine.json.required
import ai.factoredui.worldengine.json.requiredObject
import ai.factoredui.worldengine.json.requiredText
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

data class LogEvent(
    val id: String,
    val parent: String?,
    val world: String,
    val branch: String,
    val actor: String,
    val action: String,
    val parameters: JsonObject,
    val timestamp: String,
    val touches: List<String> = emptyList(),
    val removed: JsonObject? = null,
) {
    fun asApplied(): AppliedEvent = AppliedEvent(id, actor, action, parameters)

    fun toJson(): JsonObject {
        val fields = linkedMapOf(
            "id" to JsonPrimitive(id),
            "parent" to (parent?.let { JsonPrimitive(it) } ?: JsonNull),
            "world" to JsonPrimitive(world),
            "branch" to JsonPrimitive(branch),
            "actor" to JsonPrimitive(actor),
            "action" to JsonPrimitive(action),
            "parameters" to parameters,
            "timestamp" to JsonPrimitive(timestamp),
            "touches" to JsonArray(touches.map { JsonPrimitive(it) }),
        )
        if (removed != null) fields["removed"] = removed
        return JsonObject(fields)
    }

    companion object {
        fun fromJson(raw: JsonObject): LogEvent = LogEvent(
            id = raw.requiredText("id"),
            parent = raw.required("parent").asTextOrNull(),
            world = raw.requiredText("world"),
            branch = raw.requiredText("branch"),
            actor = raw.requiredText("actor"),
            action = raw.requiredText("action"),
            parameters = raw.requiredObject("parameters"),
            timestamp = raw.requiredText("timestamp"),
        )
    }
}

sealed interface LogResult {
    data class Committed(val event: LogEvent) : LogResult
    data class Refused(val refusal: Refusal) : LogResult
}
