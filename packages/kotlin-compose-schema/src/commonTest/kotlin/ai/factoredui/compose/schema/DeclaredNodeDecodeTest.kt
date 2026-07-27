package ai.factoredui.compose.schema

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Every case here threw or misparsed under the old "has id + type means node" inference. */
class DeclaredNodeDecodeTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    private fun nodeWithProps(propsJson: String): SpecNode =
        json.decodeFromString(
            """{ "id": "n", "type": "text", "props": $propsJson }"""
        )

    @Test
    fun dataObjectWithIdAndUnknownTypeIsData() {
        val node = nodeWithProps("""{ "author": { "id": "u1", "type": "admin" } }""")

        val author = assertIs<SpecValue.ObjectValue>(node.props["author"])
        assertEquals(SpecValue.StringValue("u1"), author.value["id"])
        assertEquals(SpecValue.StringValue("admin"), author.value["type"])
    }

    @Test
    fun dataObjectWhoseTypeCollidesWithAPrimitiveNameIsStillData() {
        val node = nodeWithProps("""{ "meta": { "id": "m1", "type": "text" } }""")

        assertIs<SpecValue.ObjectValue>(node.props["meta"])
    }

    @Test
    fun dataObjectWithExtraKeysAlongsideIdAndTypeIsData() {
        val node = nodeWithProps(
            """{ "record": { "id": "r1", "type": "card", "createdAt": 1700, "archived": false } }"""
        )

        val record = assertIs<SpecValue.ObjectValue>(node.props["record"])
        assertEquals(SpecValue.NumberValue(1700.0), record.value["createdAt"])
        assertEquals(SpecValue.BooleanValue(false), record.value["archived"])
    }

    @Test
    fun dataObjectWithIdAndTypeRoundTripsUnchanged() {
        val node = nodeWithProps("""{ "author": { "id": "u1", "type": "admin" } }""")

        val reDecoded = json.decodeFromString<SpecNode>(json.encodeToString(SpecNode.serializer(), node))

        assertEquals(node, reDecoded)
    }

    @Test
    fun declaredKeyStillDecodesAsNode() {
        val list = json.decodeFromString<SpecNode>(
            """
            {
              "id": "item-list",
              "type": "list",
              "props": {
                "data": "items",
                "itemTemplate": { "id": "row", "type": "row", "props": {} }
              }
            }
            """.trimIndent()
        )

        val template = assertIs<SpecValue.NodeValue>(list.props["itemTemplate"])
        assertEquals("row", template.value.id)
        assertEquals(SpecNodeType.ROW, template.value.type)
    }

    @Test
    fun nodeAtADeclaredKeyRoundTrips() {
        val list = json.decodeFromString<SpecNode>(
            """
            {
              "id": "item-list",
              "type": "list",
              "props": { "itemTemplate": { "id": "row", "type": "row", "props": { "gap": 8 } } }
            }
            """.trimIndent()
        )

        val reDecoded = json.decodeFromString<SpecNode>(json.encodeToString(SpecNode.serializer(), list))

        assertEquals(list, reDecoded)
    }

    @Test
    fun actionParamsNeverDecodeAsANode() {
        val node = json.decodeFromString<SpecNode>(
            """
            {
              "id": "btn",
              "type": "button",
              "action": {
                "action": "select",
                "params": { "itemTemplate": { "id": "row", "type": "row" } }
              }
            }
            """.trimIndent()
        )

        val action = assertNotNull(node.action)
        assertIs<SpecValue.ObjectValue>(action.params["itemTemplate"])
    }

    @Test
    fun encodingANodeAtAnUndeclaredKeyFailsLoudly() {
        val node = SpecNode(
            id = "n",
            type = SpecNodeType.LIST,
            props = mapOf("notDeclared" to SpecValue.NodeValue(SpecNode("tpl", SpecNodeType.ROW))),
        )

        val thrown = assertFailsWith<IllegalStateException> {
            json.encodeToString(SpecNode.serializer(), node)
        }
        assertTrue(
            thrown.message!!.contains("notDeclared"),
            "the error must name the offending key, got: ${thrown.message}",
        )
    }

    @Test
    fun encodingANodeIntoActionParamsFailsLoudly() {
        val action = ActionRef(
            action = "select",
            params = mapOf("itemTemplate" to SpecValue.NodeValue(SpecNode("tpl", SpecNodeType.ROW))),
        )

        assertFailsWith<IllegalStateException> {
            json.encodeToString(ActionRef.serializer(), action)
        }
    }

    @Test
    fun nodeShapedObjectNestedInAnArrayIsData() {
        val node = nodeWithProps("""{ "rows": [ { "id": "a", "type": "text" } ] }""")

        val rows = assertIs<SpecValue.ArrayValue>(node.props["rows"])
        assertIs<SpecValue.ObjectValue>(rows.value.single())
    }
}
