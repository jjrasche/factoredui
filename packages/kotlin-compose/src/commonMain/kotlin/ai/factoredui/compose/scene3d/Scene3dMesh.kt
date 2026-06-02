package ai.factoredui.compose.scene3d

import androidx.compose.ui.graphics.Color
import ai.factoredui.compose.forcegraph.math.Vec3
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
)

class PreparedMesh(
    val vertices: List<Vec3>,
    val triangles: List<Int>,
    val colors: List<Color>,
    val height: Float,
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
