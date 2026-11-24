package ai.factoredui.compose.volume

import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val EAST_AT_45 = Direction(1 / sqrt(2.0), 0.0, 1 / sqrt(2.0))

private val FLOOR = VolumeBox("floor", 0.0, 0.0, 0.0, 10000.0, 10000.0, 100.0)
private val POST = VolumeBox("post", 0.0, 0.0, 100.0, 1000.0, 1000.0, 4000.0)

class VolumeShadowsTest {

    @Test
    fun a_post_in_the_east_sun_throws_its_height_of_shadow_west_across_the_top_of_the_floor() {
        val onFloorTop = shadowsOnFaces(listOf(FLOOR, POST), listOf(FLOOR, POST), EAST_AT_45).getValue("floor")[0]
        assertEquals(1, onFloorTop.size)
        val xs = onFloorTop.single().polygon.map { it.xMm }
        assertTrue(abs(xs.min() - -4500.0) < 1.0, "west end ${xs.min()}")
        assertTrue(abs(xs.max() - 500.0) < 1.0, "east end ${xs.max()}")
        assertTrue(onFloorTop.single().polygon.all { abs(it.zMm - 100.0) < 0.01 })
    }

    @Test
    fun a_face_in_nothings_way_stays_unshaded_and_the_post_does_not_shade_itself() {
        val onPost = shadowsOnFaces(listOf(FLOOR, POST), listOf(FLOOR, POST), EAST_AT_45).getValue("post")
        assertTrue(onPost.all { it.isEmpty() })
    }

    @Test
    fun a_roof_beam_shades_the_part_of_the_post_below_it_on_the_side_facing_the_sun() {
        val beam = VolumeBox("beam", 1500.0, 0.0, 3000.0, 3000.0, 400.0, 300.0)
        val onPost = shadowsOnFaces(listOf(POST), listOf(POST, beam), EAST_AT_45).getValue("post")
        val eastSide = onPost.drop(1).filter { it.isNotEmpty() }
        assertTrue(eastSide.isNotEmpty(), "the beam's shadow falls on a side of the post")
        assertTrue(eastSide.flatten().flatMap { it.polygon }.all { it.zMm in 100.0..4100.0 })
    }

    @Test
    fun the_shadow_falls_on_the_other_side_when_the_sun_is_in_the_west() {
        val fromWest = Direction(-1 / sqrt(2.0), 0.0, 1 / sqrt(2.0))
        val onFloorTop = shadowsOnFaces(listOf(FLOOR), listOf(FLOOR, POST), fromWest).getValue("floor")[0]
        assertEquals(1, onFloorTop.size)
        assertTrue(onFloorTop.single().polygon.map { it.xMm }.max() > 4000.0)
    }

    @Test
    fun a_roof_sheet_tipped_thirty_degrees_throws_a_foreshortened_shadow_straight_down_onto_the_floor() {
        val sheet = VolumeBox("sheet", 0.0, 0.0, 1500.0, 2000.0, 2000.0, 20.0, pitchDeg = 30.0)
        val overhead = Direction(0.0, 0.0, 1.0)
        val shadow = shadowsOnFaces(listOf(FLOOR), listOf(FLOOR, sheet), overhead).getValue("floor")[0].single().polygon
        val ys = shadow.map { it.yMm }
        val xs = shadow.map { it.xMm }
        assertTrue(abs(xs.min() - -1000.0) < 2.0 && abs(xs.max() - 1000.0) < 2.0, "east-west $xs")
        assertTrue(abs(ys.max() - 871.0) < 2.0 && abs(ys.min() - -871.0) < 2.0, "north-south $ys")
    }

    @Test
    fun a_sheet_that_passes_light_casts_a_weaker_shadow_and_an_opaque_one_casts_full() {
        val glazing = VolumeBox("glazing", 0.0, 0.0, 1500.0, 2000.0, 2000.0, 20.0, transmittance = 0.8)
        val steel = VolumeBox("steel", 5000.0, 0.0, 1500.0, 2000.0, 2000.0, 20.0)
        val shadows = shadowsOnFaces(listOf(FLOOR), listOf(FLOOR, glazing, steel), Direction(0.0, 0.0, 1.0)).getValue("floor")[0]
        assertEquals(listOf(0.2, 1.0), shadows.map { it.strength }.sorted().map { kotlin.math.round(it * 100) / 100 })
    }

    @Test
    fun a_roof_sloping_across_a_wedge_top_still_takes_a_shadow_in_its_own_plane() {
        val wedge = VolumeBox("wedge", 5000.0, 0.0, 100.0, 1000.0, 1000.0, 500.0, topRiseMm = 300.0)
        val roof = VolumeBox("roof", 5000.0, 0.0, 2000.0, 3000.0, 3000.0, 20.0)
        val onWedge = shadowsOnFaces(listOf(wedge), listOf(wedge, roof), Direction(0.0, 0.0, 1.0)).getValue("wedge")[0]
        assertEquals(1, onWedge.size)
        assertTrue(onWedge.single().polygon.all { it.zMm in 599.0..901.0 })
    }
}
