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

private const val KENT = 0x2E86DE
private const val WAYNE = 0xE67E22

private fun twoCountySpec(viewport: String) = json.decodeFromString(
    Spec.serializer(),
    """
    {
      "spec_version": 1, "renderer_min": 1,
      "root": {
        "id": "map", "type": "geomap", "props": {
          "layers": [{ "id": "counties", "kind": "fill", "features": [
            { "id": "kent", "fill": "#2E86DE", "rings": [[[-85.80,42.80],[-85.30,42.80],[-85.30,43.20],[-85.80,43.20]]] },
            { "id": "wayne", "fill": "#E67E22", "rings": [[[-83.50,42.10],[-82.90,42.10],[-82.90,42.45],[-83.50,42.45]]] }
          ] }]
          $viewport
        }
      }
    }
    """.trimIndent(),
)

private fun countOf(png: ByteArray, rgb: Int): Int {
    val image = ImageIO.read(ByteArrayInputStream(png))
    var count = 0
    for (y in 0 until image.height) for (x in 0 until image.width) if ((image.getRGB(x, y) and 0xFFFFFF) == rgb) count++
    return count
}

class GeomapFitBoundsTest {

    @Test
    fun a_bounds_viewport_frames_that_region_and_leaves_the_rest_out() {
        val aroundKent = """, "viewport": { "bounds": [-85.80, 42.80, -85.30, 43.20] }"""
        val png = renderScreen(twoCountySpec(aroundKent), emptyMap(), 400, 400, theme = SpecTheme.LIGHT).png
        assertTrue(countOf(png, KENT) > 400 * 400 / 2, "Kent should fill most of a view framed on it")
        assertEquals(0, countOf(png, WAYNE), "Wayne is a county away and should be out of frame")
    }

    @Test
    fun the_same_bounds_fit_a_wide_canvas_and_a_tall_one() {
        val aroundKent = """, "viewport": { "bounds": [-85.80, 42.80, -85.30, 43.20] }"""
        val wide = renderScreen(twoCountySpec(aroundKent), emptyMap(), 600, 300, theme = SpecTheme.LIGHT).png
        val tall = renderScreen(twoCountySpec(aroundKent), emptyMap(), 300, 600, theme = SpecTheme.LIGHT).png
        assertTrue(countOf(wide, KENT) > 0 && countOf(tall, KENT) > 0, "the region must be framed whatever the canvas shape")
        assertEquals(0, countOf(wide, WAYNE) + countOf(tall, WAYNE))
    }

    @Test
    fun a_centre_and_zoom_viewport_still_works() {
        val onWayne = """, "viewport": { "lon": -83.2, "lat": 42.27, "zoom": 9 }"""
        val png = renderScreen(twoCountySpec(onWayne), emptyMap(), 400, 400, theme = SpecTheme.LIGHT).png
        assertTrue(countOf(png, WAYNE) > 0, "a centre-and-zoom viewport stopped working")
        assertEquals(0, countOf(png, KENT))
    }
}
