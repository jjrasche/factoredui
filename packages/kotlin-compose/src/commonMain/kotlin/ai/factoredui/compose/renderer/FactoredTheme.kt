package ai.factoredui.compose.renderer

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * Convenience wrapper that supplies a Material 3 color scheme to RenderSpec.
 *
 * The renderer reads MaterialTheme.colorScheme / .typography directly, so any
 * MaterialTheme works. This exists so hosts that don't want to own a palette
 * can flip dark mode with one prop. Hosts with a brand palette should ignore
 * this and wrap RenderSpec in their own MaterialTheme.
 *
 * @param darkMode null = follow the OS, true/false = override.
 */
@Composable
fun FactoredTheme(
    darkMode: Boolean? = null,
    content: @Composable () -> Unit,
) {
    val useDark = darkMode ?: isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (useDark) darkColorScheme() else lightColorScheme(),
        content = content,
    )
}
