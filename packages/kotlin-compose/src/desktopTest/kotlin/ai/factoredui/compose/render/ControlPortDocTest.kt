package ai.factoredui.compose.render

import ai.factoredui.compose.renderer.RenderContext
import ai.factoredui.compose.renderer.stackedBoardText
import ai.factoredui.compose.schema.Spec
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
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

private const val FIELDS_OPEN = "<!-- tree-node-fields -->"
private const val FIELDS_CLOSE = "<!-- /tree-node-fields -->"

private fun documentedNodeFields(guide: String): Set<String> =
    guide.substringAfter(FIELDS_OPEN, "").substringBefore(FIELDS_CLOSE, "").replace("`", " ").split(Regex("[\\s,]+")).filter { it.isNotEmpty() }.toSet()

private const val SCENE_FIELDS_OPEN = "<!-- scene-record-fields -->"
private const val SCENE_FIELDS_CLOSE = "<!-- /scene-record-fields -->"

private fun documentedSceneFields(guide: String): Set<String> =
    guide.substringAfter(SCENE_FIELDS_OPEN, "").substringBefore(SCENE_FIELDS_CLOSE, "").replace("`", " ").split(Regex("[\\s,]+")).filter { it.isNotEmpty() }.toSet()

private val BOARD_PAGE = Json.decodeFromString(
    Spec.serializer(),
    """{"spec_version":1,"renderer_min":1,"root":{"id":"board","type":"scene","props":{"view_state":{"renderer":"stacked_scene"},"scene_view":"{board.scene}","view":"iso"}}}""",
)

private fun emittedSceneFields(): Set<String> {
    val context = RenderContext(initialData = mapOf("board" to mapOf("scene" to stackedBoardText())))
    SceneSurface(BOARD_PAGE, context, 400, 300).use { surface ->
        val tree = surface.onScreenThread { screenNodes(context.screen) }
        return screenJson(tree!!).getValue("scene").jsonObject.keys
    }
}

private val PLAIN_PAGE = Json.decodeFromString(Spec.serializer(), """{"spec_version":1,"renderer_min":1,"root":{"id":"page","type":"column","children":[{"id":"t","type":"text","props":{"value":"hi"}}]}}""")

private fun emittedPlainPageFields(): Set<String> {
    val context = RenderContext()
    SceneSurface(PLAIN_PAGE, context, 200, 100).use { surface ->
        val tree = surface.onScreenThread { screenNodes(context.screen) }
        return screenJson(tree!!).keys
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
    fun the_guides_node_field_list_names_exactly_the_fields_every_tree_node_carries() {
        val documented = documentedNodeFields(GUIDE.readText())
        assertTrue(documented.isNotEmpty(), "AGENTS.md has no $FIELDS_OPEN block")
        assertEquals(emittedPlainPageFields(), documented)
    }

    @Test
    fun the_guides_scene_field_list_names_exactly_the_fields_a_scene_record_carries() {
        val documented = documentedSceneFields(GUIDE.readText())
        assertTrue(documented.isNotEmpty(), "AGENTS.md has no $SCENE_FIELDS_OPEN block")
        assertEquals(emittedSceneFields(), documented)
    }

    @Test
    fun a_route_handler_can_read_only_the_parameters_its_route_declares() {
        val query = RouteQuery("set", setOf("path", "value"), mapOf("path" to "a.b", "value" to "1", "extra" to "x"))
        assertEquals("a.b", query.required("path"))
        assertFailsWith<IllegalStateException> { query.optional("extra") }
        assertEquals("route /set needs the parameter 'value'", assertFailsWith<IllegalArgumentException> { RouteQuery("set", setOf("path", "value"), emptyMap()).required("value") }.message)
    }
}
