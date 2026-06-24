package ai.factoredui.compose.scene3d

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import ai.factoredui.compose.forcegraph.math.Camera
import ai.factoredui.compose.forcegraph.math.Matrix4
import ai.factoredui.compose.forcegraph.math.Vec3
import ai.factoredui.compose.forcegraph.math.project
import kotlinx.serialization.json.Json
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class Scene3dDesktopRenderTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun rendersHeiglMeshToPng() = runComposeUiTest {
        val mesh = json
            .decodeFromString(Scene3dMesh.serializer(), readMeshResource("heigl.json"))
            .prepare()
        val world = Scene3dWorldState(
            entities = listOf(
                Scene3dEntity(id = "heigl", position = listOf(-0.8f, 0f, 0f)),
                Scene3dEntity(id = "guest", position = listOf(0.8f, 0f, 0f), selected = true),
            ),
            lights = listOf(
                Scene3dLight(type = "key", position = listOf(2.5f, 3.0f, 2.0f), intensity = 1.2f),
                Scene3dLight(type = "fill", position = listOf(-3.0f, 2.5f, 1.0f), intensity = 0.5f),
            ),
        )
        renderToPng(world, mapOf("heigl" to mesh, "guest" to mesh), mesh.height, "scene3d_desktop_heigl.png")
        assertMeshDrew(minNonBackgroundFraction = 0.02f)
    }

    @Test
    fun posesRiggedMeshViaForwardKinematics() = runComposeUiTest {
        val mesh = json
            .decodeFromString(Scene3dMesh.serializer(), readMeshResource("heigl_rigged.json"))
            .prepare()
        val rig = requireNotNull(mesh.rig) { "rigged mesh produced no rig" }
        val pose = rig.identityPose()
        // Bend both elbows (SMPL joints 18 = L_elbow, 19 = R_elbow) ~80 degrees.
        pose[18] = Matrix4.rotate(Vec3(0f, 1f, 0f), (-1.4).toFloat())
        pose[19] = Matrix4.rotate(Vec3(0f, 1f, 0f), 1.4f)
        val posed = PreparedMesh(
            vertices = rig.posedVertices(pose),
            triangles = mesh.triangles,
            colors = mesh.colors,
            height = mesh.height,
        )
        val world = Scene3dWorldState(
            entities = listOf(Scene3dEntity(id = "heigl", position = listOf(0f, 0f, 0f))),
        )
        renderToPng(world, mapOf("heigl" to posed), mesh.height, "scene3d_desktop_posed.png")
        assertMeshDrew(minNonBackgroundFraction = 0.02f)
    }

    @Test
    fun jointAimDragMovesJointTowardCursor() {
        val mesh = json
            .decodeFromString(Scene3dMesh.serializer(), readMeshResource("heigl_rigged.json"))
            .prepare()
        val rig = requireNotNull(mesh.rig)
        val world = Scene3dWorldState(entities = listOf(Scene3dEntity(id = "heigl", position = listOf(0f, 0f, 0f))))
        val meshes = mapOf("heigl" to mesh)
        val poses = mapOf("heigl" to rig.identityPose())
        val width = 800f
        val height = 800f
        val camera = Camera(
            yawRadians = (-PI / 4.0).toFloat(),
            pitchRadians = (PI / 9.0).toFloat(),
            distance = mesh.height * 3.0f,
            target = Vec3(0f, mesh.height * 0.5f, 0f),
            fovYRadians = (PI / 3.0).toFloat(),
        )
        val wristJoint = 20
        val view = camera.viewMatrix()
        val proj = camera.projectionMatrix(width / height)
        val before = jointOrigins(rig.worldJointTransforms(poses.getValue("heigl")))[wristJoint]
            .project(view, proj, width, height)
        val cursorBelow = Offset(before.x, before.y + 220f)

        val newPose = requireNotNull(
            solveJointAim(world, meshes, poses, "heigl", wristJoint, camera, width, height, cursorBelow),
        ) { "aim-solve returned null" }
        val after = jointOrigins(rig.worldJointTransforms(newPose))[wristJoint]
            .project(view, proj, width, height)

        println("[scene3d] wrist screen y: before=${before.y} after=${after.y} (dragged cursor to ${cursorBelow.y})")
        assertTrue(after.y > before.y + 20f, "dragging the wrist down should move the joint down on screen")
    }

    @Test
    fun romClampLimitsJointRotation() {
        val mesh = json
            .decodeFromString(Scene3dMesh.serializer(), readMeshResource("heigl_rigged.json"))
            .prepare()
        val rig = requireNotNull(mesh.rig)
        val pose = rig.identityPose()
        // Spine1 (joint 3) ROM is small (~0.5 rad); a 1.5 rad twist must clamp down to it.
        pose[3] = Matrix4.rotate(Vec3(0f, 1f, 0f), 1.5f)
        val clamped = clampToRom(pose, 3)
        val m = clamped[3].m
        val angle = kotlin.math.acos(((m[0] + m[5] + m[10] - 1f) * 0.5f).coerceIn(-1f, 1f))
        println("[scene3d] spine rotation after ROM clamp: $angle (limit ~0.5)")
        assertTrue(angle <= 0.55f, "spine rotation should clamp to ROM (~0.5 rad), got $angle")
    }

    @Test
    fun movingTheTimelineSliderAdjustsTheFrame() {
        // GIVEN a 60-frame motion clip loaded in the scene3d timeline
        val frameCount = 60
        // WHEN the playback slider is dragged to the start / middle / end
        // THEN the playhead frame tracks the slider position
        assertEquals(0, scrubFrameForFraction(0f, frameCount), "start of the slider shows the first frame")
        assertEquals(frameCount - 1, scrubFrameForFraction(1f, frameCount), "end of the slider shows the last frame")
        val mid = scrubFrameForFraction(0.5f, frameCount)
        assertTrue(mid in 28..30, "the half-way slider position lands mid-clip, got frame $mid")

        // AND dragging the slider rightward only ever advances the frame — it never jumps backward
        var previous = -1
        for (step in 0..20) {
            val frame = scrubFrameForFraction(step / 20f, frameCount)
            assertTrue(frame >= previous, "frame must not go backward as the slider moves right: $frame after $previous")
            previous = frame
        }

        // AND the slider position shown for a frame round-trips back to that exact frame
        assertEquals(42, scrubFrameForFraction(scrubFractionForFrame(42, frameCount), frameCount), "frame 42 round-trips through the slider")
    }

    @Test
    fun scrubbingADegenerateClipClampsInsteadOfCrashing() {
        // GIVEN a 1-frame (or empty) clip, scrubbing must clamp — no divide-by-zero on (frameCount - 1)
        assertEquals(0, scrubFrameForFraction(0.7f, 1), "a single-frame clip stays on frame 0")
        assertEquals(0f, scrubFractionForFrame(0, 1), "a single-frame clip parks the slider at 0")
    }

    private fun androidx.compose.ui.test.ComposeUiTest.renderToPng(
        world: Scene3dWorldState,
        meshes: Map<String, PreparedMesh>,
        height: Float,
        fileName: String,
    ) {
        val camera = Camera(
            yawRadians = (-PI / 4.0).toFloat(),
            pitchRadians = (PI / 9.0).toFloat(),
            distance = height * 3.0f,
            target = Vec3(0f, height * 0.5f, 0f),
            fovYRadians = (PI / 3.0).toFloat(),
        )
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                Box(Modifier.size(800.dp)) {
                    Scene3dView(world = world, camera = camera, meshes = meshes, modifier = Modifier.size(800.dp))
                }
            }
        }
        val outFile = File(System.getProperty("user.dir"), "build/$fileName")
        outFile.parentFile.mkdirs()
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "PNG", outFile)
        println("[scene3d] wrote ${outFile.absolutePath}")
    }

    private fun androidx.compose.ui.test.ComposeUiTest.assertMeshDrew(minNonBackgroundFraction: Float) {
        val pixels = onRoot().captureToImage().toPixelMap()
        val bgRed = 0x2B / 255f
        val bgGreen = 0x2B / 255f
        val bgBlue = 0x30 / 255f
        var drawnCount = 0
        for (py in 0 until pixels.height) {
            for (px in 0 until pixels.width) {
                val pixel = pixels[px, py]
                val delta = kotlin.math.abs(pixel.red - bgRed) +
                    kotlin.math.abs(pixel.green - bgGreen) +
                    kotlin.math.abs(pixel.blue - bgBlue)
                if (delta > 0.05f) drawnCount++
            }
        }
        val fraction = drawnCount.toFloat() / (pixels.width * pixels.height)
        println("[scene3d] non-background pixel fraction: $fraction (floor $minNonBackgroundFraction)")
        assertTrue(
            fraction >= minNonBackgroundFraction,
            "Mesh did not draw: only $fraction of pixels differ from background, expected >= $minNonBackgroundFraction",
        )
    }

    private fun readMeshResource(name: String): String {
        val stream = this::class.java.classLoader.getResourceAsStream(name)
            ?: error("missing test resource: $name")
        return stream.bufferedReader().use { it.readText() }
    }
}
