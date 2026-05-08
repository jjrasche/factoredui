package ai.factoredui.compose.capture

import ai.factoredui.compose.observability.Observability
import ai.factoredui.compose.schema.ActionRef
import kotlinx.serialization.json.JsonPrimitive

/**
 * Bridges the renderer's existing [Observability] hook into the capture
 * pipeline. Every action dispatched through `RenderContext.dispatch`
 * (button tap, chip toggle, slider release, etc.) becomes a CLICK
 * event with the action name in the payload.
 *
 * Why click for everything: the SDUI primitives that fire `node.action`
 * all represent a discrete user-initiated change. The factor engine
 * cares about completion / drop-off / hesitation per-node, not about
 * the specific Compose widget that produced the gesture. If you need
 * sub-typing later (e.g. distinguishing slider drags from button taps),
 * read the `action` payload field.
 *
 * onRender is intentionally a no-op. Recomposition fires it constantly;
 * surfacing every recompose as an impression would flood the queue.
 * Use [CaptureClient.trackImpression] explicitly when an impression
 * actually matters (page mount, hero reveal, etc.).
 */
class CaptureObservability(
    private val capture: CaptureClient,
) : Observability {

    override fun onRender(nodeId: String) {
        // Intentionally empty — too noisy to capture every recompose.
    }

    override fun onInteraction(nodeId: String, action: ActionRef) {
        capture.trackClick(
            componentPath = nodeId,
            payload = mapOf("action" to JsonPrimitive(action.action)),
        )
    }
}
