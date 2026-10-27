package ai.factoredui.compose.renderer

import ai.factoredui.compose.schema.GeoPoint
import ai.factoredui.compose.schema.GeomapFeature
import ai.factoredui.compose.schema.GeomapLayer
import ai.factoredui.compose.schema.GeomapLayerKind
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private fun disc(id: String, points: Int, lon: Double): GeomapFeature = GeomapFeature(
    id = id,
    rings = listOf((0 until points).map { GeoPoint(lon + 0.1 * cos(2 * PI * it / points), 42.0 + 0.1 * sin(2 * PI * it / points)) }),
    fill = "#80c0ff",
)

private fun layerOf(points: Int) = GeomapLayer("l", GeomapLayerKind.FILL, listOf(disc("a", points, -90.0), disc("b", points, -89.0)))

class GeomapPathFillTest {

    @Test
    fun a_layer_of_big_outlines_is_filled_as_paths_and_carries_no_triangles() {
        val layer = layerOf(85)
        assertTrue(isFilledAsPaths(layer))
        assertTrue(tessellateGeomapLayers(listOf(layer)).layers.single().features.all { it.triangleWorld.isEmpty() })
    }

    @Test
    fun a_layer_of_small_shapes_stays_a_triangle_batch() {
        val layer = layerOf(6)
        assertFalse(isFilledAsPaths(layer))
        assertEquals(listOf(4 * 3 * 2, 4 * 3 * 2), tessellateGeomapLayers(listOf(layer)).layers.single().features.map { it.triangleWorld.size })
    }

    @Test
    fun only_a_fill_layer_with_features_is_filled_as_paths() {
        assertFalse(isFilledAsPaths(GeomapLayer("l", GeomapLayerKind.FILL, emptyList())))
        assertFalse(isFilledAsPaths(layerOf(85).copy(kind = GeomapLayerKind.LINE)))
    }
}
