package ai.factoredui.compose.renderer

import ai.factoredui.compose.schema.Spec
import ai.factoredui.compose.testing.SpecVisualCheck
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json

@OptIn(ExperimentalTestApi::class)
class GraphExplorerSpecTest {

    private val spec = Json { ignoreUnknownKeys = true }
        .decodeFromString(Spec.serializer(), File("examples/graph-explorer.spec.json").readText())

    private val hostData: Map<String, Any?> = mapOf(
        "nodes" to listOf(
            mapOf(
                "id" to "rain", "label" to "rain", "group" to "soil", "status" to "implemented", "kind" to "state",
                "unit" to "mm", "stock_flow_role" to "stock",
                "timescale" to mapOf("step" to 1.0, "unit" to "hour", "basis" to "implemented", "reason" to "stepped hourly"),
            ),
            mapOf("id" to "growth", "label" to "growth", "group" to "soil", "status" to "missing", "kind" to "state"),
        ),
        "edges" to listOf(
            mapOf(
                "from" to "rain", "to" to "growth", "kind" to "equation", "status" to "implemented", "via" to "eq-growth",
                "coupling" to mapOf("method" to "hold", "reason" to "reads the day's rain every hour"),
            ),
        ),
        "group_order" to listOf("soil"),
        "status_colors" to mapOf("implemented" to "#3FA34D", "missing" to "#C0392B"),
        "kind_styles" to emptyMap<String, Any?>(),
        "status_outlines" to mapOf("missing" to "dashed"),
        "legend" to emptyList<Any?>(),
    )

    private fun SpecVisualCheck.isShown(nodeId: String) = runCatching { region(nodeId) }.isSuccess

    @Test
    fun theExplorerOpensWithNoCardAndTappingANodeShowsItsFields() = runComposeUiTest {
        val check = SpecVisualCheck(this, RenderContext(initialData = hostData))
        check.render(spec.root, viewport = 600.dp)
        assertTrue(!check.isShown("node-card"), "no card before anything is selected")
        check.tap("rain")
        assertTrue(check.isShown("node-card"), "tapping a node reveals the node card")
        assertEquals("rain", check.node("node-title").props["value"])
        assertEquals("id rain  ·  kind state  ·  status implemented  ·  group soil", check.node("node-ident").props["value"])
        assertEquals("time scale 1 hour  (implemented)", check.node("node-scale").props["value"].toString().replace("1.0", "1"))
    }

    @Test
    fun tappingAnEdgeShowsItsCouplingAndHidesTheNodeCard() = runComposeUiTest {
        val check = SpecVisualCheck(this, RenderContext(initialData = hostData))
        check.render(spec.root, viewport = 600.dp)
        check.tap("rain")
        val rain = check.region("rain")
        val growth = check.region("growth")
        check.tapAt("flow", ((rain.right + growth.left) / 2).value, ((rain.top + rain.bottom) / 2 + 4.dp).value)
        assertTrue(check.isShown("edge-card"), "tapping an edge reveals the edge card")
        assertTrue(!check.isShown("node-card"), "and the node card goes away")
        assertTrue((check.node("edge-coupling").props["value"] as String).contains("coupling hold"))
        assertEquals("reads the day's rain every hour", check.node("edge-coupling-reason").props["value"])
    }
}
