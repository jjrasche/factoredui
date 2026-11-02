package ai.factoredui.compose.renderer

import kotlin.test.Test
import kotlin.test.assertEquals

class WindowSizeClassTest {
    @Test
    fun widths_under_600_are_compact_600_to_under_840_medium_and_840_or_more_expanded() {
        assertEquals(listOf("compact", "compact", "medium", "medium", "expanded", "expanded"), listOf(0f, 599.9f, 600f, 839.9f, 840f, 1400f).map { widthClassOf(it) })
    }

    @Test
    fun heights_under_480_are_compact_480_to_under_900_medium_and_900_or_more_expanded() {
        assertEquals(listOf("compact", "medium", "medium", "expanded"), listOf(479f, 480f, 899f, 900f).map { heightClassOf(it) })
    }

    @Test
    fun the_bindings_name_the_class_and_flag_each_class_and_the_at_least_medium_case() {
        assertEquals(
            mapOf<String, Any?>(
                "window.class" to "medium", "window.is_compact" to false, "window.is_medium" to true, "window.is_expanded" to false, "window.is_at_least_medium" to true,
                "window.height_class" to "expanded", "window.width" to 700.0, "window.height" to 1000.0,
            ),
            windowBindings(700f, 1000f),
        )
    }
}
