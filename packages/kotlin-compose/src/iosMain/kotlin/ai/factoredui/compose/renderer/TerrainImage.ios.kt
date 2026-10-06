package ai.factoredui.compose.renderer

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo

internal actual fun imageBitmapOfArgb(width: Int, height: Int, argb: IntArray): ImageBitmap =
    Image.makeRaster(ImageInfo(width, height, ColorType.BGRA_8888, ColorAlphaType.UNPREMUL), bgraBytesOf(argb), width * 4).toComposeImageBitmap()
