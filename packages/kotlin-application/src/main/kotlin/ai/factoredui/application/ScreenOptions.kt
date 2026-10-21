package ai.factoredui.application

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

data class ScreenOptions(val season: Path, val screen: Path, val bundle: Path, val bind: String, val port: Int)

private val SCREEN_FLAGS = setOf("--season", "--screen", "--bundle", "--bind", "--port")

fun parseScreenOptions(args: List<String>): ScreenOptions {
    val values = args.chunked(2).associate { pair ->
        if (pair[0] !in SCREEN_FLAGS) throw UsageException("unknown option '${pair[0]}'; it reads ${SCREEN_FLAGS.sorted().joinToString()}")
        if (pair.size < 2) throw UsageException("${pair[0]} needs a value")
        pair[0] to pair[1]
    }
    fun required(flag: String) = values[flag] ?: throw UsageException("$flag is required")
    val port = required("--port").toIntOrNull()?.takeIf { it in 1..65535 } ?: throw UsageException("--port must be a number from 1 to 65535, not '${values["--port"]}'")
    return ScreenOptions(Paths.get(required("--season")), Paths.get(required("--screen")), Paths.get(required("--bundle")), required("--bind"), port)
}

/** The spec of the screen as the two files stand now, so an edit to either shows at the next load. */
fun buildScreenSpec(options: ScreenOptions): ByteArray {
    val screen = readChartScreen(Files.readString(options.screen))
    val season = Json.parseToJsonElement(Files.readString(options.season)) as? JsonObject ?: throw ChartScreenFormatException("the season is not a JSON object")
    return chartScreenSpec(screen, season).toString().toByteArray()
}
