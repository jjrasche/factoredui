package ai.factoredui.compose.renderer

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

data class SpecTheme(val ground: Color, val ink: Color, val muted: Color) {
    companion object {
        val LIGHT = SpecTheme(ground = Color(0xFFFFFFFF), ink = Color(0xFF1A1A1A), muted = Color(0xFF5F6368))
        val DARK = SpecTheme(ground = Color(0xFF0B0B0F), ink = Color(0xFFE6E6EC), muted = Color(0xFF9AA0B4))
    }
}

fun Color.toRgbInt(): Int = toArgb() and 0xFFFFFF

val LocalSpecTheme = staticCompositionLocalOf { SpecTheme.LIGHT }
