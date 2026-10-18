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
}
