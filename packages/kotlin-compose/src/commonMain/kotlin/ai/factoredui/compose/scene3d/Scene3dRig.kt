package ai.factoredui.compose.scene3d

import ai.factoredui.compose.math.Matrix4
import ai.factoredui.compose.math.Vec3
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2

// Client-side SMPL rig: FK on per-joint local rotations → linear blend skinning
// of the rest verts. Live preview; accurate posed mesh is server-rendered on commit.
class Scene3dRig(
    val restVertices: List<Vec3>,
    val joints: List<Vec3>,
    val parents: IntArray,
    val weightJoints: IntArray,
    val weightValues: FloatArray,
    val weightsPerVertex: Int,
) {
    val jointCount: Int get() = joints.size
}

fun Scene3dRig.identityPose(): Array<Matrix4> = Array(jointCount) { Matrix4.identity() }

fun Scene3dRig.worldJointTransforms(localRotations: Array<Matrix4>): Array<Matrix4> {
    val world = arrayOfNulls<Matrix4>(jointCount)
    for (joint in 0 until jointCount) {
        val parent = parents[joint]
        val offset = if (parent < 0) joints[joint] else joints[joint] - joints[parent]
        val local = Matrix4.translate(offset) * localRotations[joint]
        world[joint] = if (parent < 0) local else world[parent]!! * local
    }
    @Suppress("UNCHECKED_CAST")
    return world as Array<Matrix4>
}

fun Scene3dRig.skin(worldTransforms: Array<Matrix4>): List<Vec3> {
    val skinning = Array(jointCount) { joint -> worldTransforms[joint] * Matrix4.translate(-joints[joint]) }
    val posed = ArrayList<Vec3>(restVertices.size)
    val perVertex = weightsPerVertex
    for (vertexIndex in restVertices.indices) {
        val vertex = restVertices[vertexIndex]
        var x = 0f
        var y = 0f
        var z = 0f
        for (slot in 0 until perVertex) {
            val weightIndex = vertexIndex * perVertex + slot
            val weight = weightValues[weightIndex]
            if (weight <= 0f) continue
            val transformed = skinning[weightJoints[weightIndex]].transform(vertex)
            x += weight * transformed[0]
            y += weight * transformed[1]
            z += weight * transformed[2]
        }
        posed.add(Vec3(x, y, z))
    }
    return posed
}

fun Scene3dRig.posedVertices(localRotations: Array<Matrix4>): List<Vec3> =
    skin(worldJointTransforms(localRotations))

fun jointOrigins(worldTransforms: Array<Matrix4>): List<Vec3> =
    worldTransforms.map { Vec3(it.m[12], it.m[13], it.m[14]) }

fun Matrix4.rotationPart(): Matrix4 {
    val r = m.copyOf()
    r[12] = 0f; r[13] = 0f; r[14] = 0f
    return Matrix4(r)
}

fun Matrix4.transposeRotation(): Matrix4 = Matrix4(
    floatArrayOf(
        m[0], m[4], m[8], 0f,
        m[1], m[5], m[9], 0f,
        m[2], m[6], m[10], 0f,
        0f, 0f, 0f, 1f,
    ),
)

fun rotationFromTo(from: Vec3, to: Vec3): Matrix4 {
    val a = from.normalize()
    val b = to.normalize()
    val axis = a.cross(b)
    val sine = axis.length()
    val cosine = a.dot(b)
    if (sine < 1e-6f) {
        if (cosine > 0f) return Matrix4.identity()
        val perpendicular = (if (abs(a.x) < 0.9f) Vec3(1f, 0f, 0f) else Vec3(0f, 1f, 0f)).cross(a).normalize()
        return Matrix4.rotate(perpendicular, PI.toFloat())
    }
    return Matrix4.rotate(axis.normalize(), atan2(sine, cosine))
}
