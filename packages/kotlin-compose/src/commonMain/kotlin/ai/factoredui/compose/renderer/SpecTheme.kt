package ai.factoredui.compose.renderer

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import kotlin.math.abs

data class SpecTheme(
    val ground: Color,
    val ink: Color,
    val muted: Color,
    val accent: Color = Color(0xFF5AB0FF),
    val onAccent: Color = Color(0xFF06111F),
    val pass: Color = Color(0xFF3DD68C),
    val warn: Color = Color(0xFFF5B83D),
    val fail: Color = Color(0xFFFF7A7A),
    val series: List<Color> = DARK_SERIES,
    val type: TypeScale = TypeScale(),
    val space: SpaceScale = SpaceScale(),
    val radius: RadiusScale = RadiusScale(),
    val control: ControlHeights = ControlHeights(),
    val elevation: ElevationSteps = ElevationSteps(),
) {
    val isDark: Boolean get() = 0.2126f * ground.red + 0.7152f * ground.green + 0.0722f * ground.blue < 0.5f

    val surface: Color get() = lerp(ground, ink, elevation.surface)
    val raised: Color get() = lerp(ground, ink, elevation.raised)
    val overlay: Color get() = lerp(ground, ink, elevation.overlay)

    companion object {
        val LIGHT = SpecTheme(
            ground = Color(0xFFFFFFFF), ink = Color(0xFF111827), muted = Color(0xFF4B5563),
            accent = Color(0xFF0B5FCC), onAccent = Color(0xFFFFFFFF), pass = Color(0xFF0F6F41), warn = Color(0xFF8A5A00), fail = Color(0xFFB3261E),
            series = LIGHT_SERIES,
        )
        val DARK = SpecTheme(ground = Color(0xFF0B1220), ink = Color(0xFFE8EAEE), muted = Color(0xFFA3ACB9))
    }
}

private const val SURFACE_STEP = 0.06f

private const val EVEN_CONTRAST_LUMINANCE = 0.179f
private const val HAIRLINE_STEPS = 64

/** The blend of ground and ink whose contrast with a white page equals its contrast with a black one (about 4.6:1). */
val SpecTheme.hairline: Color
    get() = (0..HAIRLINE_STEPS).map { lerp(ground, ink, it / HAIRLINE_STEPS.toFloat()) }.minBy { abs(it.luminance() - EVEN_CONTRAST_LUMINANCE) }

fun SpecTheme.tokenColor(name: String): Color? = when (name) {
    "ground" -> ground
    "surface" -> surface
    "raised" -> raised
    "overlay" -> overlay
    "ink" -> ink
    "muted" -> muted
    "accent" -> accent
    "on_accent" -> onAccent
    "pass" -> pass
    "warn" -> warn
    "fail" -> fail
    else -> null
}

private const val LOW_SURFACE_STEP = 0.03f
private const val HIGH_SURFACE_STEP = 0.08f
private const val HIGHEST_SURFACE_STEP = 0.10f

private fun SpecTheme.surfaceAt(step: Float): Color = lerp(ground, ink, step)

private fun isSchemeDark(scheme: ColorScheme): Boolean = scheme.background.luminance() < 0.5f

/** The host's scheme if it is the theme's kind, else that kind's default, with every surface and content colour from the theme. */
fun SpecTheme.colorScheme(host: ColorScheme): ColorScheme {
    val family = if (isSchemeDark(host) == isDark) host else if (isDark) darkColorScheme() else lightColorScheme()
    val hostBrand = family === host && host.primary != (if (isDark) darkColorScheme() else lightColorScheme()).primary
    val brand = if (hostBrand) host.primary else accent
    val onBrand = if (hostBrand) host.onPrimary else onAccent
    return family.copy(
        background = ground,
        onBackground = ink,
        surface = ground,
        onSurface = ink,
        primary = brand,
        onPrimary = onBrand,
        primaryContainer = lerp(raised, brand, 0.35f),
        onPrimaryContainer = ink,
        secondary = brand,
        onSecondary = onBrand,
        secondaryContainer = raised,
        onSecondaryContainer = ink,
        error = fail,
        onError = ground,
        surfaceVariant = surfaceAt(SURFACE_STEP),
        onSurfaceVariant = muted,
        surfaceTint = ink,
        surfaceBright = surfaceAt(HIGHEST_SURFACE_STEP),
        surfaceDim = ground,
        surfaceContainerLowest = ground,
        surfaceContainerLow = surfaceAt(LOW_SURFACE_STEP),
        surfaceContainer = surfaceAt(SURFACE_STEP),
        surfaceContainerHigh = surfaceAt(HIGH_SURFACE_STEP),
        surfaceContainerHighest = surfaceAt(HIGHEST_SURFACE_STEP),
        inverseSurface = ink,
        inverseOnSurface = ground,
        outline = muted,
        outlineVariant = hairline,
    )
}

fun themeNamed(name: String?): SpecTheme? = when (name) {
    "dark" -> SpecTheme.DARK
    "light" -> SpecTheme.LIGHT
    else -> null
}

fun Color.toRgbInt(): Int = toArgb() and 0xFFFFFF

val LocalSpecTheme = staticCompositionLocalOf { SpecTheme.LIGHT }
