package ai.factoredui.compose.capture

import ai.factoredui.compose.observability.Observability
import ai.factoredui.compose.schema.ActionRef
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
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

    override fun onInteraction(nodeId: String, action: ActionRef, resolvedParams: Map<String, Any?>) {
        capture.trackClick(
            componentPath = nodeId,
            payload = buildInteractionPayload(action.action, resolvedParams),
        )
    }
}

/**
 * Builds the capture payload for an interaction. The action name sits at the top
 * level as `action`; the resolved action params (if any) nest under `params`,
 * mirroring the spec's `action.params` one-to-one. Nesting keeps the two
 * namespaces from colliding by construction — the only reserved top-level key
 * is `action` — so there's no reserved-key policy to enforce and anchoring stays
 * opt-in (no params declared ⇒ no `params` key).
 */
internal fun buildInteractionPayload(
    actionName: String,
    resolvedParams: Map<String, Any?>,
): Map<String, JsonElement> {
    val payload = mutableMapOf<String, JsonElement>("action" to JsonPrimitive(actionName))
    if (resolvedParams.isNotEmpty()) {
        payload["params"] = JsonObject(resolvedParams.mapValues { anyToJsonElement(it.value) })
    }
    return payload
}

/**
 * Converts a resolved binding value to JSON. Resolved params are plain Kotlin
 * values (String / Number / Boolean / null / nested Map / List); anything
 * unexpected falls back to its string form rather than failing the capture.
 */
internal fun anyToJsonElement(value: Any?): JsonElement = when (value) {
    null -> JsonNull
    is JsonElement -> value
    is Boolean -> JsonPrimitive(value)
    is Number -> JsonPrimitive(value)
    is String -> JsonPrimitive(value)
    is Map<*, *> -> JsonObject(value.entries.associate { (k, v) -> k.toString() to anyToJsonElement(v) })
    is Iterable<*> -> JsonArray(value.map { anyToJsonElement(it) })
    else -> JsonPrimitive(value.toString())
}
