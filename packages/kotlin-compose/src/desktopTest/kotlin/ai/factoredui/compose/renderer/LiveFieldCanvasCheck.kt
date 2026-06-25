package ai.factoredui.compose.renderer

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import ai.factoredui.compose.adapter.ActionHandler
import ai.factoredui.compose.schema.SpecNode
import ai.factoredui.compose.schema.SpecNodeType
import ai.factoredui.compose.schema.SpecValue
import ai.factoredui.compose.testing.SpecVisualCheck
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class LiveFieldCanvasCheck {

    private fun liveFieldNodes(): List<Map<String, Any?>> = listOf(
        mapOf("id" to "claim-7", "x" to 30.0, "y" to 40.0, "glow" to 0.9, "label" to "Knee pain"),
        mapOf("id" to "claim-9", "x" to 180.0, "y" to 60.0, "glow" to 0.3, "label" to "Lateral lurch"),
    )

    private val liveField = SpecNode(
        id = "field",
        type = SpecNodeType.CANVAS,
        props = mapOf(
            "nodes" to SpecValue.StringValue("{field_nodes}"),
            "on_node_arranged" to SpecValue.StringValue("field.nodeArranged"),
        ),
    )

    @Test
    fun canvasMapsTheBoundNodesArrayToPositionedChildren() = runComposeUiTest {
        val context = RenderContext(initialData = mapOf("field_nodes" to liveFieldNodes()))
        val check = SpecVisualCheck(this, context)
        check.render(liveField, viewport = 400.dp)
        check.assertRenderedToPixels()
        check.assertPresent("claim-7")
        check.assertPresent("claim-9")
        check.assertLeftOf("claim-7", "claim-9")
    }

    @Test
    fun draggingALiveNodeFiresNodeArrangedWithIdAndFinalPosition() = runComposeUiTest {
        var arrangedId: String? = null
        var arrangedX = 0.0
        val capture: ActionHandler = { params ->
            arrangedId = params["node_id"] as? String
            arrangedX = (params["x"] as? Number)?.toDouble() ?: 0.0
        }
        val context = RenderContext(
            actions = mapOf("field.nodeArranged" to capture),
            initialData = mapOf("field_nodes" to liveFieldNodes()),
        )
        val check = SpecVisualCheck(this, context)
        check.render(liveField, viewport = 400.dp)
        check.drag("claim-7", 50f, 30f)
        assertEquals("claim-7", arrangedId, "drag-end must fire on_node_arranged with the dragged node id")
        assertTrue(arrangedX > 30.0, "the action must carry the node's curated final x (was 30, dragged +50)")
    }
}
