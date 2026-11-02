package ai.factoredui.application

import java.net.URI
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WorldsOptionsTest {
    private val all = listOf("--engine", "http://127.0.0.1:8765", "--bundle", "dist", "--bind", "100.1.2.3", "--port", "8090")

    @Test
    fun the_engine_the_bundle_the_address_and_the_port_are_read() {
        val options = parseWorldsOptions(all)
        assertEquals(WorldsOptions(URI("http://127.0.0.1:8765"), Paths.get("dist"), "100.1.2.3", 8090), options)
    }

    @Test
    fun a_missing_option_is_named() {
        assertTrue(assertFailsWith<UsageException> { parseWorldsOptions(all.take(6)) }.message!!.contains("--port is required"))
    }

    @Test
    fun an_option_the_command_does_not_read_is_refused_with_the_list_it_reads() {
        val refusal = assertFailsWith<UsageException> { parseWorldsOptions(listOf("--system", "w")) }
        assertTrue(refusal.message!!.contains("unknown option '--system'"))
        assertTrue(refusal.message!!.contains("--engine"))
    }

    @Test
    fun a_port_out_of_range_is_refused() {
        assertTrue(assertFailsWith<UsageException> { parseWorldsOptions(all.dropLast(1) + "70000") }.message!!.contains("--port must be a number from 1 to 65535"))
    }

    @Test
    fun an_option_with_no_value_is_refused() {
        assertTrue(assertFailsWith<UsageException> { parseWorldsOptions(listOf("--engine")) }.message!!.contains("--engine needs a value"))
    }
}
