package ai.factoredui.compose.schema

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LineChartTest {

    @Test
    fun aSeriesKeepsItsPointsInTimeOrderWhateverOrderTheyArrivedIn() {
        val series = resolveLineSeries(
            listOf(mapOf("name" to "air", "points" to listOf(mapOf("t" to 2, "v" to 21.5), mapOf("t" to 1, "v" to 20))))
        ).single()
        assertEquals(listOf(1.0, 2.0), series.points.map { it.t })
        assertEquals(listOf(20.0, 21.5), series.points.map { it.v })
    }

    @Test
    fun aPointWithoutANumericTimeOrValueIsDropped() {
        val series = resolveLineSeries(
            listOf(mapOf("name" to "air", "points" to listOf(mapOf("t" to 1, "v" to "hot"), mapOf("t" to null, "v" to 3), mapOf("t" to 4, "v" to 5))))
        ).single()
        assertEquals(1, series.points.size)
    }

    @Test
    fun aSeriesWithoutANameIsDropped() {
        assertEquals(emptyList(), resolveLineSeries(listOf(mapOf("points" to listOf(mapOf("t" to 1, "v" to 1))))))
    }

    @Test
    fun boundsCoverEverySeriesAndTheValueRangeIsPaddedSoALineNeverSitsOnTheFrame() {
        val series = resolveLineSeries(
            listOf(
                mapOf("name" to "a", "points" to listOf(mapOf("t" to 0, "v" to 10), mapOf("t" to 10, "v" to 20))),
                mapOf("name" to "b", "points" to listOf(mapOf("t" to 5, "v" to 30))),
            )
        )
        val bounds = requireNotNull(boundsOfLineSeries(series))
        assertEquals(0.0, bounds.tMin)
        assertEquals(10.0, bounds.tMax)
        assertEquals(true, bounds.vMin < 10.0 && bounds.vMax > 30.0)
    }

    @Test
    fun aFlatSeriesStillGetsAValueSpan() {
        val series = resolveLineSeries(listOf(mapOf("name" to "a", "points" to listOf(mapOf("t" to 0, "v" to 5), mapOf("t" to 1, "v" to 5)))))
        val bounds = requireNotNull(boundsOfLineSeries(series))
        assertEquals(true, bounds.vMax > bounds.vMin)
    }

    @Test
    fun noPointsMeansNoBounds() {
        assertNull(boundsOfLineSeries(resolveLineSeries(listOf(mapOf("name" to "a", "points" to emptyList<Any>())))))
        assertNull(boundsOfLineSeries(resolveLineSeries(null)))
    }
}
