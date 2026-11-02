package ai.factoredui.application

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.IOException
import java.net.URI
import java.net.URLDecoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.Semaphore

class EngineWorld(val id: String, val path: Path)

private const val CONNECT_TIMEOUT_SECONDS = 5L

/** Every call waits `timeoutSeconds` when one is given, and otherwise as long as the caller asks; at most `maxConcurrentCalls` commands are with the engine at once, however many threads ask, and a call's wait for its turn is not part of its timeout. */
class ServedEngine(private val address: URI, private val timeoutSeconds: Long? = null, maxConcurrentCalls: Int = 1) : EngineRuntime {
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS)).build()
    private val turns = Semaphore(maxConcurrentCalls, true)

    fun worlds(): List<EngineWorld> {
        val request = HttpRequest.newBuilder(address.resolve("/worlds")).GET().timeout(Duration.ofSeconds(timeoutSeconds ?: RUNTIME_TIMEOUT_SECONDS)).build()
        val response = sent(request)
        if (response.statusCode() != 200) throw RuntimeFailedException("the engine at $address listed no worlds: ${reasonIn(response)}")
        return worldsIn(Json.parseToJsonElement(response.body().toString(Charsets.UTF_8)))
    }

    override fun answer(arguments: List<String>, timeoutSeconds: Long): String {
        val command = arguments.firstOrNull() ?: throw RuntimeFailedException("no command to send to the engine")
        val config = arguments.getOrNull(1) ?: throw RuntimeFailedException("the command $command names no config")
        val body = JsonObject(mapOf("command" to JsonPrimitive(command), "config" to JsonPrimitive(config), "args" to JsonArray(arguments.drop(2).map { JsonPrimitive(it) })))
        val request = HttpRequest.newBuilder(address.resolve("/run"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
            .timeout(Duration.ofSeconds(this.timeoutSeconds ?: timeoutSeconds))
            .build()
        val response = turns.withPermit { sent(request) }
        if (response.statusCode() != 200) throw RuntimeFailedException("the engine refused $command: ${reasonIn(response)}")
        val text = response.body().toString(Charsets.UTF_8)
        return text.ifBlank { throw RuntimeFailedException("the engine answered nothing to $command: ${standardErrorOf(response)}") }
    }

    private fun sent(request: HttpRequest): HttpResponse<ByteArray> =
        try {
            client.send(request, HttpResponse.BodyHandlers.ofByteArray())
        } catch (unreachable: IOException) {
            throw RuntimeFailedException("the engine at $address did not answer: ${unreachable.message ?: unreachable::class.simpleName}")
        }

    private fun reasonIn(response: HttpResponse<ByteArray>): String {
        val text = response.body().toString(Charsets.UTF_8)
        val reason = (runCatching { Json.parseToJsonElement(text) }.getOrNull() as? JsonObject)?.get("error") as? JsonPrimitive
        return reason?.content ?: text.ifBlank { "status ${response.statusCode()}" }
    }

    private fun standardErrorOf(response: HttpResponse<ByteArray>): String =
        response.headers().firstValue("X-We-Stderr").map { URLDecoder.decode(it, Charsets.UTF_8) }.orElse("it wrote nothing to standard error")
}

private fun <T> Semaphore.withPermit(work: () -> T): T {
    acquire()
    try {
        return work()
    } finally {
        release()
    }
}

private fun worldsIn(answer: JsonElement): List<EngineWorld> {
    val listed = (answer as? JsonObject)?.get("worlds") ?: answer
    val entries = listed as? JsonArray ?: throw RuntimeFailedException("the engine's list of worlds is not a list")
    return entries.map { entry ->
        val world = entry as? JsonObject ?: throw RuntimeFailedException("the engine listed a world that is not an object: $entry")
        EngineWorld(textOf(world, "id"), Path.of(textOf(world, "path")))
    }
}

private fun textOf(world: JsonObject, key: String): String =
    (world[key] as? JsonPrimitive)?.content ?: throw RuntimeFailedException("the engine listed a world with no $key: $world")
