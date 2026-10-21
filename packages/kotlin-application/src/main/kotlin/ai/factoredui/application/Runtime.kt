package ai.factoredui.application

import java.nio.file.Path
import java.util.concurrent.TimeUnit

class RuntimeFailedException(message: String) : RuntimeException(message)

internal const val RUNTIME_TIMEOUT_SECONDS = 60L
internal const val SAMPLE_TIMEOUT_SECONDS = 600L
internal const val SIMULATE_TIMEOUT_SECONDS = 600L

interface EngineRuntime {
    fun answer(arguments: List<String>, timeoutSeconds: Long = RUNTIME_TIMEOUT_SECONDS): String
}

private fun runtimeRoot(launcher: Path): Path = launcher.toAbsolutePath().parent.parent

class ProcessRuntime(private val launcher: Path) : EngineRuntime {
    override fun answer(arguments: List<String>, timeoutSeconds: Long): String {
        val process = ProcessBuilder(listOf("sh", launcher.toAbsolutePath().toString()) + arguments)
            .directory(runtimeRoot(launcher).toFile())
            .redirectErrorStream(false)
            .start()
        val answer = process.inputStream.bufferedReader().readText()
        if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            throw RuntimeFailedException("the runtime did not answer within $timeoutSeconds seconds")
        }
        return answer.ifBlank { throw RuntimeFailedException("the runtime answered nothing: " + process.errorStream.bufferedReader().readText().trim()) }
    }
}

internal fun runInRuntime(runtime: EngineRuntime, vararg arguments: String): String = runtime.answer(arguments.toList())

/** The runtime's answer to `expand` on a system file, as the text it printed. */
fun expandSystem(runtime: EngineRuntime, systemFile: Path): String = runInRuntime(runtime, "expand", systemFile.toAbsolutePath().toString())

fun overlaySystem(runtime: EngineRuntime, systemFile: Path, overlayId: String): String =
    runInRuntime(runtime, "overlay", systemFile.toAbsolutePath().toString(), "--overlay", overlayId)

fun rankSystem(runtime: EngineRuntime, systemFile: Path, systemId: String): String =
    runInRuntime(runtime, "rank", systemFile.toAbsolutePath().toString(), "--system", systemId)

fun describeSystem(runtime: EngineRuntime, systemFile: Path): String = runInRuntime(runtime, "describe", systemFile.toAbsolutePath().toString())

fun geometrySystem(runtime: EngineRuntime, systemFile: Path): String = runInRuntime(runtime, "geometry", systemFile.toAbsolutePath().toString())

fun sampleSystem(runtime: EngineRuntime, systemFile: Path, arguments: List<String>): String =
    runtime.answer(listOf("sample", systemFile.toAbsolutePath().toString()) + arguments, SAMPLE_TIMEOUT_SECONDS)

fun simulateSystem(runtime: EngineRuntime, systemFile: Path): String =
    runtime.answer(listOf("simulate", systemFile.toAbsolutePath().toString()), SIMULATE_TIMEOUT_SECONDS)

fun solveSystem(runtime: EngineRuntime, systemFile: Path): String = runInRuntime(runtime, "solve", systemFile.toAbsolutePath().toString())

/** The short commit the runtime's checkout is at, or "unknown" when it is not a checkout. */
fun runtimePin(launcher: Path): String {
    val process = ProcessBuilder("git", "-C", runtimeRoot(launcher).toString(), "rev-parse", "--short", "HEAD").redirectErrorStream(true).start()
    val text = process.inputStream.bufferedReader().readText().trim()
    return if (process.waitFor() == 0 && text.isNotEmpty()) text else "unknown"
}
