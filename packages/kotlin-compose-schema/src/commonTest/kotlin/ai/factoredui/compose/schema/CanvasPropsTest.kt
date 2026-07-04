package ai.factoredui.compose.schema

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CanvasPropsTest {

    @Test
    fun edgesAsBindingStringBecomesEdgesBindingNotLiteral() {
        val props = mapOf<String, SpecValue>(
            "nodes" to SpecValue.StringValue("{domain_nodes}"),
            "edges" to SpecValue.StringValue("{domain_edges}"),
        ).asCanvasProps()
        assertEquals("{domain_edges}", props.edgesBinding, "a string edges prop is a live binding, parallel to nodes")
        assertTrue(props.edges.isEmpty(), "a bound edges prop carries no baked literals")
    }

    @Test
    fun literalEdgeArrayStillParsesAndLeavesBindingNull() {
        val props = mapOf<String, SpecValue>(
            "edges" to SpecValue.ArrayValue(
                listOf(
                    SpecValue.ObjectValue(mapOf("from" to SpecValue.StringValue("a"), "to" to SpecValue.StringValue("b"))),
                ),
            ),
        ).asCanvasProps()
        assertNull(props.edgesBinding, "a literal edge array is not a binding")
        assertEquals(listOf(CanvasEdge("a", "b")), props.edges, "baked edges keep parsing — back-compat preserved")
    }

    @Test
    fun resolveCanvasEdgesReadsFromToOffAResolvedListAndDropsMalformed() {
        val resolved = listOf(
            mapOf("from" to "app", "to" to "memory"),
            mapOf("from" to "memory", "to" to "db"),
            mapOf("from" to "app"),
        )
        assertEquals(
            listOf(CanvasEdge("app", "memory"), CanvasEdge("memory", "db")),
            resolveCanvasEdges(resolved),
            "the live-edge resolver mirrors resolveFieldNodeEntries: read {from,to}, drop the malformed entry",
        )
    }

    @Test
    fun viewportStringPropBecomesAHostBoundBinding() {
        val props = mapOf<String, SpecValue>(
            "nodes" to SpecValue.StringValue("{field_nodes}"),
            "viewport" to SpecValue.StringValue("{field_viewport}"),
            "on_viewport_changed" to SpecValue.StringValue("field.viewportChanged"),
        ).asCanvasProps()
        assertEquals("{field_viewport}", props.viewportBinding, "a viewport prop is a live two-way binding")
        assertEquals("field.viewportChanged", props.onViewportChanged, "writeback action carries through")
    }

    @Test
    fun resolveCanvasViewportReadsXYZoom() {
        val resolved = mapOf("x" to 0.25, "y" to 0.5, "zoom" to 3.0)
        assertEquals(CanvasViewport(0.25f, 0.5f, 3.0f), resolveCanvasViewport(resolved), "camera reads {x,y,zoom} off the resolved map")
    }

    @Test
    fun resolveCanvasViewportDefaultsToIdentityAndGuardsNonPositiveZoom() {
        assertEquals(CanvasViewport(0f, 0f, 1f), resolveCanvasViewport(null), "an absent viewport is the identity camera")
        assertEquals(1f, resolveCanvasViewport(mapOf("zoom" to 0.0)).zoom, "zoom <= 0 collapses the field, so it floors to identity 1x")
    }

    @Test
    fun identityCameraMapsNormalizedPositionsStraightToTheCanvas() {
        val t = CanvasViewport(0f, 0f, 1f).transformFor(1000f, 800f)
        assertClose(0f, t.translationX, "the identity camera adds no horizontal translation")
        assertClose(0f, t.translationY, "the identity camera adds no vertical translation")
        assertClose(1f, t.scale, "the identity camera lays [0,1] positions edge-to-edge at 1x")
    }

    @Test
    fun cameraTranslatesAndScalesTheLayerInPixels() {
        val t = CanvasViewport(0.25f, 0.5f, 2f).transformFor(1000f, 800f)
        assertClose(-500f, t.translationX, "translationX = -x * zoom * width")
        assertClose(-800f, t.translationY, "translationY = -y * zoom * height")
        assertClose(2f, t.scale, "scale is the zoom")
    }

    @Test
    fun draggingBackgroundPansTheCameraInNormalizedUnits() {
        val after = CanvasViewport(0f, 0f, 1f).afterTransformGesture(500f, 400f, 100f, 0f, 1f, 1000f, 800f)
        assertClose(-0.1f, after.x, "a +100px drag on a 1000px canvas pans the camera -0.1 in x")
        assertClose(0f, after.y, "no vertical pan leaves y put")
        assertClose(1f, after.zoom, "a pure pan does not change zoom")
    }

    @Test
    fun pinchZoomStaysAnchoredUnderTheGestureCentroid() {
        val after = CanvasViewport(0f, 0f, 1f).afterTransformGesture(500f, 400f, 0f, 0f, 2f, 1000f, 800f)
        assertClose(2f, after.zoom, "the zoom delta multiplies the camera zoom")
        assertClose(0.25f, after.x, "zooming 2x about the center keeps the center point fixed (x)")
        assertClose(0.25f, after.y, "zooming 2x about the center keeps the center point fixed (y)")
    }

    @Test
    fun zoomIsClampedSoTheFieldCannotCollapseOrRunAway() {
        assertEquals(MAX_CANVAS_ZOOM, CanvasViewport(0f, 0f, 10f).afterTransformGesture(0f, 0f, 0f, 0f, 100f, 1000f, 800f).zoom, "zoom caps at MAX_CANVAS_ZOOM")
        assertEquals(MIN_CANVAS_ZOOM, CanvasViewport(0f, 0f, 1f).afterTransformGesture(0f, 0f, 0f, 0f, 0.001f, 1000f, 800f).zoom, "zoom floors at MIN_CANVAS_ZOOM")
    }
}

private fun assertClose(expected: Float, actual: Float, message: String) {
    assertTrue(kotlin.math.abs(expected - actual) < 1e-3f, "$message (expected ~$expected, was $actual)")
}
