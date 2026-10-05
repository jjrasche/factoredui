package ai.factoredui.worldengine

import java.nio.file.Path
import java.nio.file.Paths

private const val DEFAULT_DESIGN_DIR = "C:/Users/rasche_j/Documents/workspace/van-life/.git-worktrees/design-world-engine/design/world-engine"

private fun configured(name: String): String? = System.getProperty(name)?.takeIf { it.isNotBlank() } ?: System.getenv(name)?.takeIf { it.isNotBlank() }

object ReferenceLocations {
    val designDir: Path get() = Paths.get(configured("WORLD_ENGINE_DESIGN_DIR") ?: DEFAULT_DESIGN_DIR)

    val conformanceCasesDir: Path get() = configured("WORLD_ENGINE_CONFORMANCE_DIR")?.let { Paths.get(it) } ?: designDir.resolve("conformance").resolve("cases")

    val conformanceDir: Path get() = conformanceCasesDir.parent

    val liveWorldsDir: Path get() = conformanceDir.parent.resolve("worlds")

    val frozenWorldsDir: Path get() = conformanceDir.resolve("worlds")

    val unitTable: Path get() = conformanceDir.resolve("units.json")
}
