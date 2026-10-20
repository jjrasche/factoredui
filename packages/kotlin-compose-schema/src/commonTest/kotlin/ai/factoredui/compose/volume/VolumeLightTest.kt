package ai.factoredui.compose.volume

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val POST = VolumeBox("post", 0.0, 0.0, 0.0, 100.0, 100.0, 1000.0)

class VolumeLightTest {
    @Test
    fun a_face_turned_to_the_sun_is_lighter_than_one_turned_away_and_the_top_is_lit() {
        val toSun = sunShade(Direction(-1.0, 0.0, 0.0))
        val away = sunShade(Direction(1.0, 0.0, 0.0))
        assertTrue(toSun > away)
        assertEquals(0.55, away, 1e-9)
        assertTrue(sunShade(UP) > away)
    }

    @Test
    fun the_outward_normal_of_an_anticlockwise_edge_points_away_from_the_inside() {
        val south = sideNormal(0.0 to 0.0, 1.0 to 0.0)!!
        assertEquals(0.0, south.x, 1e-9)
        assertEquals(-1.0, south.y, 1e-9)
        assertNull(sideNormal(1.0 to 1.0, 1.0 to 1.0))
    }

    @Test
    fun the_hull_of_a_square_with_a_point_inside_is_the_square() {
        val hull = convexHull(listOf(0.0 to 0.0, 2.0 to 0.0, 2.0 to 2.0, 0.0 to 2.0, 1.0 to 1.0))
        assertEquals(setOf(0.0 to 0.0, 2.0 to 0.0, 2.0 to 2.0, 0.0 to 2.0), hull.toSet())
    }

    @Test
    fun a_tall_box_throws_its_shadow_away_from_the_sun_and_its_foot_stays() {
        val shadow = shadowOutline(POST, 0.0)
        assertTrue(shadow.maxOf { it.first } > 50.0 || shadow.maxOf { it.second } > 50.0)
        assertTrue(shadow.any { it.first > 50.0 } && shadow.any { it.second > 50.0 })
        assertTrue(shadow.any { it.first == -50.0 || it.second == -50.0 })
    }

    @Test
    fun a_box_below_the_ground_throws_no_shadow_beyond_its_footprint() {
        val buried = VolumeBox("buried", 0.0, 0.0, -1000.0, 100.0, 100.0, 500.0)
        assertEquals(setOf(-50.0, 50.0), shadowOutline(buried, 0.0).flatMap { listOf(it.first, it.second) }.toSet())
    }
}
