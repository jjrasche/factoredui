package ai.factoredui.compose.scene3d

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Wire shape pinned by il-embodiment's :embodiment domain (b58fe151); the renderer reads body_frames
// rows at body_playheads.playhead. velocities/scalar_channel ride null until the believable slot lands.
@Serializable
data class BodyFrame(
    @SerialName("root_x") val rootX: Float = 0f,
    @SerialName("root_y") val rootY: Float = 0f,
    @SerialName("root_z") val rootZ: Float = 0f,
    @SerialName("facing_x") val facingX: Float = 0f,
    @SerialName("facing_y") val facingY: Float = 0f,
    val joints: List<List<Float>> = emptyList(),
    @SerialName("joint_count") val jointCount: Int = 0,
    @SerialName("up_axis") val upAxis: Int = 2,
    val velocities: List<List<Float>> = emptyList(),
    val contacts: List<BodyContact> = emptyList(),
    @SerialName("scalar_channel") val scalarChannel: List<Float> = emptyList(),
    @SerialName("scalar_channel_tag") val scalarChannelTag: String = "",
)

@Serializable
data class BodyContact(
    val effector: String,
    val on: Boolean = false,
    val load: Float = 0f,
)
