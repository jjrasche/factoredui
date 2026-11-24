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
        val xs = onFloorTop.single().map { it.xMm }
        assertTrue(abs(xs.min() - -4500.0) < 1.0, "west end ${xs.min()}")
        assertTrue(abs(xs.max() - 500.0) < 1.0, "east end ${xs.max()}")
        assertTrue(onFloorTop.single().all { abs(it.zMm - 100.0) < 0.01 })
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
        assertTrue(eastSide.flatten().flatten().all { it.zMm in 100.0..4100.0 })
    }

    @Test
    fun the_shadow_falls_on_the_other_side_when_the_sun_is_in_the_west() {
        val fromWest = Direction(-1 / sqrt(2.0), 0.0, 1 / sqrt(2.0))
        val onFloorTop = shadowsOnFaces(listOf(FLOOR), listOf(FLOOR, POST), fromWest).getValue("floor")[0]
        assertEquals(1, onFloorTop.size)
        assertTrue(onFloorTop.single().map { it.xMm }.max() > 4000.0)
    }
}
