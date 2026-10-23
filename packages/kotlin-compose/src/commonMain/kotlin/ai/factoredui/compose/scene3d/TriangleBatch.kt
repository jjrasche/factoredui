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

internal const val MAX_BATCH_VERTICES = 30_000

internal fun triangleBatchRanges(vertexCount: Int, maxVertices: Int = MAX_BATCH_VERTICES): List<IntRange> {
    val perBatch = maxVertices - maxVertices % 3
    return (0 until vertexCount - vertexCount % 3 step perBatch).map { start -> start until minOf(start + perBatch, vertexCount - vertexCount % 3) }
}

internal fun drawTriangleBatchBoxed(canvas: Canvas, positions: FloatArray, colors: IntArray, vertexCount: Int) {
    triangleBatchRanges(vertexCount).forEach { range -> drawBoxedRange(canvas, positions, colors, range) }
}

private fun drawBoxedRange(canvas: Canvas, positions: FloatArray, colors: IntArray, range: IntRange) {
    val count = range.last - range.first + 1
    val points = ArrayList<Offset>(count)
    val vertexColors = ArrayList<Color>(count)
    for (vertex in range) {
        points.add(Offset(positions[vertex * 2], positions[vertex * 2 + 1]))
        vertexColors.add(Color(colors[vertex]))
    }
    val vertices = Vertices(
        vertexMode = VertexMode.Triangles,
        positions = points,
        textureCoordinates = points,
        colors = vertexColors,
        indices = List(count) { it },
    )
    canvas.drawVertices(vertices, BlendMode.Dst, batchFallbackPaint)
}