package ai.factoredui.compose.layout

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FlowEdgeGeometryTest {

    private fun route(from: String, to: String, vararg points: Pair<Float, Float>) =
        FlowEdgeRoute(from, to, points.map { FlowPoint(it.first, it.second) }, isBackEdge = false)

    private val straight = route("a", "b", 0f to 0f, 100f to 0f)

    @Test
    fun aPointNearAnEdgeHitsIt() {
        assertEquals(straight, hitTestFlowEdge(listOf(straight), 50f, 3f, tolerance = 5f))
    }

    @Test
    fun aPointFarFromEveryEdgeHitsNothing() {
        assertNull(hitTestFlowEdge(listOf(straight), 50f, 20f, tolerance = 5f))
    }

    @Test
    fun theNearestOfTwoEdgesWins() {
        val other = route("c", "d", 0f to 8f, 100f to 8f)
        assertEquals(other, hitTestFlowEdge(listOf(straight, other), 50f, 6f, tolerance = 10f))
    }

    @Test
    fun aBentEdgeIsHitAlongItsCurveNotItsChord() {
        val bent = route("a", "b", 0f to 0f, 100f to 100f)
        val onCurve = sampleFlowRoute(bent).let { it[it.size / 4] }
        assertEquals(bent, hitTestFlowEdge(listOf(bent), onCurve.x, onCurve.y, tolerance = 2f))
        assertNull(hitTestFlowEdge(listOf(bent), 80f, 20f, tolerance = 4f), "the empty corner beside the S-curve is not on the edge")
    }

    @Test
    fun theMidpointOfAStraightEdgeIsItsMiddle() {
        val middle = flowRouteMidpoint(straight)
        assertTrue(abs(middle.x - 50f) < 1f && abs(middle.y) < 0.01f, "got $middle")
    }

    @Test
    fun theMidpointOfASymmetricBendLiesOnTheCurveHalfway() {
        val middle = flowRouteMidpoint(route("a", "b", 0f to 0f, 100f to 100f))
        assertTrue(abs(middle.x - 50f) < 1.5f && abs(middle.y - 50f) < 1.5f, "got $middle")
    }
}
