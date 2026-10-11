package ai.factoredui.compose.renderer

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.test.Test
import kotlin.test.assertTrue

private fun contrastRatio(first: Color, second: Color): Float {
    val lighter = maxOf(first.luminance(), second.luminance())
    val darker = minOf(first.luminance(), second.luminance())
    return (lighter + 0.05f) / (darker + 0.05f)
}

class SpecThemeHairlineTest {

    @Test
    fun each_themes_hairline_has_at_least_three_to_one_against_a_white_and_a_black_page() {
        listOf(SpecTheme.LIGHT, SpecTheme.DARK).forEach { theme ->
            val againstWhite = contrastRatio(theme.hairline, Color.White)
            val againstBlack = contrastRatio(theme.hairline, Color.Black)
            assertTrue(againstWhite >= 3f && againstBlack >= 3f, "dark=${theme.isDark} white=$againstWhite black=$againstBlack")
        }
    }

    @Test
    fun each_themes_hairline_has_at_least_three_to_one_against_its_own_ground() {
        listOf(SpecTheme.LIGHT, SpecTheme.DARK).forEach { theme ->
            assertTrue(contrastRatio(theme.hairline, theme.ground) >= 3f, "dark=${theme.isDark}")
        }
    }
}
