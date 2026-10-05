package ai.factoredui.compose.renderer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import coil3.ImageLoader
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.svg.SvgDecoder

@Composable
internal inline fun rememberLoadedTileImages(sourcesByUse: Map<String, String>): Map<String, Painter> {
    if (sourcesByUse.isEmpty()) return emptyMap()
    val platformContext = LocalPlatformContext.current
    val loader = remember(platformContext) { ImageLoader.Builder(platformContext).components { add(SvgDecoder.Factory()) }.build() }
    val loaded = LinkedHashMap<String, Painter>()
    for ((useId, source) in sourcesByUse) {
        key(useId, source) {
            val painter = rememberAsyncImagePainter(model = source, imageLoader = loader)
            val state by painter.state.collectAsState()
            if (state is AsyncImagePainter.State.Success) loaded[useId] = painter
        }
    }
    return loaded
}

internal fun DrawScope.drawPicture(painter: Painter, centreX: Float, bottomY: Float, widthPx: Float) {
    val intrinsic = painter.intrinsicSize
    val ratio = if (intrinsic.isSpecified && intrinsic.width > 0f) intrinsic.height / intrinsic.width else 1f
    val size = Size(widthPx, widthPx * ratio)
    translate(centreX - widthPx / 2f, bottomY - size.height) { with(painter) { draw(size) } }
}
