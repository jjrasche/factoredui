package ai.factoredui.compose.crawl

import ai.factoredui.compose.schema.Spec
import ai.factoredui.compose.schema.SpecNodeType
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val json = Json { ignoreUnknownKeys = true }

private const val CHILD_TEXT = """{ "id": "kid", "type": "text", "props": { "value": "child" } }"""

private val minimalNodeOf: Map<SpecNodeType, String> = mapOf(
    SpecNodeType.COLUMN to """{ "id": "n", "type": "column", "props": {}, "children": [$CHILD_TEXT] }""",
    SpecNodeType.ROW to """{ "id": "n", "type": "row", "props": {}, "children": [$CHILD_TEXT] }""",
    SpecNodeType.STACK to """{ "id": "n", "type": "stack", "props": {}, "children": [$CHILD_TEXT] }""",
    SpecNodeType.SCROLLVIEW to """{ "id": "n", "type": "scrollview", "props": {}, "children": [$CHILD_TEXT] }""",
    SpecNodeType.GRID to """{ "id": "n", "type": "grid", "props": { "columns": 1 }, "children": [$CHILD_TEXT] }""",
    SpecNodeType.TEXT to """{ "id": "n", "type": "text", "props": { "value": "drawn" } }""",
    SpecNodeType.IMAGE to """{ "id": "n", "type": "image", "props": { "source": "" } }""",
    SpecNodeType.VIDEO to """{ "id": "n", "type": "video", "props": { "source": "/tmp/x.mp4" } }""",
    SpecNodeType.ICON to """{ "id": "n", "type": "icon", "props": { "name": "check" } }""",
    SpecNodeType.DIVIDER to """{ "id": "n", "type": "divider", "props": {} }""",
    SpecNodeType.SPACER to """{ "id": "n", "type": "spacer", "props": { "size": 24 } }""",
    SpecNodeType.TEXTINPUT to """{ "id": "n", "type": "textinput", "props": { "value": "typed" } }""",
    SpecNodeType.BUTTON to """{ "id": "n", "type": "button", "props": { "label": "go" } }""",
    SpecNodeType.TOGGLE to """{ "id": "n", "type": "toggle", "props": { "label": "on", "value": true } }""",
    SpecNodeType.SELECT to """{ "id": "n", "type": "select", "props": { "value": "a", "options": ["a", "b"] } }""",
    SpecNodeType.SLIDER to """{ "id": "n", "type": "slider", "props": { "value": 0.5, "min": 0, "max": 1 } }""",
    SpecNodeType.CARD to """{ "id": "n", "type": "card", "props": {}, "children": [$CHILD_TEXT] }""",
    SpecNodeType.LIST to """{ "id": "n", "type": "list", "props": {}, "children": [$CHILD_TEXT] }""",
    SpecNodeType.TABS to """{ "id": "n", "type": "tabs", "props": { "items": ["one"] }, "children": [$CHILD_TEXT] }""",
    SpecNodeType.MODAL to """{ "id": "n", "type": "modal", "props": { "open": true }, "children": [$CHILD_TEXT] }""",
    SpecNodeType.CHIP to """{ "id": "n", "type": "chip", "props": { "label": "tag" } }""",
    SpecNodeType.SCENE3D to """{ "id": "n", "type": "scene3d", "props": { "background": "neutral-gray" } }""",
    SpecNodeType.CANVAS to """{ "id": "n", "type": "canvas", "props": {} }""",
    SpecNodeType.GEOMAP to """{ "id": "n", "type": "geomap", "props": {} }""",
)

// A spacer reserves space and paints nothing — that is its whole job, so it is held to
// occupying a region rather than to colouring pixels.
private val layoutOnlyPrimitives = setOf(SpecNodeType.SPACER)

class EveryPrimitiveDrawsTest {

    @Test
    fun every_primitive_in_the_enum_has_a_minimal_spec_that_should_draw() {
        val missing = SpecNodeType.entries.filterNot { it in minimalNodeOf }.map { it.name }
        assertEquals(
            emptyList(),
            missing,
            "a new primitive needs a minimal node here, or nothing proves it draws at all",
        )
    }

    @Test
    fun every_primitive_draws_into_a_tagged_region() {
        val blind = SpecNodeType.entries.mapNotNull { type ->
            val nodeJson = minimalNodeOf[type] ?: return@mapNotNull "${type.name}: no minimal node"
            val spec = json.decodeFromString(Spec.serializer(), """{"spec_version":1,"renderer_min":1,"root":$nodeJson}""")
            if (type in layoutOnlyPrimitives) {
                val reserved = renderScreen(spec, emptyMap(), 400, 800).nodes.single { it.id == "n" }.bounds
                return@mapNotNull if (reserved == null || reserved.bottom <= reserved.top) {
                    "${type.name}: reserved no space"
                } else {
                    null
                }
            }
            val undrawn = undrawnNodes(spec, emptyMap(), 400, 800).map { it.id }
            if ("n" in undrawn) "${type.name}: drew no tagged region" else null
        }
        assertTrue(blind.isEmpty(), "primitives the region instrument cannot see:\n  ${blind.joinToString("\n  ")}")
    }
}
