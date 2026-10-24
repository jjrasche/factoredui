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
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

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

private fun siteOfView(engine: ServedEngine, siteFile: Path, timings: Timings): JsonObject =
    siteSpec(engine, engine.worlds(), readSiteScreen(Files.readString(siteFile)), timings)

private fun specOfView(engine: ServedEngine, world: EngineWorld, view: WorldView, timings: Timings): JsonObject =
    view.site?.let { siteOfView(engine, it, timings) } ?: view.screen?.let { screenSpec(engine, world, it, timings) } ?: overviewSpec(engine, world, timings)

private fun drawFailure(problem: Throwable, subject: String): String = "Cannot draw $subject: ${problem.message ?: problem::class.simpleName}"

private class DrawnWorldPage(val page: JsonObject, val worldId: String? = null, val worldFile: Path? = null)

private fun pageOf(engine: ServedEngine, worldId: String?, viewId: String?, timings: Timings): DrawnWorldPage {
    val worlds = engine.worlds()
    val labels = worlds.map(::labelOf)
    if (worlds.isEmpty()) return DrawnWorldPage(noticePage(worldBar(labels, null, emptyList(), null), "The engine serves no worlds."))
    val chosen = if (worldId == null) worlds.first() else worlds.firstOrNull { it.id == worldId }
    if (chosen == null) return DrawnWorldPage(noticePage(worldBar(labels, null, emptyList(), null), "The engine serves no world named '$worldId'."))
    val views = viewsOf(chosen)
    val view = if (viewId == null) views.firstOrNull() else views.firstOrNull { it.id == viewId }
    val bar = worldBar(labels, chosen, views, view)
    val state = barState(chosen)
    if (view == null) return DrawnWorldPage(noticePage(bar, if (viewId == null) "${chosen.id} has no view to draw." else "${chosen.id} has no view named '$viewId'.", state))
    return runCatching { DrawnWorldPage(withWorldBar(specOfView(engine, chosen, view, timings), bar, state), chosen.id, chosen.path) }
        .getOrElse { DrawnWorldPage(noticePage(bar, drawFailure(it, "${view.label} of ${chosen.id}"), state)) }
}

/** How long the page waits: a visitor waits `visitorWaitSeconds` for a build before being told it is under way; the background build waits `backgroundTimeoutSeconds` for each engine call. */
data class WorldsPatience(val visitorWaitSeconds: Long = 60, val backgroundTimeoutSeconds: Long = 600, val retryBackoffSeconds: Long = 60, val retryMaxSeconds: Long = 600, val warmAtStart: Boolean = true)

private const val DEFAULT_BUILD_SECONDS = 90L
private const val WARM_RETRY_MILLIS = 30_000L

private class BuiltPage(val body: ByteArray, val timing: String, val isClean: Boolean, val key: String?)

private class WorldsCache(val store: WorldSpecStore, val patience: WorldsPatience) {
    val building = ConcurrentHashMap<String, CompletableFuture<BuiltPage>>()
    val buildSeconds = ConcurrentHashMap<String, Long>()
    val backoffSeconds = ConcurrentHashMap<String, Long>()
    val nextAttemptNanos = ConcurrentHashMap<String, Long>()
    val attemptedKey = ConcurrentHashMap<String, String>()
    val retrying: MutableSet<String> = ConcurrentHashMap.newKeySet()
}

private fun requestOf(asked: Map<String, String>) = "${asked["world"].orEmpty()}\u0000${asked["view"].orEmpty()}"

private fun respondSpec(exchange: HttpExchange, body: ByteArray, timing: String) {
    exchange.responseHeaders.add("Server-Timing", timing)
    respond(exchange, 200, CONTENT_TYPES.getValue("json"), body)
}

private fun buildAndKeep(engine: ServedEngine, cache: WorldsCache, asked: Map<String, String>): BuiltPage {
    val started = System.nanoTime()
    val timings = Timings()
    val drawn = pageOf(engine, asked["world"], asked["view"], timings)
    val body = drawn.page.toString().toByteArray()
    val isClean = !hasEngineFailure(drawn.page)
    val worldFile = drawn.worldFile
    val worldId = drawn.worldId
    val request = requestOf(asked)
    val isReplacing = isClean || cache.store.lastGood(request)?.spec?.isClean != true
    val key = if (worldFile != null && worldId != null) worldSpecKey(worldId, asked["view"], worldFile) else null
    if (worldFile != null && worldId != null && key != null && isReplacing) {
        cache.store.keep(request, WorldSpec(worldId, key, worldFile, body, isClean))
        cache.buildSeconds[request] = (System.nanoTime() - started) / 1_000_000_000
    }
    return BuiltPage(body, timings.header((System.nanoTime() - started) / 1_000_000), isClean || worldFile == null, key)
}

private fun currentKeyOf(kept: WorldSpec, viewId: String?): String? = runCatching { worldSpecKey(kept.worldId, viewId, kept.worldFile) }.getOrNull()

private fun isBackedOffFor(cache: WorldsCache, request: String, key: String?): Boolean =
    (cache.nextAttemptNanos[request] ?: 0L) > System.nanoTime() && cache.attemptedKey[request] == key

