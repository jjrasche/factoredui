package ai.factoredui.compose.renderer

import ai.factoredui.compose.crawl.renderScreen
import ai.factoredui.compose.schema.Spec
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue

private val json = Json { ignoreUnknownKeys = true }

private const val OUTLINE = 0x8E44AD

private fun outlinedSpec(dash: String) = json.decodeFromString(
    Spec.serializer(),
    """
    {
      "spec_version": 1, "renderer_min": 1,
      "root": {
        "id": "map", "type": "geomap", "props": {
          "layers": [{ "id": "margin", "kind": "fill", "features": [
            { "id": "uncertain", "stroke": "#8E44AD", "stroke_width": 4 $dash,
              "rings": [[[-85.80,42.80],[-85.30,42.80],[-85.30,43.20],[-85.80,43.20]]] }
          ] }]
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

class GeomapDashTest {

    @Test
    fun a_dashed_outline_is_broken_into_dashes() {
        val solid = countOf(renderScreen(outlinedSpec(""), emptyMap(), 400, 400, theme = SpecTheme.LIGHT).png, OUTLINE)
        val dashed = countOf(renderScreen(outlinedSpec(""", "dash": [10, 10]"""), emptyMap(), 400, 400, theme = SpecTheme.LIGHT).png, OUTLINE)
        assertTrue(solid > 500, "the solid outline itself did not draw ($solid px), so the comparison means nothing")
        assertTrue(dashed in (solid / 5)..(solid * 4 / 5), "dashed drew $dashed of a solid $solid — expected roughly half")
    }
}
