package ai.factoredui.compose.renderer

import ai.factoredui.compose.crawl.renderScreen
import ai.factoredui.compose.schema.Spec
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val json = Json { ignoreUnknownKeys = true }

private fun containerSpec(type: String) = json.decodeFromString(
    Spec.serializer(),
    """
    {
      "spec_version": 1, "renderer_min": 1,
      "root": {
        "id": "ground", "type": "$type",
        "props": { "padding": 28, "gap": 18, "background": "#0B0D0E", "flex": 1 },
        "children": [{ "id": "line", "type": "text", "props": { "value": "on a declared ground" } }]
      }
    }
    """.trimIndent(),
)

private fun pixelsOf(png: ByteArray, rgb: Int): Int {
    val image = ImageIO.read(ByteArrayInputStream(png))
    return (0 until image.height).sumOf { y ->
        (0 until image.width).count { x -> (image.getRGB(x, y) and 0xFFFFFF) == rgb }
    }
}

class ContainerBackgroundTest {

    @Test
    fun a_column_that_declares_a_background_gets_it() {
        val screen = renderScreen(containerSpec("column"), emptyMap(), 300, 300, theme = SpecTheme.LIGHT)
        assertTrue(pixelsOf(screen.png, 0x0B0D0E) > 1_000, "the declared ground never reached the pixels")
    }

    @Test
    fun a_row_that_declares_a_background_gets_it() {
        val screen = renderScreen(containerSpec("row"), emptyMap(), 300, 300, theme = SpecTheme.LIGHT)
        assertTrue(pixelsOf(screen.png, 0x0B0D0E) > 1_000, "the declared ground never reached the pixels")
    }

    @Test
    fun a_declared_background_beats_the_host_theme() {
        val screen = renderScreen(containerSpec("column"), emptyMap(), 300, 300, theme = SpecTheme.DARK)
        val image = ImageIO.read(ByteArrayInputStream(screen.png))
        assertEquals(
            0x0B0D0E,
            image.getRGB(2, 2) and 0xFFFFFF,
            "inside the container the declared ground must win over the host theme",
        )
    }

    @Test
    fun a_container_with_no_background_still_shows_the_host_ground() {
        val plain = json.decodeFromString(
            Spec.serializer(),
            """
            {
              "spec_version": 1, "renderer_min": 1,
              "root": {
                "id": "plain", "type": "column", "props": { "padding": 8 },
                "children": [{ "id": "line", "type": "text", "props": { "value": "no ground declared" } }]
              }
            }
            """.trimIndent(),
        )
        val screen = renderScreen(plain, emptyMap(), 300, 300, theme = SpecTheme.DARK)
        val image = ImageIO.read(ByteArrayInputStream(screen.png))
        assertEquals(SpecTheme.DARK.ground.toRgbInt(), image.getRGB(2, 2) and 0xFFFFFF)
    }
}
