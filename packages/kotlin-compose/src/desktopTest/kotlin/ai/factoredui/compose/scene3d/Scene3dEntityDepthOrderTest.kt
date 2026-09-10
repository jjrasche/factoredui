package ai.factoredui.compose.scene3d

import ai.factoredui.compose.math.Vec3
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val NEAR_HEX = "ff3366"
private const val FAR_HEX = "33ff66"
private const val CAPTURE_WIDTH = 400
private const val CAPTURE_HEIGHT = 400

class Scene3dEntityDepthOrderTest {

    private fun boxMesh(center: Vec3, halfSize: Float, hex: String): Scene3dMesh {
        val vertices = ArrayList<Float>(24)
        for (dx in listOf(-1, 1)) for (dy in listOf(-1, 1)) for (dz in listOf(-1, 1)) {
            vertices.add(center.x + dx * halfSize)
            vertices.add(center.y + dy * halfSize)
            vertices.add(center.z + dz * halfSize)
        }
        val quads = listOf(
            listOf(0, 1, 3, 2), listOf(4, 6, 7, 5), listOf(0, 4, 5, 1),
            listOf(2, 3, 7, 6), listOf(0, 2, 6, 4), listOf(1, 5, 7, 3),
        )
        val triangles = quads.flatMap { (a, b, c, d) -> listOf(a, b, c, a, c, d) }
        return Scene3dMesh(
            vertices = vertices,
            triangles = triangles,
            triColors = List(triangles.size / 3) { hex },
        )
    }

    private fun pixelCounts(png: ByteArray): Map<String, Int> {
        val image = ImageIO.read(ByteArrayInputStream(png))
        val counts = HashMap<String, Int>()
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                val hex = (image.getRGB(x, y) and 0xFFFFFF).toString(16).padStart(6, '0')
                counts[hex] = (counts[hex] ?: 0) + 1
            }
        }
        return counts
    }

    @Test
    fun a_nearer_entity_hides_a_farther_one_even_when_both_declare_the_same_position() {
        val world = Scene3dWorldState(
            entities = listOf(
                Scene3dEntity(id = "near"),
                Scene3dEntity(id = "far"),
            ),
        )
        val meshes = mapOf(
            "near" to boxMesh(Vec3(0f, 0f, 2f), halfSize = 1.5f, hex = NEAR_HEX).prepare(),
            "far" to boxMesh(Vec3(0f, 0f, -2f), halfSize = 1.0f, hex = FAR_HEX).prepare(),
        )
        val png = captureScene3dPoseView(
            world = world,
            pose = Scene3dCameraPose(eye = Vec3(0f, 0f, 10f), target = Vec3.ZERO),
            meshes = meshes,
            width = CAPTURE_WIDTH,
            height = CAPTURE_HEIGHT,
        )
        val counts = pixelCounts(png)
        assertTrue((counts[NEAR_HEX] ?: 0) > 1_000, "the near box drew ${counts[NEAR_HEX] ?: 0} pixels")
        assertEquals(0, counts[FAR_HEX] ?: 0, "a box fully behind another one showed through")
    }
}
