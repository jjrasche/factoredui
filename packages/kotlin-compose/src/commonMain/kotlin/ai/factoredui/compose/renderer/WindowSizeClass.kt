package ai.factoredui.compose.renderer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo

private const val MEDIUM_FROM_DP = 600f
private const val EXPANDED_FROM_DP = 840f
private const val MEDIUM_HEIGHT_FROM_DP = 480f
private const val EXPANDED_HEIGHT_FROM_DP = 900f

internal fun widthClassOf(widthDp: Float): String = when {
    widthDp >= EXPANDED_FROM_DP -> "expanded"
    widthDp >= MEDIUM_FROM_DP -> "medium"
    else -> "compact"
}

internal fun heightClassOf(heightDp: Float): String = when {
    heightDp >= EXPANDED_HEIGHT_FROM_DP -> "expanded"
    heightDp >= MEDIUM_HEIGHT_FROM_DP -> "medium"
    else -> "compact"
}

internal fun windowBindings(widthDp: Float, heightDp: Float): Map<String, Any?> {
    val width = widthClassOf(widthDp)
    return mapOf(
        "window.class" to width,
        "window.is_compact" to (width == "compact"),
        "window.is_medium" to (width == "medium"),
        "window.is_expanded" to (width == "expanded"),
        "window.is_at_least_medium" to (width != "compact"),
        "window.height_class" to heightClassOf(heightDp),
        "window.width" to widthDp.toDouble(),
        "window.height" to heightDp.toDouble(),
    )
}

@Composable
internal fun WindowSizeBindings(context: RenderContext) {
    val size = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current.density
    remember(size, density) { context.setBindings(windowBindings(size.width / density, size.height / density)) }
}