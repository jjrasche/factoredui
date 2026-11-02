package ai.factoredui.compose.renderer

import ai.factoredui.compose.crawl.DrivenScreen
import ai.factoredui.compose.crawl.driveScreen
import ai.factoredui.compose.schema.Spec
import androidx.compose.ui.graphics.toArgb
import coil3.ColorImage
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.decode.DataSource
import coil3.intercept.Interceptor
import coil3.request.ErrorResult
import coil3.request.ImageResult
import coil3.request.SuccessResult
import kotlinx.coroutines.awaitCancellation
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.util.Collections
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private val json = Json { ignoreUnknownKeys = true }

private const val MAP_SIZE = 400
private const val TILE_COLOUR = 0x336699
private const val PARCEL_COLOUR = 0xCC0000
private const val WAIT_MILLISECONDS = 6_000L

/** Answers tile requests from memory: a flat colour per URL, an error for chosen URLs, a hang for others. */
private class FakeTiles(
    private val failing: (String) -> Boolean = { false },
    private val hanging: (String) -> Boolean = { false },
) {
    val requested: MutableList<String> = Collections.synchronizedList(mutableListOf())
    val cancelled: MutableList<String> = Collections.synchronizedList(mutableListOf())

    val loader: ImageLoader = ImageLoader.Builder(PlatformContext.INSTANCE)
        .components {
            add(object : Interceptor {
                override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
                    val request = chain.request
                    val url = request.data.toString()
                    requested += url
                    if (failing(url)) return ErrorResult(image = null, request = request, throwable = RuntimeException("no tile at $url"))
                    if (hanging(url)) {
                        try {
                            awaitCancellation()
                        } finally {
                            cancelled += url
                        }
                    }
                    return SuccessResult(image = ColorImage(0xFF000000.toInt() or TILE_COLOUR), request = request, dataSource = DataSource.MEMORY)
                }
            })
        }
        .build()
}

private fun mapSpec(viewport: String, rasters: String, parcels: Boolean = true): Spec {
    val parcelLayer = if (parcels) {
        """{"id":"parcels","kind":"fill","features":[{"id":"p","fill":"#CC0000","rings":[[[-10,-10],[10,-10],[10,10],[-10,10]]]}]},"""
    } else ""
    return json.decodeFromString(
        Spec.serializer(),
        """
        {"spec_version":1,"renderer_min":1,"root":{"id":"map","type":"geomap","props":{
          "viewport": $viewport,
          "layer_visibility": "{map.visibility}",
          "layers": [ $parcelLayer $rasters ]
        }}}
        """.trimIndent(),
    )
}

private fun satellite(extra: String = "") =
    """{"id":"satellite","kind":"raster","url":"/t/[z]/[y]/[x]","tile_max_zoom":19,"attribution":"Esri, Maxar","visible":true $extra}"""

private fun rgbAt(png: ByteArray, x: Int, y: Int): Int = ImageIO.read(ByteArrayInputStream(png)).getRGB(x, y) and 0xFFFFFF

private fun DrivenScreen.untilTrue(what: String, check: () -> Boolean) {
    val deadline = System.currentTimeMillis() + WAIT_MILLISECONDS
    while (System.currentTimeMillis() < deadline) {
        frame()
        if (check()) return
        Thread.sleep(40)
    }
    throw AssertionError("gave up waiting for: $what")
}

private fun parseTileRequest(url: String): Triple<Int, Int, Int> {
    val (z, y, x) = url.removePrefix("/t/").split("/").map { it.toInt() }
    return Triple(z, y, x)
}

class GeomapRasterRenderTest {

    @Test
    fun a_raster_layer_draws_its_tiles_under_the_vector_layers_even_when_it_is_listed_after_them() {
        val tiles = FakeTiles()
        driveScreen(mapSpec("""{"lon":0,"lat":0,"zoom":3}""", satellite()), widthDp = MAP_SIZE, heightDp = MAP_SIZE, tileLoader = tiles.loader).use { screen ->
            screen.untilTrue("a tile's colour in the corner") { rgbAt(screen.frame().png, 20, 20) == TILE_COLOUR }
            val png = screen.frame().png
            assertEquals(TILE_COLOUR, rgbAt(png, 20, 20), "no tile pixels in the corner")
            assertEquals(PARCEL_COLOUR, rgbAt(png, 200, 200), "the vector layer was painted over by the tiles")
        }
    }

    @Test
    fun the_tiles_are_asked_for_by_name_in_the_templates_order_with_y_before_x() {
        val tiles = FakeTiles()
        driveScreen(mapSpec("""{"lon":-90,"lat":40,"zoom":3}""", satellite()), widthDp = MAP_SIZE, heightDp = MAP_SIZE, tileLoader = tiles.loader).use { screen ->
            screen.untilTrue("tile requests") { tiles.requested.isNotEmpty() }
            val asked = tiles.requested.map(::parseTileRequest)
            assertTrue(asked.all { (z, y, x) -> z == 3 && x in 1..3 && y in 2..4 }, "asked for $asked")
            assertTrue(asked.any { (_, y, x) -> y > x }, "at lon -90 and lat 40 the row is the larger number, so a swapped template shows here: $asked")
        }
    }

