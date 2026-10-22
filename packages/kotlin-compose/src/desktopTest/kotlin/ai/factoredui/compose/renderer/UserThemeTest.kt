package ai.factoredui.compose.renderer

import ai.factoredui.compose.tokens.ColorValue
import ai.factoredui.compose.tokens.applyUserTokens
import ai.factoredui.compose.tokens.defaultTokens
import ai.factoredui.compose.tokens.mixColours
import ai.factoredui.compose.tokens.writeTokenFile
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.test.Test
import kotlin.test.assertEquals

class UserThemeTest {
    @Test
    fun the_default_themes_are_built_from_their_token_sets() {
        assertEquals(Color(0xFF0B1220), SpecTheme.DARK.ground)
        assertEquals(Color(0xFFFFFFFF), SpecTheme.LIGHT.ground)
        assertEquals(SpecTheme.DARK, SpecTheme.from(defaultTokens("dark")))
    }

    @Test
    fun a_users_file_changes_the_accent_and_nothing_else_in_the_theme_the_renderer_reads() {
        val file = writeTokenFile(defaultTokens("dark") + ("color.accent" to ColorValue(0xFFFF8A4C)), "dark")
        val theme = SpecTheme.from(applyUserTokens("dark", file).tokens)
        assertEquals(Color(0xFFFF8A4C), theme.accent)
        assertEquals(SpecTheme.DARK.copy(accent = theme.accent), theme)
    }

    @Test
    fun a_file_whose_accent_is_unreadable_leaves_the_default_theme_exactly() {
        val file = writeTokenFile(defaultTokens("dark") + ("color.on-accent" to ColorValue(0xFF5AB1FF)), "dark")
        assertEquals(SpecTheme.DARK, SpecTheme.from(applyUserTokens("dark", file).tokens))
    }

    @Test
    fun the_surfaces_the_renderer_draws_are_the_surfaces_the_contrast_checks_judge() {
        listOf(SpecTheme.DARK, SpecTheme.LIGHT).forEach { theme ->
            val ground = theme.ground.toArgb().toLong() and 0xFFFFFFFFL
            val ink = theme.ink.toArgb().toLong() and 0xFFFFFFFFL
            assertEquals(mixColours(ground, ink, theme.elevation.raised.toDouble()).toInt(), theme.raised.toArgb())
        }
    }
}
