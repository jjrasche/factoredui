package ai.factoredui.compose.testing

/**
 * Non-wasm actual: there's no browser DOM here. No-op.
 */
actual object DomShadow {
    actual fun emit(id: String, role: String, attrs: Map<String, String?>) { /* no-op */ }
    actual fun remove(id: String) { /* no-op */ }
}
