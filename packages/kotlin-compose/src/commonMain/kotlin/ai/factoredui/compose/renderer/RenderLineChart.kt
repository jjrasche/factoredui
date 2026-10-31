package ai.factoredui.compose.renderer

import ai.factoredui.compose.schema.LineChartBounds
import ai.factoredui.compose.schema.LineSeries
import ai.factoredui.compose.schema.SpecNode
import ai.factoredui.compose.schema.assignGraphColors
import ai.factoredui.compose.schema.boundsOfLineSeries
import ai.factoredui.compose.schema.resolveLineSeries
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

private val CHART_HEIGHT = 200.dp
private const val TICKS = 4
private const val LEFT_MARGIN_DP = 44f
private const val BOTTOM_MARGIN_DP = 20f
private const val TOP_MARGIN_DP = 6f
private const val RIGHT_MARGIN_DP = 8f
private const val GRID_ALPHA = 0.25f
private const val LINE_WIDTH_DP = 2f

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RenderLineChart(node: SpecNode, resolvedProps: Map<String, Any?>) {
    val series = resolveLineSeries(resolvedProps["series"])
    val bounds = boundsOfLineSeries(series)
    val colors = assignGraphColors(series.map { it.name }, series.mapNotNull { s -> s.color?.let { s.name to it } }.toMap())
    val unit = resolvedProps["unit"] as? String
    val yLabel = resolvedProps["y_label"] as? String
    val xLabel = resolvedProps["x_label"] as? String
    val emptyText = resolvedProps["emptyText"] as? String ?: "No readings yet."
    val theme = LocalSpecTheme.current
    val measurer = rememberTextMeasurer()
    Column(Modifier.nodeTag(node.id).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (yLabel != null) Text(listOfNotNull(yLabel, unit?.let { "($it)" }).joinToString(" "), color = theme.muted, fontSize = 12.sp)
        if (bounds == null) {
            Text(emptyText, color = theme.muted, fontSize = 13.sp)
        } else {
            Canvas(Modifier.fillMaxWidth().height(CHART_HEIGHT)) { drawLineChart(series, bounds, colors, theme, measurer) }
            if (xLabel != null) Text(xLabel, color = theme.muted, fontSize = 12.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                series.forEach { Text(it.name, color = seriesColor(colors.getValue(it.name)), fontSize = 12.sp) }
            }
        }
    }
}

private val UNSTATED_SERIES_COLOR = Color(0xFF888888)

private fun seriesColor(hex: String): Color = parseGeomapColor(hex)?.let { Color(it) } ?: UNSTATED_SERIES_COLOR

private fun DrawScope.drawLineChart(
    series: List<LineSeries>, bounds: LineChartBounds, colors: Map<String, String>, theme: SpecTheme, measurer: TextMeasurer,
) {
    val left = LEFT_MARGIN_DP.dp.toPx()
    val right = size.width - RIGHT_MARGIN_DP.dp.toPx()
    val top = TOP_MARGIN_DP.dp.toPx()
    val bottom = size.height - BOTTOM_MARGIN_DP.dp.toPx()
    val tSpan = (bounds.tMax - bounds.tMin).takeIf { it > 0.0 } ?: 1.0
    val vSpan = bounds.vMax - bounds.vMin
    fun xOf(t: Double) = left + ((t - bounds.tMin) / tSpan).toFloat() * (right - left)
    fun yOf(v: Double) = bottom - ((v - bounds.vMin) / vSpan).toFloat() * (bottom - top)
    val labelStyle = TextStyle(color = theme.muted, fontSize = 10.sp)
    for (tick in 0..TICKS) {
        val fraction = tick.toDouble() / TICKS
        val value = bounds.vMin + fraction * vSpan
        val y = yOf(value)
        drawLine(theme.muted.copy(alpha = GRID_ALPHA), Offset(left, y), Offset(right, y))
        drawText(measurer, tickText(value, vSpan), Offset(0f, y - 6.dp.toPx()), labelStyle)
        val time = bounds.tMin + fraction * tSpan
        drawText(measurer, tickText(time, tSpan), Offset(xOf(time) - 10.dp.toPx(), bottom + 3.dp.toPx()), labelStyle)
    }
    series.forEach { drawSeries(it, seriesColor(colors.getValue(it.name)), ::xOf, ::yOf) }
}

private fun DrawScope.drawSeries(series: LineSeries, color: Color, xOf: (Double) -> Float, yOf: (Double) -> Float) {
    val width = LINE_WIDTH_DP.dp.toPx()
    if (series.points.size == 1) {
        drawCircle(color, width * 1.5f, Offset(xOf(series.points[0].t), yOf(series.points[0].v)))
        return
    }
    val path = Path()
    series.points.forEachIndexed { index, point ->
        if (index == 0) path.moveTo(xOf(point.t), yOf(point.v)) else path.lineTo(xOf(point.t), yOf(point.v))
    }
    drawPath(path, color, style = Stroke(width))
}

private fun tickText(value: Double, span: Double): String {
    val decimals = if (span >= 20) 0 else if (span >= 2) 1 else 2
    if (decimals == 0) return value.roundToInt().toString()
    val scale = if (decimals == 1) 10.0 else 100.0
    return ((value * scale).roundToInt() / scale).toString()
}
