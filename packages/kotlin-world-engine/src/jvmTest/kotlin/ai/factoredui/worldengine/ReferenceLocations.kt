package ai.factoredui.worldengine

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.MessageDigest
import kotlin.io.path.name

private const val PIN_FILES = "PINNED_COMMIT, CASE_COUNT, MANIFEST.sha256, .gitattributes"
private val PIN_FILE_NAMES = setOf("PINNED_COMMIT", "CASE_COUNT", "MANIFEST.sha256", ".gitattributes")

object ReferenceLocations {
    val designDir: Path
        get() {
            val configured = System.getProperty("WORLD_ENGINE_DESIGN_DIR")?.takeIf { it.isNotBlank() }
                ?: error("WORLD_ENGINE_DESIGN_DIR is not set: the reference is vendored under packages/kotlin-world-engine/reference at a pinned commit and Gradle passes it; run these tests through Gradle")
            return Paths.get(configured)
        }

    val conformanceCasesDir: Path get() = designDir.resolve("conformance").resolve("cases")

    val conformanceDir: Path get() = designDir.resolve("conformance")

    val liveWorldsDir: Path get() = designDir.resolve("worlds")

    val frozenWorldsDir: Path get() = conformanceDir.resolve("worlds")

    val unitTable: Path get() = conformanceDir.resolve("units.json")

    val pinnedCommit: String get() = Files.readString(designDir.resolve("PINNED_COMMIT")).trim()

    val expectedCaseCount: Int get() = Files.readString(designDir.resolve("CASE_COUNT")).trim().toInt()

    fun findManifestProblems(): List<String> {
        val manifest = designDir.resolve("MANIFEST.sha256")
        check(Files.exists(manifest)) { "$manifest is missing: the reference is not pinned ($PIN_FILES)" }
        val listed = Files.readAllLines(manifest).filter { it.isNotBlank() }.associate { line -> line.split("  ", limit = 2).let { it[1] to it[0] } }
        val present = Files.walk(designDir).use { stream -> stream.filter { Files.isRegularFile(it) && it.name !in PIN_FILE_NAMES }.map { designDir.relativize(it).toString().replace('\\', '/') }.toList() }.toSet()
        val missing = (listed.keys - present).sorted().map { "$it is in the manifest but missing" }
        val unlisted = (present - listed.keys).sorted().map { "$it is not in the manifest" }
        val changed = listed.entries.sortedBy { it.key }.filter { it.key in present && sha256(designDir.resolve(it.key)) != it.value }.map { "${it.key} differs from the pinned commit" }
        return missing + unlisted + changed
    }

    private fun sha256(path: Path): String = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)).joinToString("") { "%02x".format(it) }
}
