package ai.factoredui.compose.renderer

import ai.factoredui.compose.crawl.renderScreen
import ai.factoredui.compose.schema.Spec
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val json = Json { ignoreUnknownKeys = true }

private const val MAP_SIZE = 400
private const val ZOOM = 12.0
private const val CENTRE_LON = -85.65
private const val CENTRE_LAT = 42.95

private const val LON_PER_PX = 360.0 / (256 * 4096)

private fun lonAtPx(x: Int) = CENTRE_LON + (x - MAP_SIZE / 2) * LON_PER_PX

private fun spec(features: String, underlay: String = "") = json.decodeFromString(
    Spec.serializer(),
    """
    {"spec_version":1,"renderer_min":1,"root":{"id":"map","type":"geomap","props":{
      "viewport": {"lon": $CENTRE_LON, "lat": $CENTRE_LAT, "zoom": $ZOOM},
      "layers": [$underlay {"id":"places","kind":"point","features":[$features]}]
    }}}
    """.trimIndent(),
)

private fun place(id: String, xPx: Int, label: String?) =
    """{"id":"$id","point":[${lonAtPx(xPx)},$CENTRE_LAT],"radius":3,"fill":"#95A5A6" ${label?.let { ",\"label\":\"$it\"" } ?: ""}}"""

private fun image(spec: Spec): BufferedImage =
    ImageIO.read(ByteArrayInputStream(renderScreen(spec, emptyMap(), MAP_SIZE, MAP_SIZE, theme = SpecTheme.LIGHT).png))

private fun isInk(rgb: Int) = ((rgb shr 16) and 0xFF) < 110 && ((rgb shr 8) and 0xFF) < 110 && (rgb and 0xFF) < 110

private fun inkColumns(image: BufferedImage): List<Int> {
    val columns = mutableListOf<Int>()
    for (y in 0 until image.height) for (x in 0 until image.width) if (isInk(image.getRGB(x, y) and 0xFFFFFF)) columns += x
    return columns
}

class GeomapPointLabelSideTest {

    @Test
    fun a_point_near_the_right_edge_writes_its_label_to_the_left() {
        val nearEdge = MAP_SIZE - 20
        val columns = inkColumns(image(spec(place("edge", nearEdge, "Sterling Heights"))))
        assertTrue(columns.size > 20, "the label did not draw at all")
        assertTrue(columns.all { it < nearEdge }, "label ink landed right of a point with no room there")
    }

    @Test
    fun a_label_that_fits_on_neither_side_is_not_drawn_cut_off() {
        val columns = inkColumns(image(spec(place("squeezed", MAP_SIZE / 2, "An improbably long settlement name that no side can hold"))))
        assertEquals(emptyList(), columns, "a label too long for either side was drawn and clipped")
    }

    @Test
    fun a_label_crowded_on_its_right_moves_to_its_left_instead_of_vanishing() {
        val alone = inkColumns(image(spec(place("west", 180, "Warren") + "," + place("east", 215, null)))).size
        val crowded = inkColumns(image(spec(place("west", 180, "Warren") + "," + place("east", 215, "Detroit"))))
        assertTrue(crowded.any { it < 180 }, "the crowded label did not move to the left of its point")
        assertTrue(crowded.size > alone * 3 / 2, "only one of two labels drew ($crowded.size ink px against one label's $alone)")
    }

    @Test
    fun a_label_over_a_dark_fill_carries_a_halo_of_the_ground() {
        val darkLand = """{"id":"land","kind":"fill","features":[{"id":"l","fill":"#1B2631",
            "rings":[[[-85.70,42.90],[-85.60,42.90],[-85.60,43.00],[-85.70,43.00]]]}]},"""
        val ground = SpecTheme.LIGHT.ground.toRgbInt()
        fun groundCount(labelled: Boolean): Int {
            val rendered = image(spec(place("p", MAP_SIZE / 2 - 60, if (labelled) "Ada" else null), underlay = darkLand))
            var count = 0
            for (y in 150 until 250) for (x in 100 until 300) if ((rendered.getRGB(x, y) and 0xFFFFFF) == ground) count++
            return count
        }
        assertTrue(groundCount(labelled = true) > groundCount(labelled = false) + 30, "the label on dark land drew no halo")
    }
}
