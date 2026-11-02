package ai.factoredui.compose.renderer

import ai.factoredui.compose.crawl.driveScreen
import ai.factoredui.compose.schema.Spec
import ai.factoredui.compose.schema.resolveGeomapLayers
import ai.factoredui.compose.score.readScoreShade
import ai.factoredui.compose.score.ScoreShadeReading
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val json = Json { ignoreUnknownKeys = true }

private const val LOW = 0x2E86DE
private const val HIGH = 0xE67E22
private const val UNKNOWN = 0xFF00FF
private const val MAP_SIZE = 400
private const val DRAWN_ALLOWANCE = 2_000

private const val RAMP = """{"stops":[[0,"#2E86DE"],[1,"#E67E22"]],"no_figure":"#FF00FF"}"""

// Two features side by side: "wide" is three times the area of "narrow".
private fun spec(thirdFeature: String = "") = json.decodeFromString(
    Spec.serializer(),
    """
    {"spec_version":1,"renderer_min":1,"root":{"id":"map","type":"geomap","props":{
      "shade_by": "{map.shade}",
      "layers": [{"id":"parcels","kind":"fill","features":[
        {"id":"wide","values":{"a":1.0,"b":0.0},"rings":[[[-85.90,42.80],[-85.30,42.80],[-85.30,43.10],[-85.90,43.10]]]},
        {"id":"narrow","values":{"a":0.0,"b":1.0},"rings":[[[-85.20,42.80],[-85.00,42.80],[-85.00,43.10],[-85.20,43.10]]]}$thirdFeature
      ]}]
    }}}
    """.trimIndent(),
)

private fun shade(weights: String, minimum: String? = null): Any? =
    (json.parseToJsonElement("""{"weights":$weights,"ramp":$RAMP${minimum?.let { ""","minimum":$it""" }.orEmpty()}}""")
        as kotlinx.serialization.json.JsonObject).toPlain()

private fun kotlinx.serialization.json.JsonElement.toPlain(): Any? = when (this) {
    is kotlinx.serialization.json.JsonObject -> mapValues { it.value.toPlain() }
    is kotlinx.serialization.json.JsonArray -> map { it.toPlain() }
    is kotlinx.serialization.json.JsonNull -> null
    is kotlinx.serialization.json.JsonPrimitive -> if (isString) content else content.toBooleanStrictOrNull() ?: content.toDouble()
}

private fun countOf(png: ByteArray, rgb: Int): Int {
    val image = ImageIO.read(ByteArrayInputStream(png))
    var count = 0
    for (y in 0 until image.height) for (x in 0 until image.width) if ((image.getRGB(x, y) and 0xFFFFFF) == rgb) count++
    return count
}

class GeomapShadeTest {
    @Test
    fun moving_a_weight_recolours_the_parcels() {
        driveScreen(spec(), widthDp = MAP_SIZE, heightDp = MAP_SIZE).use { screen ->
            screen.type("map.shade", shade("""{"a":1,"b":0}"""))
            val byA = screen.frame().png
            screen.type("map.shade", shade("""{"a":0,"b":1}"""))
            val byB = screen.frame().png
            assertTrue(countOf(byA, HIGH) > countOf(byA, LOW) + DRAWN_ALLOWANCE, "by a, the wide parcel should be the high colour")
            assertTrue(countOf(byB, LOW) > countOf(byB, HIGH) + DRAWN_ALLOWANCE, "by b, the wide parcel should be the low colour")
        }
    }

    @Test
    fun a_weight_written_at_its_own_path_inside_the_record_recolours_the_parcels() {
        driveScreen(spec(), widthDp = MAP_SIZE, heightDp = MAP_SIZE).use { screen ->
            screen.type("map.shade", shade("""{"a":1,"b":0}"""))
            val before = screen.frame().png
            screen.type("map.shade.weights.a", 0.0)
            screen.type("map.shade.weights.b", 1.0)
            val after = screen.frame().png
            assertTrue(countOf(before, HIGH) > countOf(before, LOW) + DRAWN_ALLOWANCE, "by a, the wide parcel should be the high colour")
            assertTrue(countOf(after, LOW) > countOf(after, HIGH) + DRAWN_ALLOWANCE, "after the two writes, the wide parcel should be the low colour")
        }
    }

