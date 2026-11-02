package ai.factoredui.compose.renderer

import ai.factoredui.compose.crawl.driveScreen
import ai.factoredui.compose.schema.Spec
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
private const val HIGH = 0xE67E22
private const val RAMP = """{"stops":[[0,"#2E86DE"],[1,"#E67E22"]],"no_figure":"#FF00FF"}"""

// The list sits over the lower part of the map; tapping a row selects its parcel and frames it.
private val SPEC = json.decodeFromString(
    Spec.serializer(),
    """
    {"spec_version":1,"renderer_min":1,"root":{"id":"page","type":"stack","props":{"fill":true},"children":[
      {"id":"map","type":"geomap","props":{
        "anchor":"fill","shade_by":"{map.shade}","shade_out":"{map.out}","shade_top":5,"selected":"{map.picked}","viewport":"{map.viewport}",
        "layers":[{"id":"parcels","kind":"fill","features":[
          {"id":"big","values":{"a":0.0},"rings":[[[-85.90,42.80],[-85.30,42.80],[-85.30,43.10],[-85.90,43.10]]]},
          {"id":"small","values":{"a":1.0},"rings":[[[-85.20,42.80],[-85.00,42.80],[-85.00,43.10],[-85.20,43.10]]]}]}]}},
      {"id":"list","type":"list","props":{"anchor":"bottom","data":"{map.out.top}","itemTemplate":
        {"id":"row","type":"button","props":{"label":"{row.id}"},
         "action":{"action":"set_binding","params":{"values":{"map.picked":["{row.id}"],"map.viewport.bounds":"{row.bounds}"}}}}}}
    ]}}
    """.trimIndent(),
)

private fun JsonElement.toPlain(): Any? = when (this) {
    is JsonObject -> mapValues { it.value.toPlain() }
    is JsonArray -> map { it.toPlain() }
    is JsonNull -> null
    is JsonPrimitive -> if (isString) content else content.toBooleanStrictOrNull() ?: content.toDouble()
}

private fun countOf(png: ByteArray, rgb: Int): Int {
    val image = ImageIO.read(ByteArrayInputStream(png))
    var count = 0
    for (y in 0 until image.height) for (x in 0 until image.width) if ((image.getRGB(x, y) and 0xFFFFFF) == rgb) count++
    return count
}

private fun countInRows(png: ByteArray, rgb: Int, rows: IntRange): Int {
    val image = ImageIO.read(ByteArrayInputStream(png))
    var count = 0
    for (y in rows) for (x in 0 until image.width) if ((image.getRGB(x, y) and 0xFFFFFF) == rgb) count++
    return count
}

class GeomapRankedListTest {
    @Test
    fun a_framed_parcel_stays_in_the_part_of_the_map_a_bottom_inset_leaves_free() {
        driveScreen(SPEC, widthDp = MAP_SIZE, heightDp = MAP_SIZE).use { screen ->
            screen.type("map.shade", json.parseToJsonElement("""{"weights":{"a":1},"ramp":$RAMP}""").toPlain())
            screen.type("map.viewport", json.parseToJsonElement("""{"bounds":[-85.20,42.80,-85.00,43.10],"margin":0.6,"inset_bottom":200}""").toPlain())
            val frame = screen.frame().png
            val image = ImageIO.read(ByteArrayInputStream(frame))
            val inset = 200 * image.height / MAP_SIZE
            assertTrue(countOf(frame, HIGH) > 1_000, "the parcel was not drawn at all")
            assertEquals(0, countInRows(frame, HIGH, (image.height - inset) until image.height), "the parcel runs under the inset")
        }
    }

    @Test
    fun tapping_a_row_selects_its_parcel_and_frames_it_on_the_map() {
        driveScreen(SPEC, widthDp = MAP_SIZE, heightDp = MAP_SIZE).use { screen ->
            screen.type("map.shade", json.parseToJsonElement("""{"weights":{"a":1},"ramp":$RAMP}""").toPlain())
            val before = countOf(screen.frame().png, HIGH)
            screen.tapText("small")
            val after = countOf(screen.frame().png, HIGH)
            @Suppress("UNCHECKED_CAST")
            val picked = (screen.store()["map"] as Map<String, Any?>)["picked"]
            assertEquals(listOf("small"), picked)
            assertTrue(after > before * 2, "the parcel was not framed: $before px before, $after px after")
        }
    }
}
