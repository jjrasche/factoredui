package ai.factoredui.worldbuilder

import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

private const val NAMES_KEY = "plan_names"
private const val LOG_KEY = "log"
private const val EVENTS_KEY = "events"
private const val PLAN_SUFFIX = ".plan.json"
private val PLAN_FOLDER = listOf("Documents", "world-builder-plans")
private val STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
private val PRETTY = Json { prettyPrint = true }

internal class LoadedPlan(val log: JsonElement, val names: Map<String, String>)

internal fun encodePlanFile(log: JsonObject, names: Map<String, String>): String =
    PRETTY.encodeToString(JsonObject.serializer(), JsonObject(mapOf(NAMES_KEY to JsonObject(names.mapValues { JsonPrimitive(it.value) }), LOG_KEY to log)))

internal fun decodePlanFile(text: String): LoadedPlan {
    val root = Json.parseToJsonElement(text).jsonObject
    if (EVENTS_KEY in root) return LoadedPlan(root, emptyMap())
    val names = (root[NAMES_KEY] as? JsonObject).orEmpty().mapNotNull { (branch, name) -> (name as? JsonPrimitive)?.content?.let { branch to it } }.toMap()
    return LoadedPlan(root.getValue(LOG_KEY), names)
}

internal fun planFileName(worldId: String, now: LocalDateTime = LocalDateTime.now()): String = "$worldId-${now.format(STAMP)}$PLAN_SUFFIX"

internal fun writePlanToDocuments(fileName: String, text: String): String {
    val folder = PLAN_FOLDER.fold(File(System.getProperty("user.home"))) { parent, child -> File(parent, child) }
    folder.mkdirs()
    val file = File(folder, fileName)
    file.writeText(text)
    return file.absolutePath
}
