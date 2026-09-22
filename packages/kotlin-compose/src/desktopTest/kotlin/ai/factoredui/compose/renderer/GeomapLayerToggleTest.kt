package ai.factoredui.compose.renderer

import ai.factoredui.compose.crawl.driveScreen
import ai.factoredui.compose.schema.Spec
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val json = Json { ignoreUnknownKeys = true }

private const val WETLAND = 0x2E86DE
private const val FLOOD = 0xE67E22

private val TOGGLE_SPEC = json.decodeFromString(
    Spec.serializer(),
    """
    {"spec_version":1,"renderer_min":1,"root":{"id":"map","type":"geomap","props":{
      "layer_visibility": "{map.visibility}",
      "layers": [
        {"id":"wetland","kind":"fill","features":[{"id":"w","fill":"#2E86DE","rings":[[[-85.80,42.80],[-85.50,42.80],[-85.50,43.10],[-85.80,43.10]]]}]},
        {"id":"flood","kind":"fill","features":[{"id":"f","fill":"#E67E22","rings":[[[-85.40,42.80],[-85.10,42.80],[-85.10,43.10],[-85.40,43.10]]]}]}
      ],
      "legend": [
        {"label":"wetland","fill":"#2E86DE","layer":"wetland"},
        {"label":"just a class","fill":"#E67E22"}
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

private const val MAP_SIZE = 400
private const val LEGEND_SWATCH_ALLOWANCE = 2_000

class GeomapLayerToggleTest {

    @Test
    fun tapping_a_legend_entry_hides_its_layer() {
        driveScreen(TOGGLE_SPEC, widthDp = MAP_SIZE, heightDp = MAP_SIZE).use { screen ->
            val before = countOf(screen.frame().png, WETLAND)
            screen.tap("map:legend:wetland")
            val after = countOf(screen.frame().png, WETLAND)
            assertTrue(before > LEGEND_SWATCH_ALLOWANCE, "the wetland layer was not drawn to begin with ($before px)")
            assertTrue(after < LEGEND_SWATCH_ALLOWANCE, "wetland still covers $after px after being toggled off")
        }
    }

    @Test
    fun tapping_it_again_brings_the_layer_back() {
        driveScreen(TOGGLE_SPEC, widthDp = MAP_SIZE, heightDp = MAP_SIZE).use { screen ->
            val before = countOf(screen.frame().png, WETLAND)
            screen.tap("map:legend:wetland")
            screen.tap("map:legend:wetland")
            assertEquals(before, countOf(screen.frame().png, WETLAND), "the layer did not come back after a second tap")
        }
    }

    @Test
    fun a_legend_entry_naming_no_layer_is_inert() {
        driveScreen(TOGGLE_SPEC, widthDp = MAP_SIZE, heightDp = MAP_SIZE).use { screen ->
            val floodBefore = countOf(screen.frame().png, FLOOD)
            val wetlandBefore = countOf(screen.frame().png, WETLAND)
            screen.tap("map:legend:1")
            assertEquals(floodBefore, countOf(screen.frame().png, FLOOD), "a class entry toggled a layer")
            assertEquals(wetlandBefore, countOf(screen.frame().png, WETLAND), "a class entry toggled a layer")
        }
    }

    @Test
    fun the_host_can_hide_a_layer_through_the_bound_visibility() {
        driveScreen(TOGGLE_SPEC, widthDp = MAP_SIZE, heightDp = MAP_SIZE).use { screen ->
            screen.type("map.visibility", mapOf("flood" to false))
            assertTrue(countOf(screen.frame().png, FLOOD) < LEGEND_SWATCH_ALLOWANCE, "the host hid flood and it still drew")
        }
    }
}
