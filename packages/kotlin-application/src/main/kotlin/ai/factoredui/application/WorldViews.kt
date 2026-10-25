package ai.factoredui.application

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.nio.file.Files
import java.nio.file.Path

private const val SCREEN_SUFFIX = ".screen.json"
private const val SITE_SUFFIX = ".site.json"
private const val FIELD_SUFFIX = ".field.json"
private const val CARDS_SUFFIX = ".cards.json"
private const val HEAT_SUFFIX = ".heat.json"
private const val BLUEPRINT_SUFFIX = "-blueprint"
private const val OVERVIEW = "overview"

class WorldView(val id: String, val label: String, val screen: Path?, val site: Path? = null, val field: Path? = null, val cards: Path? = null, val blueprint: Boolean = false, val heat: Path? = null)

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

private fun isSiteOf(world: EngineWorld, file: Path): Boolean =
    runCatching { readSiteScreen(Files.readString(file)).world == world.id }.getOrDefault(false)

private fun sitesBeside(world: EngineWorld): List<WorldView> {
    val directory = world.path.toAbsolutePath().parent?.resolve("views") ?: return emptyList()
    if (!Files.isDirectory(directory)) return emptyList()
    val files = Files.list(directory).use { listing -> listing.filter { it.fileName.toString().endsWith(SITE_SUFFIX) && isSiteOf(world, it) }.sorted().toList() }
    return files.map { file ->
        val id = file.fileName.toString().removeSuffix(SITE_SUFFIX)
        WorldView(id, viewLabelOf(id), null, file)
    }
}

private fun isFieldOf(world: EngineWorld, file: Path): Boolean =
    runCatching { readFieldScreen(Files.readString(file)).world == world.id }.getOrDefault(false)

private fun fieldsBeside(world: EngineWorld): List<WorldView> {
    val directory = world.path.toAbsolutePath().parent?.resolve("views") ?: return emptyList()
    if (!Files.isDirectory(directory)) return emptyList()
    val files = Files.list(directory).use { listing -> listing.filter { it.fileName.toString().endsWith(FIELD_SUFFIX) && isFieldOf(world, it) }.sorted().toList() }
    return files.map { file ->
        val id = file.fileName.toString().removeSuffix(FIELD_SUFFIX)
        WorldView(id, viewLabelOf(id), null, null, file)
    }
}

private fun isCardsOf(world: EngineWorld, file: Path): Boolean =
    runCatching { readCardsScreen(Files.readString(file)).world == world.id }.getOrDefault(false)

private fun cardsBeside(world: EngineWorld): List<WorldView> {
    val directory = world.path.toAbsolutePath().parent?.resolve("views") ?: return emptyList()
    if (!Files.isDirectory(directory)) return emptyList()
    val files = Files.list(directory).use { listing -> listing.filter { it.fileName.toString().endsWith(CARDS_SUFFIX) && isCardsOf(world, it) }.sorted().toList() }
    return files.flatMap { file ->
        val id = file.fileName.toString().removeSuffix(CARDS_SUFFIX)
        listOf(WorldView(id, viewLabelOf(id), null, null, null, file, false), WorldView(id + BLUEPRINT_SUFFIX, "Blueprint", null, null, null, file, true))
    }
}

private fun isHeatOf(world: EngineWorld, file: Path): Boolean =
    runCatching { readHeatScreen(Files.readString(file)).world == world.id }.getOrDefault(false)

private fun heatBeside(world: EngineWorld): List<WorldView> {
    val directory = world.path.toAbsolutePath().parent?.resolve("views") ?: return emptyList()
    if (!Files.isDirectory(directory)) return emptyList()
    val files = Files.list(directory).use { listing -> listing.filter { it.fileName.toString().endsWith(HEAT_SUFFIX) && isHeatOf(world, it) }.sorted().toList() }
    return files.map { file ->
        val id = file.fileName.toString().removeSuffix(HEAT_SUFFIX)
        WorldView(id, viewLabelOf(id), null, null, null, null, false, file)
    }
}

private fun readsAsSystem(world: EngineWorld): Boolean = runCatching { readSystemFile(Files.readString(world.path)) }.isSuccess

fun viewsOf(world: EngineWorld): List<WorldView> = heatBeside(world) + screensBeside(world) + sitesBeside(world) + fieldsBeside(world) + cardsBeside(world) + listOfNotNull(if (readsAsSystem(world)) WorldView(OVERVIEW, viewLabelOf(OVERVIEW), null) else null)
