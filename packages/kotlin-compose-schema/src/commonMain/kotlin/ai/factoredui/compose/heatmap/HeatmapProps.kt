package ai.factoredui.compose.heatmap

import ai.factoredui.compose.ramp.ColourRamp
import ai.factoredui.compose.ramp.RampReading
import ai.factoredui.compose.ramp.RampStop
import ai.factoredui.compose.ramp.readColourRamp
import ai.factoredui.compose.schema.plainFigureText

data class HeatAxis(val origin: Double, val step: Double, val count: Int, val label: String?) {
    val last: Double get() = origin + step * (count - 1)
}

data class HeatmapProps(
    val x: HeatAxis,
    val y: HeatAxis,
    val frames: List<List<Double?>>,
    val min: Double,
    val max: Double,
    val ramp: ColourRamp,
    val unit: String?,
    val isYUp: Boolean,
    val notices: List<String>,
)

sealed interface HeatmapReading {
    data class Drawn(val props: HeatmapProps) : HeatmapReading
    data class Refused(val notices: List<String>) : HeatmapReading
}

private val NAMED_RAMPS: Map<String, List<Long>> = mapOf(
    "heat" to listOf(0xFF1B1B3A, 0xFF6A1B6B, 0xFFC4452F, 0xFFF59E0B, 0xFFFFF3B0),
    "cool_warm" to listOf(0xFF2B5FA8, 0xFF8FB8DE, 0xFFF1F1F1, 0xFFE9A07A, 0xFFB4352A),
    "viridis" to listOf(0xFF440154, 0xFF3B528B, 0xFF21908C, 0xFF5DC863, 0xFFFDE725),
)

private const val BLANK_CELL: Long = 0x00000000L

private fun finiteNumberOf(raw: Any?): Double? = (raw as? Number)?.toDouble()?.takeIf { it.isFinite() }

private fun axisOf(owner: String, raw: Any?): Pair<HeatAxis?, String?> {
    val fields = raw as? Map<*, *>
    val origin = finiteNumberOf(fields?.get("origin"))
    val step = finiteNumberOf(fields?.get("step"))
    val count = finiteNumberOf(fields?.get("count"))?.toInt()
    if (origin == null || step == null || step <= 0.0 || count == null || count < 1) {
        return null to "heatmap $owner needs { origin: a number, step: a number above 0, count: 1 or more }, not '$raw'."
    }
    return HeatAxis(origin, step, count, fields?.get("label") as? String) to null
}

private fun framesOf(raw: Any?, cells: Int, notices: MutableList<String>): List<List<Double?>> =
    (raw as? List<*>).orEmpty().mapIndexed { index, frame ->
        val values = (frame as? List<*>).orEmpty()
        if (values.size != cells) notices += "heatmap frame $index holds ${values.size} cells, not $cells; the cells it lacks are left blank."
        List(cells) { cell -> finiteNumberOf(values.getOrNull(cell)) }
    }

private fun namedRamp(name: String, min: Double, max: Double): ColourRamp? {
    val colours = NAMED_RAMPS[name] ?: return null
    val stops = colours.mapIndexed { index, colour -> RampStop(min + (max - min) * index / (colours.size - 1), colour) }
    return ColourRamp(stops, over = colours.last(), noFigure = BLANK_CELL)
}

private fun rampOf(raw: Any?, min: Double?, max: Double?): Pair<ColourRamp?, String?> {
    if (raw is String) {
        if (min == null || max == null || max <= min) return null to "heatmap ramp '$raw' needs min and max with max above min."
        return (namedRamp(raw, min, max)) to if (raw in NAMED_RAMPS) null else "heatmap ramp '$raw' is not one of ${NAMED_RAMPS.keys.joinToString()}; give { stops: [[value, colour], ...] } or one of those names."
    }
    return when (val reading = readColourRamp("heatmap ramp", raw)) {
        is RampReading.Readable -> reading.ramp.copy(noFigure = BLANK_CELL) to null
        is RampReading.Refused -> null to reading.notice
    }
}

/** A grid of numbers drawn as colours, one frame at a time. Cells are row-major, one row per y step. A ramp is either `{ stops: [[value, colour], ...] }` or a built-in name, which is spread over min..max. */
fun readHeatmapProps(resolved: Map<String, Any?>): HeatmapReading {
    val (x, xNotice) = axisOf("x", resolved["x"])
    val (y, yNotice) = axisOf("y", resolved["y"])
    val min = finiteNumberOf(resolved["min"])
    val max = finiteNumberOf(resolved["max"])
    val (ramp, rampNotice) = rampOf(resolved["ramp"], min, max)
    val refusals = listOfNotNull(xNotice, yNotice, rampNotice)
    if (x == null || y == null || ramp == null || refusals.isNotEmpty()) return HeatmapReading.Refused(refusals)
    val notices = mutableListOf<String>()
    val frames = framesOf(resolved["frames"], x.count * y.count, notices)
    return HeatmapReading.Drawn(HeatmapProps(x, y, frames, min ?: ramp.stops.first().at, max ?: ramp.stops.last().at, ramp, resolved["unit"] as? String, resolved["y_up"] == true, notices))
}

fun heatFrameIndex(raw: Any?, frameCount: Int): Int = (finiteNumberOf(raw) ?: 0.0).toInt().coerceIn(0, maxOf(0, frameCount - 1))

fun heatLegendText(value: Double, unit: String?): String = plainFigureText(value) + (unit?.let { " $it" } ?: "")
