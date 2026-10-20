package ai.factoredui.application

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.nio.file.Path

class LoadFormatException(message: String) : IllegalArgumentException(message)

class SectionRefusedException(message: String, val setting: String?) : RuntimeException(message)

class Loaded(private val sections: JsonObject) {
    fun isSkipped(name: String): Boolean = sectionOf(name)["ok"] is JsonNull

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

fun loadSystem(launcher: Path, systemFile: Path, overlayId: String?): String =
    runInRuntime(launcher, *listOfNotNull("load", systemFile.toAbsolutePath().toString(), overlayId?.let { "--overlay" }, overlayId).toTypedArray())