    @Test
    fun a_minimum_hides_the_parcels_whose_score_falls_under_it() {
        driveScreen(spec(), widthDp = MAP_SIZE, heightDp = MAP_SIZE).use { screen ->
            screen.type("map.shade", shade("""{"a":1,"b":0}""", minimum = "0.5"))
            val frame = screen.frame().png
            assertTrue(countOf(frame, HIGH) > DRAWN_ALLOWANCE, "the parcel at the minimum or over it was not drawn")
            assertTrue(countOf(frame, LOW) < DRAWN_ALLOWANCE, "the parcel under the minimum still drew")
        }
    }

    @Test
    fun moving_a_weight_does_not_tessellate_the_layers_again() {
        driveScreen(spec(), widthDp = MAP_SIZE, heightDp = MAP_SIZE).use { screen ->
            screen.type("map.shade", shade("""{"a":1,"b":0}"""))
            screen.frame()
            val before = geomapTessellationRuns
            screen.type("map.shade", shade("""{"a":0.3,"b":0.7}"""))
            screen.frame()
            screen.type("map.shade", shade("""{"a":0.9,"b":0.1}""", minimum = "0.2"))
            screen.frame()
            assertEquals(before, geomapTessellationRuns, "a weight or minimum change tessellated again")
        }
    }

    @Test
    fun a_parcel_that_lacks_a_weighted_field_draws_in_the_no_figure_colour_and_does_not_break_the_layer() {
        val lacking = ""","""+"""{"id":"lacking","values":{"a":0.5},"rings":[[[-85.20,42.60],[-85.00,42.60],[-85.00,42.75],[-85.20,42.75]]]}"""
        driveScreen(spec(lacking), widthDp = MAP_SIZE, heightDp = MAP_SIZE).use { screen ->
            screen.type("map.shade", shade("""{"a":1,"b":1}"""))
            val frame = screen.frame().png
            assertTrue(countOf(frame, UNKNOWN) > 0, "the parcel with no b was not drawn in the no figure colour")
        }
    }

    @Test
    fun in_rank_scale_a_minimum_keeps_the_top_share_of_the_parcels_whatever_their_scores() {
        val rankRamp = mapOf("stops" to listOf(listOf(0.25, "#2E86DE"), listOf(0.75, "#E67E22")), "no_figure" to "#FF00FF")
        val shadeInRank = (shade("""{"a":1}""", minimum = "0.6") as Map<*, *>) + mapOf("scale" to "rank", "ramp" to rankRamp)
        driveScreen(spec(), widthDp = MAP_SIZE, heightDp = MAP_SIZE).use { screen ->
            screen.type("map.shade", shadeInRank)
            val frame = screen.frame().png
            assertTrue(countOf(frame, HIGH) > DRAWN_ALLOWANCE, "the top-ranked parcel was not drawn")
            assertTrue(countOf(frame, LOW) < DRAWN_ALLOWANCE, "the bottom-ranked parcel drew under a minimum of 0.6")
        }
    }

    @Test
    fun a_tap_on_a_hidden_parcel_hits_nothing() {
        val layers = resolveGeomapLayers(
            json.parseToJsonElement(
                """[{"id":"p","kind":"fill","features":[{"id":"low","values":{"a":0.1},"rings":[[[-1.0,-1.0],[1.0,-1.0],[1.0,1.0],[-1.0,1.0]]]}]}]""",
            ).toPlain(),
        )
        val tessellation = tessellateGeomapLayers(layers)
        val shade = (readScoreShade("shade_by", shade("""{"a":1}""", minimum = "0.5")) as ScoreShadeReading.Readable).shade
        val viewport = ai.factoredui.compose.schema.GeomapViewport(0.0, 0.0, 6f)
        assertNotNull(hitTestGeomap(tessellation, viewport, 400f, 400f, 200f, 200f))
        assertNull(hitTestGeomap(tessellation, viewport, 400f, 400f, 200f, 200f, shade = shade))
    }

    @Test
    fun a_record_that_cannot_be_read_is_refused_with_its_reason() {
        val reading = readScoreShade("shade_by", shade("""{"a":-1}"""))
        assertTrue(assertIs<ScoreShadeReading.Refused>(reading).notice.contains("negative"))
    }
}