private fun shouldRebuild(cache: WorldsCache, request: String, kept: WorldSpec, viewId: String?): Boolean {
    val current = currentKeyOf(kept, viewId)
    val isStale = !kept.isClean || current != kept.key
    return isStale && !isBackedOffFor(cache, request, current)
}

private fun clearBackoff(cache: WorldsCache, request: String) {
    cache.backoffSeconds.remove(request)
    cache.nextAttemptNanos.remove(request)
    cache.attemptedKey.remove(request)
}

private fun backOff(cache: WorldsCache, request: String, key: String?): Long {
    val patience = cache.patience
    val seconds = cache.backoffSeconds.merge(request, patience.retryBackoffSeconds) { before, _ -> minOf(before * 2, patience.retryMaxSeconds) }!!
    cache.nextAttemptNanos[request] = System.nanoTime() + seconds * 1_000_000_000
    if (key == null) cache.attemptedKey.remove(request) else cache.attemptedKey[request] = key
    return seconds
}

private fun retryAfterBackoff(engine: ServedEngine, cache: WorldsCache, asked: Map<String, String>, key: String?) {
    val request = requestOf(asked)
    if (!cache.retrying.add(request)) return
    val seconds = backOff(cache, request, key)
    Thread {
        try {
            Thread.sleep(seconds * 1000)
        } finally {
            cache.retrying.remove(request)
        }
        buildInBackground(engine, cache, asked)
    }.apply { isDaemon = true }.start()
}

private fun buildInBackground(engine: ServedEngine, cache: WorldsCache, asked: Map<String, String>): CompletableFuture<BuiltPage> {
    val request = requestOf(asked)
    val started = CompletableFuture<BuiltPage>()
    val running = cache.building.putIfAbsent(request, started) ?: started
    if (running !== started) return running
    Thread {
        val outcome = runCatching { buildAndKeep(engine, cache, asked) }
        cache.building.remove(request)
        val isCleanBuild = outcome.getOrNull()?.isClean ?: false
        val kept = cache.store.lastGood(request)?.spec
        if (isCleanBuild) clearBackoff(cache, request) else if (outcome.isSuccess || kept != null) retryAfterBackoff(engine, cache, asked, outcome.getOrNull()?.key ?: kept?.let { currentKeyOf(it, asked["view"]) })
        outcome.fold({ started.complete(it) }, { started.completeExceptionally(it) })
    }.apply { isDaemon = true }.start()
    return started
}

private fun buildingPage(cache: WorldsCache, asked: Map<String, String>): ByteArray {
    val seconds = cache.buildSeconds[requestOf(asked)] ?: DEFAULT_BUILD_SECONDS
    return noticePage(worldBar(emptyList(), null, emptyList(), null), "This page is building, ready in about $seconds s. Reload then.").toString().toByteArray()
}

private fun serveBuilt(exchange: HttpExchange, cache: WorldsCache, asked: Map<String, String>, build: CompletableFuture<BuiltPage>) {
    try {
        val built = build.get(cache.patience.visitorWaitSeconds, TimeUnit.SECONDS)
        respondSpec(exchange, built.body, built.timing)
    } catch (stillBuilding: TimeoutException) {
        respondSpec(exchange, buildingPage(cache, asked), "building")
    } catch (failed: ExecutionException) {
        respondText(exchange, 502, "cannot build the page: ${failed.cause?.message}")
    }
}

private fun serveWorldSpec(exchange: HttpExchange, engine: ServedEngine, cache: WorldsCache) {
    val asked = queryOf(exchange)
    val lastGood = cache.store.lastGood(requestOf(asked))
    if (lastGood == null) return serveBuilt(exchange, cache, asked, buildInBackground(engine, cache, asked))
    if (shouldRebuild(cache, requestOf(asked), lastGood.spec, asked["view"])) buildInBackground(engine, cache, asked)
    respondSpec(exchange, lastGood.spec.body, "cache;desc=${lastGood.source}")
}

private fun requestsToWarm(worlds: List<EngineWorld>): List<Map<String, String>> =
    listOf(emptyMap<String, String>()) + worlds.flatMap { world -> listOf(mapOf("world" to world.id)) + viewsOf(world).map { mapOf("world" to world.id, "view" to it.id) } }

private fun listWorldsWhenAnswered(engine: ServedEngine): List<EngineWorld> {
    while (true) {
        runCatching { engine.worlds() }.onSuccess { return it }
        Thread.sleep(WARM_RETRY_MILLIS)
    }
}

private fun warmEveryWorld(engine: ServedEngine, cache: WorldsCache) {
    Thread {
        val worlds = listWorldsWhenAnswered(engine)
        requestsToWarm(worlds).forEach { asked -> runCatching { buildInBackground(engine, cache, asked).get() } }
    }.apply { isDaemon = true }.start()
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

fun startWorldsServer(options: WorldsOptions, cacheDirectory: Path? = defaultCacheDirectory(), patience: WorldsPatience = WorldsPatience()): HttpServer {
    val engine = ServedEngine(options.engine, patience.backgroundTimeoutSeconds)
    val cache = WorldsCache(WorldSpecStore(cacheDirectory), patience)
    val server = startServing(options.bind, options.port, options.bundle, ::serveAction) { exchange -> serveWorldSpec(exchange, engine, cache) }
    if (patience.warmAtStart) warmEveryWorld(engine, cache)
    return server
}
