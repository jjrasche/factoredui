package ai.factoredui.compose.testing

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
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

    fun recordAction(action: String, params: Map<String, String> = emptyMap()) {
        dispatchedActions += action to params
    }

    fun clearActions() = dispatchedActions.clear()

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
