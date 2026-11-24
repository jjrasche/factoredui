package ai.factoredui.compose.renderer

import ai.factoredui.compose.volume.Direction
import ai.factoredui.compose.volume.VolumeBox
import ai.factoredui.compose.volume.shadowsOnFaces
import kotlin.test.Test
import kotlin.test.assertTrue

private const val GRID = 10
private const val BAY_MM = 3000.0
private const val SLOWEST_ACCEPTABLE_MS = 2000L

private fun postsAndBeams(): List<VolumeBox> {
    val posts = (0 until GRID * GRID / 2).map { VolumeBox("post-$it", (it % GRID) * BAY_MM, (it / GRID) * BAY_MM, 0.0, 300.0, 300.0, 3000.0) }
    val beams = (0 until GRID * GRID / 2).map { VolumeBox("beam-$it", (it % GRID) * BAY_MM + BAY_MM / 2, (it / GRID) * BAY_MM, 3000.0, BAY_MM - 300, 250.0, 400.0) }
    return posts + beams
}

class VolumeShadowTimingTest {

    @Test
    fun the_shadows_of_a_hundred_boxes_on_each_other_are_found_in_a_moment() {
        val boxes = postsAndBeams()
        val sun = Direction(-0.5, -0.6, 0.75).let { val length = kotlin.math.sqrt(it.x * it.x + it.y * it.y + it.z * it.z); Direction(it.x / length, it.y / length, it.z / length) }
        val started = System.nanoTime()
        val found = shadowsOnFaces(boxes, boxes, sun)
        val millis = (System.nanoTime() - started) / 1_000_000
        println("SHADOW-TIMING ${boxes.size} boxes: $millis ms, ${found.values.sumOf { faces -> faces.sumOf { it.size } }} shadows")
        assertTrue(millis < SLOWEST_ACCEPTABLE_MS, "$millis ms")
    }
}
