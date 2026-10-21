package ai.factoredui.application

import java.nio.file.Files
import java.nio.file.Path
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals

class RuntimeTest {
    private fun slowLauncher(log: Path): Path {
        val launcher = Files.createDirectories(Files.createTempDirectory("runtime").resolve("bin")).resolve("we")
        Files.writeString(launcher, "echo start >> '$log'\nsleep 1\necho end >> '$log'\necho '{}'\n")
        return launcher
    }

    @Test
    fun two_runs_asked_at_once_are_run_one_after_the_other() {
        val log = Files.createTempFile("engine", ".log")
        val launcher = slowLauncher(log)
        val runs = List(2) { thread { runInRuntime(launcher, "describe") } }
        runs.forEach { it.join() }
        assertEquals(listOf("start", "end", "start", "end"), Files.readAllLines(log))
    }
}
