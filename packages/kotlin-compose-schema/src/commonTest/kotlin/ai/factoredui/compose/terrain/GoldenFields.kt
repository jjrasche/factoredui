package ai.factoredui.compose.terrain

import kotlin.math.hypot
import kotlin.math.roundToInt

internal fun fieldOf(cols: Int, rows: Int, version: Long = 0L, height: (vertexCol: Int, vertexRow: Int) -> Int): TerrainGrid =
    TerrainGrid(cols, rows, IntArray((cols + 1) * (rows + 1)) { height(it % (cols + 1), it / (cols + 1)) }, version)

internal fun distanceFromCentre(vertexCol: Int, vertexRow: Int, centre: Int = 4): Double =
    hypot((vertexCol - centre).toDouble(), (vertexRow - centre).toDouble())

internal fun cone(): TerrainGrid = fieldOf(8, 8) { col, row -> maxOf(0, (1000 - 250 * distanceFromCentre(col, row)).roundToInt()) }

internal fun bowl(): TerrainGrid = fieldOf(8, 8) { col, row -> minOf(1000, (250 * distanceFromCentre(col, row)).roundToInt()) }

internal fun saddle(): TerrainGrid = fieldOf(1, 1) { col, row -> if (col == row) 100 else 0 }

internal fun flatField(): TerrainGrid = fieldOf(4, 4) { _, _ -> 500 }

internal fun oneTileRisingSouth(): TerrainGrid = fieldOf(1, 1) { _, row -> 100 * row }

internal fun planeRisingEast(): TerrainGrid = fieldOf(4, 4) { col, _ -> 100 * col + 50 }
