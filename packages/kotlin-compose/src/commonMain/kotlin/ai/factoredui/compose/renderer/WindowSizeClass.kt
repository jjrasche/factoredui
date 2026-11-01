package ai.factoredui.compose.renderer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo

internal fun widthClassOf(widthDp: Float, breakpoints: Breakpoints): String = when {
    widthDp >= breakpoints.expandedFromDp -> "expanded"
    widthDp >= breakpoints.mediumFromDp -> "medium"
    else -> "compact"
}

internal fun windowBindings(widthDp: Float, heightDp: Float, breakpoints: Breakpoints): Map<String, Any?> {
    val width = widthClassOf(widthDp, breakpoints)
    return mapOf(
        "window.class" to width,
        "window.is_compact" to (width == "compact"),
        "window.is_medium" to (width == "medium"),
        "window.is_expanded" to (width == "expanded"),
        "window.is_at_least_medium" to (width != "compact"),
        "window.width" to widthDp.toDouble(),
        "window.height" to heightDp.toDouble(),
    )
}

@Composable
internal fun WindowSizeBindings(context: RenderContext) {
    val size = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current.density
    val breakpoints = LocalSpecTheme.current.breakpoints
    remember(size, density, breakpoints) { context.setBindings(windowBindings(size.width / density, size.height / density, breakpoints)) }
}
