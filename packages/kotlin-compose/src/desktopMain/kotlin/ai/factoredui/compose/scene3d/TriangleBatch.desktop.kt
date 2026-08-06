package ai.factoredui.compose.scene3d

import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.nativeCanvas
import org.jetbrains.skia.BlendMode
import org.jetbrains.skia.Paint
import org.jetbrains.skia.VertexMode

private val terrainBatchPaint = Paint().apply { isAntiAlias = false }

internal actual fun drawTriangleBatch(canvas: Canvas, positions: FloatArray, colors: IntArray, vertexCount: Int) {
    if (vertexCount < 3) return
    val floatCount = vertexCount * 2
    val trimmedPositions = if (positions.size == floatCount) positions else positions.copyOf(floatCount)
    val trimmedColors = if (colors.size == vertexCount) colors else colors.copyOf(vertexCount)
    canvas.nativeCanvas.drawVertices(
        VertexMode.TRIANGLES,
        trimmedPositions,
        trimmedColors,
        null,
        null,
        BlendMode.DST,
        terrainBatchPaint,
    )
}
