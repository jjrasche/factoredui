package ai.factoredui.compose.renderer

import ai.factoredui.compose.adapter.ActionRegistry
import ai.factoredui.compose.adapter.HostDataSource
import ai.factoredui.compose.experiments.ControlExperiments
import ai.factoredui.compose.experiments.Experiments
import ai.factoredui.compose.observability.NoOpObservability
import ai.factoredui.compose.observability.Observability
import ai.factoredui.compose.schema.ActionRef
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * Everything the renderer needs to render a spec tree.
 *
 * Matches RenderContext in packages/react/src/sdui/renderer.tsx.
 * The host app constructs this once and passes it to RenderNode.
 *
 * Milestone 2: `data` is now reactive via an internal MutableStateFlow so
 * primitives (e.g. textinput) can write back to bindings and the UI
 * recomposes. Readers should use [data] (the current snapshot) or collect
 * [dataFlow] to observe mutations.
 */
class RenderContext private constructor(
    /** Named action handlers registered by the host app. */
    val actions: ActionRegistry,
    /** Observability hook — fires onRender / onInteraction. */
    val observability: Observability,
    /** A/B experiment slot resolver. */
    val experiments: Experiments,
    /**
     * Host-implemented live query source for `data_source`-bound LIST nodes.
     * Null when the host wires no reactive data layer — lists then fall back
     * to the static [data] path, so existing specs are unaffected.
     */
    val hostDataSource: HostDataSource?,
    private val store: MutableStateFlow<Map<String, Any?>>,
    private val overlay: Map<String, Any?>,
) {
    constructor(
        actions: ActionRegistry = emptyMap(),
        initialData: Map<String, Any?> = emptyMap(),
        observability: Observability = NoOpObservability,
        experiments: Experiments = ControlExperiments,
        hostDataSource: HostDataSource? = null,
    ) : this(
        actions = actions,
        observability = observability,
        experiments = experiments,
        hostDataSource = hostDataSource,
        store = MutableStateFlow(initialData),
        overlay = emptyMap(),
    )

    /** Reactive flow of the merged (overlay + store) data map. */
    val dataFlow: StateFlow<Map<String, Any?>> =
        if (overlay.isEmpty()) store
        else DerivedStateFlow(store, overlay)

    /** Current data snapshot — overlay shadows the underlying store. */
    val data: Map<String, Any?> get() = dataFlow.value

    /**
     * Write a value into the reactive data store at the given dot-separated
     * binding path (e.g. "shell.composeText"). Intermediate maps are created
     * as needed. Triggers recomposition of any composable reading via
     * [dataFlow] / `collectAsState`.
     *
     * Writes always target the underlying store, never the overlay. Overlays
     * are a read-only scoping mechanism for list/template rendering.
     */
    fun setBinding(path: String, value: Any?) {
        if (path.isEmpty()) return
        store.update { current -> writeAtPath(current, path.split("."), value) }
    }

    /**
     * Return a context sharing this one's underlying store but with an
     * additional read-only data overlay merged on top. Used by list/template
     * rendering so per-item "item" bindings resolve without mutating the
     * shared store. Writes via [setBinding] on the child still land in the
     * parent store.
     */
    fun withAdditionalData(extra: Map<String, Any?>): RenderContext = RenderContext(
        actions = actions,
        observability = observability,
        experiments = experiments,
        hostDataSource = hostDataSource,
        store = store,
        overlay = overlay + extra,
    )

    /** Recursively rebuild a nested map structure with [value] placed at [segments]. */
    private fun writeAtPath(
        current: Map<String, Any?>,
        segments: List<String>,
        value: Any?,
    ): Map<String, Any?> {
        val head = segments.first()
        val tail = segments.drop(1)
        val mutated = current.toMutableMap()
        if (tail.isEmpty()) {
            mutated[head] = value
        } else {
            val child = current[head] as? Map<String, Any?> ?: emptyMap()
            mutated[head] = writeAtPath(child, tail, value)
        }
        return mutated
    }
}

/**
 * Read-only StateFlow that merges a live parent store with a fixed overlay
 * snapshot. Used by child contexts created via [RenderContext.withAdditionalData].
 */
private class DerivedStateFlow(
    private val store: StateFlow<Map<String, Any?>>,
    private val overlay: Map<String, Any?>,
) : StateFlow<Map<String, Any?>> {
    override val value: Map<String, Any?> get() = store.value + overlay
    override val replayCache: List<Map<String, Any?>> get() = listOf(value)
    override suspend fun collect(
        collector: kotlinx.coroutines.flow.FlowCollector<Map<String, Any?>>,
    ): Nothing = store.collect(
        object : kotlinx.coroutines.flow.FlowCollector<Map<String, Any?>> {
            override suspend fun emit(value: Map<String, Any?>) {
                collector.emit(value + overlay)
            }
        },
    )
}

/**
 * Dispatch a named action from a spec node.
 *
 * Resolves every action param against the current data snapshot before
 * invoking the handler, so params like `{"phone": "{item.phone}"}` arrive
 * as plain Kotlin primitives (String, Double, Boolean, null, Map, List).
 * Host code should cast to its expected type — no SpecValue unwrapping
 * needed at the handler site.
 */
suspend fun RenderContext.dispatch(nodeId: String, actionRef: ActionRef) {
    // Resolve once, up front, so the observability hook witnesses the same
    // resolved params the handler receives — capture anchors the event to the
    // data it was about (e.g. {row.id} → the row's id) rather than the raw
    // binding ref. Fires regardless of whether a handler is registered.
    val resolvedParams = ai.factoredui.compose.schema.BindingResolver
        .resolveProps(actionRef.params, data)
    observability.onInteraction(nodeId, actionRef, resolvedParams)
    val handler = actions[actionRef.action]
    if (handler == null) {
        println("[factoredui] unknown action '${actionRef.action}' on node '$nodeId'")
        return
    }
    handler(resolvedParams)
}
