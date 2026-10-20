package ai.factoredui.application

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private const val CACHE_NAME = "factoredui-application"
private const val KEPT_SPECS = 5

fun defaultCacheDirectory(): Path {
    val base = System.getenv("XDG_CACHE_HOME")?.takeIf { it.isNotEmpty() } ?: (System.getProperty("user.home") + "/.cache")
    return Path.of(base, CACHE_NAME)
}

/** Page specs kept on disk, one file per key, so a restart serves the last build without running the engine. */
class SpecCache(private val directory: Path) {
    private fun fileOf(key: String) = directory.resolve("$key.json")

    fun read(key: String): ByteArray? = fileOf(key).takeIf { Files.isRegularFile(it) }?.let { Files.readAllBytes(it) }

    fun write(key: String, body: ByteArray) {
        Files.createDirectories(directory)
        val partial = Files.createTempFile(directory, "spec", ".part")
        Files.write(partial, body)
        Files.move(partial, fileOf(key), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        prune()
    }

    private fun prune() {
        Files.list(directory).use { files -> files.filter { it.fileName.toString().endsWith(".json") }.sorted(compareByDescending { Files.getLastModifiedTime(it) }).skip(KEPT_SPECS.toLong()).forEach { Files.deleteIfExists(it) } }
    }
}

private fun stampOf(path: Path): String =
    if (Files.exists(path)) "${Files.getLastModifiedTime(path).toMillis()}:${Files.size(path)}" else "absent"

private fun usedFilesOf(system: Path): List<Path> {
    val uses = ((Json.parseToJsonElement(Files.readString(system)) as? JsonObject)?.get("uses") as? JsonArray).orEmpty()
    return uses.mapNotNull { ((it as? JsonObject)?.get("file") as? JsonPrimitive)?.content }.map { system.toAbsolutePath().parent.resolve(it) }
}

private fun newestSourceStamp(launcher: Path): String {
    val sources = launcher.toAbsolutePath().parent.resolve("src")
    if (!Files.isDirectory(sources)) return "no sources"
    return Files.walk(sources).use { files -> files.filter { Files.isRegularFile(it) }.mapToLong { Files.getLastModifiedTime(it).toMillis() }.max().orElse(0L).toString() }
}

private fun applicationStamp(): String =
    runCatching { stampOf(Path.of(SpecCache::class.java.protectionDomain.codeSource.location.toURI())) }.getOrDefault("unknown")

/** What the page depends on: the world file, the domain files it uses, the engine's checkout, build and sources, this application, and the options. A change in any is a different key. */
fun specCacheKey(options: Options): String {
    val engineBuild = options.runtime.toAbsolutePath().parent.resolve("build/cli.classpath")
    val parts = listOf(
        stampOf(options.system), usedFilesOf(options.system).joinToString(",") { stampOf(it) }, runtimePin(options.runtime), stampOf(engineBuild),
        newestSourceStamp(options.runtime), applicationStamp(), options.overlay.orEmpty(), options.rank.orEmpty(), options.sampleArguments.orEmpty().joinToString(" "),
    )
    return MessageDigest.getInstance("SHA-256").digest(parts.joinToString("|").toByteArray()).joinToString("") { "%02x".format(it) }
}
