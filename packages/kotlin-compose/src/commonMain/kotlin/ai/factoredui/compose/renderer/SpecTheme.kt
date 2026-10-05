package ai.factoredui.compose.renderer

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb

data class SpecTheme(val ground: Color, val ink: Color, val muted: Color) {
    val isDark: Boolean get() = 0.2126f * ground.red + 0.7152f * ground.green + 0.0722f * ground.blue < 0.5f

    companion object {
        val LIGHT = SpecTheme(ground = Color(0xFFFFFFFF), ink = Color(0xFF1A1A1A), muted = Color(0xFF5F6368))
        val DARK = SpecTheme(ground = Color(0xFF0B0B0F), ink = Color(0xFFE6E6EC), muted = Color(0xFF9AA0B4))
    }
}

private const val SURFACE_STEP = 0.06f

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
