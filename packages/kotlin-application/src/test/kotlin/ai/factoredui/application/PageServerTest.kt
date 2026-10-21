package ai.factoredui.application

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.GZIPInputStream
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

private val BUNDLE_TEXT = "the same words, ".repeat(2000).toByteArray()

class PageServerTest {
    private val bundle: Path = Files.createTempDirectory("bundle").also { Files.write(it.resolve("app.js"), BUNDLE_TEXT) }
    private val server = startPageServer(Options(Path.of("system.json"), Path.of("we"), bundle, "127.0.0.1", 0))
    private val client = HttpClient.newHttpClient()

    @AfterTest
    fun stopServer() = server.stop(0)

    private fun fetch(path: String, acceptEncoding: String?): HttpResponse<ByteArray> {
        val request = HttpRequest.newBuilder(URI("http://127.0.0.1:${server.address.port}$path")).apply { acceptEncoding?.let { header("Accept-Encoding", it) } }.build()
        return client.send(request, HttpResponse.BodyHandlers.ofByteArray())
    }

    @Test
    fun a_browser_that_accepts_gzip_gets_the_file_compressed_and_smaller() {
        val answer = fetch("/app.js", "gzip, deflate")
        assertEquals("gzip", answer.headers().firstValue("Content-Encoding").get())
        assertContentEquals(BUNDLE_TEXT, GZIPInputStream(answer.body().inputStream()).readAllBytes())
        assert(answer.body().size < BUNDLE_TEXT.size / 10) { "the answer is ${answer.body().size} bytes" }
    }

    @Test
    fun a_client_that_does_not_accept_gzip_gets_the_file_as_it_is() {
        val answer = fetch("/app.js", null)
        assertNull(answer.headers().firstValue("Content-Encoding").orElse(null))
        assertContentEquals(BUNDLE_TEXT, answer.body())
    }

    private fun fakeRuntime(callLog: Path): Path {
        val launcher = Files.createDirectories(Files.createTempDirectory("runtime").resolve("bin")).resolve("we")
        Files.writeString(launcher, "echo \"$1\" >> '$callLog'\necho '{\"sections\": {\"expansion\": {\"ok\": true, \"asked\": true, \"extent_mm\": [10, 10], \"objects\": [], \"instances\": [], \"supplied\": {}}}}'\n")
        return launcher
    }

    private fun serving(system: Path, callLog: Path, cache: Path?) = startPageServer(Options(system, fakeRuntime(callLog), bundle, "127.0.0.1", 0, cacheDirectory = cache))

    private fun calls(callLog: Path) = Files.readAllLines(callLog).count { it == "load" }

    @Test
    fun the_page_is_built_once_per_state_of_the_world_file_as_two_loads_not_once_per_request() {
        val callLog = Files.createTempFile("calls", ".log")
        val system = Files.createTempFile("system", ".json").also { Files.writeString(it, """{ "id": "w", "title": "W", "systems": {} }""") }
        val cached = serving(system, callLog, Files.createTempDirectory("cache"))
        try {
            repeat(3) { assertEquals(200, fetchFrom(cached, "/spec.json").statusCode()) }
            assertEquals(2, calls(callLog))
            val before = versionOf(cached)
            Files.writeString(system, """{ "id": "w", "title": "Changed", "systems": {} }""")
            waitForNewVersion(cached, before)
            assertEquals(4, calls(callLog))
        } finally {
            cached.stop(0)
        }
    }

    @Test
    fun a_restarted_server_serves_the_last_build_from_disk_without_running_the_engine() {
        val callLog = Files.createTempFile("calls", ".log")
        val system = Files.createTempFile("system", ".json").also { Files.writeString(it, """{ "id": "w", "title": "W", "systems": {} }""") }
        val cache = Files.createTempDirectory("cache")
        val first = serving(system, callLog, cache)
        val built = try { fetchFrom(first, "/spec.json") } finally { first.stop(0) }
        val second = serving(system, callLog, cache)
        try {
            val served = fetchFrom(second, "/spec.json")
            assertEquals(2, calls(callLog))
            assertContentEquals(built.body(), served.body())
            assertEquals(true, served.headers().firstValue("Server-Timing").get().startsWith("cache;desc=disk"))
        } finally {
            second.stop(0)
        }
    }

    @Test
    fun a_changed_world_file_is_not_served_from_the_disk() {
        val callLog = Files.createTempFile("calls", ".log")
        val system = Files.createTempFile("system", ".json").also { Files.writeString(it, """{ "id": "w", "title": "W", "systems": {} }""") }
        val cache = Files.createTempDirectory("cache")
        serving(system, callLog, cache).also { fetchFrom(it, "/spec.json"); it.stop(0) }
        Files.writeString(system, """{ "id": "w", "title": "Another title", "systems": {} }""")
        val restarted = serving(system, callLog, cache)
        try {
            assertEquals(200, fetchFrom(restarted, "/spec.json").statusCode())
            assertEquals(4, calls(callLog))
        } finally {
            restarted.stop(0)
        }
    }

    private fun versionOf(running: com.sun.net.httpserver.HttpServer) = String(fetchFrom(running, "/spec.version").body())

    private fun waitForNewVersion(running: com.sun.net.httpserver.HttpServer, old: String) {
        val deadline = System.nanoTime() + 20_000_000_000L
        while (versionOf(running) == old) {
            check(System.nanoTime() < deadline) { "the version never moved from $old" }
            Thread.sleep(100)
        }
    }

    private fun slowRuntime(callLog: Path, gate: Path): Path {
        val launcher = Files.createDirectories(Files.createTempDirectory("runtime").resolve("bin")).resolve("we")
        Files.writeString(launcher, "echo \"$1\" >> '$callLog'\nif [ -f '$gate' ]; then sleep 3; fi\necho '{\"sections\": {\"expansion\": {\"ok\": true, \"asked\": true, \"extent_mm\": [10, 10], \"objects\": [], \"instances\": [], \"supplied\": {}}}}'\n")
        return launcher
    }

    @Test
    fun the_page_already_served_keeps_being_served_while_a_changed_world_is_rebuilt_and_the_version_moves_when_it_is_ready() {
        val callLog = Files.createTempFile("calls", ".log")
        val gate = Files.createTempFile("gate", ".flag").also { Files.delete(it) }
        val system = Files.createTempFile("system", ".json").also { Files.writeString(it, """{ "id": "w", "title": "First title", "systems": {} }""") }
        val live = startPageServer(Options(system, slowRuntime(callLog, gate), bundle, "127.0.0.1", 0, cacheDirectory = null))
        try {
            assertEquals(200, fetchFrom(live, "/spec.json").statusCode())
            val before = versionOf(live)
            Files.createFile(gate)
            Files.writeString(system, """{ "id": "w", "title": "Second title", "systems": {} }""")
            Thread.sleep(2500)
            val started = System.nanoTime()
            val duringRebuild = fetchFrom(live, "/spec.json")
            assertEquals(true, (System.nanoTime() - started) < 1_000_000_000L, "the old page waited for the rebuild")
            assertEquals(true, String(duringRebuild.body()).contains("First title"))
            assertEquals(before, versionOf(live))
            waitForNewVersion(live, before)
            assertEquals(true, String(fetchFrom(live, "/spec.json").body()).contains("Second title"))
        } finally {
            live.stop(0)
        }
    }

    private fun fetchFrom(running: com.sun.net.httpserver.HttpServer, path: String): HttpResponse<ByteArray> =
        client.send(HttpRequest.newBuilder(URI("http://127.0.0.1:${running.address.port}$path")).build(), HttpResponse.BodyHandlers.ofByteArray())
}
