package ai.factoredui.compose.render

import ai.factoredui.compose.media.MediaHost
import ai.factoredui.compose.renderer.RenderContext
import ai.factoredui.compose.renderer.SpecTheme
import ai.factoredui.compose.schema.BindingResolver
import ai.factoredui.compose.schema.Spec
import androidx.compose.ui.graphics.toArgb
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val json = Json { ignoreUnknownKeys = true }

private const val DARK_ROOT = """"background":"#0b1220""""

private fun narration(extra: String = "") = """{"id":"narration","type":"audio","props":{"source":"a.mp3","title":"Chapter 1","controls":false$extra}}"""

private fun pageOf(theme: String, node: String = narration()) =
    """{"spec_version":1,"renderer_min":1$theme,"root":{"id":"root","type":"column","props":{"padding":16,$DARK_ROOT},"children":[$node]}}"""

private class Drawn(val image: BufferedImage, val titleBounds: List<Int>, val context: RenderContext)

private fun drawn(specText: String, hostTheme: SpecTheme = SpecTheme.LIGHT): Drawn {
    val context = RenderContext(theme = hostTheme, mediaHost = MediaHost({ FakeAudioEngine() }, null))
    val file = File.createTempFile("theme", ".png")
    var bounds = emptyList<Int>()
    SceneSurface(json.decodeFromString(Spec.serializer(), specText), context, 400, 300, ticking = false).use { surface ->
        val node = surface.onScreenThread { nodeById(screenJson(screenNodes(context.screen)!!), "narration")!! }
        bounds = listOf("x", "y", "w", "h").map { node.getValue("bounds").jsonObject.getValue(it).jsonPrimitive.int }
        surface.shot(file)
    }
    return Drawn(ImageIO.read(file), bounds, context)
}

private fun brightestIn(image: BufferedImage, bounds: List<Int>): Double {
    val (x, y, w, h) = bounds
    return (x until x + w).flatMap { px -> (y until y + h).map { py -> image.getRGB(px, py) } }.maxOf { rgb ->
        0.2126 * (rgb shr 16 and 0xFF) / 255.0 + 0.7152 * (rgb shr 8 and 0xFF) / 255.0 + 0.0722 * (rgb and 0xFF) / 255.0
    }
}

class SpecThemeRenderTest {
    @Test
    fun a_spec_that_names_dark_is_drawn_dark_whatever_the_host_chose() {
        val page = drawn("""{"spec_version":1,"renderer_min":1,"theme":"dark","root":{"id":"narration","type":"audio","props":{"source":"a.mp3","title":"Chapter 1","controls":false}}}""")
        assertEquals(SpecTheme.DARK.ground.toArgb() and 0xFFFFFF, page.image.getRGB(390, 290) and 0xFFFFFF)
    }

    @Test
    fun the_audio_title_is_light_ink_on_a_dark_ground_when_the_spec_is_dark() {
        val page = drawn(pageOf(""","theme":"dark""""))
        assertTrue(brightestIn(page.image, page.titleBounds) > 0.7, "the title is not light on the dark ground")
    }

    @Test
    fun without_a_dark_theme_the_same_page_has_dark_ink_on_the_dark_ground() {
        val page = drawn(pageOf(""))
        assertTrue(brightestIn(page.image, page.titleBounds) < 0.5, "the light theme was expected to give dark ink here")
    }

    @Test
    fun a_host_that_chose_dark_draws_a_spec_that_names_no_theme_dark() {
        val page = drawn(pageOf(""), hostTheme = SpecTheme.DARK)
        assertTrue(brightestIn(page.image, page.titleBounds) > 0.7)
    }

    @Test
    fun a_theme_that_is_neither_light_nor_dark_is_named_and_the_hosts_is_used() {
        val page = drawn(pageOf(""","theme":"sepia""""))
        assertTrue(brightestIn(page.image, page.titleBounds) < 0.5)
        val said = BindingResolver.resolveBinding("{renderer.error}", page.context.data) as String
        assertEquals("spec theme must be light or dark, not 'sepia'; the host's theme is used.", said)
    }

    @Test
    fun an_audio_node_that_hides_its_title_and_its_controls_takes_no_room() {
        val page = drawn(pageOf("", narration(""","show_title":false""")))
        assertEquals(0, page.titleBounds[3])
    }
}
