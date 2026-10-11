package ai.factoredui.compose.renderer

import ai.factoredui.compose.crawl.driveScreen
import ai.factoredui.compose.scene.readReports
import ai.factoredui.compose.scene.reportsState
import ai.factoredui.compose.schema.Spec
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val json = Json { ignoreUnknownKeys = true }

private const val RAMP = """{"stops":[[0,"#3b7dd8"],[0.5,"#e6a93a"],[1,"#b5382a"]],"over":"#5a1e8c","no_figure":"#9aa3ab"}"""

private val EXPECTED = listOf(
    "0%" to 0x3B7DD8,
    "25%" to 0x919389,
    "50%" to 0xE6A93A,
    "75%" to 0xCE7132,
    "100%" to 0xB5382A,
    "130%" to 0x5A1E8C,
    "–" to 0x9AA3AB,
)

private val FRACTIONS: List<Double?> = listOf(0.0, 0.25, 0.5, 0.75, 1.0, 1.3, null)

private fun spec(root: String): Spec = json.decodeFromString(Spec.serializer(), """{"spec_version":1,"renderer_min":1,"root":$root}""")

private val CELLS = spec(
    """
    {"id":"table","type":"column","children":[{"id":"rows","type":"list","props":{"data":"{rows}","itemTemplate":
      {"id":"row","type":"row","children":[
        {"id":"name","type":"text","props":{"value":"cell","flex":1}},
        {"id":"cell","type":"text","props":{"value":"{row.values.fraction}","format":"percent","flex":1,"align":"right",
          "background_ramp":{"value":"{row.values.fraction}","ramp":$RAMP}}}]}}}]}
    """.trimIndent(),
)

private val STORE = mapOf("rows" to FRACTIONS.map { mapOf("values" to mapOf("fraction" to it)) })

private fun channels(rgb: Int): List<Int> = listOf((rgb shr 16) and 0xFF, (rgb shr 8) and 0xFF, rgb and 0xFF)

private fun assertWithinOneLevel(expected: Int, actual: Int, what: String) {
    channels(expected).zip(channels(actual)).forEach { (e, a) -> assertTrue(abs(e - a) <= 1, "$what: expected ${expected.toString(16)}, drew ${actual.toString(16)}") }
}

private fun inkSpec(colorRamp: String) = spec(
    """{"id":"page","type":"column","children":[{"id":"word","type":"text","props":{"value":"MMMM","variant":"heading","bold":true,"color_ramp":$colorRamp}}]}""",
)

private const val REPORT_ROWS = """[
  {"id":"r1","label":"one","values":{"a":12},"fractions":{"a":1.3,"b":null},"flags":{"a":"over"}},
  {"id":"r2","label":"two","values":{"a":7},"fractions":{"a":null}},
  {"id":"r3","label":"three","values":{"a":5}}]"""

private val REPORT_CELLS = spec(
    """
    {"id":"table","type":"column","children":[{"id":"rows","type":"list","props":{"data":"{reports.groups.rows}","itemTemplate":
      {"id":"cell","type":"text","props":{"value":"{row.values.a}","background_ramp":{"value":"{row.fractions.a}","ramp":$RAMP}}}}}]}
    """.trimIndent(),
)

class TextRampTest {

    @Test
    fun a_report_rows_fraction_fills_its_cell_through_the_ramp_and_a_null_or_absent_fraction_fills_no_figure() {
        val reports = reportsState(readReports("""{"reports":[{"id":"groups","label":"Groups","columns":[],"rows":$REPORT_ROWS}]}"""))
        driveScreen(REPORT_CELLS, mapOf("reports" to reports), 400, 300).use { screen ->
            val image = ImageIO.read(ByteArrayInputStream(screen.frame().png))
            listOf("12" to 0x5A1E8C, "7" to 0x9AA3AB, "5" to 0x9AA3AB).forEach { (text, colour) ->
                val cell = checkNotNull(screen.boundsOfText(text)) { "'$text' was not drawn" }
                assertWithinOneLevel(colour, image.getRGB(cell.left.value.toInt() + 1, cell.top.value.toInt() + 1) and 0xFFFFFF, text)
            }
        }
    }

    @Test
    fun each_cell_fills_with_the_ramp_colour_of_its_number_exactly() {
        driveScreen(CELLS, STORE, 400, 600).use { screen ->
            val image = ImageIO.read(ByteArrayInputStream(screen.frame().png))
            EXPECTED.forEach { (text, colour) ->
                val cell = checkNotNull(screen.boundsOfText(text)) { "'$text' was not drawn" }
                assertWithinOneLevel(colour, image.getRGB(cell.left.value.toInt() + 1, cell.top.value.toInt() + 1) and 0xFFFFFF, text)
            }
        }
    }

    @Test
    fun the_ink_on_every_ramp_cell_is_black_or_white_at_four_and_a_half_to_one_or_better() {
        listOf(SpecTheme.LIGHT, SpecTheme.DARK).forEach { theme ->
            driveScreen(CELLS, STORE, 400, 600, theme).use { screen ->
                val png = screen.frame().png
                EXPECTED.forEach { (text, _) ->
                    val ratio = inkContrastIn(png, checkNotNull(screen.boundsOfText(text)))
                    assertTrue(ratio >= 4.5, "dark=${theme.isDark} '$text' $ratio:1")
                }
            }
        }
    }

    @Test
    fun a_text_colour_ramp_inks_the_words_in_the_colour_of_a_bound_or_literal_number() {
        listOf(
            """{"value":0.25,"ramp":$RAMP}""" to 0x919389,
            """{"value":"{n}","ramp":$RAMP}""" to 0xCE7132,
        ).forEach { (colorRamp, expected) ->
            driveScreen(inkSpec(colorRamp), mapOf("n" to 0.75), 400, 200).use { screen ->
                val word = checkNotNull(screen.boundsOf("word"))
                val image = ImageIO.read(ByteArrayInputStream(screen.frame().png))
                val pixels = (word.top.value.toInt() until word.bottom.value.toInt()).flatMap { y ->
                    (word.left.value.toInt() until word.right.value.toInt()).map { x -> image.getRGB(x, y) and 0xFFFFFF }
                }
                val ground = SpecTheme.LIGHT.ground.toRgbInt()
                val ink = pixels.filter { it != ground }.groupingBy { it }.eachCount().maxBy { it.value }.key
                assertWithinOneLevel(expected, ink, colorRamp)
            }
        }
    }

    @Test
    fun a_ramp_value_that_is_not_a_number_and_a_ramp_that_cannot_be_read_draw_notices_in_place_of_the_words() {
        driveScreen(inkSpec("""{"value":"abc","ramp":$RAMP}"""), emptyMap(), 400, 200).use { screen ->
            assertEquals(listOf("color_ramp needs a number, not 'abc'"), screen.textsUnder("page"))
        }
        val falling = """{"value":0.5,"ramp":{"stops":[[1,"#000000"],[0,"#ffffff"]]}}"""
        val cell = spec("""{"id":"page","type":"column","children":[{"id":"cell","type":"text","props":{"value":"7","background_ramp":$falling}}]}""")
        driveScreen(cell, emptyMap(), 400, 200).use { screen ->
            assertEquals(listOf("background_ramp stop values must rise: 1 is followed by 0"), screen.textsUnder("page"))
        }
        driveScreen(inkSpec("""{"value":0.5,"ramp":{"stops":[[0,"#000000"]]}}"""), emptyMap(), 400, 200).use { screen ->
            assertEquals(listOf("color_ramp needs at least 2 stops, not 1"), screen.textsUnder("page"))
        }
    }
}
