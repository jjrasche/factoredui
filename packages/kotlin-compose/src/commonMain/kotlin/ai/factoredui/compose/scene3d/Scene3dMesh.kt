package ai.factoredui.compose.scene3d

import androidx.compose.ui.graphics.Color
import ai.factoredui.compose.math.Vec3
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Render-ready mesh from pipeline/scene_composer/mesh.py: decimated, recentered
// feet-to-origin, one flat color per triangle. Flat arrays keep it compact.
@Serializable
data class Scene3dMesh(
    val vertices: List<Float> = emptyList(),
    val triangles: List<Int> = emptyList(),
    @SerialName("tri_colors") val triColors: List<String> = emptyList(),
    val height: Float = 1.7f,
    // Optional rig (rig.py) → client-side FK+LBS posing; absent → static baked mesh.
    @SerialName("rest_joints") val restJoints: List<Float> = emptyList(),
    val parents: List<Int> = emptyList(),
    @SerialName("weight_joints") val weightJoints: List<Int> = emptyList(),
    @SerialName("weight_values") val weightValues: List<Float> = emptyList(),
)

class PreparedMesh(
    val vertices: List<Vec3>,
    val triangles: List<Int>,
    val colors: List<Color>,
    val height: Float,
    val rig: Scene3dRig? = null,
)

fun Scene3dMesh.prepare(): PreparedMesh {
    val points = ArrayList<Vec3>(vertices.size / 3)
    var index = 0
    while (index + 2 < vertices.size) {
        points.add(Vec3(vertices[index], vertices[index + 1], vertices[index + 2]))
        index += 3
    }
    return PreparedMesh(
        vertices = points,
        triangles = triangles,
        colors = triColors.map { parseTriColor(it) },
        height = height,
        rig = buildRig(points),
    )
}

private fun Scene3dMesh.buildRig(restVerts: List<Vec3>): Scene3dRig? {
    if (restJoints.isEmpty() || parents.isEmpty() || weightJoints.isEmpty()) return null
    val joints = ArrayList<Vec3>(restJoints.size / 3)
    var index = 0
    while (index + 2 < restJoints.size) {
        joints.add(Vec3(restJoints[index], restJoints[index + 1], restJoints[index + 2]))
        index += 3
    }
    val perVertex = if (restVerts.isEmpty()) 0 else weightJoints.size / restVerts.size
    return Scene3dRig(
        restVertices = restVerts,
        joints = joints,
        parents = parents.toIntArray(),
        weightJoints = weightJoints.toIntArray(),
        weightValues = weightValues.toFloatArray(),
        weightsPerVertex = perVertex,
    )
}

private fun parseTriColor(hex: String): Color {
    if (hex.length != 6) return Color(0xFF888888)
    return runCatching {
        Color(
            red = hex.substring(0, 2).toInt(16) / 255f,
            green = hex.substring(2, 4).toInt(16) / 255f,
            blue = hex.substring(4, 6).toInt(16) / 255f,
        )
    }.getOrElse { Color(0xFF888888) }
}
