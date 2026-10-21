package ai.factoredui.application

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.net.URI
import java.net.URLDecoder
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

data class WorldsOptions(val engine: URI, val bundle: Path, val bind: String, val port: Int)

private val WORLDS_FLAGS = setOf("--engine", "--bundle", "--bind", "--port")

fun parseWorldsOptions(args: List<String>): WorldsOptions {
    val values = args.chunked(2).associate { pair ->
        if (pair[0] !in WORLDS_FLAGS) throw UsageException("unknown option '${pair[0]}'; it reads ${WORLDS_FLAGS.sorted().joinToString()}")
        if (pair.size < 2) throw UsageException("${pair[0]} needs a value")
        pair[0] to pair[1]
    }
    fun required(flag: String) = values[flag] ?: throw UsageException("$flag is required")
    val port = required("--port").toIntOrNull()?.takeIf { it in 1..65535 } ?: throw UsageException("--port must be a number from 1 to 65535, not '${values["--port"]}'")
    return WorldsOptions(URI(required("--engine")), Paths.get(required("--bundle")), required("--bind"), port)
}

private fun queryOf(exchange: HttpExchange): Map<String, String> =
    (exchange.requestURI.rawQuery ?: "").split('&').filter { it.isNotEmpty() }.associate { pair ->
        URLDecoder.decode(pair.substringBefore('='), Charsets.UTF_8) to URLDecoder.decode(pair.substringAfter('=', ""), Charsets.UTF_8)
    }

private fun seasonOf(engine: EngineRuntime, world: EngineWorld): JsonObject {
    val text = simulateSystem(engine, world.path)
    return Json.parseToJsonElement(text.substring(text.indexOf('{').coerceAtLeast(0))) as? JsonObject ?: throw ChartScreenFormatException("the simulation is not a JSON object")
}

private fun screenSpec(engine: EngineRuntime, world: EngineWorld, screenFile: Path): JsonObject =
    chartScreenSpec(readChartScreen(Files.readString(screenFile)), seasonOf(engine, world))

private fun overviewSpec(engine: EngineRuntime, world: EngineWorld): JsonObject =
    Json.parseToJsonElement(buildLensSpecBody(PageSource(world.path, engine, null, null, null, null), Timings()).toString(Charsets.UTF_8)) as JsonObject

private fun specOfView(engine: EngineRuntime, world: EngineWorld, view: WorldView): JsonObject =
    view.screen?.let { screenSpec(engine, world, it) } ?: overviewSpec(engine, world)

private fun drawFailure(problem: Throwable, subject: String): String = "Cannot draw $subject: ${problem.message ?: problem::class.simpleName}"

private fun pageOf(engine: ServedEngine, worldId: String?, viewId: String?): JsonObject {
    val worlds = engine.worlds()
    if (worlds.isEmpty()) return noticePage(worldBar(worlds, null, emptyList(), null), "The engine serves no worlds.")
    val chosen = if (worldId == null) worlds.first() else worlds.firstOrNull { it.id == worldId }
    if (chosen == null) return noticePage(worldBar(worlds, null, emptyList(), null), "The engine serves no world named '$worldId'.")
    val views = viewsOf(chosen)
    val view = if (viewId == null) views.firstOrNull() else views.firstOrNull { it.id == viewId }
    val bar = worldBar(worlds, chosen, views, view)
    if (view == null) return noticePage(bar, if (viewId == null) "${chosen.id} has no view to draw." else "${chosen.id} has no view named '$viewId'.")
    return runCatching { withWorldBar(specOfView(engine, chosen, view), bar) }.getOrElse { noticePage(bar, drawFailure(it, "${view.label} of ${chosen.id}")) }
}

private fun serveWorldSpec(exchange: HttpExchange, engine: ServedEngine) {
    val asked = queryOf(exchange)
    runCatching { pageOf(engine, asked["world"], asked["view"]) }.fold(
        onSuccess = { respond(exchange, 200, CONTENT_TYPES.getValue("json"), it.toString().toByteArray()) },
        onFailure = { respondText(exchange, 502, "cannot build the page: ${it.message}") },
    )
}

fun startWorldsServer(options: WorldsOptions): HttpServer {
    val engine = ServedEngine(options.engine)
    return startServing(options.bind, options.port, options.bundle) { exchange -> serveWorldSpec(exchange, engine) }
}
