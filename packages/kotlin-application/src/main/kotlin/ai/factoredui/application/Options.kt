package ai.factoredui.application

import java.nio.file.Path
import java.nio.file.Paths

class UsageException(message: String) : IllegalArgumentException(message)

data class Options(val system: Path, val runtime: Path, val bundle: Path, val bind: String, val port: Int, val overlay: String? = null, val rank: String? = null)

private val FLAGS = setOf("--system", "--runtime", "--bundle", "--bind", "--port", "--overlay", "--rank")

private fun flagValues(args: List<String>): Map<String, String> {
    val pairs = args.chunked(2)
    pairs.forEach { pair ->
        if (pair[0] !in FLAGS) throw UsageException("unknown option '${pair[0]}'; it reads ${FLAGS.sorted().joinToString()}")
        if (pair.size < 2) throw UsageException("${pair[0]} needs a value")
    }
    return pairs.associate { it[0] to it[1] }
}

fun parseOptions(args: List<String>): Options {
    val values = flagValues(args)
    fun required(flag: String) = values[flag] ?: throw UsageException("$flag is required")
    val port = required("--port").toIntOrNull()?.takeIf { it in 1..65535 } ?: throw UsageException("--port must be a number from 1 to 65535, not '${values["--port"]}'")
    return Options(Paths.get(required("--system")), Paths.get(required("--runtime")), Paths.get(required("--bundle")), required("--bind"), port, values["--overlay"], values["--rank"])
}
