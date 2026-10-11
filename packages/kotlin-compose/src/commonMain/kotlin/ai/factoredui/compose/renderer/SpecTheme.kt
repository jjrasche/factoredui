package ai.factoredui.compose.renderer

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import kotlin.math.abs

data class SpecTheme(val ground: Color, val ink: Color, val muted: Color) {
    val isDark: Boolean get() = 0.2126f * ground.red + 0.7152f * ground.green + 0.0722f * ground.blue < 0.5f

    companion object {
        val LIGHT = SpecTheme(ground = Color(0xFFFFFFFF), ink = Color(0xFF1A1A1A), muted = Color(0xFF5F6368))
        val DARK = SpecTheme(ground = Color(0xFF0B0B0F), ink = Color(0xFFE6E6EC), muted = Color(0xFF9AA0B4))
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
    "surface" -> lerp(ground, ink, SURFACE_STEP)
    "ink" -> ink
    "muted" -> muted
    else -> null
}

fun themeNamed(name: String?): SpecTheme? = when (name) {
    "dark" -> SpecTheme.DARK
    "light" -> SpecTheme.LIGHT
    else -> null
}

fun Color.toRgbInt(): Int = toArgb() and 0xFFFFFF

val LocalSpecTheme = staticCompositionLocalOf { SpecTheme.LIGHT }
