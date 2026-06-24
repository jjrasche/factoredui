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
import ai.factoredui.compose.fieldgraph.graph.FieldLogItem
import ai.factoredui.compose.fieldgraph.graph.FieldNode
import ai.factoredui.compose.fieldgraph.render.FieldGraphView
import ai.factoredui.compose.testing.CheckRunner
import ai.factoredui.compose.testing.FakeEngineClient
import ai.factoredui.compose.testing.SpecInteractor
import ai.factoredui.compose.testing.check
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class FieldLogCheck {

    private val canvasDp = 400

    private val topology = FieldGraphTopology(
        nodes = listOf(
            FieldNode(id = "claim-field-a", group = "claim", label = "Field claim A"),
            FieldNode(id = "claim-field-b", group = "claim", label = "Field claim B"),
        ),
        edges = emptyList(),
        logItems = listOf(
            FieldLogItem(id = "log-item-1", label = "Old memory about Marcus", entityName = "Marcus", ageSecs = 3600f, placedAtMs = 2_000_000L),
            FieldLogItem(id = "log-item-2", label = "Jake startup claim", entityName = "Jake", ageSecs = 7200f, placedAtMs = 1_000_000L),
            FieldLogItem(id = "log-item-3", label = "Newest ejected claim", entityName = "Claire", ageSecs = 300f, placedAtMs = 3_000_000L),
        ),
    )

    private val logItemsAppearInDomShadow = check("log-items-appear-in-dom-shadow") {
        awaitLogItemPresent("log-item-1")
        expectLogItemPresent("log-item-1")
        expectLogItemPresent("log-item-2")
        expectLogItemPresent("log-item-3")
    }

    private val tapLogItemFiresDetailAction = check("tap-log-item-fires-detail-action") {
        awaitLogItemPresent("log-item-1")
        tapLogItem("log-item-1")
        expectActionFiredWithNodeId("field.logItemTapped", "log-item-1")
        expectActionNotFired("field.logItemPlacedOnField")
    }

    private val dragLogItemToFieldFiresPlaceAction = check("drag-log-item-to-field-fires-place-action") {
        awaitLogItemPresent("log-item-2")
        dragLogItemToField("log-item-2", toX = 0.5f, toY = 0.5f)
        expectActionFiredWithNodeId("field.logItemPlacedOnField", "log-item-2")
        expectActionNotFired("field.logItemTapped")
    }

    private val collapseLogHidesItems = check("collapse-log-hides-items") {
        awaitLogItemPresent("log-item-1")
        expectLogColumnCollapsed(false)
        toggleLogColumn()
        expectLogColumnCollapsed(true)
        expectLogItemAbsent("log-item-1")
    }

    private val expandLogAfterCollapseShowsItems = check("expand-log-after-collapse-shows-items") {
        awaitLogItemPresent("log-item-1")
        toggleLogColumn()
        expectLogColumnCollapsed(true)
        toggleLogColumn()
        expectLogColumnCollapsed(false)
        expectLogItemPresent("log-item-1")
    }

    private val dragFieldNodeNearLeftEdgeAutoExpandsLog = check("drag-field-node-near-left-edge-auto-expands-log") {
        awaitFieldNode("claim-field-a")
        toggleLogColumn()
        expectLogColumnCollapsed(true)
        holdFieldNodeNearLeftEdge("claim-field-a", holdSecs = 2f)
        expectLogColumnCollapsed(false)
    }

    // Negative control: a hold shorter than AUTO_EXPAND_HOLD_MS (1500ms) must NOT expand —
    // proves the auto-expand is gated on dwell time, not on merely touching the edge.
    private val shortHoldNearLeftEdgeDoesNotExpandLog = check("short-hold-near-left-edge-does-not-expand-log") {
        awaitFieldNode("claim-field-a")
        toggleLogColumn()
        expectLogColumnCollapsed(true)
        holdFieldNodeNearLeftEdge("claim-field-a", holdSecs = 1f)
        expectLogColumnCollapsed(true)
    }

    private val dragFieldNodeToLogEjectsIt = check("drag-field-node-to-log-ejects-it") {
        awaitFieldNode("claim-field-a")
        dragFieldNodeToLog("claim-field-a")
        expectActionFiredWithNodeId("field.nodeEjectedToLog", "claim-field-a")
    }

    private val logItemsOrderedByPlacedAtMsDesc = check("log-items-ordered-by-placed-at-ms-desc") {
        awaitLogItemPresent("log-item-1")
        expectLogItemOrder("log-item-3", "log-item-1", "log-item-2")
    }

    private val tapFieldNodeInLogContextStillFiresDetail = check("tap-field-node-in-log-context-still-fires-detail") {
        awaitFieldNode("claim-field-a")
        tapFieldNode("claim-field-a")
        expectActionFired("on_node_tap", mapOf("node_id" to "claim-field-a"))
    }

    @Test
    fun runLogItemsAppearInDomShadow() = runLogCheck(logItemsAppearInDomShadow)

    @Test
    fun runTapLogItemFiresDetailAction() = runLogCheck(tapLogItemFiresDetailAction, trackLogActions = true)

    @Test
    fun runDragLogItemToFieldFiresPlaceAction() = runLogCheck(dragLogItemToFieldFiresPlaceAction, trackLogActions = true)

    @Test
    fun runCollapseLogHidesItems() = runLogCheck(collapseLogHidesItems, trackLogActions = true)

    @Test
    fun runExpandLogAfterCollapseShowsItems() = runLogCheck(expandLogAfterCollapseShowsItems, trackLogActions = true)

    @Test
    fun runDragFieldNodeNearLeftEdgeAutoExpandsLog() = runLogCheck(dragFieldNodeNearLeftEdgeAutoExpandsLog, trackLogActions = true)

    @Test
    fun runShortHoldNearLeftEdgeDoesNotExpandLog() = runLogCheck(shortHoldNearLeftEdgeDoesNotExpandLog, trackLogActions = true)

    @Test
    fun runDragFieldNodeToLogEjectsIt() = runLogCheck(dragFieldNodeToLogEjectsIt, trackLogActions = true)

    @Test
    fun runLogItemsOrderedByPlacedAtMsDesc() = runLogCheck(logItemsOrderedByPlacedAtMsDesc)

    @Test
    fun runTapFieldNodeInLogContextStillFiresDetail() = runLogCheck(tapFieldNodeInLogContextStillFiresDetail, trackTaps = true)

    private fun runLogCheck(
        checkScript: ai.factoredui.compose.testing.Check,
        trackTaps: Boolean = false,
        trackDrags: Boolean = false,
        trackLogActions: Boolean = false,
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
                            if (trackTaps || trackLogActions) interact.recordAction("on_node_tap", mapOf("node_id" to id))
                        },
                        onNodeDragComplete = { id, magnitude ->
                            if (trackDrags) interact.recordDragComplete(id, magnitude)
                        },
                        onLogItemTap = { id ->
                            if (trackLogActions) interact.recordAction("field.logItemTapped", mapOf("node_id" to id))
                        },
                        onLogItemToField = { id, x, y ->
                            if (trackLogActions) interact.recordAction(
                                "field.logItemPlacedOnField",
                                mapOf("node_id" to id, "x_fraction" to x.toString(), "y_fraction" to y.toString()),
                            )
                        },
                        onNodeToLog = { id ->
                            if (trackLogActions) interact.recordAction("field.nodeEjectedToLog", mapOf("node_id" to id))
                        },
                        onLogToggle = { collapsed ->
                            if (trackLogActions) interact.recordAction("field.logToggled", mapOf("collapsed" to collapsed.toString()))
                        },
                    )
                }
            }
        }
        waitForIdle()
        CheckRunner(interact, engine, state).run(checkScript)
    }
}
