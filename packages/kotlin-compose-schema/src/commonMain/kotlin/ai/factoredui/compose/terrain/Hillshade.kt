package ai.factoredui.compose.terrain

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

const val SAMPLES_PER_TILE = 4
const val SUN_AZIMUTH_DEGREES = 315.0
const val SUN_ALTITUDE_DEGREES = 45.0
const val MAX_RELIEF_EXAGGERATION = 50.0
private const val FULL_BRIGHTNESS = 255
private const val HALF_SAMPLE = 0.5 / SAMPLES_PER_TILE

class TerrainRaster(val width: Int, val height: Int, val values: IntArray) {
    fun at(x: Int, y: Int): Int = values[y * width + x]
}

private class SunDirection(val east: Double, val north: Double, val up: Double)

private fun sunDirection(azimuthDegrees: Double, altitudeDegrees: Double): SunDirection {
    val azimuth = azimuthDegrees * PI / 180.0
    val altitude = altitudeDegrees * PI / 180.0
    return SunDirection(sin(azimuth) * cos(altitude), cos(azimuth) * cos(altitude), sin(altitude))
}

fun sampleCentre(sampleIndex: Int): Double = (sampleIndex + 0.5) / SAMPLES_PER_TILE

fun rasterOf(grid: TerrainGrid, valueAt: (x: Double, y: Double) -> Int): TerrainRaster {
    val width = grid.cols * SAMPLES_PER_TILE
    val height = grid.rows * SAMPLES_PER_TILE
    return TerrainRaster(width, height, IntArray(width * height) { index -> valueAt(sampleCentre(index % width), sampleCentre(index / width)) })
}

fun hillshadeBrightness(
    grid: TerrainGrid,
    tileSideMm: Double,
    reliefExaggeration: Double = 1.0,
    azimuthDegrees: Double = SUN_AZIMUTH_DEGREES,
    altitudeDegrees: Double = SUN_ALTITUDE_DEGREES,
): TerrainRaster {
    val sun = sunDirection(azimuthDegrees, altitudeDegrees)
    val millimetresPerStep = 2 * HALF_SAMPLE * tileSideMm / reliefExaggeration
    return rasterOf(grid) { x, y -> brightnessAt(grid, x, y, millimetresPerStep, sun) }
}

private fun brightnessAt(grid: TerrainGrid, x: Double, y: Double, millimetresPerStep: Double, sun: SunDirection): Int {
    val riseEast = (sampleHeightMm(grid, x + HALF_SAMPLE, y) - sampleHeightMm(grid, x - HALF_SAMPLE, y)) / millimetresPerStep
    val riseSouth = (sampleHeightMm(grid, x, y + HALF_SAMPLE) - sampleHeightMm(grid, x, y - HALF_SAMPLE)) / millimetresPerStep
    val normalLength = sqrt(riseEast * riseEast + riseSouth * riseSouth + 1.0)
    val lit = (-riseEast * sun.east + riseSouth * sun.north + sun.up) / normalLength
    return (max(0.0, lit) * FULL_BRIGHTNESS).roundToInt()
}

fun steepestVertexSlope(grid: TerrainGrid, tileSideMm: Double): Double {
    var steepestRiseMm = 0
    for (row in 0 until grid.vertexRows) {
        for (col in 0 until grid.vertexCols) {
            val here = grid.heightAt(col, row)
            if (col + 1 < grid.vertexCols) steepestRiseMm = maxOf(steepestRiseMm, abs(grid.heightAt(col + 1, row) - here))
            if (row + 1 < grid.vertexRows) steepestRiseMm = maxOf(steepestRiseMm, abs(grid.heightAt(col, row + 1) - here))
        }
    }
    return steepestRiseMm / tileSideMm
}

fun reliefExaggeration(grid: TerrainGrid, tileSideMm: Double): Double {
    val steepest = steepestVertexSlope(grid, tileSideMm)
    if (steepest <= 0.0) return 1.0
    return (1.0 / steepest).coerceIn(1.0, MAX_RELIEF_EXAGGERATION).roundToInt().toDouble()
}
