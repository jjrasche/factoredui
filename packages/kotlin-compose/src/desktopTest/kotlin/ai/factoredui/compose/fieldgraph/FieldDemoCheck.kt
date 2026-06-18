package ai.factoredui.compose.fieldgraph

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import ai.factoredui.compose.fieldgraph.graph.FieldGraphState
import ai.factoredui.compose.fieldgraph.graph.FieldGraphTopology
import ai.factoredui.compose.fieldgraph.render.FieldGraphView
import ai.factoredui.compose.testing.CheckAssertion
import ai.factoredui.compose.testing.CheckRunner
import ai.factoredui.compose.testing.EngineClient
import ai.factoredui.compose.testing.LiveEngineClient
import ai.factoredui.compose.testing.SpecInteractor
import ai.factoredui.compose.testing.check
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.assertNotNull

fun fieldDemoCheck(firstClaimId: String, secondClaimId: String = firstClaimId) =
    check("field-live-demo") {
        awaitFieldNode(firstClaimId)
        tapFieldNode(firstClaimId)
        expectActionFired("on_node_tap", mapOf("node_id" to firstClaimId))
        dragFieldNodeToCenter(secondClaimId)
        expectDragMagnitude(secondClaimId, min = 0.5f)
        expect(CheckAssertion.EngineState("/field/relevance") { it != null })
        advanceTime(43200f)
        expectFieldNodeAgeSecs(firstClaimId, minSecs = 43200f)
        expectFieldNodePresent(firstClaimId)
    }

@OptIn(ExperimentalTestApi::class)
fun runFieldDemo(engineBaseUrl: String) {
    val json = Json { ignoreUnknownKeys = true }
    val liveClient = LiveEngineClient(engineBaseUrl)
    try {
        val topologyBody = runBlocking { liveClient.query("/memory/field-topology")?.toString() }
            ?: error("Could not fetch topology from $engineBaseUrl/memory/field-topology")

        val topology = FieldGraphTopology.fromJson(json, topologyBody)
        val claims = topology.nodes.filter { it.group != "entity" }
        assertNotNull(claims.firstOrNull(), "Live engine must have at least one claim node")

        val firstId = claims.first().id
        val secondId = claims.getOrElse(1) { claims.first() }.id

        runComposeUiTest {
            val state = FieldGraphState(topology)
            val interact = SpecInteractor(this, canvasWidthPx = 400f, canvasHeightPx = 400f)

            setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f)) {
                    Box(Modifier.size(400.dp)) {
                        FieldGraphView(
                            graphState = state,
                            reduceMotion = true,
                            onNodeTap = { id -> interact.recordAction("on_node_tap", mapOf("node_id" to id)) },
                            onNodeDragComplete = { id, magnitude ->
                                interact.recordDragComplete(id, magnitude)
                                runBlocking {
                                    liveClient.post("/field/relevance", mapOf("nodeId" to id, "magnitude" to magnitude.toDouble()))
                                }
                            },
                        )
                    }
                }
            }
            waitForIdle()

            CheckRunner(interact, liveClient, state).run(fieldDemoCheck(firstId, secondId))
        }
    } finally {
        liveClient.close()
    }
}
