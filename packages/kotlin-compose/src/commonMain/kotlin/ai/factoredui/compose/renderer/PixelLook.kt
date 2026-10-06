package ai.factoredui.compose.renderer

import ai.factoredui.compose.layout.GroundPoint
import ai.factoredui.compose.layout.TileBounds
import ai.factoredui.compose.layout.TileCoord
import ai.factoredui.compose.layout.TileFootprint
import ai.factoredui.compose.layout.TileShape
import ai.factoredui.compose.layout.TileView
import ai.factoredui.compose.layout.tileCorners
import ai.factoredui.compose.layout.tilemapScreenBounds
import ai.factoredui.compose.pixel.ArgbSheet
import ai.factoredui.compose.pixel.EMBEDDED_PIXEL_ATLAS
import ai.factoredui.compose.pixel.PixelArtScale
import ai.factoredui.compose.pixel.PixelCosts
import ai.factoredui.compose.pixel.PixelGround
import ai.factoredui.compose.pixel.PixelScale
import ai.factoredui.compose.pixel.PixelSprite
import ai.factoredui.compose.pixel.PixelSpriteKind
import ai.factoredui.compose.pixel.groundPatternName
import ai.factoredui.compose.pixel.pixelGroundTiles
import ai.factoredui.compose.pixel.swappedSprite
import ai.factoredui.compose.schema.TilemapUse
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import kotlin.math.ceil
import kotlin.math.floor

internal class PixelImages(val scale: PixelScale, val sheets: List<ImageBitmap>, private val patterns: Map<String, ImageBitmap>, private val flatColours: Map<String, Color>) {
    private val swapped = HashMap<Pair<String, String>, ImageBitmap>()

    fun pattern(name: String): ImageBitmap = patterns.getValue(name)

    fun flatColour(name: String): Color = flatColours.getValue(name)

    fun swappedImage(sprite: PixelSprite, rampName: String): ImageBitmap =
        swapped.getOrPut(sprite.name to rampName) { bitmapOf(swappedSprite(EMBEDDED_PIXEL_ATLAS, sprite, rampName)) }
}

private val builtPixelImages = HashMap<Int, PixelImages>()

internal fun pixelImagesFor(artTileWidth: Int): PixelImages = builtPixelImages.getOrPut(artTileWidth) { buildPixelImages(artTileWidth) }

private fun buildPixelImages(artTileWidth: Int): PixelImages {
    val scale = EMBEDDED_PIXEL_ATLAS.manifest.scales.getValue(artTileWidth)
    val sheets = EMBEDDED_PIXEL_ATLAS.sheets(artTileWidth)
    val patternPixels = scale.sprites.filter { it.kind == PixelSpriteKind.PATTERN }.associate { it.name to sheets[it.sheet].crop(it.x, it.y, it.width, it.height) }
    return PixelImages(scale, sheets.map(::bitmapOf), patternPixels.mapValues { bitmapOf(it.value) }, patternPixels.mapValues { meanColour(it.value) })
}

private fun bitmapOf(sheet: ArgbSheet): ImageBitmap = imageBitmapOfArgb(sheet.width, sheet.height, sheet.argb)

private fun meanColour(sheet: ArgbSheet): Color {
    val count = sheet.argb.size.toFloat()
    val red = sheet.argb.sumOf { (it shr 16) and 0xFF } / count
    val green = sheet.argb.sumOf { (it shr 8) and 0xFF } / count
    val blue = sheet.argb.sumOf { it and 0xFF } / count
    return Color(red / 255f, green / 255f, blue / 255f)
}

internal fun pixelSpaceOf(cols: Int, rows: Int, scale: PixelArtScale, images: PixelImages): TilemapSpace {
    val standing = images.scale.sprites.filter { it.kind != PixelSpriteKind.PATTERN }
    val pad = ceil(standing.maxOf { it.width } / 2f)
    val bounds = tilemapScreenBounds(TileShape.SQUARE, TileView.ISO, cols, rows, scale.worldTileArtPx)
    val padded = TileBounds(floor(bounds.minX) - pad, floor(bounds.minY), ceil(bounds.maxX) + pad, ceil(bounds.maxY) + pad)
    return TilemapSpace(TileView.ISO, scale.worldTileArtPx, padded, standing.maxOf { it.height }.toFloat())
}

internal fun pixelFocusOf(space: TilemapSpace, centreMm: Pair<Long, Long>?, sideMm: Double, cols: Int, rows: Int): Offset {
    val ground = centreMm?.let { GroundPoint((it.first / sideMm).toFloat(), (rows - it.second / sideMm).toFloat()) } ?: GroundPoint(cols / 2f, rows / 2f)
    return space.toContent(ground)
}

internal class PixelGroundLayer(val path: Path, val paint: Paint)

internal class PixelGroundScene(val origin: Offset, val layers: List<PixelGroundLayer>, val waterFrame: Int, val costs: PixelCosts)

internal fun pixelGroundSceneOf(space: TilemapSpace, cols: Int, rows: Int, footprints: List<TileFootprint>, uses: Map<String, TilemapUse>, images: PixelImages, waterFrame: Int, costs: PixelCosts): PixelGroundScene {
    val origin = space.toContent(GroundPoint(0f, 0f))
    val grass = PixelGroundLayer(parcelPath(space, origin, cols, rows), groundPaint(images, groundPatternName(PixelGround.GRASS, waterFrame), costs))
    val surfaces = pixelGroundTiles(footprints, uses).toList().sortedBy { it.first.ordinal }
    val layers = surfaces.map { (ground, tiles) -> PixelGroundLayer(tilesPath(space, origin, tiles), groundPaint(images, groundPatternName(ground, waterFrame), costs)) }
    return PixelGroundScene(origin, listOf(grass) + layers, waterFrame, costs)
}

private fun parcelPath(space: TilemapSpace, origin: Offset, cols: Int, rows: Int): Path {
    val corners = listOf(GroundPoint(0f, 0f), GroundPoint(cols.toFloat(), 0f), GroundPoint(cols.toFloat(), rows.toFloat()), GroundPoint(0f, rows.toFloat()))
    return polygon(corners.map { space.toContent(it) - origin })
}

private fun tilesPath(space: TilemapSpace, origin: Offset, tiles: List<TileCoord>): Path = Path().apply {
    for (tile in tiles) {
        val corners = tileCorners(TileShape.SQUARE, tile.col, tile.row).map { space.toContent(it) - origin }
        moveTo(corners[0].x, corners[0].y)
        for (index in 1 until corners.size) lineTo(corners[index].x, corners[index].y)
        close()
    }
}

private fun groundPaint(images: PixelImages, patternName: String, costs: PixelCosts): Paint = Paint().apply {
    isAntiAlias = false
    filterQuality = FilterQuality.None
    if (costs.isGroundTextured) shader = ImageShader(images.pattern(patternName), TileMode.Repeated, TileMode.Repeated) else color = images.flatColour(patternName)
}

internal fun DrawScope.drawPixelGround(ground: PixelGroundScene) {
    translate(ground.origin.x, ground.origin.y) {
        drawIntoCanvas { canvas -> ground.layers.forEach { canvas.drawPath(it.path, it.paint) } }
    }
}
