package ai.factoredui.compose.pixel

import ai.factoredui.compose.layout.FlowView
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.roundToInt

const val PIXEL_LOOK = "pixel"
const val COARSE_ART_TILE_WIDTH = 32
private const val COARSE_BASE_ZOOM = 3
private const val FINE_BASE_ZOOM = 2
private const val MAX_ZOOM_FACTOR = 2

data class PixelVariant(val id: String, val artTileWidth: Int, val artTileFeet: Double)

data class PixelArtScale(val variant: String, val artTileWidth: Int, val maxZoom: Int, val worldTileArtPx: Float)

fun pixelVariantsOf(manifest: PixelManifest): List<PixelVariant> =
    manifest.scales.map { (id, scale) -> PixelVariant(id, scale.artTileWidth, scale.artTileFeet) }

fun pixelVariantFor(variants: List<PixelVariant>, tileFeet: Double): PixelVariant {
    val rungFeet = variants.map { it.artTileFeet }.distinct().minBy { abs(ln(it / tileFeet)) }
    val atRung = variants.filter { it.artTileFeet == rungFeet }
    return if (tileFeet < rungFeet) atRung.maxBy { it.artTileWidth } else atRung.minBy { it.artTileWidth }
}

fun pixelArtScaleFor(variant: PixelVariant, tileFeet: Double, density: Float): PixelArtScale {
    val baseZoom = if (variant.artTileWidth > COARSE_ART_TILE_WIDTH) FINE_BASE_ZOOM else COARSE_BASE_ZOOM
    val maxZoom = (baseZoom * density * MAX_ZOOM_FACTOR).roundToInt().coerceAtLeast(1)
    return PixelArtScale(variant.id, variant.artTileWidth, maxZoom, (variant.artTileWidth * tileFeet / variant.artTileFeet).toFloat())
}

fun fitPixelZoom(contentWidth: Float, contentHeight: Float, viewWidth: Float, viewHeight: Float, margin: Float, maxZoom: Int): Int {
    val fitting = minOf((viewWidth - 2 * margin) / contentWidth, (viewHeight - 2 * margin) / contentHeight)
    return floor(fitting).toInt().coerceIn(1, maxZoom.coerceAtLeast(1))
}

fun pixelFitView(focusX: Float, focusY: Float, viewWidth: Float, viewHeight: Float, zoom: Int): FlowView =
    FlowView(zoom.toFloat(), (viewWidth / 2f - focusX * zoom).roundToInt().toFloat(), (viewHeight / 2f - focusY * zoom).roundToInt().toFloat())

fun snapPixelView(view: FlowView, viewWidth: Float, viewHeight: Float, maxZoom: Int): FlowView {
    val zoom = view.scale.roundToInt().coerceIn(1, maxZoom)
    val focusX = (viewWidth / 2f - view.translateX) / view.scale
    val focusY = (viewHeight / 2f - view.translateY) / view.scale
    return pixelFitView(focusX, focusY, viewWidth, viewHeight, zoom)
}
