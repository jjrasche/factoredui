package ai.factoredui.compose.renderer

import ai.factoredui.compose.adapter.ActionRegistry
import ai.factoredui.compose.adapter.HostDataSource
import ai.factoredui.compose.experiments.ControlExperiments
import ai.factoredui.compose.experiments.Experiments
import ai.factoredui.compose.media.MediaHost
import ai.factoredui.compose.media.platformMediaHost
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
    /** Ground and ink the host sets once; a spec that names no colour takes these. */
    val theme: SpecTheme,
    private val store: MutableStateFlow<Map<String, Any?>>,
    private val overlay: Map<String, Any?>,
    val screen: ScreenRegistry,
    val mediaHost: MediaHost,
) {
    constructor(
        actions: ActionRegistry = emptyMap(),
        initialData: Map<String, Any?> = emptyMap(),
        observability: Observability = NoOpObservability,
        experiments: Experiments = ControlExperiments,
        hostDataSource: HostDataSource? = null,
        theme: SpecTheme = SpecTheme.LIGHT,
        mediaHost: MediaHost = platformMediaHost(),
    ) : this(
        actions = actions,
        observability = observability,
        experiments = experiments,
        hostDataSource = hostDataSource,
        theme = theme,
        store = MutableStateFlow(initialData),
        overlay = emptyMap(),
        screen = ScreenRegistry(),
        mediaHost = mediaHost,
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

    fun startBindings(starts: Map<String, Any?>) {
        store.update { current ->
            starts.entries.filter { (path, _) -> path.isNotEmpty() && ai.factoredui.compose.schema.BindingResolver.resolveBinding("{$path}", current) == null }
                .fold(current) { written, (path, value) -> writeAtPath(written, path.split("."), value) }
        }
    }

    fun setBindings(writes: Map<String, Any?>) {
        val writable = writes.filterKeys { it.isNotEmpty() }
        store.update { current -> writable.entries.fold(current) { written, (path, value) -> writeAtPath(written, path.split("."), value) } }
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
        theme = theme,
        store = store,
        overlay = overlay + extra,
        screen = screen,
        mediaHost = mediaHost,
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
    if (actionRef.action == SET_BINDING_ACTION) {
        writeBindingFrom(nodeId, resolvedParams)
        return
    }
    val handler = actions[actionRef.action]
    if (handler == null) {
        println("[factoredui] unknown action '${actionRef.action}' on node '$nodeId'")
        return
    }
    handler(resolvedParams)
}

const val SET_BINDING_ACTION = "set_binding"

const val RENDERER_ERROR_PATH = "renderer.error"

private fun setBindingRefusal(nodeId: String, params: Map<String, Any?>): String? {
    val path = params["path"]
    if (path !is String || path.isEmpty()) return "set_binding on '$nodeId' was refused: 'path' must be non-empty text, not '$path'"
    if (SET_BINDING_VALUE !in params) return "set_binding on '$nodeId' was refused: it names no 'value' to write to '$path'"
    return null
}

private const val SET_BINDING_VALUE = "value"

private const val SET_BINDING_VALUES = "values"

private val SINGLE_FORM_KEYS = listOf("path", SET_BINDING_VALUE)

private fun setBindingValuesRefusal(nodeId: String, params: Map<String, Any?>): String? {
    val refused = "set_binding on '$nodeId' was refused:"
    val singleFormKeys = SINGLE_FORM_KEYS.filter { it in params }
    if (singleFormKeys.isNotEmpty()) {
        val seen = singleFormKeys.joinToString(" and ") { "'$it'" }
        return "$refused it gives 'values' and also $seen; give either 'path' with 'value' or 'values', not both"
    }
    val values = params[SET_BINDING_VALUES]
    if (values !is Map<*, *>) return "$refused 'values' must be an object of paths to values, not '$values'"
    if (values.isEmpty()) return "$refused 'values' must name at least one path, not {}"
    val unwritablePaths = values.keys.filterNot { it is String && it.isNotEmpty() }
    if (unwritablePaths.isNotEmpty()) return "$refused every path in 'values' must be non-empty text, not '${unwritablePaths.first()}'"
    return null
}

private fun bindingWritesIn(params: Map<String, Any?>): Map<String, Any?> =
    (params[SET_BINDING_VALUES] as Map<*, *>).entries.associate { (path, value) -> path.toString() to value }

private const val SET_BINDING_ADD = "add"

private fun RenderContext.writeBindingFrom(nodeId: String, params: Map<String, Any?>) {
    when {
        SET_BINDING_VALUES in params -> writeBindingValuesFrom(nodeId, params)
        SET_BINDING_ADD in params -> writeAddedBindingFrom(nodeId, params)
        else -> writeOneBindingFrom(nodeId, params)
    }
}

private fun addedBindingRefusal(nodeId: String, params: Map<String, Any?>): String? {
    val refused = "set_binding on '$nodeId' was refused:"
    val path = params["path"]
    if (path !is String || path.isEmpty()) return "$refused 'path' must be non-empty text, not '$path'"
    if (SET_BINDING_VALUE in params) return "$refused it gives 'add' and also 'value'; give one"
    if (params[SET_BINDING_ADD] !is Number) return "$refused 'add' must be a number, not '${params[SET_BINDING_ADD]}'"
    return listOf("min", "max").firstOrNull { it in params && params[it] !is Number }?.let { "$refused '$it' must be a number, not '${params[it]}'" }
}

/** Adds the number in `add` to the number at `path` (or `min`, else 0, when there is none), kept between `min` and `max` when they are given. */
private fun RenderContext.writeAddedBindingFrom(nodeId: String, params: Map<String, Any?>) {
    val refusal = addedBindingRefusal(nodeId, params)
    if (refusal != null) return setBinding(RENDERER_ERROR_PATH, refusal)
    val path = params["path"] as String
    val low = (params["min"] as? Number)?.toDouble()
    val high = (params["max"] as? Number)?.toDouble()
    val current = (ai.factoredui.compose.schema.BindingResolver.resolveBinding("{$path}", data) as? Number)?.toDouble() ?: low ?: 0.0
    setBinding(path, (current + (params[SET_BINDING_ADD] as Number).toDouble()).coerceIn(low ?: Double.NEGATIVE_INFINITY, high ?: Double.POSITIVE_INFINITY))
}

private fun RenderContext.writeOneBindingFrom(nodeId: String, params: Map<String, Any?>) {
    val refusal = setBindingRefusal(nodeId, params)
    if (refusal != null) setBinding(RENDERER_ERROR_PATH, refusal) else setBinding(params["path"] as String, params[SET_BINDING_VALUE])
}

private fun RenderContext.writeBindingValuesFrom(nodeId: String, params: Map<String, Any?>) {
    val refusal = setBindingValuesRefusal(nodeId, params)
    if (refusal != null) setBinding(RENDERER_ERROR_PATH, refusal) else setBindings(bindingWritesIn(params))
}

fun sceneIntentDispatcher(
    context: RenderContext,
    nodeId: String,
    onObserve: (String) -> Unit = {},
): suspend (String, Map<String, Any?>) -> Unit = { action, params ->
    onObserve(action)
    context.observability.onInteraction(nodeId, ActionRef(action = action), params)
    context.actions[action]?.invoke(params)
}
