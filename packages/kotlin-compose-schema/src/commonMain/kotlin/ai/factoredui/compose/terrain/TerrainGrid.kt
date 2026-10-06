package ai.factoredui.compose.terrain

import kotlin.math.floor
import kotlin.math.roundToInt

class TerrainGrid(val cols: Int, val rows: Int, val heightsMm: IntArray, val version: Long = 0L) {
    val vertexCols: Int = cols + 1
    val vertexRows: Int = rows + 1
    val minMm: Int
    val maxMm: Int
    val fingerprint: Long

    init {
        require(cols >= 1 && rows >= 1) { "a terrain grid needs at least one tile, got $cols x $rows" }
        require(heightsMm.size == vertexCols * vertexRows) { "a $cols x $rows grid has ${vertexCols * vertexRows} vertices, got ${heightsMm.size} heights" }
        minMm = heightsMm.min()
        maxMm = heightsMm.max()
        fingerprint = version * 1_000_003L + heightsMm.contentHashCode()
    }

    fun heightAt(vertexCol: Int, vertexRow: Int): Int = heightsMm[vertexRow * vertexCols + vertexCol]
}

class TerrainField(val heights: TerrainGrid, val cutFill: TerrainGrid)

enum class TerrainMode { OFF, HILLSHADE, HEAT, CUTFILL }

enum class TerrainUnits { FEET, METRES }

const val DEFAULT_CONTOUR_INTERVAL_MM = 50

fun terrainGridOf(cols: Int, rows: Int, heightsMm: List<Number>, version: Long = 0L): TerrainGrid =
    TerrainGrid(cols, rows, IntArray(heightsMm.size) { heightsMm[it].toDouble().roundToInt() }, version)

fun sampleHeightMm(grid: TerrainGrid, x: Double, y: Double): Double {
    val clampedX = x.coerceIn(0.0, grid.cols.toDouble())
    val clampedY = y.coerceIn(0.0, grid.rows.toDouble())
    val col = floor(clampedX).toInt().coerceAtMost(grid.cols - 1)
    val row = floor(clampedY).toInt().coerceAtMost(grid.rows - 1)
    val across = clampedX - col
    val down = clampedY - row
    val north = mix(grid.heightAt(col, row).toDouble(), grid.heightAt(col + 1, row).toDouble(), across)
    val south = mix(grid.heightAt(col, row + 1).toDouble(), grid.heightAt(col + 1, row + 1).toDouble(), across)
    return mix(north, south, down)
}

internal fun mix(from: Double, to: Double, fraction: Double): Double = from + (to - from) * fraction

fun resolveTerrain(raw: Any?): TerrainField? {
    val fields = raw as? Map<*, *> ?: return null
    val cols = (fields["cols"] as? Number)?.toInt() ?: return null
    val rows = (fields["rows"] as? Number)?.toInt() ?: return null
    val version = (fields["version"] as? Number)?.toLong() ?: 0L
    val heights = numbersOf(fields["heights_mm"]) ?: return null
    val expected = (cols + 1) * (rows + 1)
    if (cols < 1 || rows < 1 || heights.size != expected) return null
    val cutFill = numbersOf(fields["cut_fill_mm"])?.takeIf { it.size == expected } ?: List(expected) { 0 }
    return TerrainField(terrainGridOf(cols, rows, heights, version), terrainGridOf(cols, rows, cutFill, version))
}

private fun numbersOf(raw: Any?): List<Number>? {
    val entries = raw as? List<*> ?: return null
    return entries.map { it as? Number ?: return null }
}

fun resolveTerrainMode(raw: Any?): TerrainMode =
    TerrainMode.entries.firstOrNull { it.name.equals(raw as? String, ignoreCase = true) } ?: TerrainMode.OFF

fun resolveContourIntervalMm(raw: Any?): Int {
    val parsed = when (raw) {
        is Number -> raw.toDouble()
        is String -> raw.trim().toDoubleOrNull()
        else -> null
    }
    return parsed?.roundToInt()?.takeIf { it > 0 } ?: DEFAULT_CONTOUR_INTERVAL_MM
}

fun resolveContoursShown(raw: Any?): Boolean = raw == true || (raw as? String).equals("true", ignoreCase = true)

fun resolveTerrainUnits(raw: Any?): TerrainUnits =
    if ((raw as? String)?.lowercase() in setOf("m", "metre", "metres", "meter", "meters")) TerrainUnits.METRES else TerrainUnits.FEET