    @Test
    fun zoomed_in_past_the_ceiling_no_tile_beyond_it_is_asked_for() {
        val tiles = FakeTiles()
        driveScreen(mapSpec("""{"lon":-85.6,"lat":43.0,"zoom":21}""", satellite(), parcels = false), widthDp = MAP_SIZE, heightDp = MAP_SIZE, tileLoader = tiles.loader).use { screen ->
            screen.untilTrue("tile requests") { tiles.requested.isNotEmpty() }
            val levels = tiles.requested.map { parseTileRequest(it).first }.toSet()
            assertEquals(setOf(19), levels, "asked for levels $levels")
            assertTrue(tiles.requested.toSet().size <= 9, "a 400 px view needs a handful of tiles, asked for ${tiles.requested.toSet().size}")
        }
    }

    @Test
    fun a_tile_that_fails_leaves_a_placeholder_one_notice_and_the_rest_of_the_map() {
        val tiles = FakeTiles(failing = { url -> url == "/t/3/3/3" || url == "/t/3/3/4" })
        val placeholder = SpecThemeForTest.dimmedRgb
        driveScreen(mapSpec("""{"lon":0,"lat":0,"zoom":3}""", satellite()), widthDp = MAP_SIZE, heightDp = MAP_SIZE, tileLoader = tiles.loader).use { screen ->
            screen.untilTrue("the notice") { screen.textsUnder("map:raster-notice").isNotEmpty() }
            val notices = screen.textsUnder("map:raster-notice")
            assertEquals(1, notices.size, "notices: $notices")
            assertTrue("satellite" in notices.single(), notices.single())
            val png = screen.frame().png
            assertEquals(PARCEL_COLOUR, rgbAt(png, 200, 200), "the vector layer stopped drawing with a tile down")
            assertTrue(rgbAt(png, 20, 20) == placeholder || rgbAt(png, 380, 20) == placeholder || rgbAt(png, 20, 380) == placeholder || rgbAt(png, 380, 380) == placeholder, "no placeholder drawn where a tile failed")
        }
    }

    @Test
    fun a_failed_tile_is_not_asked_for_again_and_again() {
        val tiles = FakeTiles(failing = { true })
        driveScreen(mapSpec("""{"lon":0,"lat":0,"zoom":3}""", satellite()), widthDp = MAP_SIZE, heightDp = MAP_SIZE, tileLoader = tiles.loader).use { screen ->
            screen.untilTrue("the notice") { screen.textsUnder("map:raster-notice").isNotEmpty() }
            val firstRound = tiles.requested.size
            repeat(20) { screen.frame(); Thread.sleep(25) }
            assertEquals(firstRound, tiles.requested.size, "the failing tiles were asked for again")
        }
    }

    @Test
    fun tiles_that_leave_the_view_stop_loading() {
        val tiles = FakeTiles(hanging = { url -> url == "/t/3/3/3" })
        val start = mapOf("map" to mapOf("view" to mapOf("lon" to 0.0, "lat" to 0.0, "zoom" to 3.0)))
        driveScreen(mapSpec("\"{map.view}\"", satellite()), widthDp = MAP_SIZE, heightDp = MAP_SIZE, store = start, tileLoader = tiles.loader).use { screen ->
            screen.untilTrue("the hanging tile is asked for") { "/t/3/3/3" in tiles.requested }
            screen.type("map.view", mapOf("lon" to 120.0, "lat" to 0.0, "zoom" to 3.0))
            screen.untilTrue("the hanging tile is cancelled after the view moved away") { "/t/3/3/3" in tiles.cancelled }
        }
    }

    @Test
    fun a_hidden_raster_layer_asks_for_nothing_and_showing_it_brings_the_tiles() {
        val tiles = FakeTiles()
        val hidden = mapOf("map" to mapOf("visibility" to mapOf("satellite" to false)))
        driveScreen(mapSpec("""{"lon":0,"lat":0,"zoom":3}""", satellite()), store = hidden, widthDp = MAP_SIZE, heightDp = MAP_SIZE, tileLoader = tiles.loader).use { screen ->
            repeat(10) { screen.frame(); Thread.sleep(25) }
            assertTrue(tiles.requested.isEmpty(), "a hidden layer asked for ${tiles.requested}")
            screen.type("map.visibility", mapOf("satellite" to true))
            screen.untilTrue("tiles once shown") { tiles.requested.isNotEmpty() }
        }
    }

    @Test
    fun the_attribution_of_a_shown_layer_is_drawn_and_a_hidden_layers_is_not() {
        val tiles = FakeTiles()
        val street = """,{"id":"street","kind":"raster","url":"/s/[z]/[x]/[y]","attribution":"OSM contributors","visible":false}"""
        driveScreen(mapSpec("""{"lon":0,"lat":0,"zoom":3}""", satellite() + street), widthDp = MAP_SIZE, heightDp = MAP_SIZE, tileLoader = tiles.loader).use { screen ->
            screen.frame()
            val texts = screen.textsUnder("map:attribution")
            assertTrue(texts.any { "Esri, Maxar" in it }, "texts: $texts")
            assertFalse(texts.any { "OSM" in it }, "texts: $texts")
        }
    }
}

private object SpecThemeForTest {
    val dimmedRgb: Int = ai.factoredui.compose.renderer.SpecTheme.LIGHT.dimmed.toArgb() and 0xFFFFFF
}
