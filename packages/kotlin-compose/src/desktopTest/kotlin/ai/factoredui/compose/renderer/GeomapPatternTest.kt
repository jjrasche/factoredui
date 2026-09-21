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

private const val HATCH_INK = 0xC0392B
private const val SOLID_PROBE = 0x16A085

private const val TRIANGLE_RINGS = "[[[-85.600,42.930],[-85.596,42.930],[-85.600,42.934]]]"

private fun mapSpec(featureStyle: String, legend: String = "") = json.decodeFromString(
    Spec.serializer(),
    """
    {
      "spec_version": 1, "renderer_min": 1,
      "root": {
        "id": "map", "type": "geomap", "props": {
          "layers": [{ "id": "constraints", "kind": "fill", "features": [
            { "id": "wetland", "rings": $TRIANGLE_RINGS, $featureStyle }
          ] }]
          $legend
        }
      }
    }
    """.trimIndent(),
)

private val HATCHED = """ "pattern": { "kind": "hatch", "color": "#C0392B", "angle": 45, "spacing": 8 } """
private val SOLID = """ "fill": "#16A085" """

private fun pixelsOf(png: ByteArray, rgb: Int): Set<Pair<Int, Int>> {
    val image = ImageIO.read(ByteArrayInputStream(png))
    val found = mutableSetOf<Pair<Int, Int>>()
    for (y in 0 until image.height) for (x in 0 until image.width) {
        if ((image.getRGB(x, y) and 0xFFFFFF) == rgb) found += x to y
    }
    return found
}

class GeomapPatternTest {

    @Test
    fun a_hatched_feature_draws_its_stripes() {
        val screen = renderScreen(mapSpec(HATCHED), emptyMap(), 400, 400, theme = SpecTheme.LIGHT)
        assertTrue(pixelsOf(screen.png, HATCH_INK).size > 200, "no hatch stripes reached the pixels")
    }

    @Test
    fun a_hatch_leaves_gaps_rather_than_filling_solid() {
        val hatched = pixelsOf(renderScreen(mapSpec(HATCHED), emptyMap(), 400, 400, theme = SpecTheme.LIGHT).png, HATCH_INK)
        val solid = pixelsOf(renderScreen(mapSpec(SOLID), emptyMap(), 400, 400, theme = SpecTheme.LIGHT).png, SOLID_PROBE)
        assertTrue(hatched.size < solid.size * 0.7, "hatch covered ${hatched.size} of ${solid.size} pixels — that is a fill, not a hatch")
    }

    @Test
    fun the_hatch_stays_inside_the_parcel_it_marks() {
        val parcelArea = pixelsOf(renderScreen(mapSpec(SOLID), emptyMap(), 400, 400, theme = SpecTheme.LIGHT).png, SOLID_PROBE)
        val stripes = pixelsOf(renderScreen(mapSpec(HATCHED), emptyMap(), 400, 400, theme = SpecTheme.LIGHT).png, HATCH_INK)
        val outside = stripes.filterNot { (x, y) ->
            (-2..2).any { dx -> (-2..2).any { dy -> (x + dx) to (y + dy) in parcelArea } }
        }
        assertEquals(0, outside.size, "${outside.size} stripe pixels were painted outside the parcel")
    }

    @Test
    fun a_legend_draws_its_swatch_with_the_same_hatch_as_the_map() {
        val legend = """, "legend": [{ "label": "wetland", "pattern": { "kind": "hatch", "color": "#C0392B", "angle": 45, "spacing": 8 } }]"""
        val withLegend = pixelsOf(renderScreen(mapSpec(SOLID, legend), emptyMap(), 400, 400, theme = SpecTheme.LIGHT).png, HATCH_INK)
        assertTrue(withLegend.isNotEmpty(), "the legend swatch drew no hatch, so it cannot match the map it labels")
    }

    @Test
    fun without_a_legend_no_swatch_is_drawn() {
        val none = pixelsOf(renderScreen(mapSpec(SOLID), emptyMap(), 400, 400, theme = SpecTheme.LIGHT).png, HATCH_INK)
        assertEquals(0, none.size, "hatch ink appeared with no hatched feature and no legend")
    }
}
