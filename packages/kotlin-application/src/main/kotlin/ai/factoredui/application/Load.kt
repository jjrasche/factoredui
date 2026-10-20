package ai.factoredui.application

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.nio.file.Path

class LoadFormatException(message: String) : IllegalArgumentException(message)

class SectionRefusedException(message: String, val setting: String?) : RuntimeException(message)

class Loaded(private val sections: JsonObject) {
    /** These sections, with each one this answer did not ask for taken from [other]. */
    fun merged(other: Loaded): Loaded =
        Loaded(JsonObject(sections.mapValues { (name, section) -> if (isAsked(section)) section else other.sections[name] ?: section }))

    private fun isAsked(section: kotlinx.serialization.json.JsonElement) = (section as? JsonObject)?.get("asked") == JsonPrimitive(true)

    fun isSkipped(name: String): Boolean = sectionOf(name)["ok"] is JsonNull

    fun objectOf(name: String): JsonObject = Json.parseToJsonElement(textOf(name)) as JsonObject

    fun textOf(name: String): String {
        val section = sectionOf(name)
        if (section["ok"] == JsonPrimitive(true)) return section.toString()
        val error = section["error"] as? JsonObject
        val message = (error?.get("message") as? JsonPrimitive)?.content ?: (section["skipped"] as? JsonPrimitive)?.content ?: "the $name section was not answered"
        val setting = (error?.get("setting") as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content
        throw SectionRefusedException(if (setting == null) message else "$message (setting: $setting)", setting)
    }

    private fun sectionOf(name: String): JsonObject =
        sections[name] as? JsonObject ?: throw LoadFormatException("the load answer has no $name section")
}

fun readLoaded(text: String): Loaded {
    val root = runCatching { Json.parseToJsonElement(text.substring(text.indexOf('{').coerceAtLeast(0))) }.getOrNull() as? JsonObject
        ?: throw LoadFormatException("the load answer is not a JSON object")
    return Loaded(root["sections"] as? JsonObject ?: throw LoadFormatException("the load answer has no sections"))
}

fun loadSystem(launcher: Path, systemFile: Path, overlayId: String?, include: List<String>? = null): String =
    runInRuntime(
        launcher,
        *listOfNotNull("load", systemFile.toAbsolutePath().toString(), overlayId?.let { "--overlay" }, overlayId, include?.let { "--include" }, include?.joinToString(",")).toTypedArray(),
    )
