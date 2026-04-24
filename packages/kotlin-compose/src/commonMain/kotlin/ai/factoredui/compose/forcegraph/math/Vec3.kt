package ai.factoredui.compose.forcegraph.math

import kotlin.math.sqrt

/**
 * 3D vector. Immutable; arithmetic returns new instances.
 *
 * Float rather than Double — rendering is the only consumer and GPU/Skia
 * take floats natively. Precision tradeoff is intentional: we are animating
 * a family-scale signal graph, not simulating celestial mechanics.
 */
data class Vec3(val x: Float, val y: Float, val z: Float) {

    operator fun plus(other: Vec3): Vec3 = Vec3(x + other.x, y + other.y, z + other.z)
    operator fun minus(other: Vec3): Vec3 = Vec3(x - other.x, y - other.y, z - other.z)
    operator fun times(scalar: Float): Vec3 = Vec3(x * scalar, y * scalar, z * scalar)
    operator fun unaryMinus(): Vec3 = Vec3(-x, -y, -z)

    fun dot(other: Vec3): Float = x * other.x + y * other.y + z * other.z

    fun cross(other: Vec3): Vec3 = Vec3(
        y * other.z - z * other.y,
        z * other.x - x * other.z,
        x * other.y - y * other.x,
    )

    fun lengthSquared(): Float = x * x + y * y + z * z

    fun length(): Float = sqrt(lengthSquared())

    /**
     * Safe normalize: zero-length vector returns zero, not NaN. Callers
     * computing a direction from two close points must be resilient to
     * that degenerate case.
     */
    fun normalize(): Vec3 {
        val len = length()
        return if (len < EPSILON) ZERO else Vec3(x / len, y / len, z / len)
    }

    companion object {
        val ZERO: Vec3 = Vec3(0f, 0f, 0f)
        const val EPSILON: Float = 1e-6f
    }
}
