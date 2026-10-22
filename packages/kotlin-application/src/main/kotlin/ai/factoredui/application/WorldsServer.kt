package ai.factoredui.application

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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

private fun seasonOf(engine: EngineRuntime, world: EngineWorld, timings: Timings): JsonObject {
    val text = timings.timed("simulate") { simulateSystem(engine, world.path) }
    return Json.parseToJsonElement(text.substring(text.indexOf('{').coerceAtLeast(0))) as? JsonObject ?: throw ChartScreenFormatException("the simulation is not a JSON object")
}

private fun screenSpec(engine: EngineRuntime, world: EngineWorld, screenFile: Path, timings: Timings): JsonObject =
    timings.timed("spec") { chartScreenSpec(readChartScreen(Files.readString(screenFile)), seasonOf(engine, world, timings)) }

private fun overviewSpec(engine: EngineRuntime, world: EngineWorld, timings: Timings): JsonObject =
    Json.parseToJsonElement(buildLensSpecBody(PageSource(world.path, engine, null, null, null, null), timings).toString(Charsets.UTF_8)) as JsonObject

private fun specOfView(engine: EngineRuntime, world: EngineWorld, view: WorldView, timings: Timings): JsonObject =
    view.screen?.let { screenSpec(engine, world, it, timings) } ?: overviewSpec(engine, world, timings)

private fun drawFailure(problem: Throwable, subject: String): String = "Cannot draw $subject: ${problem.message ?: problem::class.simpleName}"

private fun pageOf(engine: ServedEngine, worldId: String?, viewId: String?, timings: Timings): JsonObject {
    val worlds = engine.worlds()
    val labels = worlds.map(::labelOf)
    if (worlds.isEmpty()) return noticePage(worldBar(labels, null, emptyList(), null), "The engine serves no worlds.")
    val chosen = if (worldId == null) worlds.first() else worlds.firstOrNull { it.id == worldId }
    if (chosen == null) return noticePage(worldBar(labels, null, emptyList(), null), "The engine serves no world named '$worldId'.")
    val views = viewsOf(chosen)
    val view = if (viewId == null) views.firstOrNull() else views.firstOrNull { it.id == viewId }
    val bar = worldBar(labels, chosen, views, view)
    val state = barState(chosen)
    if (view == null) return noticePage(bar, if (viewId == null) "${chosen.id} has no view to draw." else "${chosen.id} has no view named '$viewId'.", state)
    return runCatching { withWorldBar(specOfView(engine, chosen, view, timings), bar, state) }.getOrElse { noticePage(bar, drawFailure(it, "${view.label} of ${chosen.id}"), state) }
}

private fun serveWorldSpec(exchange: HttpExchange, engine: ServedEngine) {
    val asked = queryOf(exchange)
    val timings = Timings()
    val started = System.nanoTime()
    runCatching { pageOf(engine, asked["world"], asked["view"], timings) }.fold(
        onSuccess = {
            exchange.responseHeaders.add("Server-Timing", timings.header((System.nanoTime() - started) / 1_000_000))
            respond(exchange, 200, CONTENT_TYPES.getValue("json"), it.toString().toByteArray())
        },
        onFailure = { respondText(exchange, 502, "cannot build the page: ${it.message}") },
    )
}

private fun respondJson(exchange: HttpExchange, status: Int, body: JsonObject) = respond(exchange, status, CONTENT_TYPES.getValue("json"), body.toString().toByteArray())

private fun actionNameOf(exchange: HttpExchange): String? {
    val sent = runCatching { Json.parseToJsonElement(exchange.requestBody.readBytes().toString(Charsets.UTF_8)) as? JsonObject }.getOrNull()
    return (sent?.get("action") as? JsonPrimitive)?.takeIf { it.isString }?.content
}

private fun serveAction(exchange: HttpExchange) {
    if (exchange.requestMethod != "POST") {
        exchange.responseHeaders.add("Allow", "POST")
        return respondJson(exchange, 405, JsonObject(mapOf("reason" to JsonPrimitive("actions are sent with POST"))))
    }
    val name = actionNameOf(exchange)
        ?: return respondJson(exchange, 400, JsonObject(mapOf("reason" to JsonPrimitive("expected a JSON object with an 'action' name and optionally 'params'"))))
    val reason = "serve-worlds draws the engine's worlds and takes no actions; '$name' was not acted on"
    respondJson(exchange, 200, JsonObject(mapOf("handled" to JsonPrimitive(false), "action" to JsonPrimitive(name), "reason" to JsonPrimitive(reason))))
}

fun startWorldsServer(options: WorldsOptions): HttpServer {
    val engine = ServedEngine(options.engine)
    return startServing(options.bind, options.port, options.bundle, ::serveAction) { exchange -> serveWorldSpec(exchange, engine) }
}
