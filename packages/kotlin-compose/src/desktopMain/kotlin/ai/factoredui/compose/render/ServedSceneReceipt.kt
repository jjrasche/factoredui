package ai.factoredui.compose.render

import ai.factoredui.compose.math.Vec3
import ai.factoredui.compose.scene3d.PreparedMesh
import ai.factoredui.compose.scene3d.Scene3dCameraPose
import ai.factoredui.compose.scene3d.Scene3dMesh
import ai.factoredui.compose.scene3d.Scene3dWorldState
import ai.factoredui.compose.scene3d.UNREADABLE_TRI_COLOR
import ai.factoredui.compose.scene3d.cameraOfPose
import ai.factoredui.compose.scene3d.captureScene3dPng
import ai.factoredui.compose.scene3d.currentPose
import ai.factoredui.compose.scene3d.prepare
import ai.factoredui.compose.scene3d.resolveAssetUrl
import ai.factoredui.compose.scene3d.toPose
import ai.factoredui.compose.scene3d.toVec3
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.File
import java.net.URI
import java.time.Instant
import javax.imageio.ImageIO

/**
 * What a served scene drew, stated so the data's owner can assert their own numbers against it.
 * Triangles draw flat and unshaded, so [meshColorPixels] is evidence about the DATA, and [camera]
 * is the pose actually drawn rather than the one asked for. The reference is [worldStateUrl] as
 * read at [capturedAt], and nothing about what it serves later.
 */
@Serializable
data class ServedSceneReceipt(
    @SerialName("world_state_url") val worldStateUrl: String,
    @SerialName("captured_at") val capturedAt: String,
    @SerialName("png_path") val pngPath: String,
    val width: Int,
    val height: Int,
    val camera: ReceiptPose,
    @SerialName("eye_inside_entity") val eyeInsideEntity: String?,
    val entities: List<ReceiptEntity>,
    @SerialName("mesh_color_pixels") val meshColorPixels: List<ReceiptFrameColor>,
    @SerialName("mesh_colors_never_drawn") val meshColorsNeverDrawn: List<String>,
    @SerialName("dominant_frame_colors") val dominantFrameColors: List<ReceiptFrameColor>,
    @SerialName("dominant_frame_color_pixel_floor") val dominantFrameColorPixelFloor: Int,
    @SerialName("triangles_with_unreadable_wire_color") val trianglesWithUnreadableWireColor: Int,
)

@Serializable
data class ReceiptPose(
    val eye: List<Float>,
    val target: List<Float>,
    @SerialName("fov_y_radians") val fovYRadians: Float,
)

@Serializable
data class ReceiptEntity(
    val id: String,
    @SerialName("mesh_url") val meshUrl: String?,
    val triangles: Int,
    @SerialName("distinct_colors") val distinctColors: List<String>,
)

@Serializable
data class ReceiptFrameColor(val hex: String, val pixels: Int)

private val worldDecoder = Json { ignoreUnknownKeys = true }
private val receiptEncoder = Json { prettyPrint = true }

// Antialiased edges blend two triangle colours into thousands of one-off near-colours, so the
// dominant list carries a floor. Every mesh colour is counted EXACTLY and separately: a thin
// slab edge draws tens of pixels, and calling that absent would report the data as unrendered.
private const val VISIBLE_COLOR_PIXEL_FLOOR = 0.0002

fun main(args: Array<String>) {
    if (args.size < 2) {
        System.err.println("usage: served-scene-receipt <world_state_url> <out.png> [width=1280] [height=800] [receipt.json]")
        kotlin.system.exitProcess(2)
    }
    val width = args.getOrNull(2)?.toIntOrNull() ?: 1280
    val height = args.getOrNull(3)?.toIntOrNull() ?: 800

    val receipt = runCatching { captureServedScene(args[0], args[1], width, height) }.getOrElse { failure ->
        System.err.println("served-scene-receipt: ${failure.message ?: failure::class.simpleName}")
        kotlin.system.exitProcess(3)
    }
    val receiptJson = receiptEncoder.encodeToString(receipt)
    args.getOrNull(4)?.let { path -> File(path).also { it.parentFile?.mkdirs() }.writeText(receiptJson) }
    println(receiptJson)
}

fun captureServedScene(worldStateUrl: String, pngPath: String, width: Int, height: Int): ServedSceneReceipt {
    val world = worldDecoder.decodeFromString(
        Scene3dWorldState.serializer(),
        URI(worldStateUrl).toURL().readText(),
    )
    val meshes = fetchMeshes(worldStateUrl, world)
    val camera = cameraOfPose(world.camera?.toPose() ?: framingPoseOf(world, meshes))
    val pose = camera.currentPose()
    val png = captureScene3dPng(
        world = world,
        camera = camera,
        meshes = meshes,
        width = width,
        height = height,
    )
    require(png.isNotEmpty()) { "capture produced 0 bytes for $worldStateUrl" }
    File(pngPath).also { it.parentFile?.mkdirs() }.writeBytes(png)
    return sceneReceiptOf(worldStateUrl, pngPath, png, world, meshes, pose, width, height)
}

