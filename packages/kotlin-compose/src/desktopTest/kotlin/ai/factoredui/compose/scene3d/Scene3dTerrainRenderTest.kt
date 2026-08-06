package ai.factoredui.compose.scene3d

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import ai.factoredui.compose.math.Camera
import ai.factoredui.compose.math.Vec3
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class Scene3dTerrainRenderTest {

    @Test
    fun prepareBuildsTerrainGridFromGridMesh() {
        val prepared = rollingHillsMesh(71).prepare()
        val terrain = prepared.terrain
        assertTrue(terrain != null, "grid mesh should prepare a TerrainGrid")
        assertEquals(71, terrain.cellsX)
        assertEquals(2, terrain.chunksX)
        assertEquals(4, terrain.chunks.size)
        assertEquals(71 * 71 * 2, terrain.cellColors.size)
    }

    @Test
    fun prepareWithoutGridMetadataKeepsMeshPath() {
        val prepared = Scene3dMesh(
            vertices = listOf(0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f),
            triangles = listOf(0, 1, 2),
            triColors = listOf("88aa66"),
        ).prepare()
        assertTrue(prepared.terrain == null, "non-grid mesh must not build terrain")
        assertEquals(3, prepared.vertices.size)
    }

    @Test
    fun painterOrderStartsAtFarCornerAndEndsNearest() {
        val order = ArrayList<Pair<Int, Int>>()
        forEachGridIndexFarToNear(4, 4, eyeX = 10f, eyeZ = 8f, centerX = 1.5f, centerZ = 1.5f) { ix, iz ->
            order.add(ix to iz)
        }
        assertEquals(16, order.size)
        assertEquals(0 to 0, order.first(), "eye in +x/+z: farthest cell (0,0) draws first")
        assertEquals(3 to 3, order.last(), "nearest cell draws last")
    }

    @Test
    fun painterOrderFlipsWithEyeQuadrant() {
        val order = ArrayList<Pair<Int, Int>>()
        forEachGridIndexFarToNear(4, 4, eyeX = -10f, eyeZ = -8f, centerX = 1.5f, centerZ = 1.5f) { ix, iz ->
            order.add(ix to iz)
        }
        assertEquals(3 to 3, order.first(), "eye in -x/-z: farthest cell (3,3) draws first")
        assertEquals(0 to 0, order.last())
    }

    @Test
    fun chunkBatchWritesFlatColorTriples() {
        val terrain = requireNotNull(rollingHillsMesh(8).prepare().terrain)
        val camera = Camera(distance = 30f, target = Vec3.ZERO)
        val viewProjection = camera.projectionMatrix(800f / 600f) * camera.viewMatrix()
        terrain.projectVertices(Vec3.ZERO, viewProjection, 800f, 600f)
        val eye = camera.eyePosition()
        val vertexCount = terrain.fillChunkBatch(terrain.chunks.first(), eye.x, eye.z)
        assertEquals(8 * 8 * 2 * 3, vertexCount, "all 128 cell triangles should be visible")
        for (triangle in 0 until vertexCount / 3) {
            val base = triangle * 3
            assertEquals(terrain.batchColors[base], terrain.batchColors[base + 1])
            assertEquals(terrain.batchColors[base], terrain.batchColors[base + 2])
        }
    }

    @Test
    fun rendersTerrainThroughCaptureToImage() = runComposeUiTest {
        val prepared = rollingHillsMesh(40).prepare()
        val world = Scene3dWorldState(entities = listOf(Scene3dEntity(id = "terrain")))
        val camera = Camera(
            yawRadians = (-PI / 4.0).toFloat(),
            pitchRadians = (PI / 6.0).toFloat(),
            distance = 24f,
            target = Vec3.ZERO,
            fovYRadians = (PI / 3.0).toFloat(),
        )
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                Box(Modifier.size(800.dp)) {
                    Scene3dView(
                        world = world,
                        camera = camera,
                        meshes = mapOf("terrain" to prepared),
                        modifier = Modifier.size(800.dp),
                    )
                }
            }
        }
        val image = onRoot().captureToImage().toAwtImage()
        val outFile = File(System.getProperty("user.dir"), "build/scene3d_terrain_batched.png")
        outFile.parentFile.mkdirs()
        ImageIO.write(image, "PNG", outFile)
        println("[scene3d] wrote ${outFile.absolutePath}")
        var terrainPixels = 0
        for (y in 200 until 600 step 4) {
            for (x in 200 until 600 step 4) {
                if (image.getRGB(x, y) != BACKGROUND_ARGB) terrainPixels++
            }
        }
        assertTrue(terrainPixels > 1000, "captureToImage must see terrain pixels, saw $terrainPixels non-background samples")
    }
}

private const val BACKGROUND_ARGB = 0xFF2B2B30.toInt()
