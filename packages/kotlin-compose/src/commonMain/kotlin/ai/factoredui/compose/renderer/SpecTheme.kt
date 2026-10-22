package ai.factoredui.compose.renderer

import ai.factoredui.compose.tokens.ColorValue
import ai.factoredui.compose.tokens.DimensionValue
import ai.factoredui.compose.tokens.NumberValue
import ai.factoredui.compose.tokens.TokenSet
import ai.factoredui.compose.tokens.TypographyValue
import ai.factoredui.compose.tokens.defaultTokens
import ai.factoredui.compose.tokens.mixColours
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import kotlin.math.abs
import kotlin.math.roundToInt

data class SpecTheme(
    val ground: Color,
    val ink: Color,
    val muted: Color,
    val accent: Color,
    val onAccent: Color,
    val pass: Color,
    val warn: Color,
    val fail: Color,
    val series: List<Color>,
    val type: TypeScale,
    val space: SpaceScale,
    val radius: RadiusScale,
    val control: ControlHeights,
    val elevation: ElevationSteps,
) {
    val isDark: Boolean get() = 0.2126f * ground.red + 0.7152f * ground.green + 0.0722f * ground.blue < 0.5f

    val surface: Color get() = stepToward(elevation.surface)
    val raised: Color get() = stepToward(elevation.raised)
    val overlay: Color get() = stepToward(elevation.overlay)

    private fun stepToward(step: Float): Color = Color(mixColours(ground.toArgbLong(), ink.toArgbLong(), step.toDouble()).toInt())

    companion object {
        val DARK = from(defaultTokens("dark"))
        val LIGHT = from(defaultTokens("light"))

        fun from(tokens: TokenSet): SpecTheme {
            fun colour(name: String) = Color((tokens.getValue(name) as ColorValue).argb.toInt())
            fun pixels(name: String) = (tokens.getValue(name) as DimensionValue).px.roundToInt()
            fun number(name: String) = (tokens.getValue(name) as NumberValue).value.toFloat()
            fun text(name: String) = (tokens.getValue(name) as TypographyValue).let { TextToken(it.sizePx.roundToInt(), (it.sizePx * it.lineMultiplier).roundToInt(), it.weight) }
            return SpecTheme(
                ground = colour("color.ground"), ink = colour("color.ink"), muted = colour("color.muted"), accent = colour("color.accent"), onAccent = colour("color.on-accent"),
                pass = colour("color.status.pass"), warn = colour("color.status.warn"), fail = colour("color.status.fail"),
                series = (1..8).map { colour("color.series.$it") },
                type = TypeScale(text("type.display"), text("type.heading"), text("type.subheading"), text("type.body"), text("type.caption"), text("type.label"), text("type.mono")),
                space = SpaceScale(pixels("dimension.space.xs"), pixels("dimension.space.sm"), pixels("dimension.space.md"), pixels("dimension.space.lg"), pixels("dimension.space.xl"), pixels("dimension.space.xxl"), pixels("dimension.space.gutter")),
                radius = RadiusScale(pixels("dimension.radius.sm"), pixels("dimension.radius.md"), pixels("dimension.radius.lg"), pixels("dimension.radius.xl")),
                control = ControlHeights(pixels("dimension.control.sm"), pixels("dimension.control.md"), pixels("dimension.control.lg"), pixels("dimension.control.xl")),
                elevation = ElevationSteps(number("elevation.surface"), number("elevation.raised"), number("elevation.overlay")),
            )
        }
    }
}

private fun Color.toArgbLong(): Long = toArgb().toLong() and 0xFFFFFFFFL
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

private fun SpecTheme.surfaceAt(step: Float): Color = Color(mixColours(ground.toArgbLong(), ink.toArgbLong(), step.toDouble()).toInt())

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
