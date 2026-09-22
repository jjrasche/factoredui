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

// At zoom 12 near 43N one degree of latitude is roughly 4,000px. 0.001 degrees is 4px, so
// labels a step either side of a middle one are 8px apart — under any 12sp font's line
// height, on CI's fonts as well as a desktop's. 0.015 is 60px: clear, and inside the view.
private const val CROWDED = 0.001
private const val APART = 0.015

class GeomapLabelCollisionTest {

    @Test
    fun of_three_crowded_labels_one_takes_each_side_and_the_third_is_not_drawn() {
        val two = inkOf(
            placesSpec(place("a", CENTRE_LAT, "Ada"), place("b", CENTRE_LAT + CROWDED, "Ada"), place("c", CENTRE_LAT - CROWDED, null)),
        )
        val three = inkOf(
            placesSpec(place("a", CENTRE_LAT, "Ada"), place("b", CENTRE_LAT + CROWDED, "Ada"), place("c", CENTRE_LAT - CROWDED, "Ada")),
        )
        assertTrue(two > 40, "the two labels drew no ink, so the comparison means nothing")
        assertEquals(two, three, "a third label printed over the two already placed")
    }

    @Test
    fun labels_with_room_between_them_are_all_drawn() {
        val one = inkOf(placesSpec(place("a", CENTRE_LAT, "Ada"), place("b", CENTRE_LAT + APART, null)))
        val both = inkOf(placesSpec(place("a", CENTRE_LAT, "Ada"), place("b", CENTRE_LAT + APART, "Ada")))
        assertTrue(both > one * 3 / 2, "two well-separated labels drew $both ink px against one label's $one")
    }

    @Test
    fun the_label_on_the_upper_layer_keeps_the_first_choice_of_side() {
        val pointX = 54
        val upperAlone = inkColumnsRightOf(pointX, placesSpec(place("low", CENTRE_LAT, null, "towns"), place("high", CENTRE_LAT + CROWDED, "Grand Rapids", "cities")))
        val contested = inkColumnsRightOf(
            pointX,
            placesSpec(place("low", CENTRE_LAT, "Walker", "towns"), place("high", CENTRE_LAT + CROWDED, "Grand Rapids", "cities")),
        )
        assertTrue(upperAlone > 20, "the upper label drew no ink, so the comparison means nothing")
        assertEquals(upperAlone, contested, "the lower layer's label took the right-hand side from the upper layer's")
    }
}

private fun inkColumnsRightOf(xPx: Int, spec: Spec): Int {
    val image = ImageIO.read(ByteArrayInputStream(renderScreen(spec, emptyMap(), 400, 400, theme = SpecTheme.LIGHT).png))
    var count = 0
    for (y in 0 until image.height) for (x in xPx until image.width) {
        val rgb = image.getRGB(x, y) and 0xFFFFFF
        if (((rgb shr 16) and 0xFF) < 110 && ((rgb shr 8) and 0xFF) < 110 && (rgb and 0xFF) < 110) count++
    }
    return count
}
