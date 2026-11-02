package ai.factoredui.compose.renderer

import ai.factoredui.compose.score.ScoreShade
import ai.factoredui.compose.score.readScoreShade
import ai.factoredui.compose.score.ScoreShadeReading
import androidx.compose.ui.graphics.Color
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val SMALL_DISTANCE = 40.0

private fun distance(first: Color, second: Color): Double {
    val red = (first.red - second.red) * 255.0
    val green = (first.green - second.green) * 255.0
    val blue = (first.blue - second.blue) * 255.0
    return sqrt(red * red + green * green + blue * blue)
}

private fun hex(text: String) = Color(0xFF000000L.toInt() or text.removePrefix("#").toInt(16))

private val VIRIDIS_LIKE = listOf("#440154", "#3b528b", "#21918c", "#5ec962", "#fde725")

private fun shadeOf(stops: List<String>, noFigure: String = "#9aa0a6"): ScoreShade {
    val record = mapOf(
        "weights" to mapOf("a" to 1.0),
        "ramp" to mapOf("stops" to stops.mapIndexed { index, colour -> listOf(index / (stops.size - 1.0), colour) }, "no_figure" to noFigure),
    )
    return (readScoreShade("shade_by", record) as ScoreShadeReading.Readable).shade
}

class GeomapDimColourTest {
    private val themes = listOf("light" to SpecTheme.LIGHT, "dark" to SpecTheme.DARK)

    @Test
    fun a_dimmed_thing_is_not_the_background_in_either_theme() {
        for ((name, theme) in themes) {
            assertTrue(distance(theme.dimmed, theme.ground) > SMALL_DISTANCE, "the dim colour is nearly the $name background")
        }
    }

    @Test
    fun a_dimmed_thing_is_not_any_colour_of_a_viridis_like_ramp_in_either_theme() {
        for ((name, theme) in themes) {
            for (stop in VIRIDIS_LIKE) {
                assertTrue(distance(theme.dimmed, hex(stop)) > SMALL_DISTANCE, "the $name dim colour is nearly the ramp colour $stop")
            }
        }
    }

    @Test
    fun a_viridis_like_ramp_stays_apart_from_the_background_in_both_themes() {
        for ((name, theme) in themes) {
            assertEquals(emptyList(), shadePaletteProblems(shadeOf(VIRIDIS_LIKE), theme), "the $name theme")
        }
    }

    @Test
    fun a_ramp_colour_that_is_nearly_the_dark_background_is_named_as_a_problem() {
        val navy = shadeOf(listOf("#0d1424", "#fde725"))
        val problems = shadePaletteProblems(navy, SpecTheme.DARK)
        assertEquals(1, problems.size)
        assertTrue(problems.single().contains("#0d1424"), problems.single())
        assertEquals(emptyList(), shadePaletteProblems(navy, SpecTheme.LIGHT))
    }

    @Test
    fun the_no_figure_colour_is_checked_against_the_background_too() {
        val problems = shadePaletteProblems(shadeOf(VIRIDIS_LIKE, noFigure = "#0b1220"), SpecTheme.DARK)
        assertEquals(1, problems.size)
        assertTrue(problems.single().contains("no_figure"), problems.single())
    }
}
