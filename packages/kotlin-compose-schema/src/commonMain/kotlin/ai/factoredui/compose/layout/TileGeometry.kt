package ai.factoredui.compose.layout

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

enum class TileShape { SQUARE, HEX }

enum class TileView { ISO, TOP }

data class GroundPoint(val x: Float, val y: Float)

data class ScreenPoint(val x: Float, val y: Float)

data class TileCoord(val col: Int, val row: Int)

data class TileBounds(val minX: Float, val minY: Float, val maxX: Float, val maxY: Float)

private const val HEX_SIZE = 0.57735027f
private const val HEX_ROW_SPACING = 0.8660254f
private const val HEX_CORNER_COUNT = 6
private const val HEX_FIRST_CORNER_DEGREES = 30.0

fun tileCenter(shape: TileShape, col: Int, row: Int): GroundPoint = when (shape) {
    TileShape.SQUARE -> GroundPoint(col + 0.5f, row + 0.5f)
    TileShape.HEX -> GroundPoint(col + 0.5f + if (row % 2 != 0) 0.5f else 0f, row * HEX_ROW_SPACING + HEX_SIZE)
}

fun tileCorners(shape: TileShape, col: Int, row: Int): List<GroundPoint> = when (shape) {
    TileShape.SQUARE -> listOf(
        GroundPoint(col.toFloat(), row.toFloat()),
        GroundPoint(col + 1f, row.toFloat()),
        GroundPoint(col + 1f, row + 1f),
        GroundPoint(col.toFloat(), row + 1f),
    )
    TileShape.HEX -> {
        val centre = tileCenter(TileShape.HEX, col, row)
        (0 until HEX_CORNER_COUNT).map { corner ->
            val angle = (HEX_FIRST_CORNER_DEGREES + 60.0 * corner) * PI / 180.0
            GroundPoint(centre.x + HEX_SIZE * cos(angle).toFloat(), centre.y + HEX_SIZE * sin(angle).toFloat())
        }
    }
}

fun project(view: TileView, ground: GroundPoint, tileWidth: Float): ScreenPoint = when (view) {
    TileView.ISO -> ScreenPoint((ground.x - ground.y) * tileWidth / 2f, (ground.x + ground.y) * tileWidth / 4f)
    TileView.TOP -> ScreenPoint(ground.x * tileWidth, ground.y * tileWidth)
}

fun unproject(view: TileView, screen: ScreenPoint, tileWidth: Float): GroundPoint = when (view) {
    TileView.ISO -> {
        val difference = screen.x * 2f / tileWidth
        val sum = screen.y * 4f / tileWidth
        GroundPoint((sum + difference) / 2f, (sum - difference) / 2f)
    }
    TileView.TOP -> GroundPoint(screen.x / tileWidth, screen.y / tileWidth)
}

fun pickTile(shape: TileShape, cols: Int, rows: Int, ground: GroundPoint): TileCoord? {
    val picked = when (shape) {
        TileShape.SQUARE -> TileCoord(floor(ground.x).toInt(), floor(ground.y).toInt())
        TileShape.HEX -> nearestHex(ground)
    }
    return picked.takeIf { it.col in 0 until cols && it.row in 0 until rows }
}

private fun nearestHex(ground: GroundPoint): TileCoord {
    val approximateRow = ((ground.y - HEX_SIZE) / HEX_ROW_SPACING).roundToInt()
    val approximateCol = floor(ground.x).toInt()
    var best = TileCoord(approximateCol, approximateRow)
    var bestDistance = Float.MAX_VALUE
    for (row in approximateRow - 1..approximateRow + 1) {
        for (col in approximateCol - 1..approximateCol + 1) {
            val centre = tileCenter(TileShape.HEX, col, row)
            val distance = (centre.x - ground.x) * (centre.x - ground.x) + (centre.y - ground.y) * (centre.y - ground.y)
            if (distance < bestDistance) {
                bestDistance = distance
                best = TileCoord(col, row)
            }
        }
    }
    return best
}

fun tileDrawOrder(shape: TileShape, cols: Int, rows: Int): List<TileCoord> =
    (0 until rows).flatMap { row -> (0 until cols).map { col -> TileCoord(col, row) } }
        .sortedWith(
            compareBy<TileCoord>(
                { tileCenter(shape, it.col, it.row).let { centre -> centre.x + centre.y } },
                { tileCenter(shape, it.col, it.row).x },
            ),
        )

fun tilemapScreenBounds(shape: TileShape, view: TileView, cols: Int, rows: Int, tileWidth: Float): TileBounds {
    val corners = (0 until rows).flatMap { row ->
        (0 until cols).flatMap { col -> tileCorners(shape, col, row).map { project(view, it, tileWidth) } }
    }
    return TileBounds(corners.minOf { it.x }, corners.minOf { it.y }, corners.maxOf { it.x }, corners.maxOf { it.y })
}
