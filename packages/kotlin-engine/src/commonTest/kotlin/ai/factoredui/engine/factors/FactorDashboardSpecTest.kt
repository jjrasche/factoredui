package ai.factoredui.engine.factors

import ai.factoredui.compose.schema.RENDERER_VERSION
import ai.factoredui.compose.schema.Spec
import ai.factoredui.compose.schema.SpecNode
import ai.factoredui.compose.schema.SpecNodeType
import ai.factoredui.compose.schema.SpecValue
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FactorDashboardSpecTest {

    private val spec = buildFactorDashboardSpec("checkout/button")

    @Test
    fun buildsEnvelopeAndRoot() {
        assertEquals(1, spec.specVersion)
        assertEquals(RENDERER_VERSION, spec.rendererMin)
        assertEquals("root", spec.root.id)
        assertEquals(SpecNodeType.COLUMN, spec.root.type)
        assertEquals(2, spec.root.children.size)
    }

    @Test
    fun subtitleBindsComponentPath() {
        val header = spec.root.children[0]
        val subtitle = header.children[1]
        assertEquals(SpecValue.StringValue("checkout/button"), subtitle.props["value"])
    }

    @Test
    fun factorListBindsDataSourceWithTemplate() {
        val list = spec.root.children[1]
        assertEquals(SpecNodeType.LIST, list.type)
        assertEquals(SpecValue.StringValue("{sources.factors}"), list.props["data"])
        assertEquals(SpecValue.StringValue("No factors computed yet"), list.props["emptyText"])
        assertTrue(list.props["itemTemplate"] is SpecValue.NodeValue, "itemTemplate must be a nested node")
    }

    @Test
    fun allNodeIdsAreUnique() {
        val ids = collectIds(spec.root)
        assertEquals(ids.size, ids.toSet().size, "node ids must be globally unique")
    }

    @Test
    fun roundTripsThroughJson() {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
        val encoded = json.encodeToString(Spec.serializer(), spec)
        val decoded = json.decodeFromString(Spec.serializer(), encoded)
        assertEquals(spec, decoded)
    }

    private fun collectIds(node: SpecNode): List<String> {
        val nested = (node.props["itemTemplate"] as? SpecValue.NodeValue)?.value?.let { collectIds(it) } ?: emptyList()
        return listOf(node.id) + node.children.flatMap { collectIds(it) } + nested
    }
}
