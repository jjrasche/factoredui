package ai.factoredui.compose.scene3d

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Vertices
import androidx.compose.ui.graphics.VertexMode

internal expect fun drawTriangleBatch(canvas: Canvas, positions: FloatArray, colors: IntArray, vertexCount: Int)

private val batchFallbackPaint = Paint()

internal fun drawTriangleBatchBoxed(canvas: Canvas, positions: FloatArray, colors: IntArray, vertexCount: Int) {
    if (vertexCount < 3) return
    val points = ArrayList<Offset>(vertexCount)
    val vertexColors = ArrayList<Color>(vertexCount)
    for (vertex in 0 until vertexCount) {
        points.add(Offset(positions[vertex * 2], positions[vertex * 2 + 1]))
        vertexColors.add(Color(colors[vertex]))
    }
    val vertices = Vertices(
        vertexMode = VertexMode.Triangles,
        positions = points,
        textureCoordinates = points,
        colors = vertexColors,
        indices = List(vertexCount) { it },
    )
    canvas.drawVertices(vertices, BlendMode.Dst, batchFallbackPaint)
}
