package ai.factoredui.application

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.nio.file.Files
import java.nio.file.Path

private const val SCREEN_SUFFIX = ".screen.json"
private const val OVERVIEW = "overview"

class WorldView(val id: String, val label: String, val screen: Path?)

data class WorldLabel(val id: String, val short: String, val title: String?)

private const val WORLD_SUFFIX = "-world"

private fun readableId(id: String): String =
    id.removeSuffix(WORLD_SUFFIX).replace('-', ' ').replace('_', ' ').trim().replaceFirstChar { it.uppercase() }

private fun textField(root: JsonObject?, key: String): String? =
    (root?.get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim()?.takeIf { it.isNotEmpty() }

fun labelOf(world: EngineWorld): WorldLabel {
    val root = runCatching { Json.parseToJsonElement(Files.readString(world.path)) as? JsonObject }.getOrNull()
    return WorldLabel(world.id, textField(root, "short_title") ?: readableId(world.id), textField(root, "title"))
}

private fun viewLabelOf(id: String): String = humanize(id).replaceFirstChar { it.uppercase() }

private fun screensBeside(world: EngineWorld): List<WorldView> {
    val directory = world.path.toAbsolutePath().parent?.resolve("views") ?: return emptyList()
    if (!Files.isDirectory(directory)) return emptyList()
    val files = Files.list(directory).use { listing -> listing.filter { it.fileName.toString().endsWith(SCREEN_SUFFIX) }.sorted().toList() }
    return files.map { file ->
        val id = file.fileName.toString().removeSuffix(SCREEN_SUFFIX)
        WorldView(id, viewLabelOf(id), file)
    }
}

private fun readsAsSystem(world: EngineWorld): Boolean = runCatching { readSystemFile(Files.readString(world.path)) }.isSuccess

fun viewsOf(world: EngineWorld): List<WorldView> = screensBeside(world) + listOfNotNull(if (readsAsSystem(world)) WorldView(OVERVIEW, viewLabelOf(OVERVIEW), null) else null)
