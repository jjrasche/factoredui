package ai.factoredui.application

import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private const val SCREEN = """{ "title": "Day", "rows": "steps", "chart": { "x": "t", "series": [ { "id": "a", "path": "a" } ] } }"""

private const val SEASON = """{ "steps": [ { "t": 0, "a": 1 }, { "t": 1, "a": 2 } ] }"""

private fun get(server: com.sun.net.httpserver.HttpServer, path: String): Pair<Int, String> {
    val connection = URI("http://127.0.0.1:${server.address.port}$path").toURL().openConnection() as HttpURLConnection
    val stream = if (connection.responseCode < 400) connection.inputStream else connection.errorStream
    return connection.responseCode to stream.bufferedReader().readText()
}

private fun filesIn(directory: Path, screen: String = SCREEN, season: String = SEASON): ScreenOptions {
    Files.writeString(directory.resolve("screen.json"), screen)
    Files.writeString(directory.resolve("season.json"), season)
    Files.createDirectories(directory.resolve("bundle"))
    Files.writeString(directory.resolve("bundle/index.html"), "<html></html>")
    return ScreenOptions(directory.resolve("season.json"), directory.resolve("screen.json"), directory.resolve("bundle"), "127.0.0.1", 0)
}

class ScreenServerTest {
    @Test
    fun the_options_name_the_season_the_screen_the_bundle_and_where_to_listen() {
        val options = parseScreenOptions(listOf("--season", "s.json", "--screen", "d.json", "--bundle", "b", "--bind", "1.2.3.4", "--port", "8093"))
        assertEquals(listOf("s.json", "d.json", "b", "1.2.3.4", 8093), listOf(options.season.toString(), options.screen.toString(), options.bundle.toString(), options.bind, options.port))
    }

    @Test
    fun an_option_that_is_missing_or_unknown_or_has_no_value_is_refused_by_name() {
        assertTrue(assertFailsWith<UsageException> { parseScreenOptions(listOf("--season", "s", "--screen", "d", "--bundle", "b", "--bind", "x")) }.message!!.contains("--port is required"))
        assertTrue(assertFailsWith<UsageException> { parseScreenOptions(listOf("--system", "w")) }.message!!.contains("unknown option '--system'"))
        assertTrue(assertFailsWith<UsageException> { parseScreenOptions(listOf("--port")) }.message!!.contains("--port needs a value"))
    }

    @Test
    fun the_spec_is_built_from_the_two_files_as_they_stand_at_each_load() {
        val directory = Files.createTempDirectory("screen-server")
        val options = filesIn(directory)
        val server = startScreenServer(options)
        try {
            val (status, first) = get(server, "/spec.json")
            assertEquals(200, status)
            assertTrue(""""title"""" !in first && "Day" in first, first)
            Files.writeString(options.screen, SCREEN.replace("Day", "Another day"))
            assertTrue("Another day" in get(server, "/spec.json").second)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun a_file_the_screen_cannot_be_built_from_is_a_502_that_says_why() {
        val directory = Files.createTempDirectory("screen-server")
        val server = startScreenServer(filesIn(directory, season = """{ "steps": 3 }"""))
        try {
            val (status, body) = get(server, "/spec.json")
            assertEquals(502, status)
            assertTrue("the season has no 'steps' list" in body, body)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun the_root_goes_to_the_entry_page_and_the_bundle_is_served_beside_the_spec() {
        val directory = Files.createTempDirectory("screen-server")
        val server = startScreenServer(filesIn(directory))
        try {
            assertEquals(200, get(server, "/index.html").first)
            assertEquals(404, get(server, "/missing.js").first)
        } finally {
            server.stop(0)
        }
    }
}
