package ai.factoredui.compose.fieldgraph

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import ai.factoredui.compose.fieldgraph.graph.FieldEdge
import ai.factoredui.compose.fieldgraph.graph.FieldGraphState
import ai.factoredui.compose.fieldgraph.graph.FieldGraphTopology
import ai.factoredui.compose.fieldgraph.graph.FieldNode
import ai.factoredui.compose.fieldgraph.render.FieldGraphView
import ai.factoredui.compose.testing.DomShadow
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class FieldGraphViewGestureTest {

    private val canvasSizeDp = 400
    private val canvasPx = canvasSizeDp.toFloat()
    private val cx = canvasPx / 2f
    private val cy = canvasPx / 2f
    private val maxR = minOf(cx, cy) * 0.88f

    private fun singleClaimTopology() = FieldGraphTopology(
        nodes = listOf(FieldNode(id = "clm-0", group = "claim", label = "Test claim")),
        edges = emptyList(),
    )

    private fun claimAndEntityTopology() = FieldGraphTopology(
        nodes = listOf(
            FieldNode(id = "clm-0", group = "claim", label = "Claim about subject"),
            FieldNode(id = "ent-0", group = "entity", label = "Subject"),
        ),
        edges = listOf(FieldEdge(fromId = "clm-0", toId = "ent-0", kind = "describes")),
    )

    private fun nodePixelPos(nodeId: String): Pair<Float, Float>? {
        val entry = DomShadow.byRole("field-node").firstOrNull { it.attrs["node-id"] == nodeId }
            ?: return null
        val angle = entry.attrs["angle"]?.toFloatOrNull() ?: return null
        val rf = entry.attrs["radius-fraction"]?.toFloatOrNull() ?: return null
        return Pair(cx + cos(angle) * rf * maxR, cy + sin(angle) * rf * maxR)
    }

    @Test
    fun domShadowEmitsClaimNodePositionAfterComposition() = runComposeUiTest {
        val state = FieldGraphState(singleClaimTopology())
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                Box(Modifier.size(canvasSizeDp.dp)) {
                    FieldGraphView(graphState = state, reduceMotion = true)
                }
            }
        }
        waitForIdle()

        val entry = DomShadow.byRole("field-node").firstOrNull { it.attrs["node-id"] == "clm-0" }
        assertNotNull(entry, "FieldGraphView must emit DomShadow entry for every claim node")
        assertNotNull(entry.attrs["angle"])
        assertNotNull(entry.attrs["radius-fraction"])
        assertEquals("claim", entry.attrs["group"])
    }

    @Test
    fun entityNodeAppearsInDomShadowWithEntityGroup() = runComposeUiTest {
        val state = FieldGraphState(claimAndEntityTopology())
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                Box(Modifier.size(canvasSizeDp.dp)) {
                    FieldGraphView(graphState = state, reduceMotion = true)
                }
            }
        }
        waitForIdle()

        val entity = DomShadow.byRole("field-node").firstOrNull { it.attrs["node-id"] == "ent-0" }
        assertNotNull(entity)
        assertEquals("entity", entity.attrs["group"])
    }

    // Tap regression: will be RED until Bug1 (isTap uses node rf not pointer delta)
    // and Bug2 (detectDragGestures drops pure taps) are fixed in FieldGraphView.
    @Test
    fun tapOnClaimNodeFiresOnNodeTap() = runComposeUiTest {
        val tapped = mutableListOf<String>()
        val state = FieldGraphState(singleClaimTopology())
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                Box(Modifier.size(canvasSizeDp.dp)) {
                    FieldGraphView(
                        graphState = state,
                        reduceMotion = true,
                        onNodeTap = { id -> tapped += id },
                    )
                }
            }
        }
        waitForIdle()

        val (nx, ny) = nodePixelPos("clm-0") ?: error("DomShadow must have clm-0 position")
        onRoot().performTouchInput { down(Offset(nx, ny)); up() }
        waitForIdle()

        assertTrue(tapped.isNotEmpty(), "onNodeTap must fire when a claim node is tapped")
        assertEquals("clm-0", tapped.first())
    }

    @Test
    fun dragClaimNodeTowardCenterFiresDragComplete() = runComposeUiTest {
        val completed = mutableListOf<Pair<String, Float>>()
        val state = FieldGraphState(singleClaimTopology())
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                Box(Modifier.size(canvasSizeDp.dp)) {
                    FieldGraphView(
                        graphState = state,
                        reduceMotion = true,
                        onNodeDragComplete = { id, magnitude -> completed += id to magnitude },
                    )
                }
            }
        }
        waitForIdle()

        val (nx, ny) = nodePixelPos("clm-0") ?: error("DomShadow must have clm-0 position")
        onRoot().performTouchInput { down(Offset(nx, ny)); moveTo(Offset(cx, cy)); up() }
        waitForIdle()

        assertTrue(completed.isNotEmpty(), "onNodeDragComplete must fire after dragging to center")
        val (nodeId, magnitude) = completed.first()
        assertEquals("clm-0", nodeId)
        assertTrue(magnitude > 0.8f, "dragging to center yields high relevance, got $magnitude")
    }
}
