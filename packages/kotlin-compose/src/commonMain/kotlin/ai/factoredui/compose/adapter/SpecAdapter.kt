package ai.factoredui.compose.adapter

import ai.factoredui.compose.schema.Spec
import ai.factoredui.compose.schema.SpecNode
import kotlinx.coroutines.flow.Flow

/**
 * Data-source contract for loading SDUI specs.
 *
 * Mirrors the loadSpec / createSpecStorage pattern from @factoredui/core spec-loader.ts.
 * Implementations may load from:
 *   - Supabase (remote, via REST or realtime)
 *   - Local storage (cached active spec)
 *   - Bundled assets (baseline fallback)
 *
 * The fallback chain is: remote > cached active > bundled baseline.
 * This adapter defines the interface; platform implementations wire the actual sources.
 */
interface SpecAdapter {

    /**
     * Load a spec by [specName].
     *
     * Returns [SpecLoadResult.Success] with the spec and its origin, or
     * [SpecLoadResult.Failure] with an error if all sources are exhausted.
     */
    suspend fun loadSpec(specName: String): SpecLoadResult
}

sealed class SpecLoadResult {
    data class Success(val spec: Spec, val source: SpecSource) : SpecLoadResult()
    data class Failure(val specName: String, val reason: String) : SpecLoadResult()
}

enum class SpecSource {
    /** Fetched fresh from the remote Supabase store. */
    REMOTE,
    /** Served from device/local storage cache. */
    CACHED,
    /** Bundled with the app binary as a fallback. */
    BASELINE,
}

/**
 * ActionRegistry — maps action names to suspend handlers.
 * The host app registers all action names the spec may reference.
 * Matches ActionRegistry / ActionHandler from spec-types.ts.
 */
typealias ActionHandler = suspend (params: Map<String, Any?>) -> Unit
typealias ActionRegistry = Map<String, ActionHandler>

/**
 * DataSourceRegistry — maps source names to fetch functions.
 * Spec bindings like "{items.0.name}" resolve through this registry.
 * Matches DataSourceRegistry / DataSourceConfig from spec-types.ts.
 */
data class DataSourceConfig(
    val fetch: suspend () -> Any?,
    val cache: CachePolicy = CachePolicy.NONE,
    val maxItems: Int? = null,
)

enum class CachePolicy { NONE, LOCAL }

typealias DataSourceRegistry = Map<String, DataSourceConfig>

/**
 * Reactive sibling of [DataSourceConfig]. Where [DataSourceConfig.fetch] is a
 * one-shot pull, [HostDataSource] is a live push: the renderer subscribes a
 * `data_source`-bound LIST node to a query and re-renders whenever the host's
 * storage layer reports a change.
 *
 * The query string is opaque to the renderer (e.g.
 * "query:automations:approved_at_ms IS NULL") — only the host's storage layer
 * interprets it. Each emission is the full current result set; each element is
 * one row (typically a `Map<String, Any?>`) that the row template binds against
 * via the "row" scope key (e.g. "{row.name}", "{row.id}").
 *
 * The host implements this; the renderer only consumes. A SQLDelight-backed
 * host can return `query.asFlow().mapToList(...)`; a callback- or listener-based
 * store can wrap its source in `callbackFlow { ... awaitClose { ... } }` so
 * collection cancellation releases the underlying watch.
 */
fun interface HostDataSource {
    fun subscribe(query: String): Flow<List<Any?>>
}
