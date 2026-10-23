package ai.factoredui.application

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.GZIPOutputStream

private const val SPEC_PATH = "/spec.json"
internal const val ACTION_PATH = "/action"
private const val ENTRY_PATH = "/index.html"
private const val ENTRY_QUERY = "spec=$SPEC_PATH&full=1"

internal val CONTENT_TYPES = mapOf(
    "html" to "text/html; charset=utf-8",
    "js" to "text/javascript; charset=utf-8",
    "wasm" to "application/wasm",
    "json" to "application/json; charset=utf-8",
    "map" to "application/json; charset=utf-8",
    "css" to "text/css; charset=utf-8",
)

private fun contentTypeOf(file: Path): String = CONTENT_TYPES[file.fileName.toString().substringAfterLast('.', "")] ?: "application/octet-stream"

internal fun respond(exchange: HttpExchange, status: Int, contentType: String, body: ByteArray) {
    exchange.responseHeaders.add("Content-Type", contentType)
    exchange.responseHeaders.add("Cache-Control", "no-store")
    exchange.sendResponseHeaders(status, body.size.toLong())
    exchange.responseBody.use { it.write(body) }
}

private fun acceptsGzip(exchange: HttpExchange) = exchange.requestHeaders["Accept-Encoding"].orEmpty().any { "gzip" in it }

private fun gzip(body: ByteArray): ByteArray {
    val packed = java.io.ByteArrayOutputStream()
    GZIPOutputStream(packed).use { it.write(body) }
    return packed.toByteArray()
}

private class CompressedFile(val modifiedMillis: Long, val bytes: ByteArray)

private val compressedFiles = ConcurrentHashMap<Path, CompressedFile>()

private fun compressedBytesOf(file: Path): ByteArray {
    val modified = Files.getLastModifiedTime(file).toMillis()
    return compressedFiles.compute(file) { _, kept -> kept?.takeIf { it.modifiedMillis == modified } ?: CompressedFile(modified, gzip(Files.readAllBytes(file))) }!!.bytes
}

private fun respondFile(exchange: HttpExchange, file: Path) {
    if (!acceptsGzip(exchange)) return respond(exchange, 200, contentTypeOf(file), Files.readAllBytes(file))
    exchange.responseHeaders.add("Content-Encoding", "gzip")
    respond(exchange, 200, contentTypeOf(file), compressedBytesOf(file))
}

internal fun respondText(exchange: HttpExchange, status: Int, text: String) = respond(exchange, status, "text/plain; charset=utf-8", text.toByteArray())

private fun failureOf(problem: Throwable): String = problem.message ?: problem::class.simpleName.orEmpty()

private fun solveOrSayWhy(loaded: Loaded): Solving =
    runCatching { readSolving(loaded.textOf("solving")) }.getOrElse { Solving(emptyList(), failure = failureOf(it)) }

private fun overlayOrSayWhy(options: PageSource, loaded: Loaded): Overlaying? {
    if (options.overlay == null || loaded.isSkipped("overlay")) return null
    return runCatching { Overlaying(readOverlay(loaded.textOf("overlay"))) }.getOrElse { Overlaying(null, failure = failureOf(it)) }
}

private fun rankOrSayWhy(options: PageSource): Ranked? {
    val chosen = options.rank ?: return null
    return runCatching { Ranked(readRanking(rankSystem(options.runtime, options.system, chosen))) }
        .getOrElse { Ranked(null, failure = failureOf(it)) }
}

private fun hasRankingWeights(options: PageSource): Boolean =
    (kotlinx.serialization.json.Json.parseToJsonElement(Files.readString(options.system)) as? kotlinx.serialization.json.JsonObject)?.get("ranking") is kotlinx.serialization.json.JsonObject

private val workers: ExecutorService = Executors.newCachedThreadPool { task -> Thread(task).apply { isDaemon = true } }

private fun <T> awaited(future: CompletableFuture<T>): T =
    try {
        future.get()
    } catch (failure: ExecutionException) {
        throw failure.cause ?: failure
    }

private fun <T> inWorker(block: () -> T): CompletableFuture<T> = CompletableFuture.supplyAsync({ block() }, workers)

private fun rankEach(options: PageSource, systems: List<DescribedSystem>, timings: Timings): Map<String, Ranked> = timings.timed("rank") {
    val asked = systems.filter { it.alternatives.isNotEmpty() }.associate { system ->
        system.id to inWorker { runCatching { Ranked(readRanking(rankSystem(options.runtime, options.system, system.id))) }.getOrElse { Ranked(null, failure = failureOf(it)) } }
    }
    asked.mapValues { (_, future) -> awaited(future) }
}

