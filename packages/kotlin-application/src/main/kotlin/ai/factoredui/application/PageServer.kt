package ai.factoredui.application

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.GZIPOutputStream

private const val SPEC_PATH = "/spec.json"
private const val ENTRY_PATH = "/index.html"
private const val ENTRY_QUERY = "spec=$SPEC_PATH&full=1"

private val CONTENT_TYPES = mapOf(
    "html" to "text/html; charset=utf-8",
    "js" to "text/javascript; charset=utf-8",
    "wasm" to "application/wasm",
    "json" to "application/json; charset=utf-8",
    "map" to "application/json; charset=utf-8",
    "css" to "text/css; charset=utf-8",
)

private fun contentTypeOf(file: Path): String = CONTENT_TYPES[file.fileName.toString().substringAfterLast('.', "")] ?: "application/octet-stream"

private fun respond(exchange: HttpExchange, status: Int, contentType: String, body: ByteArray) {
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

private fun respondText(exchange: HttpExchange, status: Int, text: String) = respond(exchange, status, "text/plain; charset=utf-8", text.toByteArray())

private fun failureOf(problem: Throwable): String = problem.message ?: problem::class.simpleName.orEmpty()

private fun solveOrSayWhy(loaded: Loaded): Solving =
    runCatching { readSolving(loaded.textOf("solving")) }.getOrElse { Solving(emptyList(), failure = failureOf(it)) }

private fun overlayOrSayWhy(options: Options, loaded: Loaded): Overlaying? {
    if (options.overlay == null || loaded.isSkipped("overlay")) return null
    return runCatching { Overlaying(readOverlay(loaded.textOf("overlay"))) }.getOrElse { Overlaying(null, failure = failureOf(it)) }
}

private fun rankOrSayWhy(options: Options): Ranked? {
    val chosen = options.rank ?: return null
    return runCatching { Ranked(readRanking(rankSystem(options.runtime, options.system, chosen))) }
        .getOrElse { Ranked(null, failure = failureOf(it)) }
}

private fun hasRankingWeights(options: Options): Boolean =
    (kotlinx.serialization.json.Json.parseToJsonElement(Files.readString(options.system)) as? kotlinx.serialization.json.JsonObject)?.get("ranking") is kotlinx.serialization.json.JsonObject

private fun rankEach(options: Options, systems: List<DescribedSystem>): Map<String, Ranked> =
    systems.filter { it.alternatives.isNotEmpty() }.associate { system ->
        system.id to runCatching { Ranked(readRanking(rankSystem(options.runtime, options.system, system.id))) }
            .getOrElse { Ranked(null, failure = failureOf(it)) }
    }

private fun describeOrSayWhy(options: Options, loaded: Loaded): Describing =
    runCatching {
        val systems = readDescribed(loaded.textOf("describe"))
        val weighted = hasRankingWeights(options)
        Describing(systems, if (weighted) rankEach(options, systems) else emptyMap(), weighted)
    }.getOrElse { Describing(emptyList(), failure = failureOf(it)) }

private fun geometryOrSayWhy(loaded: Loaded): Geometried =
    runCatching { Geometried(readGeometry(loaded.textOf("geometry"))) }.getOrElse { Geometried(null, failure = failureOf(it)) }

private class BuiltSpec(val worldStamp: Pair<Long, Long>, val body: ByteArray)

private fun stampOf(system: Path): Pair<Long, Long> = Files.getLastModifiedTime(system).toMillis() to Files.size(system)

private fun buildSpecBody(options: Options): ByteArray {
    val file = readSystemFile(Files.readString(options.system))
    val loaded = readLoaded(loadSystem(options.runtime, options.system, options.overlay))
    return buildLensPageSpec(file, readExpansion(loaded.textOf("expansion")), solveOrSayWhy(loaded), overlayOrSayWhy(options, loaded), rankOrSayWhy(options), describeOrSayWhy(options, loaded), geometryOrSayWhy(loaded)).toString().toByteArray()
}

private fun serveSpec(exchange: HttpExchange, options: Options, built: AtomicReference<BuiltSpec?>) {
    val body = runCatching {
        synchronized(built) {
            val stamp = stampOf(options.system)
            built.get()?.takeIf { it.worldStamp == stamp }?.body
                ?: buildSpecBody(options).also { built.set(BuiltSpec(stamp, it)) }
        }
    }
    body.fold(
        onSuccess = { respond(exchange, 200, CONTENT_TYPES.getValue("json"), it) },
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

fun startPageServer(options: Options): HttpServer {
    val bundle = options.bundle.toAbsolutePath().normalize()
    val builtSpec = AtomicReference<BuiltSpec?>(null)
    val server = HttpServer.create(InetSocketAddress(options.bind, options.port), 0)
    server.createContext("/") { exchange ->
        when (exchange.requestURI.path) {
            "/" -> redirectToEntry(exchange)
            SPEC_PATH -> serveSpec(exchange, options, builtSpec)
            else -> serveBundleFile(exchange, bundle)
        }
    }
    server.start()
    return server
}
