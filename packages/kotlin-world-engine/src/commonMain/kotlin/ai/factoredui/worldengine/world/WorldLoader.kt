package ai.factoredui.worldengine.world

import ai.factoredui.worldengine.schema.WORLD_SCHEMA_JSON
import ai.factoredui.worldengine.schema.schemaErrors
import ai.factoredui.worldengine.text.pythonListRepr
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

class WorldLoadException(override val message: String) : Exception(message)

object WorldLoader {
    val worldSchema: JsonElement by lazy { Json.parseToJsonElement(WORLD_SCHEMA_JSON) }

    fun load(path: String, library: WorldLibrary): World {
        val text = library.readText(path) ?: throw WorldLoadException("world file $path does not exist")
        return loadDocument(path, Json.parseToJsonElement(text), library)
    }

    fun loadFromJson(json: String, path: String = "world.world.json", library: WorldLibrary = MapWorldLibrary(emptyMap())): World =
        loadDocument(path, Json.parseToJsonElement(json), library)

    private fun loadDocument(path: String, document: JsonElement, library: WorldLibrary): World {
        val problems = schemaErrors(document, worldSchema)
        if (problems.isNotEmpty()) throw WorldLoadException("${fileNameOf(path)} does not match world.schema.json: ${pythonListRepr(problems.take(3))}")
        val world = World.fromDocument(path, document as JsonObject, library)
        val findings = diagnose(world)
        if (findings.isNotEmpty()) throw WorldLoadException(findings.joinToString("; ") { "${it.where}: ${it.kind}: ${it.message}" })
        return world
    }
}
