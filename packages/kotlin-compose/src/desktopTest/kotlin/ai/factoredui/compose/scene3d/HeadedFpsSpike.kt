package ai.factoredui.compose.scene3d

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import ai.factoredui.compose.math.Camera
import ai.factoredui.compose.math.Vec3
import java.awt.Rectangle
import java.awt.Robot
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.PI

private const val HEADED_WARMUP_FRAMES = 30
private const val HEADED_TIMED_FRAMES = 120
private val STAGE_CELLS_PER_SIDE = listOf(71, 158, 224)

// Headed counterpart of Scene3dTerrainFpsSpikeTest: same rolling-hills meshes and orbiting
// camera, rendered through a real GPU-backed Compose window (default Skiko renderer, vsync
// disabled so fps reflects render capability rather than the monitor refresh rate).
// Run: ./gradlew :kotlin-compose:headedFpsSpike — a window opens, results print to stdout.
fun main() {
    System.setProperty("skiko.vsync.enabled", "false")
    application {
        val windowState = rememberWindowState(size = DpSize(800.dp, 600.dp))
        Window(
            onCloseRequest = ::exitApplication,
            state = windowState,
            title = "scene3d headed fps spike",
            resizable = false,
        ) {
            val preparedStages = remember { STAGE_CELLS_PER_SIDE.map { rollingHillsMesh(it).prepare() } }
            val world = remember { Scene3dWorldState(entities = listOf(Scene3dEntity(id = "terrain"))) }
            val camera = remember { orbitStartCamera() }
            var stageIndex by remember { mutableStateOf(0) }
            var cameraVersion by remember { mutableStateOf(0) }
            Scene3dView(
                world = world,
                camera = camera,
                meshes = mapOf("terrain" to preparedStages[stageIndex]),
                cameraVersion = cameraVersion,
            )
            val awtWindow = window
            LaunchedEffect(Unit) {
                val resultRows = ArrayList<String>(STAGE_CELLS_PER_SIDE.size)
                for ((index, cellsPerSide) in STAGE_CELLS_PER_SIDE.withIndex()) {
                    stageIndex = index
                    repeat(HEADED_WARMUP_FRAMES) {
                        withFrameNanos {
                            camera.yawRadians += 0.05f
                            cameraVersion++
                        }
                    }
                    val startNanos = System.nanoTime()
                    repeat(HEADED_TIMED_FRAMES) {
                        withFrameNanos {
                            camera.yawRadians += 0.05f
                            cameraVersion++
                        }
                    }
                    val elapsedNanos = System.nanoTime() - startNanos
                    val msPerFrame = elapsedNanos / HEADED_TIMED_FRAMES / 1e6f
                    resultRows.add(
                        "[scene3d-headed-fps] %d | %.1f | %.1f".format(
                            cellsPerSide * cellsPerSide * 2,
                            msPerFrame,
                            1000f / msPerFrame,
                        ),
                    )
                    if (cellsPerSide == STAGE_CELLS_PER_SIDE.last()) {
                        awtWindow.isAlwaysOnTop = true
                        awtWindow.toFront()
                        repeat(10) {
                            withFrameNanos {
                                camera.yawRadians += 0.05f
                                cameraVersion++
                            }
                        }
                        writeWindowReceipt(awtWindow)
                    }
                }
                println(
                    "[scene3d-headed-fps] triangles | ms/frame | fps " +
                        "($HEADED_TIMED_FRAMES frames after $HEADED_WARMUP_FRAMES warmup, vsync off)",
                )
                resultRows.forEach(::println)
                exitApplication()
            }
        }
    }
}

private fun orbitStartCamera(): Camera = Camera(
    yawRadians = (-PI / 4.0).toFloat(),
    pitchRadians = (PI / 6.0).toFloat(),
    distance = 24f,
    target = Vec3.ZERO,
    fovYRadians = (PI / 3.0).toFloat(),
)

private fun writeWindowReceipt(window: java.awt.Window) {
    val screenBounds = Rectangle(window.locationOnScreen, window.size)
    val capture = Robot().createScreenCapture(screenBounds)
    val outFile = File(System.getProperty("user.dir"), "build/terrain_headed_100k.png")
    outFile.parentFile.mkdirs()
    ImageIO.write(capture, "png", outFile)
    println("[scene3d-headed-fps] wrote ${outFile.absolutePath}")
}
