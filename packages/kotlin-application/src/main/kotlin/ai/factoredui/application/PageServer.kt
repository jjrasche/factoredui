package ai.factoredui.application

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path

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

private fun respondText(exchange: HttpExchange, status: Int, text: String) = respond(exchange, status, "text/plain; charset=utf-8", text.toByteArray())

private fun serveSpec(exchange: HttpExchange, options: Options) {
    val spec = runCatching {
        val file = readSystemFile(Files.readString(options.system))
        buildPageSpec(file, readExpansion(expandSystem(options.runtime, options.system)))
    }
    spec.fold(
        onSuccess = { respond(exchange, 200, CONTENT_TYPES.getValue("json"), it.toString().toByteArray()) },
        onFailure = { respondText(exchange, 502, "cannot build the page: ${it.message}") },
    )
}

private fun serveBundleFile(exchange: HttpExchange, bundle: Path) {
    val relative = exchange.requestURI.path.removePrefix("/")
    val file = bundle.resolve(relative).normalize()
    if (!file.startsWith(bundle) || !Files.isRegularFile(file)) return respondText(exchange, 404, "no such file: /$relative")
    respond(exchange, 200, contentTypeOf(file), Files.readAllBytes(file))
}

private fun redirectToEntry(exchange: HttpExchange) {
    exchange.responseHeaders.add("Location", "$ENTRY_PATH?$ENTRY_QUERY")
    exchange.sendResponseHeaders(302, -1)
    exchange.close()
}

fun startPageServer(options: Options): HttpServer {
    val bundle = options.bundle.toAbsolutePath().normalize()
    val server = HttpServer.create(InetSocketAddress(options.bind, options.port), 0)
    server.createContext("/") { exchange ->
        when (exchange.requestURI.path) {
            "/" -> redirectToEntry(exchange)
            SPEC_PATH -> serveSpec(exchange, options)
            else -> serveBundleFile(exchange, bundle)
        }
    }
    server.start()
    return server
}
