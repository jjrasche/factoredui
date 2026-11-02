package ai.factoredui.compose.schema

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GeomapRasterSpecTest {
    private val satellite = mapOf(
        "id" to "satellite",
        "kind" to "raster",
        "url" to "/tiles/satellite/[z]/[y]/[x]",
        "tile_size" to 256,
        "tile_max_zoom" to 19,
        "attribution" to "Esri, Maxar, Earthstar Geographics",
        "min_zoom" to 3,
        "max_zoom" to 21,
    )
    private val parcels = mapOf(
        "id" to "parcels",
        "kind" to "fill",
        "features" to listOf(mapOf("id" to "a", "rings" to listOf(listOf(listOf(0, 0), listOf(1, 0), listOf(1, 1))))),
    )

    @Test
    fun a_raster_entry_is_read_with_its_template_size_ceiling_attribution_and_zoom_limits() {
        val layer = resolveGeomapRasterLayers(listOf(satellite)).single()
        assertEquals("satellite", layer.id)
        assertEquals("/tiles/satellite/[z]/[y]/[x]", layer.urlTemplate)
        assertEquals(256, layer.tileSize)
        assertEquals(19, layer.tileMaxZoom)
        assertEquals("Esri, Maxar, Earthstar Geographics", layer.attribution)
        assertEquals(3f, layer.minZoom)
        assertEquals(21f, layer.maxZoom)
        assertTrue(layer.visible)
    }

    @Test
    fun the_template_keeps_the_order_its_author_wrote() {
        val xFirst = satellite + ("url" to "https://tiles.example/[z]/[x]/[y].png")
        assertEquals("https://tiles.example/[z]/[x]/[y].png", resolveGeomapRasterLayers(listOf(xFirst)).single().urlTemplate)
    }

    @Test
    fun a_raster_entry_is_not_a_vector_layer_and_a_vector_layer_is_not_a_raster() {
        val entries = listOf(satellite, parcels)
        assertEquals(listOf("satellite"), resolveGeomapRasterLayers(entries).map { it.id })
        assertEquals(listOf("parcels"), resolveGeomapLayers(entries).map { it.id })
    }

    @Test
    fun a_missing_size_ceiling_and_visibility_take_their_defaults() {
        val bare = mapOf("id" to "street", "kind" to "raster", "url" to "https://t.example/[z]/[x]/[y].png")
        val layer = resolveGeomapRasterLayers(listOf(bare)).single()
        assertEquals(256, layer.tileSize)
        assertEquals(DEFAULT_TILE_MAX_ZOOM, layer.tileMaxZoom)
        assertNull(layer.attribution)
        assertNull(layer.minZoom)
        assertTrue(layer.visible)
        assertEquals(false, resolveGeomapRasterLayers(listOf(bare + ("visible" to false))).single().visible)
    }

    @Test
    fun a_template_missing_a_placeholder_is_no_layer_and_is_named_in_a_problem() {
        val noY = satellite + ("url" to "/tiles/[z]/[x]")
        assertTrue(resolveGeomapRasterLayers(listOf(noY)).isEmpty())
        val problem = geomapRasterProblems(listOf(noY)).single()
        assertTrue("satellite" in problem && "[y]" in problem, problem)

        val bracesEaten = satellite + ("url" to "/tiles/{z}/{y}/{x}")
        assertTrue("square brackets" in geomapRasterProblems(listOf(bracesEaten)).single())
    }

    @Test
    fun a_raster_with_no_template_or_a_nonsense_tile_size_is_named_in_a_problem() {
        assertEquals(1, geomapRasterProblems(listOf(mapOf("id" to "bare", "kind" to "raster"))).size)
        val zeroSize = satellite + ("tile_size" to 0)
        assertTrue(resolveGeomapRasterLayers(listOf(zeroSize)).isEmpty())
        assertEquals(1, geomapRasterProblems(listOf(zeroSize)).size)
    }

    @Test
    fun a_good_raster_has_no_problems() {
        assertTrue(geomapRasterProblems(listOf(satellite, parcels)).isEmpty())
    }
}
