package ai.factoredui.compose.render

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

// Kotlin successor to illuminant's Python render gate: chrome=false + transparent makes alpha the
// silhouette, so IoU measures occupancy not shading — painterly-drift-invariant, but catches the
// skinning/bind-pose/quaternion breaks the believability judge is blind to. il-verify owns the inputs.
class GoldenPoseSilhouetteGateTest {

    private val width = 400
    private val height = 600
    private val density = 2f

    private val json = Json { ignoreUnknownKeys = true }

    private val resourceRoot = File("src/desktopTest/resources")
    private val posesDir = File(resourceRoot, "golden_poses")
    private val silhouettesDir = File(resourceRoot, "golden_silhouettes")

    private data class GoldenPose(val poseId: String, val tolerance: Double, val bareSpec: String)

    private fun loadGoldenPoses(): List<GoldenPose> {
        val files = posesDir.listFiles { file -> file.extension == "json" }
            ?: fail("golden_poses not found at ${posesDir.absolutePath} — workingDir must be the kotlin-compose module")
        return files.sortedBy { it.name }.map { file ->
            val pose = json.parseToJsonElement(file.readText()).jsonObject
            val poseId = pose.getValue("pose_id").jsonPrimitive.content
            val tolerance = pose.getValue("iou_tolerance").jsonPrimitive.double
            val bodyFramesResponse = pose.getValue("body_frames_response").jsonObject
            GoldenPose(poseId, tolerance, bareSpec(bodyFramesResponse))
        }
    }

    private fun bareSpec(bodyFramesResponse: JsonObject): String {
        val stringified = json.encodeToString(JsonObject.serializer(), bodyFramesResponse)
        val asJsonString = json.encodeToString(JsonPrimitive.serializer(), JsonPrimitive(stringified))
        return """{"spec_version":1,"renderer_min":1,"root":{"id":"scene","type":"scene3d","props":{"body_frame":$asJsonString,"chrome":false}}}"""
    }

    private fun renderSilhouette(pose: GoldenPose): BooleanArray {
        val png = renderSpecToPng(pose.bareSpec, width = width, height = height, density = density, transparent = true)
        return alphaMask(ImageIO.read(ByteArrayInputStream(png)))
    }

    private fun alphaMask(image: BufferedImage): BooleanArray {
        val mask = BooleanArray(image.width * image.height)
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                mask[y * image.width + x] = ((image.getRGB(x, y) ushr 24) and 0xFF) > 0
            }
        }
        return mask
    }

    private fun intersectionOverUnion(rendered: BooleanArray, golden: BooleanArray): Double {
        require(rendered.size == golden.size) { "silhouette size mismatch ${rendered.size} vs ${golden.size}" }
        var intersection = 0
        var union = 0
        for (i in rendered.indices) {
            if (rendered[i] && golden[i]) intersection++
            if (rendered[i] || golden[i]) union++
        }
        return if (union == 0) 1.0 else intersection.toDouble() / union
    }

    @Test
    fun everyGoldenPoseSilhouetteMatchesItsCommittedGoldenWithinTolerance() {
        val poses = loadGoldenPoses()
        assertTrue(poses.isNotEmpty(), "no golden poses found at ${posesDir.absolutePath}")
        val regressions = mutableListOf<String>()
        for (pose in poses) {
            val goldenPng = File(silhouettesDir, "${pose.poseId}.png")
            if (!goldenPng.exists()) {
                fail("golden silhouette missing for '${pose.poseId}' — run establish (-Dgate.establish=true), eyeball, commit ${goldenPng.path}")
            }
            val iou = intersectionOverUnion(renderSilhouette(pose), alphaMask(ImageIO.read(goldenPng)))
            if (iou < pose.tolerance) {
                regressions += "${pose.poseId}: IoU=%.4f < tol=%.2f".format(iou, pose.tolerance)
            }
        }
        assertTrue(regressions.isEmpty(), "scene3d silhouette regression on ${regressions.size} pose(s):\n${regressions.joinToString("\n")}")
    }

    // GATE_ESTABLISH=true gates this so CI never regenerates goldens silently; run once, eyeball, commit.
    @Test
    fun establishGoldenSilhouettesWhenRequested() {
        if (System.getenv("GATE_ESTABLISH") != "true") return
        silhouettesDir.mkdirs()
        for (pose in loadGoldenPoses()) {
            val png = renderSpecToPng(pose.bareSpec, width = width, height = height, density = density, transparent = true)
            File(silhouettesDir, "${pose.poseId}.png").writeBytes(png)
        }
    }
}
