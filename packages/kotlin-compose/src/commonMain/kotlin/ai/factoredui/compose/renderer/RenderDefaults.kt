package ai.factoredui.compose.renderer

import androidx.compose.ui.unit.dp

/**
 * Renderer-internal fallbacks used when a spec omits a sizing prop.
 *
 * Why named constants: these magic numbers appear as defaults in render
 * paths where the spec author didn't supply a value. Centralizing them
 * makes a future "themed defaults" hook straightforward and removes the
 * `8.dp` / `200.dp` literals that were scattered across RenderNode.kt.
 */
internal object RenderDefaults {
    /** Default spacer height when `size` is unset or non-positive. */
    val SPACER_HEIGHT = 8.dp

    /** Image height used when no `aspectRatio` prop is supplied. */
    val IMAGE_FALLBACK_HEIGHT = 200.dp
}
