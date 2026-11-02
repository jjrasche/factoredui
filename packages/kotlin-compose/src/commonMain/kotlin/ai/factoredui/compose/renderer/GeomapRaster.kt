package ai.factoredui.compose.renderer

import ai.factoredui.compose.schema.GeomapRasterLayer
import ai.factoredui.compose.schema.GeomapViewport
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.roundToInt

internal data class TileKey(val z: Int, val x: Int, val y: Int)

internal data class TileRect(val left: Float, val top: Float, val size: Float)

/** The whole zoom whose tiles cover the view, held to the layer's ceiling; past it the last level is stretched. A bigger tile covers more ground, so it is asked for one level up. */
internal fun rasterTileLevel(zoom: Float, tileSize: Int, tileMaxZoom: Int): Int {
    val levelsSavedByBiggerTiles = log2(tileSize / GEOMAP_TILE_PX).roundToInt()
    return (floor(zoom.toDouble()).toInt() - levelsSavedByBiggerTiles).coerceIn(0, tileMaxZoom)
}

internal fun visibleRasterTiles(viewport: GeomapViewport, widthPx: Float, heightPx: Float, level: Int): List<TileKey> {
    val view = visibleWorldBounds(viewport, widthPx, heightPx)
    if (view.maxX < 0.0 || view.minX > 1.0 || view.maxY < 0.0 || view.minY > 1.0) return emptyList()
    val count = 1 shl level
    fun firstTile(world: Double) = floor(world * count).toInt().coerceIn(0, count - 1)
    fun lastTile(world: Double) = (ceil(world * count).toInt() - 1).coerceIn(0, count - 1)
    val firstX = firstTile(view.minX)
    val firstY = firstTile(view.minY)
    val lastX = max(firstX, lastTile(view.maxX))
    val lastY = max(firstY, lastTile(view.maxY))
    return (firstY..lastY).flatMap { y -> (firstX..lastX).map { x -> TileKey(level, x, y) } }
}

internal fun rasterTileUrl(template: String, key: TileKey): String =
    template.replace("[z]", key.z.toString()).replace("[x]", key.x.toString()).replace("[y]", key.y.toString())

/** Where a tile sits in the map's pixels: its top left corner, and its side, which is the viewport's zoom applied to the tile's level. */
internal fun rasterTileRect(key: TileKey, viewport: GeomapViewport, widthPx: Float, heightPx: Float): TileRect {
    val scale = geomapScalePx(viewport.zoom)
    val count = 1 shl key.z
    val left = (key.x.toDouble() / count - lonToWorldX(viewport.lon)) * scale + widthPx / 2.0
    val top = (key.y.toDouble() / count - latToWorldY(viewport.lat)) * scale + heightPx / 2.0
    return TileRect(left.toFloat(), top.toFloat(), (scale / count).toFloat())
}

internal fun GeomapRasterLayer.isShownAt(zoom: Float, overrides: Map<String, Boolean>): Boolean {
    val lowest = minZoom
    val highest = maxZoom
    return visible && (overrides[id] ?: true) && (lowest == null || zoom >= lowest) && (highest == null || zoom < highest)
}

internal class RasterFailures {
    val byLayer: SnapshotStateMap<String, Set<TileKey>> = mutableStateMapOf()
}

/** The raster layers, drawn first so every vector layer lies over them whatever order the spec lists them in. */
@Composable
internal fun BoxScope.GeomapRasterTiles(
    layers: List<GeomapRasterLayer>,
    overrides: Map<String, Boolean>,
    viewport: GeomapViewport,
    widthPx: Float,
    heightPx: Float,
    loader: ImageLoader?,
    placeholder: Color,
    failures: RasterFailures,
) {
    val platformContext = LocalPlatformContext.current
    val imageLoader = loader ?: remember(platformContext) { ImageLoader.Builder(platformContext).build() }
    Box(Modifier.fillMaxSize().clipToBounds()) {
        layers.filter { it.isShownAt(viewport.zoom, overrides) }.forEach { layer ->
            key(layer.id) {
                RasterLayerTiles(layer, viewport, widthPx, heightPx, imageLoader, placeholder, failures)
            }
        }
    }
}

@Composable
private fun RasterLayerTiles(
    layer: GeomapRasterLayer,
    viewport: GeomapViewport,
    widthPx: Float,
    heightPx: Float,
    imageLoader: ImageLoader,
    placeholder: Color,
    failures: RasterFailures,
) {
    val density = LocalDensity.current.density
    val level = rasterTileLevel(viewport.zoom, layer.tileSize, layer.tileMaxZoom)
    val tiles = visibleRasterTiles(viewport, widthPx, heightPx, level)
    val failed = failures.byLayer[layer.id].orEmpty()
    LaunchedEffect(tiles) {
        val stillWanted = failed.filter { it in tiles }.toSet()
        if (stillWanted.size != failed.size) failures.byLayer[layer.id] = stillWanted
    }
    tiles.forEach { tile ->
        key(tile) {
            val rect = rasterTileRect(tile, viewport, widthPx, heightPx)
            val left = floor(rect.left).toInt()
            val top = floor(rect.top).toInt()
            val side = (ceil(rect.left + rect.size) - left).toInt().coerceAtLeast(1)
            val high = (ceil(rect.top + rect.size) - top).toInt().coerceAtLeast(1)
            Box(
                Modifier.offset { IntOffset(left, top) }
                    .requiredSize((side / density).dp, (high / density).dp)
                    .background(placeholder),
            ) {
                if (tile !in failed) {
                    AsyncImage(
                        model = rasterTileUrl(layer.urlTemplate, tile),
                        imageLoader = imageLoader,
                        contentDescription = null,
                        contentScale = ContentScale.FillBounds,
                        onError = { failures.byLayer[layer.id] = failures.byLayer[layer.id].orEmpty() + tile },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

/** The credit of every shown layer and one line for each layer whose tiles did not load or whose entry is unusable. */
@Composable
internal fun BoxScope.GeomapRasterNotes(
    nodeId: String,
    layers: List<GeomapRasterLayer>,
    overrides: Map<String, Boolean>,
    zoom: Float,
    problems: List<String>,
    failures: RasterFailures,
) {
    val credits = layers.filter { it.isShownAt(zoom, overrides) }.mapNotNull { it.attribution }
    val notices = problems + layers.mapNotNull { layer ->
        val count = failures.byLayer[layer.id]?.size ?: 0
        if (count > 0 && layer.isShownAt(zoom, overrides)) "${layer.id}: $count map tile(s) did not load" else null
    }
    if (notices.isNotEmpty()) {
        Column(Modifier.align(Alignment.TopStart).padding(8.dp).testTag("$nodeId:raster-notice")) {
            notices.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = LocalSpecTheme.current.ink) }
        }
    }
    if (credits.isNotEmpty()) {
        Column(Modifier.align(Alignment.BottomEnd).padding(4.dp).testTag("$nodeId:attribution")) {
            credits.forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = LocalSpecTheme.current.ink) }
        }
    }
}
