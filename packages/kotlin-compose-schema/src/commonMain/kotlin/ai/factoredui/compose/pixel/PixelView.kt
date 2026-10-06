package ai.factoredui.compose.pixel

import ai.factoredui.compose.layout.FlowView
import kotlin.math.roundToInt

const val PIXEL_LOOK = "pixel"
const val ART_TILE_FEET = 5.0
const val COARSE_ART_TILE_WIDTH = 32
const val FINE_ART_TILE_WIDTH = 64
private const val COARSE_BASE_ZOOM = 3
private const val FINE_BASE_ZOOM = 2
private const val MAX_ZOOM_FACTOR = 2

data class PixelArtScale(val artTileWidth: Int, val zoom: Int, val maxZoom: Int, val worldTileArtPx: Float)

fun pixelArtScaleFor(tileFeet: Double, density: Float): PixelArtScale {
    val isFine = tileFeet < ART_TILE_FEET
    val artTileWidth = if (isFine) FINE_ART_TILE_WIDTH else COARSE_ART_TILE_WIDTH
    val zoom = ((if (isFine) FINE_BASE_ZOOM else COARSE_BASE_ZOOM) * density).roundToInt().coerceAtLeast(1)
    return PixelArtScale(artTileWidth, zoom, zoom * MAX_ZOOM_FACTOR, (artTileWidth * tileFeet / ART_TILE_FEET).toFloat())
}

fun pixelFitView(focusX: Float, focusY: Float, viewWidth: Float, viewHeight: Float, zoom: Int): FlowView =
    FlowView(zoom.toFloat(), (viewWidth / 2f - focusX * zoom).roundToInt().toFloat(), (viewHeight / 2f - focusY * zoom).roundToInt().toFloat())

fun snapPixelView(view: FlowView, viewWidth: Float, viewHeight: Float, maxZoom: Int): FlowView {
    val zoom = view.scale.roundToInt().coerceIn(1, maxZoom)
    val focusX = (viewWidth / 2f - view.translateX) / view.scale
    val focusY = (viewHeight / 2f - view.translateY) / view.scale
    return pixelFitView(focusX, focusY, viewWidth, viewHeight, zoom)
}
