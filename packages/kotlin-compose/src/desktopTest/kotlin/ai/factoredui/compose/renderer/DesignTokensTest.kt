package ai.factoredui.compose.renderer

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val TEXT_CONTRAST = 4.5f
private const val GRAPHIC_CONTRAST = 3f

private val THEMES = mapOf("dark" to SpecTheme.DARK, "light" to SpecTheme.LIGHT)

private fun contrast(a: Color, b: Color): Float {
    val (lighter, darker) = listOf(a.luminance(), b.luminance()).sortedDescending()
    return (lighter + 0.05f) / (darker + 0.05f)
}

private fun SpecTheme.grounds(): Map<String, Color> = mapOf("ground" to ground, "surface" to surface, "raised" to raised)

private fun SpecTheme.texts(): Map<String, Color> = mapOf("ink" to ink, "muted" to muted, "accent" to accent, "pass" to pass, "warn" to warn, "fail" to fail)

class DesignTokensTest {
    @Test
    fun every_text_colour_meets_aa_on_the_ground_the_surface_and_the_raised_surface_in_both_themes() {
        val failures = THEMES.flatMap { (themeName, theme) ->
            theme.texts().flatMap { (textName, text) ->
                theme.grounds().mapNotNull { (groundName, ground) -> contrast(text, ground).takeIf { it < TEXT_CONTRAST }?.let { "$themeName $textName on $groundName: $it" } }
            }
        }
        assertEquals(emptyList(), failures)
    }

    @Test
    fun the_ink_on_the_accent_meets_aa_in_both_themes() {
        THEMES.forEach { (name, theme) -> assertTrue(contrast(theme.onAccent, theme.accent) >= TEXT_CONTRAST, "$name: ${contrast(theme.onAccent, theme.accent)}") }
    }

    @Test
    fun every_series_colour_is_a_graphic_that_meets_3_to_1_on_each_ground_in_its_theme() {
        val failures = THEMES.flatMap { (themeName, theme) ->
            theme.series.flatMapIndexed { index, colour ->
                theme.grounds().mapNotNull { (groundName, ground) -> contrast(colour, ground).takeIf { it < GRAPHIC_CONTRAST }?.let { "$themeName series $index on $groundName: $it" } }
            }
        }
        assertEquals(emptyList(), failures)
    }

    @Test
    fun the_series_palette_has_eight_distinct_colours_in_each_theme() {
        THEMES.forEach { (name, theme) -> assertEquals(8, theme.series.toSet().size, name) }
    }

    @Test
    fun the_series_palette_is_not_the_status_colours_so_a_line_is_never_mistaken_for_pass_or_fail() {
        THEMES.forEach { (name, theme) -> assertTrue(theme.series.none { it in listOf(theme.pass, theme.warn, theme.fail) }, name) }
    }

    @Test
    fun the_surfaces_step_up_from_the_ground_by_the_elevation_steps() {
        THEMES.forEach { (name, theme) ->
            val levels = listOf(theme.ground, theme.surface, theme.raised, theme.overlay).map { it.luminance() }
            val towardTheInk = if (theme.isDark) levels.sorted() else levels.sortedDescending()
            assertEquals(towardTheInk, levels, "$name: each surface is one step nearer the ink than the one below")
            assertEquals(levels.size, levels.toSet().size, "$name: no two surfaces are the same")
        }
    }

    @Test
    fun the_type_scale_steps_down_and_never_goes_below_a_readable_size() {
        val type = TypeScale()
        assertEquals(type.let { listOf(it.display.size, it.heading.size, it.subheading.size, it.body.size, it.caption.size) }.sortedDescending(), type.let { listOf(it.display.size, it.heading.size, it.subheading.size, it.body.size, it.caption.size) })
        assertTrue(type.body.size >= 16 && type.caption.size >= 12)
        listOf(type.display, type.heading, type.subheading, type.body, type.caption, type.label, type.mono).forEach { assertTrue(it.line >= it.size * 1.2, "line height of ${it.size}") }
    }

    @Test
    fun the_spacing_and_radius_steps_grow_and_the_phone_gutter_is_16() {
        val space = SpaceScale()
        assertEquals(listOf(space.xs, space.sm, space.md, space.lg, space.xl, space.xxl).sorted(), listOf(space.xs, space.sm, space.md, space.lg, space.xl, space.xxl))
        assertEquals(16, space.gutter)
        val radius = RadiusScale()
        assertEquals(listOf(radius.sm, radius.md, radius.lg, radius.xl).sorted(), listOf(radius.sm, radius.md, radius.lg, radius.xl))
    }

    @Test
    fun control_heights_grow_by_size_and_the_default_meets_a_48_dp_touch_target() {
        val control = ControlHeights()
        assertEquals(listOf(control.sm, control.md, control.lg, control.xl).sorted(), listOf(control.sm, control.md, control.lg, control.xl))
        assertTrue(control.md >= 48 && control.xl >= 72)
    }
}
