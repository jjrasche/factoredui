package ai.factoredui.compose.volume

import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val TOLERANCE = 1e-6

private val SHEET = VolumeBox("sheet", 1000.0, 2000.0, 300.0, 1200.0, 2000.0, 0.0)

private fun assertPoint(expected: Triple<Double, Double, Double>, actual: VolumePoint, message: String) {
    assertEquals(expected.first, actual.xMm, TOLERANCE, "$message x")
    assertEquals(expected.second, actual.yMm, TOLERANCE, "$message y")
    assertEquals(expected.third, actual.zMm, TOLERANCE, "$message z")
}

class BoxRingsTest {
    @Test
    fun a_box_with_no_pitch_has_its_rings_straight_above_its_footprint() {
        val box = SHEET.copy(heightMm = 400.0, rotationDeg = 30.0)
        val rings = boxRings(box)
        assertEquals(footprintCorners(box), rings.low.map { it.xMm to it.yMm })
        assertEquals(footprintCorners(box), rings.high.map { it.xMm to it.yMm })
        assertEquals(listOf(300.0), rings.low.map { it.zMm }.distinct())
        assertEquals(listOf(700.0), rings.high.map { it.zMm }.distinct())
    }

    @Test
    fun a_sheet_pitched_thirty_degrees_has_its_far_edge_raised_and_its_near_edge_lowered_about_its_centre() {
        val rings = boxRings(SHEET.copy(pitchDeg = 30.0))
        val rise = 1000.0 * sin(Math.toRadians(30.0))
        val reach = 1000.0 * cos(Math.toRadians(30.0))
        assertPoint(Triple(400.0, 2000.0 - reach, 300.0 - rise), rings.low[0], "near west")
        assertPoint(Triple(1600.0, 2000.0 - reach, 300.0 - rise), rings.low[1], "near east")
        assertPoint(Triple(1600.0, 2000.0 + reach, 300.0 + rise), rings.low[2], "far east")
        assertPoint(Triple(400.0, 2000.0 + reach, 300.0 + rise), rings.low[3], "far west")
    }

    @Test
    fun a_pitched_sheet_turned_ninety_degrees_rises_toward_the_west() {
        val rings = boxRings(SHEET.copy(pitchDeg = 30.0, rotationDeg = 90.0))
        val far = rings.low[3]
        assertTrue(far.xMm < SHEET.xMm - 800.0, "the far edge lies west of the centre: ${far.xMm}")
        assertTrue(far.zMm > SHEET.zMm, "and is raised: ${far.zMm}")
    }

    @Test
    fun the_span_of_a_pitched_box_follows_its_corners() {
        val span = volumeSpan(listOf(SHEET.copy(pitchDeg = 30.0)))!!
        assertEquals(300.0 + 500.0, span.ceiling, TOLERANCE)
        assertEquals(300.0 - 500.0, span.floor, TOLERANCE)
    }

    @Test
    fun the_normals_of_a_pitched_sheet_lean_away_from_the_raised_side() {
        val normals = boxFaceNormals(boxRings(SHEET.copy(heightMm = 20.0, pitchDeg = 30.0)))
        val top = normals.first()
        assertEquals(cos(Math.toRadians(30.0)), top.z, TOLERANCE)
        assertTrue(top.y < 0.0, "the top of a slope rising to the north faces south: ${top.y}")
    }

    @Test
    fun a_pitched_box_is_never_taken_for_a_vertical_plane() {
        val wall = VolumeBox("wall", 0.0, 0.0, 0.0, 1000.0, 0.0, 2000.0)
        assertEquals(false, isDrawnOn(wall.copy(pitchDeg = 10.0), wall))
    }

    @Test
    fun pitch_is_read_from_pitch_deg_and_is_zero_when_absent() {
        val sized = mapOf("x_mm" to 0, "y_mm" to 0, "z_mm" to 0, "width_mm" to 1, "depth_mm" to 1, "height_mm" to 1)
        val read = readVolume(listOf(sized + mapOf("id" to "a", "pitch_deg" to 15), sized + mapOf("id" to "b")), null, null)
        assertEquals(listOf(15.0, 0.0), read.boxes.map { it.pitchDeg })
    }
}
