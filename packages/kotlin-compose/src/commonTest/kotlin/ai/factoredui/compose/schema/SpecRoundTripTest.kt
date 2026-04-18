package ai.factoredui.compose.schema

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Verifies that the Kotlin schema data classes round-trip cleanly with the JSON
 * format produced by @factoredui/core on the TypeScript side.
 *
 * If these tests pass, a spec serialised by the TS core can be deserialised here
 * without data loss and re-serialised to identical JSON.
 */
class SpecRoundTripTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    /** Minimal spec matching the Spec envelope contract. */
    private val minimalSpecJson = """
        {
          "spec_version": 1,
          "renderer_min": 1,
          "root": {
            "id": "root",
            "type": "column",
            "props": { "padding": 16 },
            "children": []
          }
        }
    """.trimIndent()

    @Test
    fun minimalSpecDeserializesWithoutError() {
        val spec = json.decodeFromString<Spec>(minimalSpecJson)
        assertEquals(1, spec.specVersion)
        assertEquals(1, spec.rendererMin)
        assertEquals("root", spec.root.id)
        assertEquals(SpecNodeType.COLUMN, spec.root.type)
    }

    @Test
    fun specRoundTripsToEquivalentJson() {
        val spec = json.decodeFromString<Spec>(minimalSpecJson)
        val reEncoded = json.encodeToString(Spec.serializer(), spec)
        val reDecoded = json.decodeFromString<Spec>(reEncoded)
        assertEquals(spec, reDecoded)
    }

    @Test
    fun textNodeWithBindingRefDeserializesCorrectly() {
        val nodeJson = """
            {
              "id": "status",
              "type": "text",
              "props": { "value": "{shell.inputText}", "variant": "body" },
              "visible": "{shell.hasResult}"
            }
        """.trimIndent()

        val node = json.decodeFromString<SpecNode>(nodeJson)

        assertEquals("status", node.id)
        assertEquals(SpecNodeType.TEXT, node.type)
        assertEquals("{shell.hasResult}", node.visible)

        val valueProp = node.props["value"]
        assertNotNull(valueProp)
        assertTrue(valueProp.isBindingRef(), "value prop should be a binding ref")
        assertEquals("shell.inputText", valueProp.bindingPath())
    }

    @Test
    fun buttonNodeWithActionDeserializesCorrectly() {
        val nodeJson = """
            {
              "id": "submit-btn",
              "type": "button",
              "props": { "label": "Submit", "variant": "primary" },
              "action": { "action": "submit", "params": { "route": "/home" } }
            }
        """.trimIndent()

        val node = json.decodeFromString<SpecNode>(nodeJson)

        assertEquals("submit-btn", node.id)
        assertNotNull(node.action)
        assertEquals("submit", node.action!!.action)
        assertEquals(SpecValue.StringValue("/home"), node.action.params["route"])
    }

    @Test
    fun listNodeWithItemTemplateDeserializesCorrectly() {
        val nodeJson = """
            {
              "id": "item-list",
              "type": "list",
              "props": {
                "data": "items",
                "emptyText": "No items",
                "itemTemplate": {
                  "id": "item-row",
                  "type": "row",
                  "props": {},
                  "children": []
                }
              }
            }
        """.trimIndent()

        val node = json.decodeFromString<SpecNode>(nodeJson)
        val listProps = node.props.asListProps()

        assertEquals("items", listProps.data)
        assertEquals("No items", listProps.emptyText)
        assertNotNull(listProps.itemTemplate)
        assertEquals("item-row", listProps.itemTemplate!!.id)
    }

    @Test
    fun nestedChildrenRoundTrip() {
        val specJson = """
            {
              "spec_version": 1,
              "renderer_min": 1,
              "root": {
                "id": "root",
                "type": "column",
                "props": {},
                "children": [
                  {
                    "id": "header",
                    "type": "text",
                    "props": { "value": "Hello", "variant": "heading" }
                  },
                  {
                    "id": "cta",
                    "type": "button",
                    "props": { "label": "Go" },
                    "action": { "action": "navigate", "params": { "route": "/next" } }
                  }
                ]
              }
            }
        """.trimIndent()

        val spec = json.decodeFromString<Spec>(specJson)
        assertEquals(2, spec.root.children.size)

        val reEncoded = json.encodeToString(Spec.serializer(), spec)
        val reDecoded = json.decodeFromString<Spec>(reEncoded)
        assertEquals(spec, reDecoded)
    }
}
