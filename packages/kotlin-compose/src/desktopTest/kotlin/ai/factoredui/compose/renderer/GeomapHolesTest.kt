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

private const val OUTER = "[[-85.610,42.920],[-85.590,42.920],[-85.590,42.940],[-85.610,42.940]]"
private const val HOLE = "[[-85.604,42.926],[-85.596,42.926],[-85.596,42.934],[-85.604,42.934]]"
private const val LAND = 0x16A085
private const val HATCH_INK = 0xC0392B

private fun parcelWithHole(style: String) = json.decodeFromString(
    Spec.serializer(),
    """
    {
      "spec_version": 1, "renderer_min": 1,
      "root": {
        "id": "map", "type": "geomap", "props": {
          "layers": [{ "id": "parcels", "kind": "fill", "features": [
            { "id": "donut", "rings": [$OUTER, $HOLE], $style }
          ] }]
        }
      }
    }
    """.trimIndent(),
)

private fun rgbAt(png: ByteArray, x: Int, y: Int): Int =
    ImageIO.read(ByteArrayInputStream(png)).getRGB(x, y) and 0xFFFFFF

private fun countOf(png: ByteArray, rgb: Int): Int {
    val image = ImageIO.read(ByteArrayInputStream(png))
    var count = 0
    for (y in 0 until image.height) for (x in 0 until image.width) if ((image.getRGB(x, y) and 0xFFFFFF) == rgb) count++
    return count
}

class GeomapHolesTest {

    @Test
    fun an_inner_ring_is_a_hole_not_more_land() {
        val png = renderScreen(parcelWithHole(""" "fill": "#16A085" """), emptyMap(), 400, 400, theme = SpecTheme.LIGHT).png
        assertNotEquals(LAND, rgbAt(png, 200, 200), "the middle of the hole was painted as land")
        assertEquals(SpecTheme.LIGHT.ground.toRgbInt(), rgbAt(png, 200, 200), "the ground should show through the hole")
    }

    @Test
    fun the_land_around_the_hole_is_still_filled() {
        val png = renderScreen(parcelWithHole(""" "fill": "#16A085" """), emptyMap(), 400, 400, theme = SpecTheme.LIGHT).png
        assertTrue(countOf(png, LAND) > 2_000, "the ring of land around the hole was not drawn")
    }

    @Test
    fun a_hatched_parcel_is_not_hatched_inside_its_hole() {
        val png = renderScreen(
            parcelWithHole(""" "pattern": { "kind": "hatch", "color": "#C0392B", "spacing": 6 } """),
            emptyMap(),
            400,
            400,
            theme = SpecTheme.LIGHT,
        ).png
        val holeInk = (185..215).sumOf { x -> (185..215).count { y -> rgbAt(png, x, y) == HATCH_INK } }
        assertEquals(0, holeInk, "hatch stripes were drawn inside the hole")
        assertTrue(countOf(png, HATCH_INK) > 100, "the land around the hole lost its hatch")
    }

    @Test
    fun a_tap_in_the_hole_and_the_drawing_of_the_hole_agree() {
        val tessellation = tessellateGeomapLayers(
            ai.factoredui.compose.schema.resolveGeomapLayers(
                listOf(
                    mapOf(
                        "id" to "parcels",
                        "kind" to "fill",
                        "features" to listOf(
                            mapOf(
                                "id" to "donut",
                                "fill" to "#16A085",
                                "rings" to listOf(
                                    json.decodeFromString<List<List<Double>>>(OUTER),
                                    json.decodeFromString<List<List<Double>>>(HOLE),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
        val view = fitGeomapViewport(tessellation.worldBounds, 400f, 400f)
        assertEquals(null, hitTestGeomap(tessellation, view, 400f, 400f, 200f, 200f), "a tap in the hole hit the parcel")
    }
}
