package ai.factoredui.compose.renderer

import ai.factoredui.compose.adapter.ActionRegistry
import ai.factoredui.compose.experiments.Experiments
import ai.factoredui.compose.experiments.ControlExperiments
import ai.factoredui.compose.observability.Observability
import ai.factoredui.compose.observability.NoOpObservability
import ai.factoredui.compose.schema.ActionRef

/**
 * Everything the renderer needs to render a spec tree.
 *
 * Matches RenderContext in packages/react/src/sdui/renderer.tsx.
 * The host app constructs this once and passes it to RenderNode.
 */
data class RenderContext(
    /** Named action handlers registered by the host app. */
    val actions: ActionRegistry = emptyMap(),
    /** Live data from all resolved data sources, keyed by source name. */
    val data: Map<String, Any?> = emptyMap(),
    /** Observability hook — fires onRender / onInteraction. */
    val observability: Observability = NoOpObservability,
    /** A/B experiment slot resolver. */
    val experiments: Experiments = ControlExperiments,
)

/**
 * Dispatch a named action from a spec node.
 * Logs the interaction through observability before invoking the handler.
 */
suspend fun RenderContext.dispatch(nodeId: String, actionRef: ActionRef) {
    observability.onInteraction(nodeId, actionRef)
    val handler = actions[actionRef.action]
    if (handler == null) {
        println("[factoredui] unknown action '${actionRef.action}' on node '$nodeId'")
        return
    }
    val resolvedParams = actionRef.params.mapValues { it.value }
    handler(resolvedParams)
}
