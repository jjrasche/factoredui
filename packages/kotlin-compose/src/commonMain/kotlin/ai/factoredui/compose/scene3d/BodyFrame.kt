package ai.factoredui.compose.scene3d

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

// @JsonNames accepts both casings: the :embodiment DB row is snake_case (b58fe151), the
// POST /embodiment/body-frames DTO is camelCase — neither can silently parse to all-defaults.
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class BodyFrame(
    @JsonNames("root_x") val rootX: Float = 0f,
    @JsonNames("root_y") val rootY: Float = 0f,
    @JsonNames("root_z") val rootZ: Float = 0f,
    @JsonNames("facing_x") val facingX: Float = 0f,
    @JsonNames("facing_y") val facingY: Float = 0f,
    val joints: List<List<Float>> = emptyList(),
    @JsonNames("joint_count") val jointCount: Int = 0,
    @JsonNames("up_axis") val upAxis: Int = 2,
    val velocities: List<List<Float>> = emptyList(),
    val contacts: List<BodyContact> = emptyList(),
    @JsonNames("scalar_channel") val scalarChannel: List<Float> = emptyList(),
    @JsonNames("scalar_channel_tag") val scalarChannelTag: String = "",
)

@Serializable
data class BodyContact(
    val effector: String,
    val on: Boolean = false,
    val load: Float = 0f,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class BodyFramesResponse(
    val frames: List<BodyFrame> = emptyList(),
    val playhead: Int = 0,
    @JsonNames("target_x") val targetX: Float = 0f,
    @JsonNames("target_y") val targetY: Float = 0f,
    @JsonNames("target_z") val targetZ: Float = 0f,
    val trajectory: List<List<Float>> = emptyList(),
)
