package ai.factoredui.compose.renderer

import ai.factoredui.compose.schema.resolveGeomapLayers
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

private const val GEOMETRY_COUNT = 344
private const val POINTS_PER_RING = 85
private const val GRID_SIDE = 19

private fun jaggedRing(row: Int, column: Int, random: Random): List<List<Double>> {
    val centreLon = -90.0 + column * 0.5
    val centreLat = 42.0 + row * 0.4
    return (0 until POINTS_PER_RING).map { index ->
        val angle = 2 * PI * index / POINTS_PER_RING
        val radius = 0.2 * (0.6 + 0.4 * random.nextDouble())
        listOf(centreLon + radius * cos(angle), centreLat + radius * sin(angle))
    }
}

private fun climateLikeLayers(): Pair<List<Map<String, Any?>>, Map<String, Any?>> {
    val random = Random(7)
    val geometries = (0 until GEOMETRY_COUNT).associate { index ->
        "g$index" to listOf(jaggedRing(index / GRID_SIDE, index % GRID_SIDE, random))
    }
    val features = (0 until GEOMETRY_COUNT).map { index ->
        mapOf("id" to "f$index", "geometry" to "g$index", "fill" to "#80c0ff", "stroke" to "#303030", "stroke_width" to 0.5)
    }
    return listOf(mapOf("id" to "temp", "kind" to "fill", "features" to features)) to geometries
}

class GeomapFirstFrameCostTest {

    @Test
    fun prints_where_a_344_geometry_map_spends_its_time_before_the_first_frame() {
        val (layers, geometries) = climateLikeLayers()
        repeat(3) { run ->
            val resolveStart = System.nanoTime()
            val resolved = resolveGeomapLayers(layers, geometries)
            val tessellateStart = System.nanoTime()
            val tessellation = tessellateGeomapLayers(resolved)
            val end = System.nanoTime()
            println("GEOMAP-COST run=$run resolve_ms=${(tessellateStart - resolveStart) / 1_000_000} tessellate_ms=${(end - tessellateStart) / 1_000_000}")
            assertEquals(GEOMETRY_COUNT, tessellation.layers.single().features.size)
        }
    }
}
