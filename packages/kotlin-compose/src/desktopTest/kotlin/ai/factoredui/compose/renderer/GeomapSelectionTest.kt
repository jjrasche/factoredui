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

private const val HIGHLIGHT = 0xFF00FF
private const val MAP_SIZE = 400
private const val OUTLINE_FLOOR = 300

private const val WEST_PARCEL_X = 100f
private const val GAP_X = 200f
private const val MIDDLE_Y = 200f

private fun selectionSpec(selected: String) = json.decodeFromString(
    Spec.serializer(),
    """
    {"spec_version":1,"renderer_min":1,"root":{"id":"map","type":"geomap","props":{
      "selected": $selected,
      "selection_stroke": "#FF00FF",
      "on_feature_tap": "parcel_tapped",
      "layers": [
        {"id":"parcels","kind":"fill","features":[
          {"id":"west","fill":"#2E86DE","rings":[[[-85.80,42.80],[-85.50,42.80],[-85.50,43.10],[-85.80,43.10]]]},
          {"id":"east","fill":"#E67E22","rings":[[[-85.40,42.80],[-85.10,42.80],[-85.10,43.10],[-85.40,43.10]]]}
        ]}
      ]
    }}}
    """.trimIndent(),
)

private val BOUND = selectionSpec("\"{map.selected}\"")

private fun highlightOf(png: ByteArray): Int {
    val image = ImageIO.read(ByteArrayInputStream(png))
    var count = 0
    for (y in 0 until image.height) for (x in 0 until image.width) if ((image.getRGB(x, y) and 0xFFFFFF) == HIGHLIGHT) count++
    return count
}

private fun selectedIn(store: Map<String, Any?>): Any? = (store["map"] as? Map<*, *>)?.get("selected")

class GeomapSelectionTest {

    @Test
    fun a_feature_the_host_selects_is_outlined_and_nothing_is_outlined_otherwise() {
        driveScreen(BOUND, store = mapOf("map" to mapOf("selected" to listOf("west"))), MAP_SIZE, MAP_SIZE).use { screen ->
            assertTrue(highlightOf(screen.frame().png) > OUTLINE_FLOOR, "the selected parcel drew no highlight")
        }
        driveScreen(BOUND, store = mapOf("map" to mapOf("selected" to emptyList<String>())), MAP_SIZE, MAP_SIZE).use { screen ->
            assertEquals(0, highlightOf(screen.frame().png), "a highlight drew with nothing selected")
        }
    }

    @Test
    fun the_host_can_select_several_features_at_once() {
        val one = driveScreen(BOUND, store = mapOf("map" to mapOf("selected" to listOf("west"))), MAP_SIZE, MAP_SIZE)
            .use { highlightOf(it.frame().png) }
        val both = driveScreen(BOUND, store = mapOf("map" to mapOf("selected" to listOf("west", "east"))), MAP_SIZE, MAP_SIZE)
            .use { highlightOf(it.frame().png) }
        assertTrue(both > one * 3 / 2, "two selected parcels drew $both highlight px against one parcel's $one")
    }

    @Test
    fun tapping_a_feature_selects_it_in_the_host_store() {
        driveScreen(BOUND, widthDp = MAP_SIZE, heightDp = MAP_SIZE).use { screen ->
            screen.tapAt(WEST_PARCEL_X, MIDDLE_Y)
            assertEquals(listOf("west"), selectedIn(screen.store()))
            assertTrue(highlightOf(screen.frame().png) > OUTLINE_FLOOR, "the tapped parcel was selected but not outlined")
        }
    }

    @Test
    fun tapping_the_selected_feature_again_clears_the_selection() {
        driveScreen(BOUND, widthDp = MAP_SIZE, heightDp = MAP_SIZE).use { screen ->
            screen.tapAt(WEST_PARCEL_X, MIDDLE_Y)
            screen.tapAt(WEST_PARCEL_X, MIDDLE_Y)
            assertEquals(emptyList<String>(), selectedIn(screen.store()))
            assertEquals(0, highlightOf(screen.frame().png))
        }
    }

    @Test
    fun tapping_empty_ground_clears_the_selection() {
        driveScreen(BOUND, store = mapOf("map" to mapOf("selected" to listOf("west"))), MAP_SIZE, MAP_SIZE).use { screen ->
            screen.tapAt(GAP_X, MIDDLE_Y)
            assertEquals(emptyList<String>(), selectedIn(screen.store()))
        }
    }

    @Test
    fun selecting_still_tells_the_host_which_feature_was_tapped() {
        driveScreen(BOUND, widthDp = MAP_SIZE, heightDp = MAP_SIZE).use { screen ->
            val intents = screen.tapAt(WEST_PARCEL_X, MIDDLE_Y)
            assertEquals(listOf("parcel_tapped"), intents.map { it.action })
            assertEquals("west", intents.single().params["feature_id"])
        }
    }

    @Test
    fun an_unbound_map_still_selects_on_tap() {
        driveScreen(selectionSpec("[]"), widthDp = MAP_SIZE, heightDp = MAP_SIZE).use { screen ->
            screen.tapAt(WEST_PARCEL_X, MIDDLE_Y)
            assertTrue(highlightOf(screen.frame().png) > OUTLINE_FLOOR, "a map with no bound selection ignored the tap")
        }
    }
}
