package ai.factoredui.compose.terrain

import kotlin.math.abs
import kotlin.math.roundToInt

val HEAT_RAMP: List<Int> = listOf(0xFF2B83BA.toInt(), 0xFF80BFAB.toInt(), 0xFFC7E8AD.toInt(), 0xFFFDB96B.toInt(), 0xFFD7191C.toInt())
const val CUT_COLOUR: Int = 0xFFB35806.toInt()
const val FILL_COLOUR: Int = 0xFF542788.toInt()
const val NEUTRAL_COLOUR: Int = 0xFFF7F7F7.toInt()
val HILLSHADE_RAMP: List<Int> = listOf(hillshadeColourFor(0), hillshadeColourFor(255))

private fun channel(argb: Int, shift: Int): Int = (argb ushr shift) and 0xFF

fun argbOf(red: Int, green: Int, blue: Int): Int = (0xFF shl 24) or (red shl 16) or (green shl 8) or blue

fun mixArgb(from: Int, to: Int, fraction: Double): Int {
    fun mixed(shift: Int): Int = mix(channel(from, shift).toDouble(), channel(to, shift).toDouble(), fraction).roundToInt()
    return argbOf(mixed(16), mixed(8), mixed(0))
}

fun rampColourAt(ramp: List<Int>, fraction: Double): Int {
    val position = fraction.coerceIn(0.0, 1.0) * (ramp.size - 1)
    val lower = position.toInt().coerceAtMost(ramp.size - 2)
    return mixArgb(ramp[lower], ramp[lower + 1], position - lower)
}

fun heatColourFor(heightMm: Double, minMm: Double, maxMm: Double): Int {
    if (maxMm <= minMm) return rampColourAt(HEAT_RAMP, 0.5)
    return rampColourAt(HEAT_RAMP, (heightMm - minMm) / (maxMm - minMm))
}

fun cutFillColourFor(deltaMm: Double, maxAbsMm: Double): Int {
    if (maxAbsMm <= 0.0 || deltaMm == 0.0) return NEUTRAL_COLOUR
    val toward = if (deltaMm < 0.0) CUT_COLOUR else FILL_COLOUR
    return mixArgb(NEUTRAL_COLOUR, toward, (abs(deltaMm) / maxAbsMm).coerceAtMost(1.0))
}

fun hillshadeColourFor(brightness: Int): Int {
    val level = brightness.coerceIn(0, 255)
    return argbOf(level, level, level)
}

fun largestCutOrFillMm(cutFill: TerrainGrid): Double = maxOf(abs(cutFill.minMm), abs(cutFill.maxMm)).toDouble()

fun heatRaster(grid: TerrainGrid): TerrainRaster =
    rasterOf(grid) { x, y -> heatColourFor(sampleHeightMm(grid, x, y), grid.minMm.toDouble(), grid.maxMm.toDouble()) }

fun cutFillRaster(cutFill: TerrainGrid): TerrainRaster {
    val largest = largestCutOrFillMm(cutFill)
    return rasterOf(cutFill) { x, y -> cutFillColourFor(sampleHeightMm(cutFill, x, y), largest) }
}

fun hillshadeRaster(grid: TerrainGrid, tileSideMm: Double, reliefExaggeration: Double): TerrainRaster {
    val brightness = hillshadeBrightness(grid, tileSideMm, reliefExaggeration)
    return TerrainRaster(brightness.width, brightness.height, IntArray(brightness.values.size) { hillshadeColourFor(brightness.values[it]) })
}

fun terrainRaster(mode: TerrainMode, field: TerrainField, tileSideMm: Double): TerrainRaster? = when (mode) {
    TerrainMode.OFF -> null
    TerrainMode.HILLSHADE -> hillshadeRaster(field.heights, tileSideMm, reliefExaggeration(field.heights, tileSideMm))
    TerrainMode.HEAT -> heatRaster(field.heights)
    TerrainMode.CUTFILL -> cutFillRaster(field.cutFill)
}
