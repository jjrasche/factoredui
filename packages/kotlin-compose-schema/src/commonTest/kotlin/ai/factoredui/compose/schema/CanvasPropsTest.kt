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
    fun pathsAsBindingStringBecomesPathsBindingParallelToNodesAndEdges() {
        val props = mapOf<String, SpecValue>(
            "nodes" to SpecValue.StringValue("{field_nodes}"),
            "edges" to SpecValue.StringValue("{field_edges}"),
            "paths" to SpecValue.StringValue("{field_trails}"),
        ).asCanvasProps()
        assertEquals("{field_trails}", props.pathsBinding, "a string paths prop is a live binding, parallel to nodes+edges — the compass trail layer")
    }

    @Test
    fun absentPathsBindingStaysNull() {
        val props = mapOf<String, SpecValue>("nodes" to SpecValue.StringValue("{field_nodes}")).asCanvasProps()
        assertNull(props.pathsBinding, "a canvas with no paths prop carries no trail layer")
    }

    @Test
    fun resolveCanvasPathsReadsOrderedPointsAndColorPerPath() {
        val resolved = listOf(
            mapOf(
                "id" to "user",
                "color" to "#3B82F6",
                "points" to listOf(mapOf("x" to 0.1, "y" to 0.1), mapOf("x" to 0.4, "y" to 0.3), mapOf("x" to 0.6, "y" to 0.5)),
            ),
            mapOf(
                "id" to "advisor",
                "color" to "#F59E0B",
                "points" to listOf(mapOf("x" to 0.2, "y" to 0.8), mapOf("x" to 0.5, "y" to 0.7)),
            ),
        )
        val paths = resolveCanvasPaths(resolved)
        assertEquals(2, paths.size, "two overlaid trails resolve — the multi-path compass (user vs advisor)")
        assertEquals("user", paths[0].id)
        assertEquals("#3B82F6", paths[0].color, "each trail keeps its own color so the two are distinguishable")
        assertEquals(3, paths[0].points.size, "the ordered comet-tail keeps every recent point")
        assertEquals(CanvasPathPoint(0.6f, 0.5f), paths[0].points.last(), "the LAST point is the head (newest = where the cursor is now)")
    }

    @Test
    fun resolveCanvasPathsDropsAPathMissingItsIdAndPointsMissingCoords() {
        val resolved = listOf(
            mapOf("color" to "#fff", "points" to listOf(mapOf("x" to 0.1, "y" to 0.1))),
            mapOf(
                "id" to "ok",
                "points" to listOf(mapOf("x" to 0.2, "y" to 0.2), mapOf("y" to 0.9)),
            ),
        )
        val paths = resolveCanvasPaths(resolved)
        assertEquals(1, paths.size, "a path with no id is dropped, mirroring resolveFieldNodeEntries")
        assertEquals(1, paths[0].points.size, "a point missing a coord is dropped, the rest of the trail survives")
        assertEquals(DEFAULT_PATH_COLOR, paths[0].color, "a path with no color falls back to the neutral default rather than dropping")
    }

    @Test
    fun trailFadesFromDimTailToFullBrightHead() {
        val segmentCount = 4
        val tail = trailSegmentAlpha(0, segmentCount)
        val head = trailSegmentAlpha(segmentCount - 1, segmentCount)
        assertTrue(tail < head, "the oldest segment must be dimmer than the newest — the comet-tail fade")
        assertClose(1f, head, "the head segment reaches full opacity — the cursor is where the motion is now")
        assertTrue(tail >= TRAIL_MIN_ALPHA, "even the oldest segment stays at least faintly visible, never fully transparent")
        for (i in 1 until segmentCount) {
            assertTrue(trailSegmentAlpha(i, segmentCount) > trailSegmentAlpha(i - 1, segmentCount), "opacity rises monotonically toward the head")
        }
    }

    @Test
    fun singlePointTrailIsFullyOpaque() {
        assertClose(1f, trailSegmentAlpha(0, 0), "a lone head point (no segments) draws at full opacity, not divided by zero")
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
