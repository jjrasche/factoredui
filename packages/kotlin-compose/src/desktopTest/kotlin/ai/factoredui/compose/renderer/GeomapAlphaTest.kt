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

private fun square(id: String, lon: Double, lat: Double, fill: String) = """
    {"id":"$id","fill":"$fill","rings":[[[${lon},${lat}],[${lon + 0.002},${lat}],[${lon + 0.002},${lat + 0.002}],[${lon},${lat + 0.002}]]]}
""".trimIndent()

private fun twoLayerSpec(lowerFill: String, upperFill: String) = json.decodeFromString(
    Spec.serializer(),
    """
    {
      "spec_version": 1, "renderer_min": 1,
      "root": {
        "id": "map", "type": "geomap", "props": {
          "layers": [
            { "id": "wetland", "kind": "fill", "features": [${square("w", -85.600, 42.930, lowerFill)}] },
            { "id": "floodplain", "kind": "fill", "features": [${square("f", -85.599, 42.931, upperFill)}] }
          ]
        }
      }
    }
    """.trimIndent(),
)

private fun colourCounts(png: ByteArray): Map<Int, Int> {
    val image = ImageIO.read(ByteArrayInputStream(png))
    val counts = HashMap<Int, Int>()
    for (y in 0 until image.height) for (x in 0 until image.width) {
        val rgb = image.getRGB(x, y) and 0xFFFFFF
        counts[rgb] = (counts[rgb] ?: 0) + 1
    }
    return counts
}

private fun isBlendOfRedAndBlue(rgb: Int): Boolean {
    val red = (rgb shr 16) and 0xFF
    val green = (rgb shr 8) and 0xFF
    val blue = rgb and 0xFF
    return red in 60..200 && blue in 60..220 && green < red && green < blue
}

class GeomapAlphaTest {

    @Test
    fun overlapping_translucent_layers_blend_where_they_stack() {
        val screen = renderScreen(twoLayerSpec("#80FF0000", "#800000FF"), emptyMap(), 400, 400, theme = SpecTheme.LIGHT)
        val blended = colourCounts(screen.png).filterKeys(::isBlendOfRedAndBlue).values.sum()
        assertTrue(blended > 200, "expected a visible red-and-blue blend where the layers overlap, found $blended pixels")
    }

    @Test
    fun a_translucent_fill_lets_the_ground_show_through() {
        val screen = renderScreen(twoLayerSpec("#80FF0000", "#800000FF"), emptyMap(), 400, 400, theme = SpecTheme.LIGHT)
        assertEquals(0, colourCounts(screen.png)[0xFF0000] ?: 0, "a half-transparent red drew as solid red")
    }

    @Test
    fun opaque_layers_do_not_blend_so_the_upper_one_hides_the_lower() {
        val screen = renderScreen(twoLayerSpec("#FF0000", "#0000FF"), emptyMap(), 400, 400, theme = SpecTheme.LIGHT)
        val blended = colourCounts(screen.png).filterKeys(::isBlendOfRedAndBlue).values.sum()
        assertTrue(blended < 50, "opaque layers produced $blended blended pixels, so the blend test proves nothing")
    }
}
