package ai.factoredui.compose.scene3d

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import ai.factoredui.compose.math.Camera
import ai.factoredui.compose.math.Vec3
import java.io.File
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertTrue

private const val WARMUP_FRAMES = 2
private const val TIMED_FRAMES = 5
private const val FRAME_NANOS = 16_666_667L

// Same harness protocol as the Q-003 baseline spike: headless ImageComposeScene 800x600,
// rolling-hills heightfield, camera orbiting every frame, 2 warmup + 5 timed frames.
@OptIn(ExperimentalComposeUiApi::class)
class Scene3dTerrainFpsSpikeTest {

    @Test
    fun measuresBatchedPainterFrameTimes() {
        val rows = listOf(71, 158, 224).map { measureTerrainFrameTime(it) }
        println("[scene3d-fps] triangles | ms/frame | fps")
        for (row in rows) {
            println(
                "[scene3d-fps] %d | %.1f | %.1f".format(row.triangles, row.msPerFrame, row.fps),
            )
        }
        assertTrue(rows.all { it.fps > 0f })
    }

    @Test
    fun profilesBatchedPainterStages() {
        val terrain = requireNotNull(rollingHillsMesh(224).prepare().terrain)
        val camera = Camera(
            yawRadians = (-PI / 4.0).toFloat(),
            pitchRadians = (PI / 6.0).toFloat(),
            distance = 24f,
            target = Vec3.ZERO,
            fovYRadians = (PI / 3.0).toFloat(),
        )
        val viewProjection = camera.projectionMatrix(800f / 600f) * camera.viewMatrix()
        val eye = camera.eyePosition()
        repeat(WARMUP_FRAMES) { runPainterCpuStages(terrain, viewProjection, eye) }
        var projectNanos = 0L
        var assembleNanos = 0L
        repeat(TIMED_FRAMES) {
            val (projected, assembled) = runPainterCpuStages(terrain, viewProjection, eye)
            projectNanos += projected
            assembleNanos += assembled
        }
        println(
            "[scene3d-fps] profile 100k: project=%.1fms assemble=%.1fms per frame (remainder of frame time = skia fill + compose)".format(
                projectNanos / TIMED_FRAMES / 1e6f,
                assembleNanos / TIMED_FRAMES / 1e6f,
            ),
        )
        assertTrue(projectNanos > 0 && assembleNanos > 0)
    }

    private fun runPainterCpuStages(
        terrain: TerrainGrid,
        viewProjection: ai.factoredui.compose.math.Matrix4,
        eye: Vec3,
    ): Pair<Long, Long> {
        val projectStart = System.nanoTime()
        terrain.projectVertices(Vec3.ZERO, viewProjection, 800f, 600f)
        val projectNanos = System.nanoTime() - projectStart
        val assembleStart = System.nanoTime()
        var totalVertices = 0
        terrain.forEachChunkFarToNear(eye.x, eye.z) { chunk ->
            totalVertices += terrain.fillChunkBatch(chunk, eye.x, eye.z)
        }
        val assembleNanos = System.nanoTime() - assembleStart
        assertTrue(totalVertices > 0)
        return projectNanos to assembleNanos
    }

    private fun measureTerrainFrameTime(cellsPerSide: Int): FpsRow {
        val prepared = rollingHillsMesh(cellsPerSide).prepare()
        val world = Scene3dWorldState(entities = listOf(Scene3dEntity(id = "terrain")))
        val camera = Camera(
            yawRadians = (-PI / 4.0).toFloat(),
            pitchRadians = (PI / 6.0).toFloat(),
            distance = 24f,
            target = Vec3.ZERO,
            fovYRadians = (PI / 3.0).toFloat(),
        )
        val cameraVersion = mutableStateOf(0)
        val scene = ImageComposeScene(800, 600, Density(1f)) {
            Scene3dView(
                world = world,
                camera = camera,
                meshes = mapOf("terrain" to prepared),
                cameraVersion = cameraVersion.value,
            )
        }
        var frame = 0
        fun renderOrbitFrame(): Long {
            camera.yawRadians += 0.05f
            cameraVersion.value++
            Snapshot.sendApplyNotifications()
            val start = System.nanoTime()
            val image = scene.render((frame++).toLong() * FRAME_NANOS)
            val elapsed = System.nanoTime() - start
            if (cellsPerSide == 224 && frame == WARMUP_FRAMES + TIMED_FRAMES) {
                writeReceipt(image.encodeToData(org.jetbrains.skia.EncodedImageFormat.PNG)?.bytes)
            }
            image.close()
            return elapsed
        }
        repeat(WARMUP_FRAMES) { renderOrbitFrame() }
        val totalNanos = (1..TIMED_FRAMES).sumOf { renderOrbitFrame() }
        scene.close()
        val msPerFrame = totalNanos / TIMED_FRAMES / 1e6f
        return FpsRow(
            triangles = cellsPerSide * cellsPerSide * 2,
            msPerFrame = msPerFrame,
            fps = 1000f / msPerFrame,
        )
    }

    private fun writeReceipt(pngBytes: ByteArray?) {
        if (pngBytes == null) return
        val outFile = File(System.getProperty("user.dir"), "build/terrain_batched_100k.png")
        outFile.parentFile.mkdirs()
        outFile.writeBytes(pngBytes)
        println("[scene3d-fps] wrote ${outFile.absolutePath}")
    }
}

private class FpsRow(val triangles: Int, val msPerFrame: Float, val fps: Float)
