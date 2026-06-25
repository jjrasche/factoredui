package ai.factoredui.compose.math

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * Column-major 4×4 matrix. Index convention: m[col * 4 + row].
 * Column-major matches OpenGL/Skia conventions and lets us compose
 * transforms left-to-right as `parent.times(child)`.
 */
class Matrix4(val m: FloatArray) {

    init {
        require(m.size == 16) { "Matrix4 requires 16 floats, got ${m.size}" }
    }

    operator fun times(other: Matrix4): Matrix4 {
        val result = FloatArray(16)
        for (col in 0..3) {
            for (row in 0..3) {
                var sum = 0f
                for (k in 0..3) {
                    sum += m[k * 4 + row] * other.m[col * 4 + k]
                }
                result[col * 4 + row] = sum
            }
        }
        return Matrix4(result)
    }

    /**
     * Apply this matrix to a 3D point (w=1). Returns the transformed
     * (x, y, z, w) as a 4-tuple — caller divides by w for NDC.
     */
    fun transform(v: Vec3): FloatArray {
        val out = FloatArray(4)
        for (row in 0..3) {
            out[row] =
                m[0 * 4 + row] * v.x +
                    m[1 * 4 + row] * v.y +
                    m[2 * 4 + row] * v.z +
                    m[3 * 4 + row] * 1f
        }
        return out
    }

    companion object {

        fun identity(): Matrix4 = Matrix4(
            floatArrayOf(
                1f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f,
                0f, 0f, 1f, 0f,
                0f, 0f, 0f, 1f,
            ),
        )

        fun translate(t: Vec3): Matrix4 = Matrix4(
            floatArrayOf(
                1f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f,
                0f, 0f, 1f, 0f,
                t.x, t.y, t.z, 1f,
            ),
        )

        /**
         * Rotation around an arbitrary axis by `angleRadians` (right-hand rule).
         * Axis is normalized internally.
         */
        fun rotate(axis: Vec3, angleRadians: Float): Matrix4 {
            val a = axis.normalize()
            val c = cos(angleRadians)
            val s = sin(angleRadians)
            val t = 1f - c
            val x = a.x; val y = a.y; val z = a.z
            return Matrix4(
                floatArrayOf(
                    t * x * x + c, t * x * y + s * z, t * x * z - s * y, 0f,
                    t * x * y - s * z, t * y * y + c, t * y * z + s * x, 0f,
                    t * x * z + s * y, t * y * z - s * x, t * z * z + c, 0f,
                    0f, 0f, 0f, 1f,
                ),
            )
        }

        /**
         * Standard OpenGL-style perspective projection. Maps view-space z in
         * [-near, -far] to clip-space z in [-1, 1] after perspective divide.
         */
        fun perspective(fovYRadians: Float, aspect: Float, near: Float, far: Float): Matrix4 {
            val f = 1f / tan(fovYRadians / 2f)
            val nf = 1f / (near - far)
            return Matrix4(
                floatArrayOf(
                    f / aspect, 0f, 0f, 0f,
                    0f, f, 0f, 0f,
                    0f, 0f, (far + near) * nf, -1f,
                    0f, 0f, 2f * far * near * nf, 0f,
                ),
            )
        }

        /**
         * Camera look-at: builds a view matrix placing the camera at `eye`
         * looking toward `target` with `up` as the world-up reference.
         */
        fun lookAt(eye: Vec3, target: Vec3, up: Vec3): Matrix4 {
            val zAxis = (eye - target).normalize()
            val xAxis = up.cross(zAxis).normalize()
            val yAxis = zAxis.cross(xAxis)
            return Matrix4(
                floatArrayOf(
                    xAxis.x, yAxis.x, zAxis.x, 0f,
                    xAxis.y, yAxis.y, zAxis.y, 0f,
                    xAxis.z, yAxis.z, zAxis.z, 0f,
                    -xAxis.dot(eye), -yAxis.dot(eye), -zAxis.dot(eye), 1f,
                ),
            )
        }
    }
}
