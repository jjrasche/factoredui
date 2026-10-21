package ai.factoredui.application

import java.nio.file.Files
import java.nio.file.Path

private const val SCREEN_SUFFIX = ".screen.json"
private const val OVERVIEW = "overview"

class WorldView(val id: String, val label: String, val screen: Path?)

private fun labelOf(id: String): String = humanize(id).replaceFirstChar { it.uppercase() }

private fun screensBeside(world: EngineWorld): List<WorldView> {
    val directory = world.path.toAbsolutePath().parent?.resolve("views") ?: return emptyList()
    if (!Files.isDirectory(directory)) return emptyList()
    val files = Files.list(directory).use { listing -> listing.filter { it.fileName.toString().endsWith(SCREEN_SUFFIX) }.sorted().toList() }
    return files.map { file ->
        val id = file.fileName.toString().removeSuffix(SCREEN_SUFFIX)
        WorldView(id, labelOf(id), file)
    }
}

private fun readsAsSystem(world: EngineWorld): Boolean = runCatching { readSystemFile(Files.readString(world.path)) }.isSuccess

fun viewsOf(world: EngineWorld): List<WorldView> = screensBeside(world) + listOfNotNull(if (readsAsSystem(world)) WorldView(OVERVIEW, labelOf(OVERVIEW), null) else null)