private fun describeOrSayWhy(options: PageSource, loaded: Loaded, timings: Timings): Describing =
    runCatching {
        val systems = readDescribed(loaded.textOf("describe"))
        val weighted = hasRankingWeights(options)
        Describing(systems, if (weighted) rankEach(options, systems, timings) else emptyMap(), weighted)
    }.getOrElse { Describing(emptyList(), failure = failureOf(it)) }

private fun boardOrSayWhy(loaded: Loaded): Boarded? {
    return runCatching { if (loaded.isSkipped("simulation")) null else Boarded(readBoard(loaded.textOf("simulation"))) }.getOrElse { Boarded(null, failureOf(it)) }
}

private fun buildOrSayWhy(loaded: Loaded): Built? =
    runCatching { if (loaded.isSkipped("simulation")) null else Built(readBuild(loaded.textOf("simulation"))) }.getOrElse { Built(null, failureOf(it)) }

private fun traceOrSayWhy(loaded: Loaded): Traced? =
    runCatching { if (loaded.isSkipped("simulation")) null else Traced(readTrace(loaded.textOf("simulation"))) }.getOrElse { Traced(null, failureOf(it)) }

private fun sampleOrSayWhy(options: PageSource): Sampled? {
    options.sampleFile?.let { file -> return runCatching { Sampled(readSampling(Files.readString(file))) }.getOrElse { Sampled(null, failureOf(it)) } }
    val arguments = options.sampleArguments ?: return null
    return runCatching { Sampled(readSampling(sampleSystem(options.runtime, options.system, arguments))) }.getOrElse { Sampled(null, failureOf(it)) }
}

private fun geometryOrSayWhy(loaded: Loaded): Geometried =
    runCatching { Geometried(readGeometry(loaded.textOf("geometry"))) }.getOrElse { Geometried(null, failure = failureOf(it)) }

internal class PageSource(val system: Path, val runtime: EngineRuntime, val overlay: String?, val rank: String?, val sampleArguments: List<String>?, val sampleFile: Path?)

private fun Options.source(engine: EngineRuntime) = PageSource(system, engine, overlay, rank, sampleArguments, sampleFile)

private class BuiltSpec(val worldStamp: Pair<Long, Long>, val body: ByteArray)

private fun stampOf(system: Path): Pair<Long, Long> = Files.getLastModifiedTime(system).toMillis() to Files.size(system)

private val CORE_SECTIONS = listOf("describe", "expansion", "geometry", "solving")

private val SIMULATION_SECTIONS = listOf("simulation")

private class Gathered(val loaded: Loaded, val described: Describing)

/** The sections the page reads, asked for as two loads at once because the simulation is most of the time; the rankings start as soon as the core sections are in, while the simulation is still running. */
private fun gather(options: PageSource, timings: Timings): Gathered {
    val core = inWorker { timings.timed("load") { readLoaded(loadSystem(options.runtime, options.system, options.overlay, CORE_SECTIONS)) } }
    val simulation = inWorker { timings.timed("simulation") { readLoaded(loadSystem(options.runtime, options.system, null, SIMULATION_SECTIONS)) } }
    val coreLoaded = awaited(core)
    val described = describeOrSayWhy(options, coreLoaded, timings)
    return Gathered(runCatching { coreLoaded.merged(awaited(simulation)) }.getOrDefault(coreLoaded), described)
}

internal fun buildLensSpecBody(options: PageSource, timings: Timings): ByteArray {
    val file = readSystemFile(Files.readString(options.system))
    val sampling = inWorker { timings.timed("sample") { sampleOrSayWhy(options) } }
    val gathered = gather(options, timings)
    val loaded = gathered.loaded
    val ranked = rankOrSayWhy(options)
    return timings.timed("spec") {
        buildLensPageSpec(
            file, readExpansion(loaded.textOf("expansion")), solveOrSayWhy(loaded), overlayOrSayWhy(options, loaded), ranked, gathered.described, geometryOrSayWhy(loaded),
            boardOrSayWhy(loaded), awaited(sampling), buildOrSayWhy(loaded), traceOrSayWhy(loaded),
        ).toString().toByteArray()
    }
}

private class Served(val body: ByteArray, val timing: String)

