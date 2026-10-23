package ai.factoredui.application

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

private const val ENGINE_PIN_FILE = "engine.pin"

private fun hexOf(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }

private fun sha256Of(text: String) = hexOf(MessageDigest.getInstance("SHA-256").digest(text.toByteArray()))

private class FileDigest(val modifiedMillis: Long, val size: Long, val sha256: String)

private val fileDigests = ConcurrentHashMap<Path, FileDigest>()

private fun sha256OfFile(file: Path): String {
    if (!Files.isRegularFile(file)) return "absent"
    val modified = Files.getLastModifiedTime(file).toMillis()
    val size = Files.size(file)
    return fileDigests.compute(file.toAbsolutePath()) { _, kept ->
        kept?.takeIf { it.modifiedMillis == modified && it.size == size } ?: FileDigest(modified, size, hexOf(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file))))
    }!!.sha256
}

private fun usedJarsOf(worldFile: Path): List<Path> {
    val uses = (Json.parseToJsonElement(Files.readString(worldFile)) as? JsonObject)?.get("uses") as? kotlinx.serialization.json.JsonArray ?: return emptyList()
    return uses.mapNotNull { ((it as? JsonObject)?.get("file") as? JsonPrimitive)?.content }.map { worldFile.toAbsolutePath().parent.resolve(it) }
}

private fun enginePinOf(worldFile: Path): String =
    worldFile.toAbsolutePath().parent.resolve(ENGINE_PIN_FILE).takeIf { Files.isRegularFile(it) }?.let { Files.readString(it).trim() } ?: "no engine pin"

/** What one served page depends on: the world's id, the view asked for, the world file's bytes, each pinned domain file's bytes and the engine pin beside the world. A change in any is a different key. */
fun worldSpecKey(worldId: String, viewId: String?, worldFile: Path): String =
    sha256Of(listOf(worldId, viewId.orEmpty(), sha256OfFile(worldFile), usedJarsOf(worldFile).joinToString(",") { sha256OfFile(it) }, enginePinOf(worldFile)).joinToString("|"))

internal class WorldSpec(val worldId: String, val key: String, val worldFile: Path, val body: ByteArray)

internal class KeptWorldSpec(val spec: WorldSpec, val source: String)

/** The last good page of each request, in memory and, when a directory is given, on disk so a restart serves it before the engine has answered. */
internal class WorldSpecStore(private val directory: Path?) {
    private val inMemory = ConcurrentHashMap<String, WorldSpec>()

    private fun fileOf(request: String) = directory?.resolve(sha256Of(request) + ".json")

    fun lastGood(request: String): KeptWorldSpec? {
        inMemory[request]?.let { return KeptWorldSpec(it, "memory") }
        val read = runCatching { fileOf(request)?.takeIf { Files.isRegularFile(it) }?.let { readSpec(it) } }.getOrNull() ?: return null
        inMemory[request] = read
        return KeptWorldSpec(read, "disk")
    }

    fun keep(request: String, spec: WorldSpec) {
        inMemory[request] = spec
        runCatching { fileOf(request)?.let { writeSpec(it, spec) } }
    }

    private fun readSpec(file: Path): WorldSpec {
        val kept = Json.parseToJsonElement(Files.readString(file)) as JsonObject
        fun text(name: String) = (kept.getValue(name) as JsonPrimitive).content
        return WorldSpec(text("world_id"), text("key"), Path.of(text("world_file")), text("body").toByteArray())
    }

    private fun writeSpec(file: Path, spec: WorldSpec) {
        val text = JsonObject(mapOf("world_id" to JsonPrimitive(spec.worldId), "key" to JsonPrimitive(spec.key), "world_file" to JsonPrimitive(spec.worldFile.toString()), "body" to JsonPrimitive(spec.body.toString(Charsets.UTF_8)))).toString()
        Files.createDirectories(file.parent)
        val partial = Files.createTempFile(file.parent, "world-spec", ".part")
        Files.writeString(partial, text)
        Files.move(partial, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }
}
