package ai.factoredui.application

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class SystemFileFormatException(message: String) : IllegalArgumentException(message)

data class SystemSetting(val name: String, val value: JsonElement, val reason: String?)

data class SystemEntry(val name: String, val solution: String?, val settings: List<SystemSetting>)

data class SystemFile(val id: String, val title: String, val systems: List<SystemEntry>)

private fun textOf(element: JsonElement?): String? = (element as? JsonPrimitive)?.takeIf { it.isString }?.content

private fun entryOf(name: String, body: JsonObject): SystemEntry {
    val settings = body["settings"] as? JsonObject ?: JsonObject(emptyMap())
    val reasons = body["reasons"] as? JsonObject ?: JsonObject(emptyMap())
    return SystemEntry(name, textOf(body["solution"]), settings.map { (key, value) -> SystemSetting(key, value, textOf(reasons[key])) })
}

/** Reads a saved system file: its systems keep the order the file gives them. */
fun readSystemFile(text: String): SystemFile {
    val root = runCatching { Json.parseToJsonElement(text) }.getOrNull() as? JsonObject
        ?: throw SystemFileFormatException("the system file is not a JSON object")
    val id = textOf(root["id"]) ?: throw SystemFileFormatException("the system file has no id")
    val systems = (root["systems"] as? JsonObject).orEmpty()
    return SystemFile(
        id = id,
        title = textOf(root["title"]) ?: id,
        systems = systems.map { (name, body) -> entryOf(name, body as? JsonObject ?: throw SystemFileFormatException("system '$name' is not an object")) },
    )
}
