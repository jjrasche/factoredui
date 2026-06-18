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
import ai.factoredui.compose.fieldgraph.graph.FieldEdge
import ai.factoredui.compose.fieldgraph.graph.FieldGraphState
import ai.factoredui.compose.fieldgraph.graph.FieldGraphTopology
import ai.factoredui.compose.fieldgraph.graph.FieldNode
import ai.factoredui.compose.fieldgraph.render.FieldGraphView
import ai.factoredui.compose.testing.CheckRunner
import ai.factoredui.compose.testing.FakeEngineClient
import ai.factoredui.compose.testing.SpecInteractor
import ai.factoredui.compose.testing.check
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class FieldGraphCheck {

    private val canvasDp = 400
    private val topology = FieldGraphTopology(
        nodes = listOf(
            FieldNode(id = "claim-pain", group = "pain", label = "Knee pain on impact"),
            FieldNode(id = "claim-motion", group = "motion", label = "Lateral lurch pattern"),
            FieldNode(id = "entity-knee", group = "entity", label = "Knee"),
        ),
        edges = listOf(
            FieldEdge(fromId = "claim-pain", toId = "entity-knee", kind = "describes"),
            FieldEdge(fromId = "claim-motion", toId = "entity-knee", kind = "describes"),
        ),
    )

    private val claimNodesAppear = check("claim-nodes-appear") {
        awaitFieldNode("claim-pain")
        awaitFieldNode("claim-motion")
        expectFieldNodePresent("claim-pain")
        expectFieldNodePresent("claim-motion")
    }

    private val entityNodeRejectsTap = check("entity-node-rejects-tap") {
        awaitFieldNode("entity-knee")
        tapFieldNode("entity-knee")
        expectNoActionFired()
    }

    private val tapClaimFiresAction = check("tap-claim-fires-on-node-tap") {
        awaitFieldNode("claim-pain")
        tapFieldNode("claim-pain")
        expectActionFired("on_node_tap", mapOf("node_id" to "claim-pain"))
    }

    private val dragClaimTowardCenterRanksRelevance = check("drag-claim-ranks-relevance") {
        awaitFieldNode("claim-motion")
        dragFieldNodeToCenter("claim-motion")
        expectDragMagnitude("claim-motion", min = 0.8f)
    }

    @Test
    fun runClaimNodesAppear() = runCheck(claimNodesAppear)

    @Test
    fun runEntityNodeRejectsTap() = runCheck(entityNodeRejectsTap, trackTaps = true)

    @Test
    fun runTapClaimFiresAction() = runCheck(tapClaimFiresAction, trackTaps = true)

    @Test
    fun runDragClaimRanksRelevance() = runCheck(dragClaimTowardCenterRanksRelevance, trackDrags = true)

    private fun runCheck(
        checkScript: ai.factoredui.compose.testing.Check,
        trackTaps: Boolean = false,
        trackDrags: Boolean = false,
    ) = runComposeUiTest {
        val state = FieldGraphState(topology)
        val interact = SpecInteractor(this, canvasWidthPx = canvasDp.toFloat(), canvasHeightPx = canvasDp.toFloat())
        val engine = FakeEngineClient()

        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                Box(Modifier.size(canvasDp.dp)) {
                    FieldGraphView(
                        graphState = state,
                        reduceMotion = true,
                        onNodeTap = { id ->
                            if (trackTaps) interact.recordAction("on_node_tap", mapOf("node_id" to id))
                        },
                        onNodeDragComplete = { id, magnitude ->
                            if (trackDrags) interact.recordDragComplete(id, magnitude)
                        },
                    )
                }
            }
        }
        waitForIdle()

        CheckRunner(interact, engine).run(checkScript)
    }
}
