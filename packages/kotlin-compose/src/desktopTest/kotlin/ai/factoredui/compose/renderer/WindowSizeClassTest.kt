package ai.factoredui.compose.renderer

import kotlin.test.Test
import kotlin.test.assertEquals

class WindowSizeClassTest {
    private val theme = Breakpoints()

    @Test
    fun widths_under_600_are_compact_600_to_under_840_medium_and_840_or_more_expanded() {
        assertEquals(listOf("compact", "compact", "medium", "medium", "expanded", "expanded"), listOf(0f, 599.9f, 600f, 839.9f, 840f, 1400f).map { widthClassOf(it, theme) })
    }

    @Test
    fun a_theme_that_moves_a_breakpoint_moves_the_class() {
        assertEquals(listOf("compact", "medium", "expanded"), listOf(399f, 400f, 700f).map { widthClassOf(it, Breakpoints(mediumFromDp = 400f, expandedFromDp = 700f)) })
    }

    @Test
    fun the_bindings_name_the_class_and_flag_each_class_and_the_at_least_medium_case() {
        assertEquals(
            mapOf<String, Any?>(
                "window.class" to "medium", "window.is_compact" to false, "window.is_medium" to true, "window.is_expanded" to false, "window.is_at_least_medium" to true,
                "window.width" to 700.0, "window.height" to 1000.0,
            ),
            windowBindings(700f, 1000f, theme),
        )
    }
}
