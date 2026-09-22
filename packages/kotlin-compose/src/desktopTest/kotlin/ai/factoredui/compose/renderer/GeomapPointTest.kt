package ai.factoredui.compose.renderer

import ai.factoredui.compose.crawl.driveScreen
import ai.factoredui.compose.crawl.renderScreen
import ai.factoredui.compose.schema.Spec
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val json = Json { ignoreUnknownKeys = true }

private const val WELL = 0xC0392B
private const val HIGHLIGHT = 0xFF00FF
private const val RADIUS = 10
private const val CENTRE = 200f

private fun pointSpec(zoom: Double, label: String? = null, selected: String = "[]") = json.decodeFromString(
    Spec.serializer(),
    """
    {"spec_version":1,"renderer_min":1,"root":{"id":"map","type":"geomap","props":{
      "viewport": {"lon": -85.65, "lat": 42.95, "zoom": $zoom},
      "on_feature_tap": "tapped",
      "selected": $selected,
      "selection_stroke": "#FF00FF",
      "layers": [
        {"id":"wells","kind":"point","features":[
          {"id":"well-7","point":[-85.65,42.95],"radius":$RADIUS,"fill":"#C0392B" ${label?.let { ",\"label\":\"$it\"" } ?: ""}}]}
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

private fun rendered(spec: Spec) = renderScreen(spec, emptyMap(), 400, 400, theme = SpecTheme.LIGHT).png

private fun isDark(rgb: Int): Boolean =
    ((rgb shr 16) and 0xFF) < 110 && ((rgb shr 8) and 0xFF) < 110 && (rgb and 0xFF) < 110

class GeomapPointTest {

    private val discArea = (PI * RADIUS * RADIUS).toInt()

    @Test
    fun a_point_draws_a_disc_of_its_radius_in_pixels() {
        val drawn = countOf(rendered(pointSpec(zoom = 12.0)), WELL)
        assertTrue(drawn in (discArea * 2 / 3)..(discArea * 4 / 3), "a radius-$RADIUS point drew $drawn px, a disc is about $discArea")
    }

    @Test
    fun a_point_keeps_its_pixel_size_at_every_zoom() {
        val near = countOf(rendered(pointSpec(zoom = 16.0)), WELL)
        val far = countOf(rendered(pointSpec(zoom = 6.0)), WELL)
        assertTrue(far > 0 && near > 0, "the point vanished at one of the zooms ($far / $near)")
        assertTrue(kotlin.math.abs(near - far) < discArea / 5, "the disc changed size with zoom: $far px at 6, $near px at 16")
    }

    @Test
    fun a_tap_on_the_disc_hits_the_point_and_a_tap_well_clear_of_it_does_not() {
        driveScreen(pointSpec(zoom = 12.0), widthDp = 400, heightDp = 400).use { screen ->
            assertEquals(listOf("well-7"), screen.tapAt(CENTRE + RADIUS - 2, CENTRE).map { it.params["feature_id"] })
            assertEquals(emptyList(), screen.tapAt(CENTRE + RADIUS * 4, CENTRE).map { it.params["feature_id"] })
        }
    }

    @Test
    fun a_point_label_is_written_beside_the_disc_not_over_it() {
        val image = ImageIO.read(ByteArrayInputStream(rendered(pointSpec(zoom = 12.0, label = "Well 7"))))
        var besideInk = 0
        var discInk = 0
        for (y in 0 until image.height) for (x in 0 until image.width) {
            if (!isDark(image.getRGB(x, y) and 0xFFFFFF)) continue
            if (x > CENTRE + RADIUS) besideInk++ else discInk++
        }
        assertTrue(besideInk > 20, "the label drew no ink beside the point")
        assertEquals(0, discInk, "$discInk label pixels landed on or left of the disc")
    }

    @Test
    fun a_selected_point_is_ringed() {
        assertEquals(0, countOf(rendered(pointSpec(zoom = 12.0)), HIGHLIGHT))
        assertTrue(countOf(rendered(pointSpec(zoom = 12.0, selected = "[\"well-7\"]")), HIGHLIGHT) > 20, "the selected point drew no ring")
    }
}
