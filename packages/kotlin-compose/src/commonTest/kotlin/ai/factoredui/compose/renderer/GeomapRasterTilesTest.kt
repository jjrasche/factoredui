package ai.factoredui.compose.renderer

import ai.factoredui.compose.schema.GeomapViewport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GeomapRasterTilesTest {
    private val centre = GeomapViewport(lon = 0.0, lat = 0.0, zoom = 3f)

    @Test
    fun the_tile_level_is_the_whole_zoom_below_the_viewport_zoom() {
        assertEquals(3, rasterTileLevel(zoom = 3.9f, tileSize = 256, tileMaxZoom = 19))
        assertEquals(0, rasterTileLevel(zoom = 0.4f, tileSize = 256, tileMaxZoom = 19))
    }

    @Test
    fun beyond_the_ceiling_the_last_level_is_stretched_not_asked_for_again() {
        assertEquals(19, rasterTileLevel(zoom = 21f, tileSize = 256, tileMaxZoom = 19))
    }

    @Test
    fun a_bigger_tile_covers_more_ground_so_it_is_asked_for_one_level_up() {
        assertEquals(4, rasterTileLevel(zoom = 5f, tileSize = 512, tileMaxZoom = 19))
        assertEquals(6, rasterTileLevel(zoom = 5f, tileSize = 128, tileMaxZoom = 19))
    }

    @Test
    fun a_view_centred_on_the_world_origin_needs_the_four_tiles_around_it() {
        val tiles = visibleRasterTiles(centre, widthPx = 512f, heightPx = 512f, level = 3)
        assertEquals(
            setOf(TileKey(3, 3, 3), TileKey(3, 4, 3), TileKey(3, 3, 4), TileKey(3, 4, 4)),
            tiles.toSet(),
        )
        assertEquals(4, tiles.size)
    }

    @Test
    fun tiles_beyond_the_edge_of_the_world_are_not_asked_for() {
        val west = GeomapViewport(lon = -179.9, lat = 0.0, zoom = 2f)
        val tiles = visibleRasterTiles(west, widthPx = 1024f, heightPx = 512f, level = 2)
        assertTrue(tiles.all { it.x in 0..3 && it.y in 0..3 }, tiles.toString())
        assertTrue(tiles.any { it.x == 0 })
    }

    @Test
    fun the_template_is_filled_by_name_so_a_zyx_template_and_a_zxy_template_differ() {
        val key = TileKey(z = 3, x = 5, y = 2)
        assertEquals("/t/3/2/5", rasterTileUrl("/t/[z]/[y]/[x]", key))
        assertEquals("/t/3/5/2.png", rasterTileUrl("/t/[z]/[x]/[y].png", key))
    }

    @Test
    fun a_tile_at_the_viewports_whole_zoom_is_the_tile_size_on_screen_and_sits_where_the_math_says() {
        val rect = rasterTileRect(TileKey(3, 3, 3), centre, widthPx = 512f, heightPx = 512f)
        assertEquals(0f, rect.left, 0.01f)
        assertEquals(0f, rect.top, 0.01f)
        assertEquals(256f, rect.size, 0.01f)
    }

    @Test
    fun between_whole_zooms_the_tile_grows_with_the_zoom() {
        val halfWay = centre.copy(zoom = 3.5f)
        val rect = rasterTileRect(TileKey(3, 3, 3), halfWay, widthPx = 512f, heightPx = 512f)
        assertEquals(362.04f, rect.size, 0.05f)
    }

    @Test
    fun past_the_ceiling_the_tile_is_stretched_to_the_viewports_zoom() {
        val deep = GeomapViewport(lon = 0.0, lat = 0.0, zoom = 21f)
        val level = rasterTileLevel(zoom = 21f, tileSize = 256, tileMaxZoom = 19)
        val rect = rasterTileRect(TileKey(level, 1 shl (level - 1), 1 shl (level - 1)), deep, widthPx = 512f, heightPx = 512f)
        assertEquals(1024f, rect.size, 0.5f)
    }
}
