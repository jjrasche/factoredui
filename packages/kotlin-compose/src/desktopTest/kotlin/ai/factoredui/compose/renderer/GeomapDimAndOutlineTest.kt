package ai.factoredui.compose.renderer

import ai.factoredui.compose.crawl.driveScreen
import ai.factoredui.compose.schema.Spec
import ai.factoredui.compose.schema.resolveGeomapLayers
import androidx.compose.ui.graphics.toArgb
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
import kotlin.test.assertTrue

private val json = Json { ignoreUnknownKeys = true }
private const val MAP_SIZE = 400
private const val LOW = 0x2E86DE
private const val HIGH = 0xE67E22
private const val DRAWN_ALLOWANCE = 2_000
private const val RAMP = """{"stops":[[0,"#2E86DE"],[1,"#E67E22"]],"no_figure":"#FF00FF"}"""

private val SPEC = json.decodeFromString(
    Spec.serializer(),
    """
    {"spec_version":1,"renderer_min":1,"root":{"id":"map","type":"geomap","props":{
      "shade_by": "{map.shade}",
      "layers": [{"id":"parcels","kind":"fill","features":[
        {"id":"high","values":{"a":1.0},"rings":[[[-85.90,42.80],[-85.30,42.80],[-85.30,43.10],[-85.90,43.10]]]},
        {"id":"low","values":{"a":0.0},"rings":[[[-85.20,42.80],[-85.00,42.80],[-85.00,43.10],[-85.20,43.10]]]}]}]
    }}}
    """.trimIndent(),
)

private fun JsonElement.toPlain(): Any? = when (this) {
    is JsonObject -> mapValues { it.value.toPlain() }
    is JsonArray -> map { it.toPlain() }
    is JsonNull -> null
    is JsonPrimitive -> if (isString) content else content.toBooleanStrictOrNull() ?: content.toDouble()
}

private fun record(below: String? = null): Any? =
    json.parseToJsonElement("""{"weights":{"a":1},"minimum":0.5,"ramp":$RAMP${below?.let { ""","below":"$it"""" }.orEmpty()}}""").toPlain()

private fun countOf(png: ByteArray, rgb: Int): Int {
    val image = ImageIO.read(ByteArrayInputStream(png))
    var count = 0
    for (y in 0 until image.height) for (x in 0 until image.width) if ((image.getRGB(x, y) and 0xFFFFFF) == rgb) count++
    return count
}

private fun tessellationOf(layers: String): GeomapTessellation =
    tessellateGeomapLayers(resolveGeomapLayers(json.parseToJsonElement(layers).toPlain()))

private val EVERYWHERE = WorldBounds(0.0, 0.0, 1.0, 1.0)

private fun parcel(id: String, west: Double) =
    """{"id":"$id","values":{"a":0.5},"rings":[[[$west,0.0],[${west + 1.0},0.0],[${west + 1.0},1.0],[$west,1.0]]]}"""

class GeomapDimAndOutlineTest {
    @Test
    fun a_parcel_under_the_minimum_is_drawn_in_the_themes_dim_colour_not_hidden() {
        val dim = SpecTheme.LIGHT.dimmed.toArgb() and 0xFFFFFF
        driveScreen(SPEC, widthDp = MAP_SIZE, heightDp = MAP_SIZE).use { screen ->
            screen.type("map.shade", record())
            val frame = screen.frame().png
            assertTrue(countOf(frame, HIGH) > DRAWN_ALLOWANCE, "the parcel over the minimum was not drawn")
            assertTrue(countOf(frame, dim) > DRAWN_ALLOWANCE, "the parcel under the minimum was not drawn dimmed")
            assertEquals(0, countOf(frame, LOW), "the parcel under the minimum kept its ramp colour")
        }
    }

    @Test
    fun a_parcel_under_the_minimum_is_hidden_when_the_record_says_hide() {
        val dim = SpecTheme.LIGHT.dimmed.toArgb() and 0xFFFFFF
        driveScreen(SPEC, widthDp = MAP_SIZE, heightDp = MAP_SIZE).use { screen ->
            screen.type("map.shade", record(below = "hide"))
            val frame = screen.frame().png
            assertEquals(0, countOf(frame, dim), "a parcel the record hides still drew dimmed")
            assertTrue(countOf(frame, HIGH) > DRAWN_ALLOWANCE)
        }
    }

    @Test
    fun selecting_one_parcel_outlines_exactly_one_feature_even_when_neighbouring_layers_repeat_its_id() {
        val tessellation = tessellationOf(
            """[{"id":"region-a","kind":"fill","features":[${parcel("p1", 0.0)},${parcel("p2", 1.0)},${parcel("p3", 2.0)}]},
                {"id":"region-b","kind":"fill","features":[${parcel("p1", 0.0)},${parcel("p4", -1.0)}]}]""",
        )
        assertEquals(1, featuresToOutline(tessellation, setOf("p1"), 6f, EVERYWHERE, null).size)
        assertEquals(2, featuresToOutline(tessellation, setOf("p1", "p3"), 6f, EVERYWHERE, null).size)
        assertEquals(0, featuresToOutline(tessellation, emptySet(), 6f, EVERYWHERE, null).size)
    }

    @Test
    fun a_selected_parcel_that_the_minimum_hides_or_dims_is_not_outlined() {
        val tessellation = tessellationOf("""[{"id":"region-a","kind":"fill","features":[${parcel("p1", 0.0)}]}]""")
        val shade = (ai.factoredui.compose.score.readScoreShade("shade_by", json.parseToJsonElement(
            """{"weights":{"a":1},"minimum":0.9,"ramp":$RAMP}""").toPlain()) as ai.factoredui.compose.score.ScoreShadeReading.Readable).shade
        assertEquals(0, featuresToOutline(tessellation, setOf("p1"), 6f, EVERYWHERE, shade).size)
    }
}
