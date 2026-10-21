package ai.factoredui.application

import com.sun.net.httpserver.HttpServer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.net.InetSocketAddress
import java.net.URI
import java.net.URLEncoder
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

internal class FakeEngine {
    val posted = mutableListOf<JsonObject>()
    var worldsAnswer: String = """{"worlds": []}"""
    var status: Int = 200
    var runAnswer: (JsonObject) -> String = { "" }
    var runHeaders: Map<String, String> = emptyMap()
    private val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)

    val address: URI get() = URI("http://127.0.0.1:${server.address.port}")

    init {
        server.createContext("/worlds") { exchange ->
            val bytes = worldsAnswer.toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.createContext("/run") { exchange ->
            val request = Json.parseToJsonElement(exchange.requestBody.readBytes().toString(Charsets.UTF_8)) as JsonObject
            posted += request
            runHeaders.forEach { (name, value) -> exchange.responseHeaders.add(name, value) }
            val bytes = runAnswer(request).toByteArray()
            exchange.sendResponseHeaders(status, if (bytes.isEmpty()) -1 else bytes.size.toLong())
            if (bytes.isNotEmpty()) exchange.responseBody.use { it.write(bytes) }
            exchange.close()
        }
        server.start()
    }

    fun stop() = server.stop(0)
}

class ServedEngineTest {
    private val engine = FakeEngine()

    @AfterTest
    fun stopEngine() = engine.stop()

    @Test
    fun the_worlds_are_the_ids_and_paths_the_engine_lists() {
        engine.worldsAnswer = """{"worlds": [{"id": "greenhouse-world", "path": "/w/greenhouse/world.json"}, {"id": "small-house", "path": "/w/small/world.json"}]}"""
        val worlds = ServedEngine(engine.address).worlds()
        assertEquals(listOf("greenhouse-world", "small-house"), worlds.map { it.id })
        assertEquals(Path.of("/w/greenhouse/world.json"), worlds.first().path)
    }

    @Test
    fun a_bare_list_of_worlds_reads_the_same_as_one_under_a_worlds_key() {
        engine.worldsAnswer = """[{"id": "van", "path": "/w/van/world.json"}]"""
        assertEquals(listOf("van"), ServedEngine(engine.address).worlds().map { it.id })
    }

    @Test
    fun a_command_is_posted_with_its_config_and_arguments_and_the_engines_text_comes_back() {
        engine.runAnswer = { """{"sections": {}}""" }
        val answer = ServedEngine(engine.address).answer(listOf("load", "/w/small/world.json", "--include", "describe,geometry"))
        assertEquals("""{"sections": {}}""", answer)
        val request = engine.posted.single()
        assertEquals(JsonPrimitive("load"), request["command"])
        assertEquals(JsonPrimitive("/w/small/world.json"), request["config"])
        assertEquals(JsonArray(listOf(JsonPrimitive("--include"), JsonPrimitive("describe,geometry"))), request["args"])
    }

    @Test
    fun a_refusal_from_the_engine_is_raised_with_the_reason_it_gave() {
        engine.status = 404
        engine.runAnswer = { """{"error": "no world with id or path nowhere"}""" }
        val refusal = assertFailsWith<RuntimeFailedException> { ServedEngine(engine.address).answer(listOf("load", "nowhere")) }
        assertTrue(refusal.message!!.contains("no world with id or path nowhere"), refusal.message)
    }

    @Test
    fun an_empty_answer_raises_with_what_the_engine_wrote_to_standard_error() {
        engine.runHeaders = mapOf("X-We-Exit" to "2", "X-We-Stderr" to URLEncoder.encode("usage: we simulate <config>", Charsets.UTF_8))
        val empty = assertFailsWith<RuntimeFailedException> { ServedEngine(engine.address).answer(listOf("simulate", "/w/small/world.json")) }
        assertTrue(empty.message!!.contains("usage: we simulate <config>"), empty.message)
    }

    @Test
    fun an_engine_that_is_not_listening_is_named_by_its_address() {
        val address = engine.address
        engine.stop()
        val unreachable = assertFailsWith<RuntimeFailedException> { ServedEngine(address).worlds() }
        assertTrue(unreachable.message!!.contains(address.toString()), unreachable.message)
    }
}
