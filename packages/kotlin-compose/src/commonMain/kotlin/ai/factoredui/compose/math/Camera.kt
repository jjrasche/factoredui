package ai.factoredui.compose.math

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Orbit camera — yaw/pitch/distance describe position relative to `target`.
 * Mutable state is intentional: Camera is owned by the Compose view and
 * updated from pointer events every frame.
 */
class Camera(
    var yawRadians: Float = -PI.toFloat() / 4f,
    var pitchRadians: Float = (PI / 9f).toFloat(),
    var distance: Float = 25f,
    var target: Vec3 = Vec3.ZERO,
    var fovYRadians: Float = (PI / 3f).toFloat(),
    var near: Float = 0.1f,
    var far: Float = 500f,
) {

    fun eyePosition(): Vec3 {
        val cosPitch = cos(pitchRadians)
        val x = target.x + distance * cosPitch * sin(yawRadians)
        val y = target.y + distance * sin(pitchRadians)
        val z = target.z + distance * cosPitch * cos(yawRadians)
        return Vec3(x, y, z)
    }

    fun viewMatrix(): Matrix4 = Matrix4.lookAt(eyePosition(), target, Vec3(0f, 1f, 0f))

    fun projectionMatrix(aspect: Float): Matrix4 =
        Matrix4.perspective(fovYRadians, aspect, near, far)

    /**
     * Orbit by pointer delta. Dragging right increases yaw; dragging down
     * decreases pitch. Pitch clamped shy of the poles so lookAt's up-vector
     * doesn't degenerate.
     */
    fun drag(deltaX: Float, deltaY: Float) {
        yawRadians -= deltaX * DRAG_SENSITIVITY
        pitchRadians = clampPitch(pitchRadians + deltaY * DRAG_SENSITIVITY)
    }

    /**
     * Scroll zoom. Positive delta = zoom in. Distance clamped so the camera
     * can't pass through the target nor fly off to infinity.
     */
    fun zoom(delta: Float) {
        val factor = if (delta >= 0f) 1f / (1f + delta * ZOOM_SENSITIVITY) else 1f - delta * ZOOM_SENSITIVITY
        distance = clampDistance(distance * factor)
    }

    fun pan(deltaX: Float, deltaY: Float) {
        // Pan in camera-space axes projected into world: use right and up.
        val eye = eyePosition()
        val forward = (target - eye).normalize()
        val right = forward.cross(Vec3(0f, 1f, 0f)).normalize()
        val up = right.cross(forward).normalize()
        val scale = distance * PAN_SENSITIVITY
        target = target + right * (-deltaX * scale) + up * (deltaY * scale)
    }

    private fun clampPitch(value: Float): Float = max(min(value, PITCH_LIMIT), -PITCH_LIMIT)

    private fun clampDistance(value: Float): Float = max(min(value, MAX_DISTANCE), MIN_DISTANCE)

    companion object {
        private const val DRAG_SENSITIVITY = 0.01f
        private const val ZOOM_SENSITIVITY = 0.1f
        private const val PAN_SENSITIVITY = 0.002f
        private const val PITCH_LIMIT = (PI / 2 - 0.01).toFloat()
        private const val MIN_DISTANCE = 2f
        private const val MAX_DISTANCE = 300f
    }
}
