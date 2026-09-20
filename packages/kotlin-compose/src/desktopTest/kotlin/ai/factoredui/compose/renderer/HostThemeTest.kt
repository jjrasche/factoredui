package ai.factoredui.compose.renderer

import ai.factoredui.compose.crawl.renderScreen
import ai.factoredui.compose.schema.Spec
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

private val json = Json { ignoreUnknownKeys = true }

private val COLOURLESS_SPEC = json.decodeFromString(
    Spec.serializer(),
    """
    {
      "spec_version": 1, "renderer_min": 1,
      "root": {
        "id": "screen", "type": "column", "props": { "padding": 16 },
        "children": [{ "id": "line", "type": "text", "props": { "value": "says nothing about ground" } }]
      }
    }
    """.trimIndent(),
)

private fun cornerPixelOf(png: ByteArray): Int =
    ImageIO.read(ByteArrayInputStream(png)).getRGB(2, 2) and 0xFFFFFF

class HostThemeTest {

    @Test
    fun a_spec_that_declares_no_colour_takes_the_hosts_dark_ground() {
        val screen = renderScreen(COLOURLESS_SPEC, emptyMap(), 300, 200, theme = SpecTheme.DARK)
        assertEquals(SpecTheme.DARK.ground.toRgbInt(), cornerPixelOf(screen.png))
    }

    @Test
    fun the_same_spec_takes_a_light_hosts_ground_instead() {
        val screen = renderScreen(COLOURLESS_SPEC, emptyMap(), 300, 200, theme = SpecTheme.LIGHT)
        assertEquals(SpecTheme.LIGHT.ground.toRgbInt(), cornerPixelOf(screen.png))
    }

    @Test
    fun text_on_a_dark_host_is_never_the_ground_it_stands_on() {
        val dark = renderScreen(COLOURLESS_SPEC, emptyMap(), 300, 200, theme = SpecTheme.DARK)
        assertNotEquals(
            SpecTheme.DARK.ground.toRgbInt(),
            SpecTheme.DARK.ink.toRgbInt(),
            "default ink equal to the ground would be invisible text",
        )
        assertTrue(dark.png.isNotEmpty())
    }

    @Test
    fun a_spec_that_names_a_colour_still_wins_over_the_host() {
        val insistent = json.decodeFromString(
            Spec.serializer(),
            """
            {
              "spec_version": 1, "renderer_min": 1,
              "root": { "id": "line", "type": "text", "props": { "value": "loud", "color": "#FF0000" } }
            }
            """.trimIndent(),
        )
        val screen = renderScreen(insistent, emptyMap(), 300, 200, theme = SpecTheme.DARK)
        val image = ImageIO.read(ByteArrayInputStream(screen.png))
        val reds = (0 until image.height).sumOf { y ->
            (0 until image.width).count { x -> (image.getRGB(x, y) and 0xFFFFFF) == 0xFF0000 }
        }
        assertTrue(reds > 0, "the spec's own colour must survive the host theme")
    }

    @Test
    fun the_host_theme_is_readable_back_so_a_check_need_not_hardcode_a_literal() {
        val context = RenderContext(theme = SpecTheme.DARK)
        assertEquals(SpecTheme.DARK.ground, context.theme.ground)
    }
}
