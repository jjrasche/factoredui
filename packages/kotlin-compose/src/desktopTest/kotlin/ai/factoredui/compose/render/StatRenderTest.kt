package ai.factoredui.compose.render

import ai.factoredui.compose.renderer.RenderContext
import ai.factoredui.compose.renderer.SpecTheme
import ai.factoredui.compose.schema.Spec
import androidx.compose.ui.graphics.toArgb
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val json = Json { ignoreUnknownKeys = true }

private fun statPage(props: String, theme: SpecTheme = SpecTheme.DARK, data: Map<String, Any?> = emptyMap()) =
    Triple(
        """{"spec_version":1,"renderer_min":1,"root":{"id":"page","type":"column","props":{"padding":16},"children":[{"id":"cost","type":"stat","props":$props}]}}""",
        theme, data,
    )

private class DrawnStat(val node: JsonObject, val bounds: List<Int>, val png: File)

private fun drawn(page: Triple<String, SpecTheme, Map<String, Any?>>): DrawnStat {
    val context = RenderContext(theme = page.second, initialData = page.third)
    val file = File.createTempFile("stat", ".png")
    var node = JsonObject(emptyMap())
    SceneSurface(json.decodeFromString(Spec.serializer(), page.first), context, 360, 240, ticking = false).use { surface ->
        node = surface.onScreenThread { nodeById(screenJson(screenNodes(context.screen)!!), "cost")!! }
        surface.shot(file)
    }
    return DrawnStat(node, listOf("x", "y", "w", "h").map { node.getValue("bounds").jsonObject.getValue(it).jsonPrimitive.int }, file)
}

private fun DrawnStat.pixel(x: Int, y: Int): Int = ImageIO.read(png).getRGB(x, y) and 0xFFFFFF

class StatRenderTest {
    @Test
    fun a_stat_reads_back_its_label_value_and_unit_as_one_line_of_text() {
        val stat = drawn(statPage("""{"label":"Heat loss","value":1842,"unit":"W"}"""))
        assertEquals("Heat loss 1842 W", stat.node.getValue("text").jsonPrimitive.content)
    }

    @Test
    fun a_value_is_formatted_as_a_text_node_formats_it() {
        val stat = drawn(statPage("""{"label":"Cost","value":12345.6,"format":"usd"}"""))
        assertTrue(stat.node.getValue("text").jsonPrimitive.content.contains("$12,346") || stat.node.getValue("text").jsonPrimitive.content.contains("$12,345"), stat.node.toString())
    }

    @Test
    fun a_value_with_no_figure_shows_the_missing_mark_not_an_empty_card() {
        val stat = drawn(statPage("""{"label":"Cost","value":"{house.cost}"}""", data = mapOf("house" to mapOf("cost" to null))))
        assertTrue(stat.node.getValue("text").jsonPrimitive.content.startsWith("Cost –"), stat.node.toString())
    }

    @Test
    fun the_card_is_the_surface_one_step_above_the_ground_with_the_status_bar_in_the_status_colour() {
        val stat = drawn(statPage("""{"label":"Load","value":0.91,"status":"fail"}"""))
        val (x, y, _, h) = stat.bounds
        assertEquals(SpecTheme.DARK.fail.toArgb() and 0xFFFFFF, stat.pixel(x + 1, y + h / 2))
        assertEquals(SpecTheme.DARK.surface.toArgb() and 0xFFFFFF, stat.pixel(x + 40, y + 2))
    }

    @Test
    fun the_status_is_said_in_words_so_colour_is_never_the_only_signal() {
        val words = listOf("pass", "warn", "fail").map { status -> drawn(statPage("""{"label":"L","value":1,"status":"$status"}""")).node.getValue("props").jsonObject.getValue("status_shown").jsonPrimitive.content }
        assertEquals(listOf("Pass", "Warn", "Fail"), words)
    }

    @Test
    fun a_status_the_spec_words_itself_is_shown_as_given() {
        assertEquals("Fits", drawn(statPage("""{"label":"L","value":1,"status":"pass","status_text":"Fits"}""")).node.getValue("props").jsonObject.getValue("status_shown").jsonPrimitive.content)
    }

    @Test
    fun a_status_that_is_not_pass_warn_or_fail_is_named_and_drawn_without_one() {
        val stat = drawn(statPage("""{"label":"L","value":1,"status":"great"}"""))
        assertTrue(stat.node.getValue("notices").jsonArray.any { it.jsonPrimitive.content == "stat status must be pass, warn or fail, not 'great'; it is drawn without one." }, stat.node.toString())
    }

    @Test
    fun the_stat_is_drawn_in_the_light_theme_with_a_surface_darker_than_the_ground() {
        val stat = drawn(statPage("""{"label":"L","value":1}""", theme = SpecTheme.LIGHT))
        val (x, y) = stat.bounds
        assertEquals(SpecTheme.LIGHT.surface.toArgb() and 0xFFFFFF, stat.pixel(x + 40, y + 2))
    }

    @Test
    fun a_stat_with_a_fraction_reads_it_back_and_a_negative_one_is_ignored() {
        val within = drawn(statPage("""{"label":"Deflection","value":0.71,"fraction":0.71}"""))
        assertEquals("0.71", within.node.getValue("props").jsonObject.getValue("fraction_shown").jsonPrimitive.content)
        val over = drawn(statPage("""{"label":"Deflection","value":1.4,"fraction":1.4}"""))
        assertEquals("1.4", over.node.getValue("props").jsonObject.getValue("fraction_shown").jsonPrimitive.content)
        val negative = drawn(statPage("""{"label":"Deflection","value":1,"fraction":-1}"""))
        assertEquals(null, negative.node.getValue("props").jsonObject["fraction_shown"])
    }
}
