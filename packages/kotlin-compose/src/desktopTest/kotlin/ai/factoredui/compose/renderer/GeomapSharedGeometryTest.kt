package ai.factoredui.compose.renderer

import ai.factoredui.compose.crawl.renderScreen
import ai.factoredui.compose.schema.Spec
import ai.factoredui.compose.schema.resolveGeomapLayers
import ai.factoredui.compose.schema.unresolvedGeometryRefs
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

private val json = Json { ignoreUnknownKeys = true }

private const val KENT_RINGS = "[[[-85.80,42.80],[-85.30,42.80],[-85.30,43.20],[-85.80,43.20]]]"
private const val LAKE_HOLE_RINGS =
    "[[[-85.80,42.80],[-85.30,42.80],[-85.30,43.20],[-85.80,43.20]],[[-85.60,42.95],[-85.50,42.95],[-85.50,43.05],[-85.60,43.05]]]"

private val GEOMETRIES = mapOf("kent" to kentRings(KENT_RINGS), "kent-with-lake" to kentRings(LAKE_HOLE_RINGS))

private fun kentRings(ringsJson: String): Any? = anyOf(json.parseToJsonElement(ringsJson))

private fun anyOf(element: kotlinx.serialization.json.JsonElement): Any? = when (element) {
    is kotlinx.serialization.json.JsonArray -> element.map(::anyOf)
    is kotlinx.serialization.json.JsonObject -> element.mapValues { anyOf(it.value) }
    is kotlinx.serialization.json.JsonPrimitive -> element.content.toDoubleOrNull() ?: element.content
    else -> null
}

private fun layer(id: String, geometry: String, fill: String) =
    mapOf("id" to id, "kind" to "fill", "features" to listOf(mapOf("id" to "$id-$geometry", "geometry" to geometry, "fill" to fill)))

private fun specWith(layers: String, geometries: String) = json.decodeFromString(
    Spec.serializer(),
    """
    {"spec_version":1,"renderer_min":1,"root":{"id":"map","type":"geomap","props":{
      "geometries": $geometries,
      "layers": $layers
    }}}
    """.trimIndent(),
)

private fun countOf(png: ByteArray, rgb: Int): Int {
    val image = ImageIO.read(ByteArrayInputStream(png))
    var count = 0
    for (y in 0 until image.height) for (x in 0 until image.width) if ((image.getRGB(x, y) and 0xFFFFFF) == rgb) count++
    return count
}

class GeomapSharedGeometryTest {

    @Test
    fun two_layers_referencing_one_geometry_both_draw_it() {
        val layers = resolveGeomapLayers(listOf(layer("population", "kent", "#2E86DE"), layer("vacancy", "kent", "#E67E22")), GEOMETRIES)
        assertEquals(listOf("population", "vacancy"), layers.map { it.id })
        assertTrue(layers.all { it.features.single().rings.isNotEmpty() }, "a referenced geometry resolved to no rings")
    }

    @Test
    fun a_shared_geometry_is_tessellated_once_not_once_per_layer() {
        val tessellation = tessellateGeomapLayers(
            resolveGeomapLayers(listOf(layer("population", "kent", "#2E86DE"), layer("vacancy", "kent", "#E67E22")), GEOMETRIES),
        )
        val first = tessellation.layers[0].features.single()
        val second = tessellation.layers[1].features.single()
        assertSame(first.worldRings, second.worldRings, "the same outline was projected twice")
        assertSame(first.triangleWorld, second.triangleWorld, "the same outline was triangulated twice")
    }

    @Test
    fun a_geometry_in_the_table_keeps_its_hole() {
        val spec = specWith(
            layers = """[{"id":"land","kind":"fill","features":[{"id":"k","geometry":"kent-with-lake","fill":"#16A085"}]}]""",
            geometries = """{"kent-with-lake": $LAKE_HOLE_RINGS}""",
        )
        val png = renderScreen(spec, emptyMap(), 400, 400, theme = SpecTheme.LIGHT).png
        val image = ImageIO.read(ByteArrayInputStream(png))
        assertEquals(SpecTheme.LIGHT.ground.toRgbInt(), image.getRGB(200, 200) and 0xFFFFFF, "the lake was drawn as land")
        assertTrue(countOf(png, 0x16A085) > 2_000, "the land around the lake did not draw")
    }

    @Test
    fun inline_rings_still_work_beside_the_table() {
        val spec = specWith(
            layers = """[{"id":"land","kind":"fill","features":[{"id":"k","rings":$KENT_RINGS,"fill":"#16A085"}]}]""",
            geometries = "{}",
        )
        assertTrue(countOf(renderScreen(spec, emptyMap(), 400, 400, theme = SpecTheme.LIGHT).png, 0x16A085) > 2_000)
    }

    @Test
    fun a_reference_to_a_missing_geometry_is_reported_not_silently_dropped() {
        val missing = unresolvedGeometryRefs(listOf(layer("population", "wayne", "#2E86DE")), GEOMETRIES)
        assertEquals(listOf("wayne"), missing)
    }

    @Test
    fun a_missing_geometry_puts_a_notice_on_the_map() {
        val broken = specWith(
            layers = """[{"id":"land","kind":"fill","features":[{"id":"w","geometry":"wayne","fill":"#16A085"}]}]""",
            geometries = """{"kent": $KENT_RINGS}""",
        )
        val intact = specWith(
            layers = """[{"id":"land","kind":"fill","features":[{"id":"k","geometry":"kent","fill":"#16A085"}]}]""",
            geometries = """{"kent": $KENT_RINGS}""",
        )
        val brokenNodes = renderScreen(broken, emptyMap(), 400, 400, theme = SpecTheme.LIGHT)
        val intactNodes = renderScreen(intact, emptyMap(), 400, 400, theme = SpecTheme.LIGHT)
        val noticeInk = SpecTheme.LIGHT.ink.toRgbInt()
        assertTrue(countOf(brokenNodes.png, noticeInk) > 20, "a missing geometry left no mark on the map")
        assertEquals(0, countOf(intactNodes.png, noticeInk), "a notice appeared on a map with no missing geometry")
    }
}
