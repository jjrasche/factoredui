package ai.factoredui.compose.renderer

import ai.factoredui.compose.layout.GroundPoint
import ai.factoredui.compose.layout.TileShape
import ai.factoredui.compose.layout.tileCorners
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke

private const val GRID_LINE_PASSES = 2

internal fun DrawScope.drawGround(shape: TileShape, space: TilemapSpace, cols: Int, rows: Int, look: TileLook, density: Float) {
    if (shape == TileShape.HEX) return drawHexGround(space, cols, rows, look, density)
    drawPath(parcelOutline(space, cols, rows), look.ground, style = Fill)
    drawPath(checkerTiles(space, cols, rows), look.groundAlt, style = Fill)
    val grid = gridLines(space, cols, rows)
    repeat(GRID_LINE_PASSES) { drawPath(grid, look.gridLine, style = Stroke(width = density)) }
}

private fun parcelOutline(space: TilemapSpace, cols: Int, rows: Int): Path {
    val corners = listOf(GroundPoint(0f, 0f), GroundPoint(cols.toFloat(), 0f), GroundPoint(cols.toFloat(), rows.toFloat()), GroundPoint(0f, rows.toFloat()))
    return polygon(corners.map { space.toContent(it) })
}

private fun checkerTiles(space: TilemapSpace, cols: Int, rows: Int): Path = Path().apply {
    for (row in 0 until rows) {
        for (col in 0 until cols) {
            if ((col + row) % 2 == 0) continue
            addTile(this, space, col, row)
        }
    }
}

private fun addTile(path: Path, space: TilemapSpace, col: Int, row: Int) {
    val corners = tileCorners(TileShape.SQUARE, col, row).map { space.toContent(it) }
    path.moveTo(corners[0].x, corners[0].y)
    for (index in 1 until corners.size) path.lineTo(corners[index].x, corners[index].y)
    path.close()
}

private fun gridLines(space: TilemapSpace, cols: Int, rows: Int): Path = Path().apply {
    for (col in 0..cols) addLine(this, space.toContent(GroundPoint(col.toFloat(), 0f)), space.toContent(GroundPoint(col.toFloat(), rows.toFloat())))
    for (row in 0..rows) addLine(this, space.toContent(GroundPoint(0f, row.toFloat())), space.toContent(GroundPoint(cols.toFloat(), row.toFloat())))
}

private fun addLine(path: Path, from: Offset, to: Offset) {
    path.moveTo(from.x, from.y)
    path.lineTo(to.x, to.y)
}

private fun DrawScope.drawHexGround(space: TilemapSpace, cols: Int, rows: Int, look: TileLook, density: Float) {
    for (row in 0 until rows) {
        for (col in 0 until cols) {
            val corners = tileCorners(TileShape.HEX, col, row).map { space.toContent(it) }
            val base = if ((col + row) % 2 == 0) look.ground else look.groundAlt
            drawPath(polygon(corners), base, style = Fill)
            drawPath(polygon(corners), look.gridLine, style = Stroke(width = density))
        }
    }
}
