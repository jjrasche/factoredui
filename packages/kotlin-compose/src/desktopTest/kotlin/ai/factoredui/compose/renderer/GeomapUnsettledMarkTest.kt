package ai.factoredui.compose.renderer

import ai.factoredui.compose.crawl.driveScreen
import ai.factoredui.compose.schema.GeomapPatternKind
import ai.factoredui.compose.schema.Spec
import ai.factoredui.compose.score.ScoreShadeReading
import ai.factoredui.compose.score.readScoreShade
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val json = Json { ignoreUnknownKeys = true }
private const val MAP_SIZE = 400
private const val MARK = 0x00FF00
private const val MARK_ALLOWANCE = 300
private const val RAMP = """{"stops":[[0,"#2E86DE"],[1,"#E67E22"]],"no_figure":"#FF00FF"}"""
private const val SCALING = """{"spring":{"good":"high"},"drive":{"good":"low"}}"""

private fun specOf(driveOfTheOnlyParcel: String): Spec = json.decodeFromString(
    Spec.serializer(),
    """
    {"spec_version":1,"renderer_min":1,"root":{"id":"map","type":"geomap","props":{
      "shade_by": "{map.shade}",
      "layers": [{"id":"parcels","kind":"fill","features":[
        {"id":"only","figures":{"spring":50.0,"drive":$driveOfTheOnlyParcel},"rings":[[[-85.90,42.80],[-85.30,42.80],[-85.30,43.10],[-85.90,43.10]]]},
        {"id":"loader","figures":{"spring":0.0,"drive":0.0},"rings":[[[-85.20,42.80],[-85.00,42.80],[-85.00,42.81],[-85.20,42.81]]]},
        {"id":"loader2","figures":{"spring":100.0,"drive":100.0},"rings":[[[-84.90,42.80],[-84.89,42.80],[-84.89,42.81],[-84.90,42.81]]]}
      ]}]
    }}}
    """.trimIndent(),
)

private fun JsonElement.toPlain(): Any? = when (this) {
    is JsonObject -> mapValues { it.value.toPlain() }
    is JsonArray -> map { it.toPlain() }
    is JsonNull -> null
    is JsonPrimitive -> if (isString) content else content.toBooleanStrictOrNull() ?: content.toDouble()
}

private fun record(mark: String = "#00ff00"): Any? =
    json.parseToJsonElement(
        """{"weights":{"spring":1,"drive":1},"figure_scaling":$SCALING,"ramp":$RAMP,"unsettled_mark":"$mark"}""",
    ).toPlain()

private fun countOf(png: ByteArray, rgb: Int): Int {
    val image = ImageIO.read(ByteArrayInputStream(png))
    var count = 0
    for (y in 0 until image.height) for (x in 0 until image.width) if ((image.getRGB(x, y) and 0xFFFFFF) == rgb) count++
    return count
}

class GeomapUnsettledMarkTest {
    @Test
    fun a_thing_with_a_missing_figure_draws_the_mark_and_a_settled_thing_does_not() {
        driveScreen(specOf("null"), widthDp = MAP_SIZE, heightDp = MAP_SIZE).use { screen ->
            screen.type("map.shade", record())
            assertTrue(countOf(screen.frame().png, MARK) > MARK_ALLOWANCE, "the unsettled thing carried no mark")
        }
        driveScreen(specOf("50.0"), widthDp = MAP_SIZE, heightDp = MAP_SIZE).use { screen ->
            screen.type("map.shade", record())
            assertEquals(0, countOf(screen.frame().png, MARK), "a settled thing was marked")
        }
    }

    @Test
    fun the_mark_is_a_hatch_in_the_given_colour_and_only_for_a_record_that_scores_raw() {
        val unsettled = mapOf<String, Double?>("spring" to 50.0, "drive" to null)
        val settled = mapOf<String, Double?>("spring" to 50.0, "drive" to 50.0)
        val reading = readScoreShade("shade_by", record("#00ff00")) as ScoreShadeReading.Readable
        val shade = reading.shade.withExtentsOf(listOf(unsettled, settled))
        val pattern = assertNotNull(unsettledMarkFor(shade, emptyMap(), unsettled))
        assertEquals(GeomapPatternKind.HATCH, pattern.kind)
        assertEquals("#00ff00", pattern.color)
        assertNull(unsettledMarkFor(shade, emptyMap(), settled))
    }
}
