package ai.factoredui.compose.renderer

import androidx.compose.ui.graphics.ImageBitmap

internal expect fun imageBitmapOfArgb(width: Int, height: Int, argb: IntArray): ImageBitmap

internal fun bgraBytesOf(argb: IntArray): ByteArray {
    val bytes = ByteArray(argb.size * 4)
    for (index in argb.indices) {
        val colour = argb[index]
        bytes[index * 4] = colour.toByte()
        bytes[index * 4 + 1] = (colour shr 8).toByte()
        bytes[index * 4 + 2] = (colour shr 16).toByte()
        bytes[index * 4 + 3] = (colour ushr 24).toByte()
    }
    return bytes
}
