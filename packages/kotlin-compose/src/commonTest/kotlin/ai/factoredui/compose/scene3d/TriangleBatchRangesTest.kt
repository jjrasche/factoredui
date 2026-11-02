package ai.factoredui.compose.scene3d

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TriangleBatchRangesTest {
    @Test
    fun a_batch_that_fits_is_one_range() {
        assertEquals(listOf(0 until 300), triangleBatchRanges(300))
    }

    @Test
    fun a_hundred_thousand_boundary_points_of_triangles_split_into_ranges_none_over_the_limit_and_none_cutting_a_triangle() {
        val vertexCount = 3 * 100_000
        val ranges = triangleBatchRanges(vertexCount)
        assertTrue(ranges.size > 1)
        assertTrue(ranges.all { (it.last - it.first + 1) <= MAX_BATCH_VERTICES && (it.last - it.first + 1) % 3 == 0 })
        assertEquals(vertexCount, ranges.sumOf { it.last - it.first + 1 })
        assertEquals((0 until ranges.size).map { ranges[it].first }, ranges.runningFold(0) { next, range -> next + (range.last - range.first + 1) }.dropLast(1))
    }

    @Test
    fun no_vertices_or_fewer_than_one_triangle_draws_nothing() {
        assertEquals(emptyList(), triangleBatchRanges(0))
        assertEquals(emptyList(), triangleBatchRanges(2))
    }

    @Test
    fun a_trailing_partial_triangle_is_left_out() {
        assertEquals(listOf(0 until 9), triangleBatchRanges(10))
    }
}