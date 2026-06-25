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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import ai.factoredui.compose.forcegraph.math.Camera
import ai.factoredui.compose.forcegraph.math.Matrix4
import ai.factoredui.compose.forcegraph.math.Vec3
import ai.factoredui.compose.forcegraph.math.project
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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

    // Instrument validation: a 24-joint jointFrame skeleton (lines, no mesh, no paint) must
    // rasterize to a recognizable standing human. Source pose = agent-platform human24().restPose
    // (Z-up meters); mapped to entity space (x, z, -y) exactly as RenderScene3d maps the live stream.
    @Test
    fun rendersStandingSkeletonToPng() = runComposeUiTest {
        val standing = STANDING_HUMAN24
        val world = Scene3dWorldState(
            entities = listOf(Scene3dEntity(id = "body", jointFrame = standing, kind = "body")),
        )
        renderSkeletonToPng(world, bodyHeight = 1.65f, fileName = "scene3d_skeleton_standing.png")
    }

    @Test
    fun rendersWalkStripToPng() {
        renderWalkStrip("walk_kin.json", "scene3d_walk_kin_strip.png", trackLateral = false)
        renderWalkStrip("walk_tracked.json", "scene3d_walk_tracked_strip.png", trackLateral = false)
    }

    @Test
    fun rendersPokeStripToPng() {
        renderWalkStrip("walk_poke.json", "scene3d_walk_poke_strip.png", fromFrac = 0.35f, trackLateral = false)
    }

    @OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
    private fun renderWalkStrip(
        srcName: String,
        outName: String,
        fromFrac: Float = 0f,
        trackLateral: Boolean = true,
    ) {
        val frames = loadScratchFrames(srcName)
        val samples = 8
        val cellW = 220
        val cellH = 460
        val first = (fromFrac * (frames.size - 1)).toInt()
        val strip = java.awt.image.BufferedImage(cellW * samples, cellH, java.awt.image.BufferedImage.TYPE_INT_ARGB)
        val g = strip.createGraphics()
        for (i in 0 until samples) {
            val span = frames.size - 1 - first
            val frameIdx = if (samples == 1) first else first + i * span / (samples - 1)
            val jointFrame = frames[frameIdx]
            val pelvis = jointFrame[0]
            val camera = Camera(
                yawRadians = (-PI / 3.4).toFloat(),
                pitchRadians = (PI / 18.0).toFloat(),
                distance = 3.2f,
                target = Vec3(if (trackLateral) pelvis[0] else 0f, 0.95f, pelvis[2]),
                fovYRadians = (PI / 3.0).toFloat(),
            )
            val world = Scene3dWorldState(
                entities = listOf(Scene3dEntity(id = "body", jointFrame = jointFrame, kind = "body")),
            )
            val scene = androidx.compose.ui.ImageComposeScene(cellW, cellH, androidx.compose.ui.unit.Density(1f)) {
                Scene3dView(world = world, camera = camera, modifier = Modifier.size(cellW.dp, cellH.dp))
            }
            val png = scene.render().encodeToData(org.jetbrains.skia.EncodedImageFormat.PNG)?.bytes ?: ByteArray(0)
            scene.close()
            g.drawImage(ImageIO.read(java.io.ByteArrayInputStream(png)), i * cellW, 0, null)
        }
        g.dispose()
        val outFile = File(System.getProperty("user.dir"), "build/$outName")
        outFile.parentFile.mkdirs()
        ImageIO.write(strip, "PNG", outFile)
        println("[scene3d] wrote ${outFile.absolutePath}")
    }

    private fun loadScratchFrames(srcName: String): List<List<List<Float>>> {
        val file = File("C:/Users/rasche_j/Documents/workspace/.scene-scratch/$srcName")
        val root = json.parseToJsonElement(file.readText()).jsonObject
        return root["frames"]!!.jsonArray.map { frame ->
            frame.jsonArray.map { joint -> joint.jsonArray.map { it.jsonPrimitive.float } }
        }
    }

    private fun androidx.compose.ui.test.ComposeUiTest.renderSkeletonToPng(
        world: Scene3dWorldState,
        bodyHeight: Float,
        fileName: String,
    ) {
        val camera = Camera(
            yawRadians = (-PI / 5.0).toFloat(),
            pitchRadians = (PI / 16.0).toFloat(),
            distance = bodyHeight * 2.1f,
            target = Vec3(0f, bodyHeight * 0.52f, 0f),
            fovYRadians = (PI / 3.0).toFloat(),
        )
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                Box(Modifier.size(800.dp)) {
                    Scene3dView(world = world, camera = camera, modifier = Modifier.size(800.dp))
                }
            }
        }
        val outFile = File(System.getProperty("user.dir"), "build/$fileName")
        outFile.parentFile.mkdirs()
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "PNG", outFile)
        println("[scene3d] wrote ${outFile.absolutePath}")
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

    private fun readMeshResource(name: String): String {
        val stream = this::class.java.classLoader.getResourceAsStream(name)
            ?: error("missing test resource: $name")
        return stream.bufferedReader().use { it.readText() }
    }
}

// agent-platform human24().restPose (Z-up meters) mapped to entity space (x, z, -y).
private val STANDING_HUMAN24: List<List<Float>> = listOf(
    listOf(0.0f, 0.95f, 0.0f),
    listOf(0.10f, 0.90f, 0.0f), listOf(0.10f, 0.50f, 0.0f), listOf(0.10f, 0.08f, 0.0f), listOf(0.10f, 0.04f, -0.12f),
    listOf(-0.10f, 0.90f, 0.0f), listOf(-0.10f, 0.50f, 0.0f), listOf(-0.10f, 0.08f, 0.0f), listOf(-0.10f, 0.04f, -0.12f),
    listOf(0.0f, 1.10f, 0.0f), listOf(0.0f, 1.25f, 0.0f), listOf(0.0f, 1.40f, 0.0f), listOf(0.0f, 1.50f, 0.0f), listOf(0.0f, 1.65f, 0.0f),
    listOf(0.05f, 1.42f, 0.0f), listOf(0.18f, 1.42f, 0.0f), listOf(0.30f, 1.20f, 0.0f), listOf(0.32f, 0.98f, 0.0f),
    listOf(-0.05f, 1.42f, 0.0f), listOf(-0.18f, 1.42f, 0.0f), listOf(-0.30f, 1.20f, 0.0f), listOf(-0.32f, 0.98f, 0.0f),
    listOf(0.33f, 0.90f, 0.0f), listOf(-0.33f, 0.90f, 0.0f),
)