private fun cacheOf(options: Options): SpecCache? = options.cacheDirectory?.let { SpecCache(it) }

private fun keyOrNull(options: Options): String? = runCatching { specCacheKey(options) }.getOrNull()

private fun buildAndKeep(options: Options, engine: EngineRuntime, built: AtomicReference<BuiltSpec?>, stamp: Pair<Long, Long>, started: Long): Served {
    val timings = Timings()
    val body = buildLensSpecBody(options.source(engine), timings)
    runCatching { keyOrNull(options)?.let { cacheOf(options)?.write(it, body) } }
    built.set(BuiltSpec(stamp, body))
    return Served(body, timings.header((System.nanoTime() - started) / 1_000_000))
}

private fun serveFromMemoryOrDiskOrBuild(options: Options, engine: EngineRuntime, built: AtomicReference<BuiltSpec?>): Served {
    val started = System.nanoTime()
    val stamp = stampOf(options.system)
    built.get()?.takeIf { it.worldStamp == stamp }?.let { return Served(it.body, "cache;desc=memory") }
    val key = keyOrNull(options)
    val kept = key?.let { cacheOf(options)?.read(it) }
    if (kept != null) {
        built.set(BuiltSpec(stamp, kept))
        return Served(kept, "cache;desc=disk, total;dur=${(System.nanoTime() - started) / 1_000_000}")
    }
    return buildAndKeep(options, engine, built, stamp, started)
}

private fun serveSpec(exchange: HttpExchange, options: Options, engine: EngineRuntime, built: AtomicReference<BuiltSpec?>) {
    val served = runCatching { synchronized(built) { serveFromMemoryOrDiskOrBuild(options, engine, built) } }
    served.fold(
        onSuccess = {
            exchange.responseHeaders.add("Server-Timing", it.timing)
            respond(exchange, 200, CONTENT_TYPES.getValue("json"), it.body)
        },
        onFailure = { respondText(exchange, 502, "cannot build the page: ${it.message}") },
    )
}

private fun serveBundleFile(exchange: HttpExchange, bundle: Path) {
    val relative = exchange.requestURI.path.removePrefix("/")
    val file = bundle.resolve(relative).normalize()
    if (!file.startsWith(bundle) || !Files.isRegularFile(file)) return respondText(exchange, 404, "no such file: /$relative")
    respondFile(exchange, file)
}

private fun redirectToEntry(exchange: HttpExchange) {
    exchange.responseHeaders.add("Location", "$ENTRY_PATH?$ENTRY_QUERY")
    exchange.sendResponseHeaders(302, -1)
    exchange.close()
}

internal fun startServing(bind: String, port: Int, bundle: Path, serveActionRequest: ((HttpExchange) -> Unit)? = null, serveSpecRequest: (HttpExchange) -> Unit): HttpServer {
    val bundleRoot = bundle.toAbsolutePath().normalize()
    val server = HttpServer.create(InetSocketAddress(bind, port), 0)
    server.createContext("/") { exchange ->
        when {
            exchange.requestURI.path == "/" -> redirectToEntry(exchange)
            exchange.requestURI.path == SPEC_PATH -> serveSpecRequest(exchange)
            exchange.requestURI.path == ACTION_PATH && serveActionRequest != null -> serveActionRequest(exchange)
            else -> serveBundleFile(exchange, bundleRoot)
        }
    }
    server.executor = Executors.newCachedThreadPool { task -> Thread(task, "page-server-request").also { it.isDaemon = true } }
    server.start()
    return server
}

fun startPageServer(options: Options): HttpServer = startPageServer(options, ProcessRuntime(options.runtime))

internal fun startPageServer(options: Options, engine: EngineRuntime): HttpServer {
    val builtSpec = AtomicReference<BuiltSpec?>(null)
    return startServing(options.bind, options.port, options.bundle) { exchange -> serveSpec(exchange, options, engine, builtSpec) }
}

private fun serveScreen(exchange: HttpExchange, options: ScreenOptions) {
    runCatching { buildScreenSpec(options) }.fold(
        onSuccess = { respond(exchange, 200, CONTENT_TYPES.getValue("json"), it) },
        onFailure = { respondText(exchange, 502, "cannot build the screen: ${it.message}") },
    )
}

fun startScreenServer(options: ScreenOptions): HttpServer = startServing(options.bind, options.port, options.bundle) { exchange -> serveScreen(exchange, options) }
