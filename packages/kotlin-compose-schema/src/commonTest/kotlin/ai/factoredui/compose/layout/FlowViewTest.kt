package ai.factoredui.compose.layout

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FlowViewTest {

    private fun assertNear(expected: Float, actual: Float, message: String) =
        assertTrue(abs(expected - actual) < 0.01f, "$message: expected $expected, got $actual")

    @Test
    fun aWideGraphShrinksUntilItsWidthFitsAndIsCenteredVertically() {
        val view = fitFlowView(contentWidth = 1000f, contentHeight = 500f, viewWidth = 500f, viewHeight = 500f, margin = 8f)
        assertNear(0.484f, view.scale, "scale is bound by width less margins")
        assertNear(8f, view.translateX, "left margin")
        assertNear((500f - 500f * view.scale) / 2f, view.translateY, "centred vertically")
    }

    @Test
    fun aTallGraphShrinksUntilItsHeightFits() {
        val view = fitFlowView(contentWidth = 400f, contentHeight = 1600f, viewWidth = 800f, viewHeight = 800f, margin = 0f)
        assertNear(0.5f, view.scale, "height bound")
        assertNear(300f, view.translateX, "centred horizontally")
    }

    @Test
    fun aSmallGraphIsNotBlownUpPastTheMaximumScale() {
        val view = fitFlowView(contentWidth = 100f, contentHeight = 50f, viewWidth = 2000f, viewHeight = 2000f, maxScale = 1.5f, margin = 0f)
        assertEquals(1.5f, view.scale)
    }

    @Test
    fun theFittedGraphLiesInsideTheView() {
        val view = fitFlowView(contentWidth = 1234f, contentHeight = 777f, viewWidth = 640f, viewHeight = 480f, margin = 10f)
        assertTrue(view.translateX >= 0f && view.translateY >= 0f)
        assertTrue(view.translateX + 1234f * view.scale <= 640f + 0.01f)
        assertTrue(view.translateY + 777f * view.scale <= 480f + 0.01f)
    }

    @Test
    fun zoomingKeepsTheContentPointUnderTheCursorFixed() {
        val start = FlowView(scale = 0.6f, translateX = 40f, translateY = 25f)
        val cursorX = 300f
        val cursorY = 200f
        val worldX = (cursorX - start.translateX) / start.scale
        val worldY = (cursorY - start.translateY) / start.scale
        val zoomed = start.afterGesture(cursorX, cursorY, panX = 0f, panY = 0f, zoomDelta = 1.7f)
        assertNear(cursorX, zoomed.translateX + worldX * zoomed.scale, "x stays under the cursor")
        assertNear(cursorY, zoomed.translateY + worldY * zoomed.scale, "y stays under the cursor")
        assertNear(1.02f, zoomed.scale, "scale multiplied")
    }

    @Test
    fun aPureDragMovesTheContentByTheDragAndLeavesScaleAlone() {
        val moved = FlowView(1f, 10f, 20f).afterGesture(0f, 0f, panX = 30f, panY = -5f, zoomDelta = 1f)
        assertEquals(1f, moved.scale)
        assertEquals(40f, moved.translateX)
        assertEquals(15f, moved.translateY)
    }

    @Test
    fun zoomIsClampedBothWays() {
        val tooFar = FlowView(1f, 0f, 0f).afterGesture(0f, 0f, 0f, 0f, zoomDelta = 1000f)
        val tooNear = FlowView(1f, 0f, 0f).afterGesture(0f, 0f, 0f, 0f, zoomDelta = 0.00001f)
        assertEquals(MAX_FLOW_SCALE, tooFar.scale)
        assertEquals(MIN_FLOW_SCALE, tooNear.scale)
    }
}
