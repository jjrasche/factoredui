package ai.factoredui.compose.render

import ai.factoredui.compose.scene.readReports
import ai.factoredui.compose.scene.reportsState
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.io.File

enum class StateFileKind(val flagName: String) { TEXT("text"), JSON("json"), REPORTS("reports") }

data class StateFile(val path: String, val kind: StateFileKind, val file: String)

private val stateDecoder = Json { ignoreUnknownKeys = true }

fun parseStateFile(flagValue: String): StateFile? {
    val path = flagValue.substringBefore('=', missingDelimiterValue = "")
    val source = flagValue.substringAfter('=', missingDelimiterValue = "")
    val kindName = source.substringBefore(':', missingDelimiterValue = "")
    val file = source.substringAfter(':', missingDelimiterValue = "")
    val kind = StateFileKind.entries.firstOrNull { it.flagName == kindName }
    if (path.isEmpty() || file.isEmpty() || kind == null) return null
    return StateFile(path, kind, file)
}

fun windowState(seeds: Map<String, Any?>, stateFiles: List<StateFile>): Map<String, Any?> =
    stateFiles.fold(seeds) { state, stateFile -> mergeAtPath(state, stateFile.path.split("."), stateValueOf(stateFile)) }

private fun stateValueOf(stateFile: StateFile): Any? {
    val text = File(stateFile.file).readText()
    return when (stateFile.kind) {
        StateFileKind.TEXT -> text
        StateFileKind.JSON -> jsonObjectToMap(stateDecoder.parseToJsonElement(text) as? JsonObject ?: error("${stateFile.file} must hold a JSON object"))
        StateFileKind.REPORTS -> reportsState(readReports(text))
    }
}

// Two maps meeting at one key merge, so a reports file and a seeds file can share a parent.
private fun mergeAtPath(current: Map<String, Any?>, segments: List<String>, value: Any?): Map<String, Any?> {
    val head = segments.first()
    val existing = current[head]
    val placed = when {
        segments.size > 1 -> mergeAtPath(existing as? Map<String, Any?> ?: emptyMap(), segments.drop(1), value)
        existing is Map<*, *> && value is Map<*, *> -> (existing as Map<String, Any?>) + (value as Map<String, Any?>)
        else -> value
    }
    return current + (head to placed)
}
