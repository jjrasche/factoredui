package ai.factoredui.compose.testing

/**
 * Test-introspection shadow DOM. Compose Multiplatform doesn't (as of 1.7.x)
 * mirror its semantic tree into the browser's DOM, so the canvas is opaque
 * to Playwright / accessibility tools / external agents. This module fills
 * that gap: every interesting primitive emits a hidden `<div>` carrying
 * `data-fui-*` attributes that describe what's rendered on the canvas.
 *
 * Querying from outside (e.g. Playwright):
 *
 *   document.querySelector('[data-fui-id="signal_graph_root"]')
 *   document.querySelectorAll('[data-fui-role="graph-node"]')
 *
 * The emitter does NOT attempt to overlay positions or replace the canvas
 * for users — these elements have `display: none`. Their entire purpose is
 * machine-readable structure.
 *
 * Per-target actuals:
 *  - wasmJsMain: real JS interop, attaches nodes to a hidden #fui-shadow
 *    container appended to <body>.
 *  - non-wasm targets: no-op (no browser DOM to mirror into).
 *
 * Identity model: callers pass a stable `id`. Re-emitting with the same id
 * updates the existing node's attributes rather than appending a duplicate,
 * so it's safe to call from a Composable's body where it'll re-run on
 * recomposition.
 */
expect object DomShadow {
    /**
     * Create or update a hidden shadow node identified by [id]. Idempotent.
     *
     * @param id        stable identifier — typically the SpecNode's `id`,
     *                  or `"<view>:<sub-id>"` for compound primitives.
     * @param role      semantic role: "graph", "graph-node", "graph-edge",
     *                  "signal-log-row", "filter-input", "scrub-bar",
     *                  "tooltip", … free-form, but stay consistent within
     *                  a primitive so external queries are stable.
     * @param attrs     extra `data-fui-*` attributes. Keys should be
     *                  lowercase-kebab. Null values are skipped.
     */
    fun emit(id: String, role: String, attrs: Map<String, String?> = emptyMap())

    /**
     * Remove a shadow node by id. Call from `DisposableEffect.onDispose`
     * when a primitive that emitted goes out of composition. Forgetting
     * to call this is a leak (memory + a stale visible-to-tools node)
     * but not a crash.
     */
    fun remove(id: String)
}
