package ai.factoredui.compose.renderer

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Neutral design tokens the consumer fills to own the look without speaking
 * Material3. [FactoredTheme] maps these onto an internal MaterialTheme, so
 * every primitive (which reads MaterialTheme) is themed with no per-primitive
 * change. Per-instance props (e.g. card `background`) override per node; the
 * tokens are the fallback look.
 *
 * Names match the thoughtbox-ux-ratified vocabulary; default values are that
 * palette and can be overridden wholesale by the consumer.
 */
data class FactoredColors(
    val bg: Color = Color(0xFFFAF8FD),
    val surface: Color = Color(0xFFF1EEF7),
    val accent: Color = Color(0xFF6C5CE7),
    val onAccent: Color = Color(0xFFFFFFFF),
    val ink: Color = Color(0xFF1C1B22),
    val muted: Color = Color(0xFF8A8694),
    val success: Color = Color(0xFF22C55E),
    val warning: Color = Color(0xFFF59E0B),
    val danger: Color = Color(0xFFEF4444),
)

data class FactoredRadii(val sm: Int = 8, val md: Int = 16, val lg: Int = 24, val full: Int = 9999)

data class FactoredType(val display: Int = 20, val body: Int = 16, val caption: Int = 12)

data class FactoredSpacing(val xs: Int = 4, val sm: Int = 8, val md: Int = 12, val lg: Int = 16)

data class FactoredTokens(
    val colors: FactoredColors = FactoredColors(),
    val radii: FactoredRadii = FactoredRadii(),
    val type: FactoredType = FactoredType(),
    val spacing: FactoredSpacing = FactoredSpacing(),
)

/**
 * Theme the whole render tree from neutral [FactoredTokens]. Supersedes the
 * darkMode-only [FactoredTheme] for consumers who own a palette: they think in
 * tokens, Material3 is the hidden implementation.
 */
@Composable
fun FactoredTheme(tokens: FactoredTokens, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = tokens.colors.accent,
            onPrimary = tokens.colors.onAccent,
            background = tokens.colors.bg,
            onBackground = tokens.colors.ink,
            surface = tokens.colors.surface,
            onSurface = tokens.colors.ink,
            surfaceVariant = tokens.colors.surface,
            onSurfaceVariant = tokens.colors.muted,
            error = tokens.colors.danger,
        ),
        typography = factoredTypography(tokens.type),
        shapes = factoredShapes(tokens.radii),
        content = content,
    )
}

private fun factoredTypography(type: FactoredType): Typography {
    val base = Typography()
    return base.copy(
        headlineMedium = base.headlineMedium.copy(fontSize = type.display.sp),
        titleMedium = base.titleMedium.copy(fontSize = ((type.display + type.body) / 2).sp),
        bodyMedium = base.bodyMedium.copy(fontSize = type.body.sp),
        labelMedium = base.labelMedium.copy(fontSize = ((type.body + type.caption) / 2).sp),
        bodySmall = base.bodySmall.copy(fontSize = type.caption.sp),
    )
}

private fun factoredShapes(radii: FactoredRadii): Shapes = Shapes(
    small = RoundedCornerShape(radii.sm.dp),
    medium = RoundedCornerShape(radii.md.dp),
    large = RoundedCornerShape(radii.lg.dp),
)
