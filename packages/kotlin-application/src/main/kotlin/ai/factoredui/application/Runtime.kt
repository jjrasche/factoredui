package ai.factoredui.application

import java.nio.file.Path
import java.util.concurrent.TimeUnit

class RuntimeFailedException(message: String) : RuntimeException(message)

private const val RUNTIME_TIMEOUT_SECONDS = 60L

private fun runtimeRoot(launcher: Path): Path = launcher.toAbsolutePath().parent.parent

private fun runInRuntime(launcher: Path, vararg arguments: String): String {
    val process = ProcessBuilder(listOf("sh", launcher.toAbsolutePath().toString()) + arguments)
        .directory(runtimeRoot(launcher).toFile())
        .redirectErrorStream(false)
        .start()
    val answer = process.inputStream.bufferedReader().readText()
    if (!process.waitFor(RUNTIME_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
        process.destroyForcibly()
        throw RuntimeFailedException("the runtime did not answer within $RUNTIME_TIMEOUT_SECONDS seconds")
    }
    return answer.ifBlank { throw RuntimeFailedException("the runtime answered nothing: " + process.errorStream.bufferedReader().readText().trim()) }
}

/** The runtime's answer to `expand` on a system file, as the text it printed. */
fun expandSystem(launcher: Path, systemFile: Path): String = runInRuntime(launcher, "expand", systemFile.toAbsolutePath().toString())

fun overlaySystem(launcher: Path, systemFile: Path, overlayId: String): String =
    runInRuntime(launcher, "overlay", systemFile.toAbsolutePath().toString(), "--overlay", overlayId)

fun rankSystem(launcher: Path, systemFile: Path, systemId: String): String =
    runInRuntime(launcher, "rank", systemFile.toAbsolutePath().toString(), "--system", systemId)

fun solveSystem(launcher: Path, systemFile: Path): String = runInRuntime(launcher, "solve", systemFile.toAbsolutePath().toString())

/** The short commit the runtime's checkout is at, or "unknown" when it is not a checkout. */
fun runtimePin(launcher: Path): String {
    val process = ProcessBuilder("git", "-C", runtimeRoot(launcher).toString(), "rev-parse", "--short", "HEAD").redirectErrorStream(true).start()
    val text = process.inputStream.bufferedReader().readText().trim()
    return if (process.waitFor() == 0 && text.isNotEmpty()) text else "unknown"
}
