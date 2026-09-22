package ai.factoredui.compose.renderer

import ai.factoredui.compose.crawl.renderScreen
import ai.factoredui.compose.schema.Spec
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val json = Json { ignoreUnknownKeys = true }

private const val ZOOM = 12.0
private const val CENTRE_LAT = 42.95

private fun place(id: String, lat: Double, label: String?, layer: String = "places") =
    Triple(layer, id, """{"id":"$id","point":[-85.70,$lat],"radius":3,"fill":"#95A5A6" ${label?.let { ",\"label\":\"$it\"" } ?: ""}}""")

private fun placesSpec(vararg places: Triple<String, String, String>): Spec {
    val layers = places.groupBy { it.first }.entries.joinToString(",") { (layerId, members) ->
        """{"id":"$layerId","kind":"point","features":[${members.joinToString(",") { it.third }}]}"""
    }
    return json.decodeFromString(
        Spec.serializer(),
        """
        {"spec_version":1,"renderer_min":1,"root":{"id":"map","type":"geomap","props":{
          "viewport": {"lon": -85.65, "lat": $CENTRE_LAT, "zoom": $ZOOM},
          "layers": [$layers]
        }}}
        """.trimIndent(),
    )
}

private fun inkRows(spec: Spec): Map<Int, Int> {
    val image = ImageIO.read(ByteArrayInputStream(renderScreen(spec, emptyMap(), 400, 400, theme = SpecTheme.LIGHT).png))
    val rows = mutableMapOf<Int, Int>()
    for (y in 0 until image.height) for (x in 0 until image.width) {
        val rgb = image.getRGB(x, y) and 0xFFFFFF
        val isInk = ((rgb shr 16) and 0xFF) < 110 && ((rgb shr 8) and 0xFF) < 110 && (rgb and 0xFF) < 110
        if (isInk) rows[y] = (rows[y] ?: 0) + 1
    }
    return rows
}

private fun inkOf(spec: Spec) = inkRows(spec).values.sum()

// At zoom 12 near 43N one degree of latitude is roughly 1,400px, so 0.002 degrees is under
// 3px: close enough that two labels would print over each other.
private const val CROWDED = 0.002
private const val APART = 0.05

class GeomapLabelCollisionTest {

    @Test
    fun of_two_labels_that_would_overprint_only_one_is_drawn() {
        val one = inkOf(placesSpec(place("a", CENTRE_LAT, "Ada"), place("b", CENTRE_LAT + CROWDED, null)))
        val both = inkOf(placesSpec(place("a", CENTRE_LAT, "Ada"), place("b", CENTRE_LAT + CROWDED, "Ada")))
        assertTrue(one > 20, "the lone label drew no ink, so the comparison means nothing")
        assertEquals(one, both, "a second label printed over the first")
    }

    @Test
    fun labels_with_room_between_them_are_all_drawn() {
        val one = inkOf(placesSpec(place("a", CENTRE_LAT, "Ada"), place("b", CENTRE_LAT + APART, null)))
        val both = inkOf(placesSpec(place("a", CENTRE_LAT, "Ada"), place("b", CENTRE_LAT + APART, "Ada")))
        assertTrue(both > one * 3 / 2, "two well-separated labels drew $both ink px against one label's $one")
    }

    @Test
    fun the_label_on_the_upper_layer_wins_the_collision() {
        val upperAlone = inkRows(placesSpec(place("low", CENTRE_LAT, null, "towns"), place("high", CENTRE_LAT + CROWDED, "Grand Rapids", "cities")))
        val contested = inkRows(
            placesSpec(place("low", CENTRE_LAT, "Walker", "towns"), place("high", CENTRE_LAT + CROWDED, "Grand Rapids", "cities")),
        )
        assertEquals(upperAlone, contested, "the lower layer's label was drawn instead of, or over, the upper layer's")
    }
}
