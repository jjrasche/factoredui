package ai.factoredui.compose.scene3d

import ai.factoredui.compose.math.Camera
import ai.factoredui.compose.math.Vec3
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

const val DEFAULT_FOV_Y_RADIANS: Float = (PI / 3.0).toFloat()

private const val REACH_EPSILON = 1e-4f

/** A viewpoint stated the way a person states one: stand here, look there, this wide. */
data class Scene3dCameraPose(
    val eye: Vec3,
    val target: Vec3,
    val fovYRadians: Float = DEFAULT_FOV_Y_RADIANS,
)

fun Camera.applyPose(pose: Scene3dCameraPose) {
    target = pose.target
    fovYRadians = pose.fovYRadians
    val offset = pose.eye - pose.target
    val reach = offset.length()
    if (reach < REACH_EPSILON) return
    val direction = offset.normalize()
    setOrbit(
        yaw = atan2(direction.x, direction.z),
        pitch = asin(direction.y.coerceIn(-1f, 1f)),
        distance = reach,
    )
}

fun Camera.currentPose(): Scene3dCameraPose = Scene3dCameraPose(eyePosition(), target, fovYRadians)

fun Scene3dCameraState.toPose(): Scene3dCameraPose = Scene3dCameraPose(
    eye = position.toVec3(),
    target = target.toVec3(),
    fovYRadians = fov ?: DEFAULT_FOV_Y_RADIANS,
)

fun cameraOfPose(pose: Scene3dCameraPose): Camera = Camera().apply { applyPose(pose) }

fun orbitPoseSequence(
    target: Vec3,
    distance: Float,
    pitchRadians: Float,
    steps: Int,
    startYawRadians: Float = 0f,
    fovYRadians: Float = DEFAULT_FOV_Y_RADIANS,
): List<Scene3dCameraPose> {
    require(steps > 0) { "an orbit needs at least one step, asked for $steps" }
    val sweep = 2.0 * PI
    return (0 until steps).map { step ->
        val yaw = startYawRadians + (sweep * step / steps).toFloat()
        Scene3dCameraPose(eye = orbitEyeOf(target, distance, pitchRadians, yaw), target = target, fovYRadians = fovYRadians)
    }
}

fun orbitEyeOf(target: Vec3, distance: Float, pitchRadians: Float, yawRadians: Float): Vec3 {
    val horizontalReach = distance * cos(pitchRadians)
    return Vec3(
        target.x + horizontalReach * sin(yawRadians),
        target.y + distance * sin(pitchRadians),
        target.z + horizontalReach * cos(yawRadians),
    )
}
