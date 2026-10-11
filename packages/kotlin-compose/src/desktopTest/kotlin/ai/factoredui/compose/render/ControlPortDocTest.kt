package ai.factoredui.compose.render

import ai.factoredui.compose.renderer.RenderContext
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

private val GUIDE = File("../../AGENTS.md")

private const val ROUTES_OPEN = "<!-- control-port-routes -->"
private const val ROUTES_CLOSE = "<!-- /control-port-routes -->"

/** Each line of the delimited block is a route then the query parameters it reads. */
private fun documentedRoutes(guide: String): Map<String, Set<String>> {
    val block = guide.substringAfter(ROUTES_OPEN, "").substringBefore(ROUTES_CLOSE, "")
    return block.lines().map { it.trim() }.filter { it.startsWith("/") }.associate { line ->
        val words = line.split(Regex("\\s+"))
        words.first().removePrefix("/") to words.drop(1).toSet()
    }
}

private fun servedRoutes(): Map<String, Set<String>> = WindowControl(RenderContext()).routes.associate { it.name to it.parameters }

class ControlPortDocTest {

    @Test
    fun the_guides_route_block_names_exactly_the_routes_and_parameters_the_control_port_serves() {
        val documented = documentedRoutes(GUIDE.readText())
        assertTrue(documented.isNotEmpty(), "AGENTS.md has no $ROUTES_OPEN block")
        assertEquals(servedRoutes(), documented)
    }

    @Test
    fun a_guide_that_renames_a_route_or_drops_a_parameter_no_longer_matches() {
        val guide = GUIDE.readText()
        assertNotEquals(servedRoutes(), documentedRoutes(guide.replace("/wheel ", "/scroll ")))
        assertNotEquals(servedRoutes(), documentedRoutes(guide.replace("/set    path value", "/set    path")))
    }

    @Test
    fun a_route_handler_can_read_only_the_parameters_its_route_declares() {
        val query = RouteQuery("set", setOf("path", "value"), mapOf("path" to "a.b", "value" to "1", "extra" to "x"))
        assertEquals("a.b", query.required("path"))
        assertFailsWith<IllegalStateException> { query.optional("extra") }
        assertEquals("route /set needs the parameter 'value'", assertFailsWith<IllegalArgumentException> { RouteQuery("set", setOf("path", "value"), emptyMap()).required("value") }.message)
    }
}
