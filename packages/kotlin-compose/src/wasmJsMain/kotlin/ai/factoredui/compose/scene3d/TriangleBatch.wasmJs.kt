package ai.factoredui.compose.scene3d

import androidx.compose.ui.graphics.Canvas

internal actual fun drawTriangleBatch(canvas: Canvas, positions: FloatArray, colors: IntArray, vertexCount: Int) =
    drawTriangleBatchBoxed(canvas, positions, colors, vertexCount)
