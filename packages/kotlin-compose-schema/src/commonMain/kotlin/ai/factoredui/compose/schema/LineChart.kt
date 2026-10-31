package ai.factoredui.compose.schema

data class LinePoint(val t: Double, val v: Double)

data class LineSeries(val name: String, val color: String?, val points: List<LinePoint>)

data class LineChartBounds(val tMin: Double, val tMax: Double, val vMin: Double, val vMax: Double)

private const val VALUE_PADDING_FRACTION = 0.08
private const val FLAT_VALUE_SPAN = 1.0

fun resolveLineSeries(resolvedSeries: Any?): List<LineSeries> =
    (resolvedSeries as? List<*>).orEmpty().mapNotNull { entry ->
        val fields = entry as? Map<*, *> ?: return@mapNotNull null
        val name = fields["name"] as? String ?: return@mapNotNull null
        LineSeries(name, fields["color"] as? String, resolveLinePoints(fields["points"]))
    }

private fun resolveLinePoints(resolvedPoints: Any?): List<LinePoint> =
    (resolvedPoints as? List<*>).orEmpty().mapNotNull { entry ->
        val fields = entry as? Map<*, *> ?: return@mapNotNull null
        val time = (fields["t"] as? Number)?.toDouble() ?: return@mapNotNull null
        val value = (fields["v"] as? Number)?.toDouble() ?: return@mapNotNull null
        LinePoint(time, value)
    }.sortedBy { it.t }

fun boundsOfLineSeries(series: List<LineSeries>): LineChartBounds? {
    val points = series.flatMap { it.points }
    if (points.isEmpty()) return null
    val valueMin = points.minOf { it.v }
    val valueMax = points.maxOf { it.v }
    val span = if (valueMax > valueMin) valueMax - valueMin else FLAT_VALUE_SPAN
    val padding = span * VALUE_PADDING_FRACTION
    return LineChartBounds(points.minOf { it.t }, points.maxOf { it.t }, valueMin - padding, valueMin + span + padding)
}
