package ai.factoredui.compose.scene3d

import ai.factoredui.compose.math.Camera
import ai.factoredui.compose.math.Vec3
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.math.PI
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val BACKGROUND_ARGB = 0xFF2B2B30.toInt()
private const val CAPTURE_WIDTH = 640
private const val CAPTURE_HEIGHT = 480

class Scene3dCameraDriveTest {

    private fun hillWorld() = Scene3dWorldState(entities = listOf(Scene3dEntity(id = "terrain")))

    private fun hillMeshes() = mapOf("terrain" to rollingHillsMesh(40).prepare())

    private fun nonBackgroundFraction(pixels: java.awt.image.BufferedImage, fromRow: Int, toRow: Int): Double {
        var drawn = 0
        var sampled = 0
        for (y in fromRow until toRow) {
            for (x in 0 until pixels.width) {
                sampled++
                if (pixels.getRGB(x, y) != BACKGROUND_ARGB) drawn++
            }
        }
        return drawn.toDouble() / sampled
    }

    @Test
    fun a_pose_round_trips_through_the_orbit_camera() {
        val pose = Scene3dCameraPose(
            eye = Vec3(12f, 9f, -14f),
            target = Vec3(1f, 2f, 3f),
            fovYRadians = (PI / 4.0).toFloat(),
        )
        val camera = cameraOfPose(pose)
        val recovered = camera.currentPose()
        assertTrue(abs(recovered.eye.x - pose.eye.x) < 1e-2f, "eye.x ${recovered.eye.x} != ${pose.eye.x}")
        assertTrue(abs(recovered.eye.y - pose.eye.y) < 1e-2f, "eye.y ${recovered.eye.y} != ${pose.eye.y}")
        assertTrue(abs(recovered.eye.z - pose.eye.z) < 1e-2f, "eye.z ${recovered.eye.z} != ${pose.eye.z}")
        assertEquals(pose.target, recovered.target)
        assertEquals(pose.fovYRadians, recovered.fovYRadians)
    }

    @Test
    fun a_straight_down_pose_clamps_to_the_same_pitch_limit_a_drag_hits() {
        val overhead = cameraOfPose(Scene3dCameraPose(eye = Vec3(0f, 40f, 0f), target = Vec3.ZERO))
        val dragged = Camera().apply { drag(0f, 10_000f) }
        assertEquals(dragged.pitchRadians, overhead.pitchRadians, "programmatic pose must land inside the drag-reachable set")
        assertTrue(overhead.pitchRadians < (PI / 2.0).toFloat(), "pitch must stay shy of the pole")
    }

    @Test
    fun a_pose_beyond_the_zoom_limit_clamps_to_the_same_distance_a_scroll_hits() {
        val faraway = cameraOfPose(Scene3dCameraPose(eye = Vec3(0f, 0f, 5_000f), target = Vec3.ZERO))
        val scrolled = Camera().apply { repeat(400) { zoom(-1f) } }
        assertEquals(scrolled.distance, faraway.distance)
    }

    @Test
    fun an_orbit_sequence_walks_one_full_circle_at_a_fixed_distance() {
        val poses = orbitPoseSequence(target = Vec3(0f, 1f, 0f), distance = 20f, pitchRadians = 0.3f, steps = 6)
        assertEquals(6, poses.size)
        for (pose in poses) {
            assertTrue(abs((pose.eye - pose.target).length() - 20f) < 1e-3f, "orbit step left the sphere: ${pose.eye}")
        }
        val distinctYaws = poses.map { cameraOfPose(it).yawRadians }.distinct()
        assertEquals(6, distinctYaws.size, "every orbit step must look from a different angle")
    }

    @Test
    fun the_captured_frame_changes_when_the_pose_changes() {
        val world = hillWorld()
        val meshes = hillMeshes()
        val overhead = captureScene3dPoseView(
            world = world,
            pose = Scene3dCameraPose(eye = Vec3(0.1f, 30f, 0.1f), target = Vec3.ZERO),
            meshes = meshes,
            width = CAPTURE_WIDTH,
            height = CAPTURE_HEIGHT,
        )
        val fromTheSide = captureScene3dPoseView(
            world = world,
            pose = Scene3dCameraPose(eye = Vec3(0f, 2f, 26f), target = Vec3.ZERO),
            meshes = meshes,
            width = CAPTURE_WIDTH,
            height = CAPTURE_HEIGHT,
        )
        assertTrue(overhead.isNotEmpty() && fromTheSide.isNotEmpty(), "capture produced no PNG bytes")
        assertTrue(!overhead.contentEquals(fromTheSide), "two different poses produced byte-identical frames")
    }

    @Test
    fun a_ground_level_pose_puts_sky_above_the_terrain() {
        val png = captureScene3dPoseView(
            world = hillWorld(),
            pose = Scene3dCameraPose(eye = Vec3(0f, 2f, 9f), target = Vec3(0f, 1.5f, -6f)),
            meshes = hillMeshes(),
            width = CAPTURE_WIDTH,
            height = CAPTURE_HEIGHT,
        )
        val image = ImageIO.read(ByteArrayInputStream(png))
        val sky = nonBackgroundFraction(image, 0, CAPTURE_HEIGHT / 4)
        val ground = nonBackgroundFraction(image, CAPTURE_HEIGHT * 3 / 4, CAPTURE_HEIGHT)
        assertTrue(ground > 0.5, "expected terrain across the bottom quarter, drew $ground")
        assertTrue(sky < 0.1, "expected empty sky across the top quarter, drew $sky")
    }
}
