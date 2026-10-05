package ai.factoredui.compose.renderer

import ai.factoredui.compose.layout.GroundPoint
import ai.factoredui.compose.layout.TileShape
import ai.factoredui.compose.layout.TileView
import ai.factoredui.compose.layout.fitFlowView
import ai.factoredui.compose.layout.project
import ai.factoredui.compose.layout.tileCenter
import ai.factoredui.compose.layout.tilemapScreenBounds
import ai.factoredui.compose.testing.SpecVisualCheck
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap

internal class PlacedTiles(
    val check: SpecVisualCheck,
    val shape: TileShape,
    val view: TileView,
    cols: Int,
    rows: Int,
    mapTag: String = "world:map",
) {
    private val region = check.region(mapTag)
    private val tileWidth = TILEMAP_TILE_WIDTH_DP
    private val bounds = tilemapScreenBounds(shape, view, cols, rows, tileWidth)
    val fit = fitFlowView(
        contentWidth = bounds.maxX - bounds.minX,
        contentHeight = bounds.maxY - bounds.minY + TILEMAP_HEADROOM * tileWidth,
        viewWidth = region.right.value - region.left.value,
        viewHeight = region.bottom.value - region.top.value,
        maxScale = 2f,
        margin = 12f,
    )

    fun screenOf(col: Int, row: Int): Pair<Float, Float> = screenAt(tileCenter(shape, col, row))

    fun screenAt(point: GroundPoint): Pair<Float, Float> {
        val ground = project(view, point, tileWidth)
        val contentX = ground.x - bounds.minX
        val contentY = ground.y - bounds.minY + TILEMAP_HEADROOM * tileWidth
        return (region.left.value + fit.translateX + contentX * fit.scale) to (region.top.value + fit.translateY + contentY * fit.scale)
    }

    fun pixelAtGround(point: GroundPoint, dx: Float = 0f, dy: Float = 0f): Color {
        val (x, y) = screenAt(point)
        return check.png().toPixelMap()[(x + dx * fit.scale).toInt(), (y + dy * fit.scale).toInt()]
    }

    fun tapGround(point: GroundPoint) {
        val (x, y) = screenAt(point)
        check.tapAt("world:map", x - region.left.value, y - region.top.value)
    }

    fun pixelAt(col: Int, row: Int, dx: Float = 0f, dy: Float = 0f): Color {
        val (x, y) = screenOf(col, row)
        return check.png().toPixelMap()[(x + dx * fit.scale).toInt(), (y + dy * fit.scale).toInt()]
    }

    fun tap(col: Int, row: Int) {
        val (x, y) = screenOf(col, row)
        check.tapAt("world:map", x - region.left.value, y - region.top.value)
    }

    fun pixelsAround(col: Int, row: Int, dx: IntRange, dy: IntRange): List<Color> {
        val (x, y) = screenOf(col, row)
        val image = check.png().toPixelMap()
        return dy.flatMap { offsetY -> dx.map { offsetX -> image[(x + offsetX * fit.scale).toInt(), (y + offsetY * fit.scale).toInt()] } }
    }
}
