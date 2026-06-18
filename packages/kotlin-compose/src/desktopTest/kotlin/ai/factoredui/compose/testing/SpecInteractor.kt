package ai.factoredui.compose.testing

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.graphics.toPixelMap
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class SpecInteractor(
    private val scope: ComposeUiTest,
    private val canvasWidthPx: Float = 400f,
    private val canvasHeightPx: Float = 400f,
) {
    private val dispatchedActions = mutableListOf<Pair<String, Map<String, String>>>()
    private val dragCompletions = mutableListOf<Pair<String, Float>>()

    fun recordAction(action: String, params: Map<String, String> = emptyMap()) {
        dispatchedActions += action to params
    }

    fun clearActions() { dispatchedActions.clear(); dragCompletions.clear() }
    fun waitForIdle() { scope.waitForIdle() }

    fun advanceFrameAndIdle() {
        scope.mainClock.advanceTimeBy(34)
        scope.waitForIdle()
    }

    fun recordDragComplete(nodeId: String, magnitude: Float) {
        dragCompletions += nodeId to magnitude
    }

    fun assertFieldNodeAgeSecs(nodeId: String, minSecs: Float) {
        val entry = DomShadow.byRole("field-node").firstOrNull { it.attrs["node-id"] == nodeId }
            ?: error("Field node '$nodeId' not found in DomShadow")
        val ageSecs = entry.attrs["age-secs"]?.toFloatOrNull()
            ?: error("Field node '$nodeId' missing age-secs in DomShadow")
        assertTrue(ageSecs >= minSecs, "Expected age-secs >= $minSecs for '$nodeId', got $ageSecs")
    }

    fun assertFieldNodeMinAlpha(nodeId: String, minAlpha: Float) {
        val (nx, ny) = fieldNodePixelPos(nodeId)
        val img = scope.onRoot().captureToImage().toPixelMap()
        val px = nx.coerceIn(0f, canvasWidthPx - 1f).toInt()
        val py = ny.coerceIn(0f, canvasHeightPx - 1f).toInt()
        val pixel = img[px, py]
        val nodeLum = 0.2126f * pixel.red + 0.7152f * pixel.green + 0.0722f * pixel.blue
        val bgLum = 0.0334f  // Color(0xFF08080F): 0.2126*0.031 + 0.7152*0.031 + 0.0722*0.059
        val contrastRatio = (nodeLum + 0.05f) / (bgLum + 0.05f)
        val minContrast = (bgLum + 0.05f + minAlpha * 0.15f * bgLum) / (bgLum + 0.05f)
        assertTrue(
            contrastRatio >= minAlpha * 12f + 1f,
            "Node '$nodeId' contrast ratio $contrastRatio:1, expected >= ${minAlpha * 12f + 1f}:1 (minAlpha=$minAlpha). Node is not visually distinct from background.",
        )
    }

    fun assertFieldNodeWcagContrast(nodeId: String, minRatio: Float = 3.0f) {
        val (nx, ny) = fieldNodePixelPos(nodeId)
        val img = scope.onRoot().captureToImage().toPixelMap()
        val px = nx.coerceIn(0f, canvasWidthPx - 1f).toInt()
        val py = ny.coerceIn(0f, canvasHeightPx - 1f).toInt()
        val pixel = img[px, py]
        val nodeLum = 0.2126f * pixel.red + 0.7152f * pixel.green + 0.0722f * pixel.blue
        val bgLum = 0.0334f
        val lighter = maxOf(nodeLum, bgLum)
        val darker = minOf(nodeLum, bgLum)
        val ratio = (lighter + 0.05f) / (darker + 0.05f)
        assertTrue(ratio >= minRatio, "Node '$nodeId' WCAG contrast ratio $ratio:1, required >= $minRatio:1.")
    }

    fun assertLastDragMagnitude(nodeId: String, min: Float) {
        val last = dragCompletions.lastOrNull { it.first == nodeId }
        assertNotNull(last, "No drag completion recorded for '$nodeId'")
        assertTrue(last.second >= min, "Expected drag magnitude >= $min for '$nodeId', got ${last.second}")
    }

    fun tap(nodeId: String) {
        scope.onNodeWithTag(nodeId).performClick()
    }

    fun assertNodeExists(nodeId: String) {
        scope.onNodeWithTag(nodeId).assertIsDisplayed()
    }

    fun tapFieldNode(nodeId: String) {
        val (nx, ny) = fieldNodePixelPos(nodeId)
        scope.onRoot().performTouchInput { down(Offset(nx, ny)); up() }
    }

    fun dragFieldNodeToCenter(nodeId: String) {
        val (nx, ny) = fieldNodePixelPos(nodeId)
        val cx = canvasWidthPx / 2f
        val cy = canvasHeightPx / 2f
        scope.onRoot().performTouchInput {
            down(Offset(nx, ny))
            moveTo(Offset(cx, cy))
            up()
        }
    }

    fun dragFieldNode(nodeId: String, toX: Float, toY: Float) {
        val (nx, ny) = fieldNodePixelPos(nodeId)
        scope.onRoot().performTouchInput {
            down(Offset(nx, ny))
            moveTo(Offset(toX, toY))
            up()
        }
    }

    fun assertFieldNodePresent(nodeId: String) {
        val entry = DomShadow.byRole("field-node").firstOrNull { it.attrs["node-id"] == nodeId }
        assertNotNull(entry, "Field node '$nodeId' should be present in DomShadow")
    }

    fun assertFieldNodeAbsent(nodeId: String) {
        val entry = DomShadow.byRole("field-node").firstOrNull { it.attrs["node-id"] == nodeId }
        assertTrue(entry == null, "Field node '$nodeId' should not be in DomShadow")
    }

    fun assertActionFired(action: String) {
        assertTrue(
            dispatchedActions.any { it.first == action },
            "Expected action '$action' to fire. Fired: ${dispatchedActions.map { it.first }}"
        )
    }

    fun assertActionFired(action: String, params: Map<String, String>) {
        assertTrue(
            dispatchedActions.any { (a, p) -> a == action && params.all { (k, v) -> p[k] == v } },
            "Expected action '$action' with params $params. Fired: $dispatchedActions"
        )
    }

    fun assertNoActionFired() {
        assertTrue(dispatchedActions.isEmpty(), "Expected no actions. Fired: $dispatchedActions")
    }

    private fun fieldNodePixelPos(nodeId: String): Pair<Float, Float> {
        val cx = canvasWidthPx / 2f
        val cy = canvasHeightPx / 2f
        val maxR = minOf(cx, cy) * 0.88f
        val entry = DomShadow.byRole("field-node").firstOrNull { it.attrs["node-id"] == nodeId }
            ?: error("Field node '$nodeId' not found in DomShadow — did you call waitForIdle()?")
        val angle = entry.attrs["angle"]?.toFloatOrNull()
            ?: error("Field node '$nodeId' missing angle in DomShadow")
        val rf = entry.attrs["radius-fraction"]?.toFloatOrNull()
            ?: error("Field node '$nodeId' missing radius-fraction in DomShadow")
        return Pair(cx + cos(angle) * rf * maxR, cy + sin(angle) * rf * maxR)
    }
}
