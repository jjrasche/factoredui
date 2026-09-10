package ai.factoredui.compose.render

import ai.factoredui.compose.math.Vec3
import ai.factoredui.compose.scene3d.PreparedMesh
import ai.factoredui.compose.scene3d.Scene3dCameraPose
import ai.factoredui.compose.scene3d.Scene3dEntity
import ai.factoredui.compose.scene3d.Scene3dMesh
import ai.factoredui.compose.scene3d.Scene3dWorldState
import ai.factoredui.compose.scene3d.cameraOfPose
import ai.factoredui.compose.scene3d.captureScene3dPoseView
import ai.factoredui.compose.scene3d.currentPose
import ai.factoredui.compose.scene3d.prepare
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val IN_VIEW_HEX = "ff3366"
private const val OUT_OF_VIEW_HEX = "33ff66"
private const val CAPTURE_WIDTH = 400
private const val CAPTURE_HEIGHT = 400

class ServedSceneReceiptTest {

    private fun boxMesh(center: Vec3, hex: String, half: Float = 1f): Scene3dMesh {
        val vertices = ArrayList<Float>(24)
        for (dx in listOf(-1, 1)) for (dy in listOf(-1, 1)) for (dz in listOf(-1, 1)) {
            vertices.add(center.x + dx * half)
            vertices.add(center.y + dy * half)
            vertices.add(center.z + dz * half)
        }
        val quads = listOf(
            listOf(0, 1, 3, 2), listOf(4, 6, 7, 5), listOf(0, 4, 5, 1),
            listOf(2, 3, 7, 6), listOf(0, 2, 6, 4), listOf(1, 5, 7, 3),
        )
        val triangles = quads.flatMap { (a, b, c, d) -> listOf(a, b, c, a, c, d) }
        return Scene3dMesh(vertices = vertices, triangles = triangles, triColors = List(triangles.size / 3) { hex })
    }

    private fun receiptOfTwoBoxes(): ServedSceneReceipt {
        val world = Scene3dWorldState(
            entities = listOf(Scene3dEntity(id = "in-view"), Scene3dEntity(id = "out-of-view")),
        )
        val meshes: Map<String, PreparedMesh> = mapOf(
            "in-view" to boxMesh(Vec3(0f, 0f, 0f), IN_VIEW_HEX).prepare(),
            "out-of-view" to boxMesh(Vec3(400f, 0f, 0f), OUT_OF_VIEW_HEX).prepare(),
        )
        val pose = Scene3dCameraPose(eye = Vec3(0f, 0f, 8f), target = Vec3.ZERO)
        val png = captureScene3dPoseView(
            world = world,
            pose = pose,
            meshes = meshes,
            width = CAPTURE_WIDTH,
            height = CAPTURE_HEIGHT,
        )
        return sceneReceiptOf(
            worldStateUrl = "http://example.invalid/world.json",
            pngPath = "receipt.png",
            png = png,
            world = world,
            meshes = meshes,
            pose = cameraOfPose(pose).currentPose(),
            width = CAPTURE_WIDTH,
            height = CAPTURE_HEIGHT,
        )
    }

    @Test
    fun a_colour_that_never_reached_a_pixel_is_reported_as_never_drawn() {
        val receipt = receiptOfTwoBoxes()
        assertContains(receipt.meshColorsNeverDrawn, "#$OUT_OF_VIEW_HEX")
    }

    @Test
    fun a_colour_that_was_drawn_is_never_reported_as_never_drawn() {
        val receipt = receiptOfTwoBoxes()
        val drawn = receipt.meshColorPixels.single { it.hex == "#$IN_VIEW_HEX" }
        assertTrue(drawn.pixels > 1_000, "the visible box drew ${drawn.pixels} pixels")
        assertEquals(listOf("#$OUT_OF_VIEW_HEX"), receipt.meshColorsNeverDrawn)
    }

    @Test
    fun an_eye_inside_a_box_is_named_rather_than_left_for_the_viewer_to_notice() {
        val world = Scene3dWorldState(entities = listOf(Scene3dEntity(id = "room")))
        val meshes = mapOf("room" to boxMesh(Vec3(0f, 0f, 0f), IN_VIEW_HEX, half = 6f).prepare())
        val pose = Scene3dCameraPose(eye = Vec3(0f, 0f, 3f), target = Vec3.ZERO)
        val png = captureScene3dPoseView(
            world = world,
            pose = pose,
            meshes = meshes,
            width = CAPTURE_WIDTH,
            height = CAPTURE_HEIGHT,
        )
        val receipt = sceneReceiptOf(
            worldStateUrl = "http://example.invalid/world.json",
            pngPath = "receipt.png",
            png = png,
            world = world,
            meshes = meshes,
            pose = cameraOfPose(pose).currentPose(),
            width = CAPTURE_WIDTH,
            height = CAPTURE_HEIGHT,
        )
        assertEquals("room", receipt.eyeInsideEntity)
    }
}
