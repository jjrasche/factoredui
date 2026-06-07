package ai.factoredui.compose.scene3d

import ai.factoredui.compose.forcegraph.math.Vec3
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Server owns the canonical copy; viewport POSTs intents, never mutates. Wire shape: spec-types.ts.
@Serializable
data class Scene3dWorldState(
    val entities: List<Scene3dEntity> = emptyList(),
    val camera: Scene3dCameraState? = null,
    val lights: List<Scene3dLight> = emptyList(),
    val background: String? = null,
)

@Serializable
data class Scene3dEntity(
    val id: String,
    @SerialName("mesh_url") val meshUrl: String? = null,
    val position: List<Float> = listOf(0f, 0f, 0f),
    val rotation: List<Float> = listOf(0f, 0f, 0f),
    val scale: Float = 1f,
    val selected: Boolean = false,
    val status: String = "ready",
    @SerialName("pose_ref") val poseRef: String? = null,
    @SerialName("joint_frame") val jointFrame: List<List<Float>>? = null,
)

@Serializable
data class Scene3dCameraState(
    val position: List<Float> = listOf(6f, 4f, 6f),
    val target: List<Float> = listOf(0f, 1f, 0f),
    val fov: Float? = null,
)

@Serializable
data class Scene3dLight(
    val type: String = "key",
    val position: List<Float> = listOf(4f, 6f, 4f),
    val intensity: Float = 1f,
    val color: String? = null,
)

fun List<Float>.toVec3(): Vec3 =
    Vec3(getOrElse(0) { 0f }, getOrElse(1) { 0f }, getOrElse(2) { 0f })
