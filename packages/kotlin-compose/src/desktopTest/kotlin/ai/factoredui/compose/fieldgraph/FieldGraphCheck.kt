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
import ai.factoredui.compose.testing.SpecInteractor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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

    @Test
    fun claimNodeAppearsInField() = runComposeUiTest {
        val state = FieldGraphState(topology)
        val interact = SpecInteractor(this, canvasWidthPx = canvasDp.toFloat(), canvasHeightPx = canvasDp.toFloat())
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                Box(Modifier.size(canvasDp.dp)) {
                    FieldGraphView(graphState = state, reduceMotion = true)
                }
            }
        }
        waitForIdle()
        interact.assertFieldNodePresent("claim-pain")
        interact.assertFieldNodePresent("claim-motion")
    }

    @Test
    fun entityNodeDoesNotReceiveTap() = runComposeUiTest {
        val tapped = mutableListOf<String>()
        val state = FieldGraphState(topology)
        val interact = SpecInteractor(this, canvasWidthPx = canvasDp.toFloat(), canvasHeightPx = canvasDp.toFloat())
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                Box(Modifier.size(canvasDp.dp)) {
                    FieldGraphView(
                        graphState = state,
                        reduceMotion = true,
                        onNodeTap = { id -> tapped += id },
                    )
                }
            }
        }
        waitForIdle()
        interact.tapFieldNode("entity-knee")
        waitForIdle()
        assertTrue(tapped.isEmpty(), "entity nodes must not fire onNodeTap")
    }

    @Test
    fun tapClaimNodeFiresOnNodeTap() = runComposeUiTest {
        val tapped = mutableListOf<String>()
        val state = FieldGraphState(topology)
        val interact = SpecInteractor(this, canvasWidthPx = canvasDp.toFloat(), canvasHeightPx = canvasDp.toFloat())
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                Box(Modifier.size(canvasDp.dp)) {
                    FieldGraphView(
                        graphState = state,
                        reduceMotion = true,
                        onNodeTap = { id -> tapped += id },
                    )
                }
            }
        }
        waitForIdle()
        interact.tapFieldNode("claim-pain")
        waitForIdle()
        assertTrue(tapped.isNotEmpty(), "tapping a claim node must fire onNodeTap")
        assertEquals("claim-pain", tapped.first())
    }

    @Test
    fun dragClaimNodeTowardCenterIncreasesRelevance() = runComposeUiTest {
        val completed = mutableListOf<Pair<String, Float>>()
        val state = FieldGraphState(topology)
        val interact = SpecInteractor(this, canvasWidthPx = canvasDp.toFloat(), canvasHeightPx = canvasDp.toFloat())
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                Box(Modifier.size(canvasDp.dp)) {
                    FieldGraphView(
                        graphState = state,
                        reduceMotion = true,
                        onNodeDragComplete = { id, magnitude -> completed += id to magnitude },
                    )
                }
            }
        }
        waitForIdle()
        interact.dragFieldNodeToCenter("claim-motion")
        waitForIdle()
        assertTrue(completed.isNotEmpty(), "dragging a claim node must fire onNodeDragComplete")
        val (nodeId, magnitude) = completed.first()
        assertEquals("claim-motion", nodeId)
        assertTrue(magnitude > 0.8f, "dragging to center yields high relevance magnitude, got $magnitude")
    }
}
