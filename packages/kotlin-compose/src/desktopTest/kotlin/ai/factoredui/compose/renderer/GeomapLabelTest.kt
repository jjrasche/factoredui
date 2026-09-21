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

private const val FILL = 0x9FD8C8

private fun countySpec(label: String?, viewport: String = "") = json.decodeFromString(
    Spec.serializer(),
    """
    {
      "spec_version": 1, "renderer_min": 1,
      "root": {
        "id": "map", "type": "geomap", "props": {
          "layers": [{ "id": "counties", "kind": "fill", "features": [
            { "id": "kent", "fill": "#9FD8C8",
              ${label?.let { "\"label\": \"$it\"," } ?: ""}
              "rings": [[[-85.80,42.80],[-85.30,42.80],[-85.30,43.20],[-85.80,43.20]]] }
          ] }]
          $viewport
        }
      }
    }
    """.trimIndent(),
)

private fun inkPixels(png: ByteArray, ink: Int): Int {
    val image = ImageIO.read(ByteArrayInputStream(png))
    var count = 0
    for (y in 0 until image.height) for (x in 0 until image.width) {
        val rgb = image.getRGB(x, y) and 0xFFFFFF
        if (rgb != FILL && rgb != SpecTheme.LIGHT.ground.toRgbInt() && isDark(rgb)) count++
    }
    return count
}

private fun isDark(rgb: Int): Boolean =
    ((rgb shr 16) and 0xFF) < 110 && ((rgb shr 8) and 0xFF) < 110 && (rgb and 0xFF) < 110

class GeomapLabelTest {

    @Test
    fun a_feature_label_is_drawn_on_the_map() {
        val png = renderScreen(countySpec("Kent"), emptyMap(), 400, 400, theme = SpecTheme.LIGHT).png
        assertTrue(inkPixels(png, SpecTheme.LIGHT.ink.toRgbInt()) > 20, "the label 'Kent' never reached the pixels")
    }

    @Test
    fun without_a_label_no_text_is_drawn() {
        val png = renderScreen(countySpec(null), emptyMap(), 400, 400, theme = SpecTheme.LIGHT).png
        assertEquals(0, inkPixels(png, SpecTheme.LIGHT.ink.toRgbInt()), "text appeared on a feature that has no label")
    }

    @Test
    fun a_label_that_does_not_fit_its_feature_is_not_drawn() {
        val zoomedOut = """, "viewport": { "lon": -85.55, "lat": 43.0, "zoom": 4 }"""
        val png = renderScreen(countySpec("Kent County Housing Trigger Met"), emptyMap(), 400, 400, theme = SpecTheme.LIGHT).png
        val tiny = renderScreen(countySpec("Kent County Housing Trigger Met", zoomedOut), emptyMap(), 400, 400, theme = SpecTheme.LIGHT).png
        assertTrue(inkPixels(png, 0) > 20, "at a fitting zoom the long label should draw")
        assertEquals(0, inkPixels(tiny, 0), "a label wider than its feature on screen was drawn anyway")
    }
}
