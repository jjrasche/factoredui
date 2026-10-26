package ai.factoredui.compose.renderer

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun starRing(points: Int, clockwise: Boolean): DoubleArray {
    val ring = DoubleArray(points * 2)
    for (index in 0 until points) {
        val angle = 2 * PI * index / points * (if (clockwise) -1 else 1)
        val radius = if (index % 2 == 0) 10.0 else 4.0 + (index % 5)
        ring[index * 2] = radius * cos(angle)
        ring[index * 2 + 1] = radius * sin(angle)
    }
    return ring
}

private fun trianglesAreaOf(ring: DoubleArray, indices: IntArray): Double =
    (0 until indices.size / 3).sumOf { triangle ->
        val a = indices[triangle * 3]
        val b = indices[triangle * 3 + 1]
        val c = indices[triangle * 3 + 2]
        abs((ring[b * 2] - ring[a * 2]) * (ring[c * 2 + 1] - ring[a * 2 + 1]) - (ring[c * 2] - ring[a * 2]) * (ring[b * 2 + 1] - ring[a * 2 + 1])) / 2
    }

class GeomapStarTriangulationTest {

    @Test
    fun a_concave_star_of_85_points_is_covered_by_exactly_its_own_area_either_way_round() {
        listOf(false, true).forEach { clockwise ->
            val ring = starRing(85, clockwise)
            val indices = earClipTriangulate(ring)
            assertEquals(83 * 3, indices.size)
            assertTrue(abs(trianglesAreaOf(ring, indices) - abs(signedRingArea(ring))) < 1e-6, "clockwise=$clockwise")
        }
    }
}
