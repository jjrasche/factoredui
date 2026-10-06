package ai.factoredui.compose.terrain

import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong

const val MM_PER_FOOT = 304.8
const val MM_PER_METRE = 1000.0
const val SCALE_BUCKETS_PER_DOUBLING = 4

class TerrainLegend(val title: String, val ramp: List<Int>, val lowLabel: String, val highLabel: String, val notes: List<String>)

fun formatFixed(value: Double, decimals: Int): String {
    val scale = 10.0.pow(decimals).roundToLong()
    val scaled = (abs(value) * scale).roundToLong()
    val sign = if (value < 0 && scaled != 0L) "-" else ""
    if (decimals == 0) return "$sign$scaled"
    return "$sign${scaled / scale}.${(scaled % scale).toString().padStart(decimals, '0')}"
}

fun lengthLabel(millimetres: Double): String =
    "${formatFixed(millimetres / MM_PER_METRE, 2)} m / ${formatFixed(millimetres / MM_PER_FOOT, 2)} ft"

fun contourLabel(levelMm: Long, units: TerrainUnits): String = when (units) {
    TerrainUnits.FEET -> "${formatFixed(levelMm / MM_PER_FOOT, 2)} ft"
    TerrainUnits.METRES -> "${formatFixed(levelMm / MM_PER_METRE, 2)} m"
}

fun contourNote(intervalMm: Int): String =
    "Contours every $intervalMm mm (${formatFixed(intervalMm / MM_PER_FOOT, 2)} ft), heavier every ${intervalMm * INDEX_CONTOUR_EVERY} mm"

fun terrainLegendFor(mode: TerrainMode, field: TerrainField, isContoursShown: Boolean, intervalMm: Int, tileSideMm: Double): TerrainLegend? {
    if (mode == TerrainMode.OFF && !isContoursShown) return null
    val heights = field.heights
    val low = "low ${lengthLabel(heights.minMm.toDouble())}"
    val high = "high ${lengthLabel(heights.maxMm.toDouble())}"
    val contours = if (isContoursShown) contourNote(intervalMm) else null
    return when (mode) {
        TerrainMode.OFF -> TerrainLegend("Contours", emptyList(), low, high, listOfNotNull(contours))
        TerrainMode.HEAT -> TerrainLegend("Height", HEAT_RAMP, low, high, listOfNotNull(contours))
        TerrainMode.HILLSHADE -> TerrainLegend("Hillshade, sun from the north-west at 45°", HILLSHADE_RAMP, low, high, listOfNotNull(reliefNote(heights, tileSideMm), contours))
        TerrainMode.CUTFILL -> cutFillLegend(field.cutFill, contours)
    }
}

private fun reliefNote(heights: TerrainGrid, tileSideMm: Double): String? {
    val times = reliefExaggeration(heights, tileSideMm).roundToInt()
    return if (times > 1) "Relief exaggerated $times times" else null
}

private fun cutFillLegend(cutFill: TerrainGrid, contours: String?): TerrainLegend {
    val deepestCut = (-cutFill.minMm).coerceAtLeast(0).toDouble()
    val highestFill = cutFill.maxMm.coerceAtLeast(0).toDouble()
    val nothingMoved = if (deepestCut == 0.0 && highestFill == 0.0) "No cut or fill on this plan yet" else null
    return TerrainLegend(
        "Cut and fill against the surveyed ground",
        listOf(CUT_COLOUR, NEUTRAL_COLOUR, FILL_COLOUR),
        "cut ${lengthLabel(deepestCut)}",
        "fill ${lengthLabel(highestFill)}",
        listOfNotNull(nothingMoved, contours),
    )
}

fun viewScaleBucket(scale: Float): Int = (log2(scale) * SCALE_BUCKETS_PER_DOUBLING).roundToInt()

fun bucketScale(bucket: Int): Float = 2f.pow(bucket.toFloat() / SCALE_BUCKETS_PER_DOUBLING)
