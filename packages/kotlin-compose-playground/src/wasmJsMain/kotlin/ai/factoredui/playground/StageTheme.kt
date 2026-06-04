package ai.factoredui.playground

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object StageTokens {
    val canvas = Color(0xFF0E0F13)
    val surface = Color(0xFF16181F)
    val surfaceRaised = Color(0xFF1E2128)
    val border = Color(0xFF2A2E37)
    val textPrimary = Color(0xFFE9EBF0)
    val textMuted = Color(0xFF9AA1AE)
    val accent = Color(0xFF6E8BFF)
    val accentMuted = Color(0xFF3A4570)
    val iffy = Color(0xFFE0A84E)
    val locked = Color(0xFF4FB477)

    val gapXs = 4.dp
    val gapSm = 8.dp
    val gapMd = 16.dp
    val gapLg = 24.dp
    val radius = 10.dp
}

private val StageColorScheme = darkColorScheme(
    primary = StageTokens.accent,
    onPrimary = StageTokens.canvas,
    background = StageTokens.canvas,
    onBackground = StageTokens.textPrimary,
    surface = StageTokens.surface,
    onSurface = StageTokens.textPrimary,
    surfaceVariant = StageTokens.surfaceRaised,
    outline = StageTokens.border,
)

@Composable
fun StageTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = StageColorScheme, content = content)
}
