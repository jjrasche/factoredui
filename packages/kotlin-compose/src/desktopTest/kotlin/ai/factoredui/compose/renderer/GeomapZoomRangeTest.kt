package ai.factoredui.compose.renderer

import ai.factoredui.compose.crawl.driveScreen
import ai.factoredui.compose.crawl.renderScreen
import ai.factoredui.compose.schema.Spec
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val json = Json { ignoreUnknownKeys = true }

private const val COUNTY = 0x2E86DE
private const val PARCEL = 0xE67E22
private const val DRAWN_FLOOR = 2_000

private fun zoomedSpec(zoom: Double) = json.decodeFromString(
    Spec.serializer(),
    """
    {"spec_version":1,"renderer_min":1,"root":{"id":"map","type":"geomap","props":{
      "viewport": {"lon": -85.65, "lat": 42.95, "zoom": $zoom},
      "on_feature_tap": "tapped",
      "layers": [
        {"id":"county","kind":"fill","max_zoom":12,"features":[
          {"id":"kent","fill":"#2E86DE","rings":[[[-86.20,42.60],[-85.10,42.60],[-85.10,43.30],[-86.20,43.30]]]}]},
        {"id":"parcels","kind":"fill","min_zoom":12,"features":[
          {"id":"lot","fill":"#E67E22","rings":[[[-85.70,42.93],[-85.60,42.93],[-85.60,42.97],[-85.70,42.97]]]}]}
      ]
    }}}
    """.trimIndent(),
)

private fun countOf(png: ByteArray, rgb: Int): Int {
    val image = ImageIO.read(ByteArrayInputStream(png))
    var count = 0
    for (y in 0 until image.height) for (x in 0 until image.width) if ((image.getRGB(x, y) and 0xFFFFFF) == rgb) count++
    return count
}

private fun rendered(zoom: Double) = renderScreen(zoomedSpec(zoom), emptyMap(), 400, 400, theme = SpecTheme.LIGHT).png

class GeomapZoomRangeTest {

    @Test
    fun below_its_min_zoom_a_layer_is_hidden() {
        val png = rendered(11.0)
        assertEquals(0, countOf(png, PARCEL), "parcels drew at zoom 11 though they start at 12")
        assertTrue(countOf(png, COUNTY) > DRAWN_FLOOR, "the county did not draw at zoom 11")
    }

    @Test
    fun at_its_min_zoom_a_layer_is_shown() {
        assertTrue(countOf(rendered(12.0), PARCEL) > DRAWN_FLOOR, "parcels did not draw at their own min_zoom")
    }

    @Test
    fun at_its_max_zoom_a_layer_is_already_hidden() {
        assertEquals(0, countOf(rendered(12.0), COUNTY), "the county still drew at its max_zoom")
    }

    @Test
    fun a_layer_hidden_by_zoom_cannot_be_tapped() {
        driveScreen(zoomedSpec(13.0), widthDp = 400, heightDp = 400).use { screen ->
            val offTheParcel = screen.tapAt(10f, 10f)
            assertEquals(emptyList(), offTheParcel.map { it.params["layer_id"] }, "a tap reached the county hidden above its max_zoom")
        }
    }
}
