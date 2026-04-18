package ai.factoredui.compose.observability

import ai.factoredui.compose.schema.ActionRef

/**
 * Observability hook interface.
 *
 * Implementations wire this to OpenTelemetry spans, PostHog, Supabase events, etc.
 * The no-op default records to console — suitable for development and tests.
 *
 * Called by RenderNode on every render and interaction, mirroring the TypeScript
 * CaptureAdapter pattern in @factoredui/core.
 */
interface Observability {

    /** Called when a node is rendered for the first time (impression). */
    fun onRender(nodeId: String)

    /** Called when the user interacts with a node (tap, submit, etc.). */
    fun onInteraction(nodeId: String, action: ActionRef)
}

/**
 * No-op observability — records events as debug log lines.
 * Use in tests and during initial integration to confirm hooks fire without
 * requiring a real telemetry backend.
 */
class LoggingObservability : Observability {
    override fun onRender(nodeId: String) {
        println("[factoredui] render: $nodeId")
    }

    override fun onInteraction(nodeId: String, action: ActionRef) {
        println("[factoredui] interaction: $nodeId -> ${action.action}(${action.params})")
    }
}

/** Silent observability for tests that only check side effects. */
object NoOpObservability : Observability {
    override fun onRender(nodeId: String) = Unit
    override fun onInteraction(nodeId: String, action: ActionRef) = Unit
}
