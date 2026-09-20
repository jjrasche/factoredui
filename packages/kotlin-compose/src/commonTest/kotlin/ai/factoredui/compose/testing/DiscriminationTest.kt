package ai.factoredui.compose.testing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

private data class Frame(val colors: List<String>, val width: Int)

class DiscriminationTest {

    private val intact = Frame(colors = listOf("#b5c1ed", "#4164df"), width = 1280)

    @Test
    fun a_check_that_tells_the_two_apart_passes() {
        assertDiscriminates(
            intact = intact,
            broken = intact.copy(colors = listOf("#888888", "#888888")),
            brokenBy = "every wire colour falling back to grey",
        ) { frame -> assertTrue(frame.colors.none { it == "#888888" }, "grey reached the frame") }
    }

    @Test
    fun a_check_blind_to_the_break_fails_and_names_the_break() {
        val blindness = runCatching {
            assertDiscriminates(
                intact = intact,
                broken = intact.copy(colors = listOf("#888888", "#888888")),
                brokenBy = "every wire colour falling back to grey",
            ) { frame -> assertEquals(1280, frame.width) }
        }.exceptionOrNull() ?: fail("a check that ignores the break must not pass")
        assertTrue(
            blindness.message.orEmpty().contains("every wire colour falling back to grey"),
            "the failure must name the break it missed, said: ${blindness.message}",
        )
    }

    @Test
    fun a_check_that_fails_on_the_intact_value_reports_that_rather_than_blindness() {
        val failure = runCatching {
            assertDiscriminates(
                intact = intact,
                broken = intact.copy(colors = emptyList()),
                brokenBy = "no colours at all",
            ) { frame -> assertEquals(99, frame.width) }
        }.exceptionOrNull() ?: fail("a check that fails on the intact value must not pass")
        assertTrue(
            failure.message.orEmpty().contains("intact"),
            "the failure must say the check failed on the intact value, said: ${failure.message}",
        )
    }
}
