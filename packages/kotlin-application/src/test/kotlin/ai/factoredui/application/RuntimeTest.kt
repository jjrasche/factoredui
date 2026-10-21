package ai.factoredui.application

import java.nio.file.Files
import java.nio.file.Path
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RuntimeTest {
    private fun slowLauncher(log: Path): Path {
        val launcher = Files.createDirectories(Files.createTempDirectory("runtime").resolve("bin")).resolve("we")
        Files.writeString(launcher, "echo start >> '$log'\nsleep 1\necho end >> '$log'\necho '{}'\n")
        return launcher
    }

    @Test
    fun a_run_waits_while_someone_else_holds_the_build_lock() {
        val lock = Files.createTempFile("build", ".lock")
        val kept = System.getProperty("factoredui.buildLock")
        System.setProperty("factoredui.buildLock", lock.toString())
        val holder = ProcessBuilder("flock", lock.toString(), "sleep", "2").start()
        try {
            Thread.sleep(300)
            val log = Files.createTempFile("engine", ".log")
            val started = System.nanoTime()
            runInRuntime(slowLauncher(log), "describe")
            assertTrue((System.nanoTime() - started) >= 1_500_000_000L, "the run did not wait for the lock")
        } finally {
            holder.destroy()
            System.setProperty("factoredui.buildLock", kept)
        }
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
