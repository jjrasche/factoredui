package ai.factoredui.compose.render

import ai.factoredui.compose.renderer.RenderContext
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WindowControlTest {

    private fun call(port: Int, route: String, vararg query: Pair<String, String>): Pair<Int, String> {
        val encoded = query.joinToString("&") { (key, value) -> "$key=${URLEncoder.encode(value, Charsets.UTF_8)}" }
        val connection = URI("http://127.0.0.1:$port/$route?$encoded").toURL().openConnection() as HttpURLConnection
        val stream = if (connection.responseCode < 400) connection.inputStream else connection.errorStream
        return connection.responseCode to stream.bufferedReader().readText()
    }

    @Test
    fun aSetWritesTheLiveStoreAndAGetReadsItBack() {
        val context = RenderContext(initialData = mapOf("board" to mapOf("view" to "plan", "ids" to emptyList<String>())))
        val server = WindowControl(context).start(0)
        try {
            val port = server.address.port
            call(port, "set", "path" to "board.view", "value" to "\"iso\"")
            call(port, "set", "path" to "board.ids", "value" to "[\"p1\",\"p2\"]")
            assertEquals(mapOf("view" to "iso", "ids" to listOf("p1", "p2")), context.data["board"])
            assertEquals(200 to "\"iso\"", call(port, "get", "path" to "board.view"))
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun aGetOfAPathThatNamesNoKeyIsRefusedRatherThanReadAsNull() {
        val server = WindowControl(RenderContext(initialData = mapOf("board" to mapOf("gone" to null)))).start(0)
        try {
            assertEquals(200 to "null", call(server.address.port, "get", "path" to "board.gone"))
            assertEquals(400, call(server.address.port, "get", "path" to "board.missing").first)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun aClickBeforeTheWindowOpensIsRefusedWithTheReason() {
        val server = WindowControl(RenderContext()).start(0)
        try {
            val (status, body) = call(server.address.port, "click", "x" to "10", "y" to "10")
            assertEquals(400, status)
            assertTrue(body.contains("not open"), body)
        } finally {
            server.stop(0)
        }
    }
}
