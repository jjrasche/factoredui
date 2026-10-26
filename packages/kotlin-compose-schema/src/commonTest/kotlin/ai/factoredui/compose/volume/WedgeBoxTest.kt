package ai.factoredui.compose.volume

import kotlin.test.Test
import kotlin.test.assertEquals

private val AIR = VolumeBox("air", 0.0, 0.0, 0.0, 6000.0, 4800.0, 400.0, topRiseMm = 1000.0)

class WedgeBoxTest {

    @Test
    fun the_underside_stays_flat_and_the_top_rises_only_on_the_far_edge() {
        val rings = boxRings(AIR)
        assertEquals(listOf(0.0), rings.low.map { it.zMm }.distinct())
        assertEquals(listOf(400.0, 400.0, 1400.0, 1400.0), rings.high.map { it.zMm })
    }

    @Test
    fun the_footprint_is_the_same_as_a_level_box() {
        val level = boxRings(AIR.copy(topRiseMm = 0.0))
        val wedge = boxRings(AIR)
        assertEquals(level.low, wedge.low)
        assertEquals(level.high.map { it.xMm to it.yMm }, wedge.high.map { it.xMm to it.yMm })
    }

    @Test
    fun a_box_without_a_rise_is_exactly_as_before() {
        assertEquals(listOf(400.0), boxRings(AIR.copy(topRiseMm = 0.0)).high.map { it.zMm }.distinct())
    }

    @Test
    fun the_rise_is_read_from_top_rise_mm_and_is_zero_when_absent() {
        val sized = mapOf("x_mm" to 0, "y_mm" to 0, "z_mm" to 0, "width_mm" to 10, "depth_mm" to 10, "height_mm" to 1)
        val read = readVolume(listOf(sized + mapOf("id" to "a", "top_rise_mm" to 250), sized + mapOf("id" to "b")), null, null)
        assertEquals(listOf(250.0, 0.0), read.boxes.map { it.topRiseMm })
    }

    @Test
    fun the_top_face_leans_toward_the_near_edge_and_the_span_reaches_the_far_top() {
        val normal = boxFaceNormals(boxRings(AIR)).first()
        assertEquals(true, normal.y < 0.0 && normal.z > 0.0)
        assertEquals(1400.0, volumeSpan(listOf(AIR))!!.ceiling)
    }
}
