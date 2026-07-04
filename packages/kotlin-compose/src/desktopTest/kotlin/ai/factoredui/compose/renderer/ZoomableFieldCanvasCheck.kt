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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ZoomableFieldCanvasCheck {

    private fun atlasNodes(): List<Map<String, Any?>> = listOf(
        mapOf("id" to "claim-near", "x" to 0.15, "y" to 0.2, "glow" to 0.9, "label" to "near"),
        mapOf("id" to "claim-far", "x" to 0.8, "y" to 0.6, "glow" to 0.5, "label" to "far"),
    )

    private val zoomableField = SpecNode(
        id = "atlas",
        type = SpecNodeType.CANVAS,
        props = mapOf(
            "nodes" to SpecValue.StringValue("{field_nodes}"),
            "viewport" to SpecValue.StringValue("{camera}"),
            "on_viewport_changed" to SpecValue.StringValue("field.viewportChanged"),
        ),
    )

    @Test
    fun aBoundViewportPlacesNodesInNormalizedSpaceNotRawDp() = runComposeUiTest {
        val context = RenderContext(
            initialData = mapOf(
                "field_nodes" to atlasNodes(),
                "camera" to mapOf("x" to 0.0, "y" to 0.0, "zoom" to 1.0),
            ),
        )
        val check = SpecVisualCheck(this, context)
        check.render(zoomableField, viewport = 400.dp)
        check.assertRenderedToPixels()
        check.assertPresent("claim-near")
        check.assertPresent("claim-far")
        check.assertLeftOf("claim-near", "claim-far")
        assertTrue(
            check.region("claim-far").left > 200.dp,
            "x=0.8 on a 400dp canvas must land near 320dp (normalized), not ~0.8dp (raw-dp path)",
        )
    }

    @Test
    fun draggingTheBackgroundPansTheCameraAndFiresViewportChanged() = runComposeUiTest {
        var firedX: Double? = null
        var firedZoom: Double? = null
        val capture: ActionHandler = { params ->
            firedX = (params["x"] as? Number)?.toDouble()
            firedZoom = (params["zoom"] as? Number)?.toDouble()
        }
        val context = RenderContext(
            actions = mapOf("field.viewportChanged" to capture),
            initialData = mapOf(
                "field_nodes" to atlasNodes(),
                "camera" to mapOf("x" to 0.0, "y" to 0.0, "zoom" to 1.0),
            ),
        )
        val check = SpecVisualCheck(this, context)
        check.render(zoomableField, viewport = 400.dp)
        check.drag("atlas", 120f, 0f)
        assertNotNull(firedX, "a background pan must fire on_viewport_changed")
        assertTrue(firedX!! < 0.0, "dragging content to the right moves the camera to a negative x")
        assertEquals(1.0, firedZoom, "a pure pan leaves zoom at 1x")
    }
}
