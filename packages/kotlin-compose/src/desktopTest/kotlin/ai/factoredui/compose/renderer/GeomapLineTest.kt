package ai.factoredui.compose.renderer

import ai.factoredui.compose.crawl.renderScreen
import ai.factoredui.compose.schema.Spec
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue

private val json = Json { ignoreUnknownKeys = true }

private const val ROAD = 0x7F8C8D

private fun roadSpec(rings: String, geometries: String = "{}") = json.decodeFromString(
    Spec.serializer(),
    """
    {"spec_version":1,"renderer_min":1,"root":{"id":"map","type":"geomap","props":{
      "viewport": {"lon": -85.65, "lat": 42.95, "zoom": 11},
      "geometries": $geometries,
      "layers": [{"id":"roads","kind":"line","features":[{"id":"m6","stroke":"#7F8C8D","stroke_width":3, $rings}]}]
    }}}
    """.trimIndent(),
)

private fun roadPixels(spec: Spec): Int {
    val image = ImageIO.read(ByteArrayInputStream(renderScreen(spec, emptyMap(), 400, 400, theme = SpecTheme.LIGHT).png))
    var count = 0
    for (y in 0 until image.height) for (x in 0 until image.width) if ((image.getRGB(x, y) and 0xFFFFFF) == ROAD) count++
    return count
}

class GeomapLineTest {

    @Test
    fun a_straight_road_of_two_points_draws() {
        assertTrue(roadPixels(roadSpec(""""rings": [[-85.80, 42.95], [-85.50, 42.95]]""")) > 300, "a two-point road drew nothing")
    }

    @Test
    fun a_two_point_road_in_the_geometry_table_draws() {
        val spec = roadSpec(""""geometry": "m6"""", geometries = """{"m6": [[-85.80, 42.95], [-85.50, 42.95]]}""")
        assertTrue(roadPixels(spec) > 300, "a two-point road referenced from the table drew nothing")
    }
}
