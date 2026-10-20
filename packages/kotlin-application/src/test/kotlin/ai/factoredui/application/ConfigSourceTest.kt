package ai.factoredui.application

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

private fun run(directory: Path, vararg arguments: String): String {
    val process = ProcessBuilder(listOf("git", "-C", directory.toString(), "-c", "user.email=a@b.c", "-c", "user.name=n") + arguments).redirectErrorStream(true).start()
    return process.inputStream.bufferedReader().readText().trim().also { process.waitFor() }
}

class ConfigSourceTest {
    @Test
    fun the_config_is_stamped_with_the_commit_that_last_changed_it_not_the_checkouts_head() {
        val directory = Files.createTempDirectory("world")
        run(directory, "init", "-q")
        Files.writeString(directory.resolve("world.json"), "{}")
        run(directory, "add", "world.json")
        run(directory, "commit", "-q", "-m", "world")
        val worldCommit = run(directory, "rev-parse", "--short", "HEAD")
        Files.writeString(directory.resolve("notes.txt"), "other")
        run(directory, "add", "notes.txt")
        run(directory, "commit", "-q", "-m", "notes")
        assertEquals(worldCommit, configSource(directory.resolve("world.json")))
    }
}