internal fun sceneReceiptOf(
    worldStateUrl: String,
    pngPath: String,
    png: ByteArray,
    world: Scene3dWorldState,
    meshes: Map<String, PreparedMesh>,
    pose: Scene3dCameraPose,
    width: Int,
    height: Int,
): ServedSceneReceipt {
    val meshColors = meshes.values.flatMap { mesh -> mesh.colors.map { it.hex() } }.distinct().sorted()
    val histogram = frameColorHistogram(png)
    val meshColorPixels = meshColors
        .map { hex -> ReceiptFrameColor(hex = hex, pixels = histogram[hex] ?: 0) }
        .sortedByDescending { it.pixels }
    val dominanceFloor = (width.toLong() * height * VISIBLE_COLOR_PIXEL_FLOOR).toInt()
    return ServedSceneReceipt(
        worldStateUrl = worldStateUrl,
        capturedAt = Instant.now().toString(),
        pngPath = File(pngPath).absolutePath,
        width = width,
        height = height,
        camera = ReceiptPose(pose.eye.axes(), pose.target.axes(), pose.fovYRadians),
        eyeInsideEntity = entityHoldingEye(pose.eye, world, meshes),
        entities = world.entities.map { entity -> receiptEntityOf(entity.id, entity.meshUrl, meshes[entity.id]) },
        meshColorPixels = meshColorPixels,
        meshColorsNeverDrawn = meshColorPixels.filter { it.pixels == 0 }.map { it.hex },
        dominantFrameColors = histogram.entries
            .filter { it.value > dominanceFloor }
            .sortedByDescending { it.value }
            .map { ReceiptFrameColor(hex = it.key, pixels = it.value) },
        dominantFrameColorPixelFloor = dominanceFloor,
        trianglesWithUnreadableWireColor = meshes.values.sumOf { mesh -> mesh.colors.count { it == UNREADABLE_TRI_COLOR } },
    )
}

private fun fetchMeshes(worldStateUrl: String, world: Scene3dWorldState): Map<String, PreparedMesh> =
    world.entities.mapNotNull { entity ->
        val meshUrl = entity.meshUrl ?: return@mapNotNull null
        val body = URI(resolveAssetUrl(worldStateUrl, meshUrl)).toURL().readText()
        entity.id to worldDecoder.decodeFromString(Scene3dMesh.serializer(), body).prepare()
    }.toMap()

private fun receiptEntityOf(id: String, meshUrl: String?, mesh: PreparedMesh?) = ReceiptEntity(
    id = id,
    meshUrl = meshUrl,
    triangles = (mesh?.triangles?.size ?: 0) / 3,
    distinctColors = mesh?.colors?.map { it.hex() }?.distinct()?.sorted() ?: emptyList(),
)

private fun frameColorHistogram(png: ByteArray): Map<String, Int> {
    val image = ImageIO.read(ByteArrayInputStream(png))
    val counts = HashMap<String, Int>()
    for (y in 0 until image.height) {
        for (x in 0 until image.width) {
            val hex = argbHex(image.getRGB(x, y))
            counts[hex] = (counts[hex] ?: 0) + 1
        }
    }
    return counts
}

private fun entityHoldingEye(eye: Vec3, world: Scene3dWorldState, meshes: Map<String, PreparedMesh>): String? =
    world.entities.firstOrNull { entity ->
        val mesh = meshes[entity.id] ?: return@firstOrNull false
        if (mesh.vertices.isEmpty()) return@firstOrNull false
        val origin = entity.position.toVec3()
        val lowX = mesh.vertices.minOf { it.x } + origin.x
        val lowY = mesh.vertices.minOf { it.y } + origin.y
        val lowZ = mesh.vertices.minOf { it.z } + origin.z
        val highX = mesh.vertices.maxOf { it.x } + origin.x
        val highY = mesh.vertices.maxOf { it.y } + origin.y
        val highZ = mesh.vertices.maxOf { it.z } + origin.z
        eye.x in lowX..highX && eye.y in lowY..highY && eye.z in lowZ..highZ
    }?.id

private fun framingPoseOf(world: Scene3dWorldState, meshes: Map<String, PreparedMesh>): Scene3dCameraPose {
    val points = world.entities.flatMap { entity ->
        val origin = entity.position.toVec3()
        meshes[entity.id]?.vertices?.map { Vec3(it.x + origin.x, it.y + origin.y, it.z + origin.z) } ?: emptyList()
    }
    if (points.isEmpty()) return Scene3dCameraPose(eye = Vec3(6f, 4f, 6f), target = Vec3(0f, 1f, 0f))
    val center = Vec3(
        (points.minOf { it.x } + points.maxOf { it.x }) / 2f,
        (points.minOf { it.y } + points.maxOf { it.y }) / 2f,
        (points.minOf { it.z } + points.maxOf { it.z }) / 2f,
    )
    val standOff = points.maxOf { (it - center).length() } * 2.2f
    return Scene3dCameraPose(
        eye = Vec3(center.x + standOff, center.y + standOff * 0.6f, center.z + standOff),
        target = center,
    )
}

private fun Vec3.axes(): List<Float> = listOf(x, y, z)

private fun Color.hex(): String = argbHex(toArgb())

private fun argbHex(argb: Int): String = "#" + (argb and 0xFFFFFF).toString(16).padStart(6, '0')
