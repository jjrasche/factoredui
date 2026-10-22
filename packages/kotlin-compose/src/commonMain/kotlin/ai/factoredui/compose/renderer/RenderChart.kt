package ai.factoredui.compose.renderer

import ai.factoredui.compose.chart.ChartPlot
import ai.factoredui.compose.chart.ChartRange
import ai.factoredui.compose.chart.ChartReadout
import ai.factoredui.compose.chart.ChartSeriesKind
import ai.factoredui.compose.chart.ChartSide
import ai.factoredui.compose.chart.PlottedPoint
import ai.factoredui.compose.chart.PlottedSeries
import ai.factoredui.compose.chart.chartAreaPieces
import ai.factoredui.compose.chart.chartRange
import ai.factoredui.compose.chart.chartReadout
import ai.factoredui.compose.chart.chartXRange
import ai.factoredui.compose.chart.chartXTicks
import ai.factoredui.compose.chart.formatChartValue
import ai.factoredui.compose.chart.plotChart
import ai.factoredui.compose.plan.PlanLabelAlign
import ai.factoredui.compose.schema.SpecNode
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.ceil

private const val CHART_HEIGHT_PER_WIDTH = 0.55f
private const val CHART_MIN_HEIGHT_DP = 200f
private const val CHART_MAX_HEIGHT_DP = 340f
private const val AXIS_MARGIN_DP = 46f
private const val TOP_MARGIN_DP = 20f
private const val BOTTOM_MARGIN_DP = 24f
private const val SERIES_LINE_DP = 2f
private const val AREA_FILL_ALPHA = 0.35f
private const val GRID_ALPHA = 0.18f
private const val HOVER_DOT_DP = 3.5f
private const val LEGEND_SWATCH_DP = 12f
private const val LABEL_GAP_DP = 6f
private const val TICK_LABEL_GAP_DP = 44f
private val CHART_TEXT = TextStyle(fontSize = 11.sp, lineHeight = 13.sp)
private val READOUT_TEXT = TextStyle(fontSize = 12.sp, lineHeight = 15.sp)
private val NOTICE_INK = Color(0xFFB3261E)

private class ChartInks(val ink: Color, val muted: Color, val palette: List<Color>)

private val LocalChartInks = compositionLocalOf { ChartInks(Color.Black, Color.Gray, SpecTheme.DARK.series) }

private fun colourOf(plot: ChartPlot, series: PlottedSeries, palette: List<Color>): Color =
    planColour(series.def.colour) ?: palette[plot.series.indexOf(series) % palette.size]

/** Where the plotted area sits in the chart's own pixels, and what data range it spans. */
private class ChartFrame(val left: Float, val top: Float, val right: Float, val bottom: Float, val xLow: Double, val xHigh: Double, val ranges: Map<ChartSide, ChartRange>) {
    fun xOf(x: Double): Float = if (xHigh == xLow) (left + right) / 2 else left + ((x - xLow) / (xHigh - xLow) * (right - left)).toFloat()

    fun yOf(side: ChartSide, value: Double): Float {
        val range = ranges.getValue(side)
        return bottom - ((value - range.low) / (range.high - range.low) * (bottom - top)).toFloat()
    }

    fun dataXOf(pixel: Float): Double = if (right == left) xLow else xLow + ((pixel - left) / (right - left)).coerceIn(0f, 1f) * (xHigh - xLow)
}

private fun frameOf(plot: ChartPlot, widthPx: Float, heightPx: Float, marginPx: Float, topPx: Float, bottomPx: Float): ChartFrame? {
    val (xLow, xHigh) = chartXRange(plot) ?: return null
    val ranges = ChartSide.entries.mapNotNull { side -> chartRange(plot, side)?.let { side to it } }.toMap()
    if (ranges.isEmpty()) return null
    val leftMargin = if (ChartSide.LEFT in ranges) marginPx else 0f
    val rightMargin = if (ChartSide.RIGHT in ranges) marginPx else 0f
    return ChartFrame(leftMargin, topPx, widthPx - rightMargin, heightPx - bottomPx, xLow, xHigh, ranges)
}

@Composable
internal fun RenderChart(node: SpecNode, resolvedProps: Map<String, Any?>, context: RenderContext) {
    val plot = remember(resolvedProps) { plotChart(resolvedProps) }
    var hoverX by remember(plot) { mutableStateOf<Double?>(null) }
    val readout = hoverX?.let { chartReadout(plot, it) }
    val theme = LocalSpecTheme.current
    RecordDrawn { notices = plot.notices; chart = chartRecordOf(plot, readout, theme.series) }
    val inks = ChartInks(planColour(resolvedProps["ink"] as? String) ?: theme.ink, planColour(resolvedProps["muted"] as? String) ?: theme.muted, theme.series)
    CompositionLocalProvider(LocalChartInks provides inks) { ChartBody(node, resolvedProps, plot, readout) { hoverX = it } }
}

@Composable
private fun ChartBody(node: SpecNode, resolvedProps: Map<String, Any?>, plot: ChartPlot, readout: ChartReadout?, onHover: (Double?) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().nodeTag(node.id)) {
        plot.notices.forEach { Text(text = it, style = READOUT_TEXT, color = NOTICE_INK) }
        if (plot.series.isEmpty()) {
            Text(text = resolvedProps["empty_text"] as? String ?: "", style = READOUT_TEXT, color = LocalChartInks.current.muted)
        } else {
            ChartLegend(node.id, plot)
            ChartDrawing(node.id, plot, readout, resolvedProps["x_label"] as? String, onHover)
            ChartReadoutLines(node.id, plot, readout, resolvedProps["x_label"] as? String)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChartLegend(nodeId: String, plot: ChartPlot) {
    val ink = LocalChartInks.current.ink
    val palette = LocalChartInks.current.palette
    FlowRow(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        plot.series.forEachIndexed { index, series ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val colour = colourOf(plot, series, palette)
                Canvas(modifier = Modifier.size(LEGEND_SWATCH_DP.dp)) {
                    if (series.def.kind == ChartSeriesKind.AREA) drawRect(colour.copy(alpha = AREA_FILL_ALPHA * 2)) else drawLine(colour, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = SERIES_LINE_DP.dp.toPx())
                }
                Text(text = series.def.label, style = READOUT_TEXT, color = ink, modifier = Modifier.nodeTag("$nodeId/legend/$index"))
            }
        }
    }
}

@Composable
private fun ChartDrawing(nodeId: String, plot: ChartPlot, readout: ChartReadout?, xLabel: String?, onHover: (Double?) -> Unit) {
    val inks = LocalChartInks.current
    val density = LocalDensity.current
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val heightDp = (maxWidth * CHART_HEIGHT_PER_WIDTH).coerceIn(CHART_MIN_HEIGHT_DP.dp, CHART_MAX_HEIGHT_DP.dp)
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { heightDp.toPx() }
        val frame = frameOf(plot, widthPx, heightPx, with(density) { AXIS_MARGIN_DP.dp.toPx() }, with(density) { TOP_MARGIN_DP.dp.toPx() }, with(density) { BOTTOM_MARGIN_DP.dp.toPx() })
        if (frame != null) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier.fillMaxWidth().height(heightDp).pointerInput(frame) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            when (event.type) {
                                PointerEventType.Enter, PointerEventType.Move, PointerEventType.Press -> onHover(frame.dataXOf(event.changes.first().position.x))
                                PointerEventType.Exit -> onHover(null)
                                else -> Unit
                            }
                        }
                    }
                },
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) { drawChart(plot, frame, readout, inks.ink, inks.palette) }
                ChartAxisLabels(nodeId, plot, frame, xLabel)
            }
        }
    }
}

private fun DrawScope.drawChart(plot: ChartPlot, frame: ChartFrame, readout: ChartReadout?, ink: Color, palette: List<Color>) {
    drawGrid(frame, ink)
    chartXTicks(plot).forEach { tick -> frame.xOf(tick.x).let { x -> drawLine(ink.copy(alpha = GRID_ALPHA), Offset(x, frame.top), Offset(x, frame.bottom), strokeWidth = 1.dp.toPx()) } }
    plot.series.filter { it.def.kind == ChartSeriesKind.AREA }.forEach { drawArea(it, frame, colourOf(plot, it, palette)) }
    plot.series.filter { it.def.kind == ChartSeriesKind.LINE }.forEach { drawLineSeries(it, frame, colourOf(plot, it, palette)) }
    readout?.let { drawHover(plot, frame, it, ink, palette) }
}

private fun DrawScope.drawGrid(frame: ChartFrame, ink: Color) {
    val gridRange = frame.ranges[ChartSide.LEFT] ?: frame.ranges.getValue(ChartSide.RIGHT)
    val side = if (ChartSide.LEFT in frame.ranges) ChartSide.LEFT else ChartSide.RIGHT
    gridRange.ticks.forEach { tick ->
        val y = frame.yOf(side, tick)
        val weight = if (tick == 0.0) 1.5f else 1f
        drawLine(ink.copy(alpha = if (tick == 0.0) GRID_ALPHA * 2 else GRID_ALPHA), Offset(frame.left, y), Offset(frame.right, y), strokeWidth = weight.dp.toPx())
    }
}

private fun DrawScope.drawArea(series: PlottedSeries, frame: ChartFrame, colour: Color) {
    series.runs.flatMap { chartAreaPieces(it) }.filter { it.size > 1 }.forEach { piece ->
        val outline = Path().apply {
            moveTo(frame.xOf(piece.first().x), frame.yOf(series.def.side, piece.first().to))
            piece.drop(1).forEach { lineTo(frame.xOf(it.x), frame.yOf(series.def.side, it.to)) }
            piece.reversed().forEach { lineTo(frame.xOf(it.x), frame.yOf(series.def.side, it.from)) }
            close()
        }
        drawPath(outline, colour.copy(alpha = AREA_FILL_ALPHA), style = Fill)
        drawPath(outline, colour, style = Stroke(width = 1.dp.toPx()))
    }
}

private fun DrawScope.drawLineSeries(series: PlottedSeries, frame: ChartFrame, colour: Color) {
    series.runs.forEach { run ->
        if (run.size == 1) return@forEach drawCircle(colour, radius = SERIES_LINE_DP.dp.toPx() * 1.5f, center = Offset(frame.xOf(run[0].x), frame.yOf(series.def.side, run[0].value)))
        val path = Path().apply {
            moveTo(frame.xOf(run.first().x), frame.yOf(series.def.side, run.first().value))
            run.drop(1).forEach { lineTo(frame.xOf(it.x), frame.yOf(series.def.side, it.value)) }
        }
        drawPath(path, colour, style = Stroke(width = SERIES_LINE_DP.dp.toPx()))
    }
}

private fun pointAt(series: PlottedSeries, x: Double): PlottedPoint? = series.runs.flatten().firstOrNull { it.x == x }

private fun DrawScope.drawHover(plot: ChartPlot, frame: ChartFrame, readout: ChartReadout, ink: Color, palette: List<Color>) {
    val x = frame.xOf(readout.x)
    drawLine(ink.copy(alpha = 0.5f), Offset(x, frame.top), Offset(x, frame.bottom), strokeWidth = 1.dp.toPx())
    plot.series.forEach { series ->
        val point = pointAt(series, readout.x) ?: return@forEach
        val edge = if (series.def.kind == ChartSeriesKind.AREA) point.to else point.value
        drawCircle(colourOf(plot, series, palette), radius = HOVER_DOT_DP.dp.toPx(), center = Offset(x, frame.yOf(series.def.side, edge)))
    }
}

@Composable
private fun ChartAxisLabels(nodeId: String, plot: ChartPlot, frame: ChartFrame, xLabel: String?) {
    val muted = LocalChartInks.current.muted
    val gapPx = with(LocalDensity.current) { LABEL_GAP_DP.dp.toPx() }
    ChartSide.entries.forEach { side ->
        val range = frame.ranges[side] ?: return@forEach
        val isLeft = side == ChartSide.LEFT
        val edge = if (isLeft) frame.left - gapPx else frame.right + gapPx
        range.ticks.forEachIndexed { index, tick ->
            PlanPlaced(edge, frame.yOf(side, tick), if (isLeft) PlanLabelAlign.END else PlanLabelAlign.START, null) {
                Text(text = formatChartValue(tick), style = CHART_TEXT, color = muted, modifier = Modifier.nodeTag("$nodeId/axis/${side.wire}/$index"))
            }
        }
        val unit = listOfNotNull(plot.axes[side]?.label, plot.axes[side]?.unit?.let { "($it)" }).joinToString(" ")
        if (unit.isNotEmpty()) {
            PlanPlaced(if (isLeft) frame.left else frame.right, frame.top / 2, if (isLeft) PlanLabelAlign.START else PlanLabelAlign.END, null) {
                Text(text = unit, style = CHART_TEXT, color = muted, modifier = Modifier.nodeTag("$nodeId/axis/${side.wire}/unit"))
            }
        }
    }
    val ticks = chartXTicks(plot)
    val spacingPx = if (ticks.size > 1) frame.xOf(ticks[1].x) - frame.xOf(ticks[0].x) else Float.MAX_VALUE
    val stride = ceil(with(LocalDensity.current) { TICK_LABEL_GAP_DP.dp.toPx() } / spacingPx).toInt().coerceAtLeast(1)
    ticks.filterIndexed { index, _ -> index % stride == 0 }.forEachIndexed { index, tick ->
        PlanPlaced(frame.xOf(tick.x), frame.bottom + gapPx * 2, PlanLabelAlign.CENTER, null) { Text(text = tick.label, style = CHART_TEXT, color = muted, modifier = Modifier.nodeTag("$nodeId/axis/x/$index")) }
    }
    if (ticks.isNotEmpty()) return
    val xName = xLabel?.let { "$it " }.orEmpty()
    PlanPlaced(frame.left, frame.bottom + gapPx * 2, PlanLabelAlign.START, null) { Text(text = xName + formatChartValue(frame.xLow), style = CHART_TEXT, color = muted, modifier = Modifier.nodeTag("$nodeId/axis/x/first")) }
    PlanPlaced(frame.right, frame.bottom + gapPx * 2, PlanLabelAlign.END, null) { Text(text = xName + formatChartValue(frame.xHigh), style = CHART_TEXT, color = muted, modifier = Modifier.nodeTag("$nodeId/axis/x/last")) }
}

private fun argbOf(colour: Color): Long = colour.toArgb().toLong() and 0xFFFFFFFFL

private fun axisRecordOf(plot: ChartPlot, side: ChartSide): ChartAxisRecord? =
    chartRange(plot, side)?.let { ChartAxisRecord(side.wire, plot.axes[side]?.unit, plot.axes[side]?.label, it.low, it.high, it.ticks) }

private fun seriesRecordOf(plot: ChartPlot, series: PlottedSeries, palette: List<Color>): ChartSeriesRecord =
    ChartSeriesRecord(series.def.id, series.def.label, series.def.kind.wire, series.def.side.wire, argbOf(colourOf(plot, series, palette)), series.runs.map { run -> run.map { listOf(it.x, it.value, it.from, it.to) } })

internal fun chartRecordOf(plot: ChartPlot, readout: ChartReadout?, palette: List<Color>): ChartRecord {
    val xRange = chartXRange(plot)
    return ChartRecord(
        xRange?.first, xRange?.second, chartXTicks(plot).map { it.x to it.label }, ChartSide.entries.mapNotNull { axisRecordOf(plot, it) }, plot.series.map { seriesRecordOf(plot, it, palette) },
        readout?.let { found -> ChartHoverRecord(found.x, found.entries.map { it.id to it.text }) },
    )
}

@Composable
private fun ChartReadoutLines(nodeId: String, plot: ChartPlot, readout: ChartReadout?, xLabel: String?) {
    if (readout == null) return
    val ink = LocalChartInks.current.ink
    val palette = LocalChartInks.current.palette
    Column(modifier = Modifier.padding(top = 6.dp).nodeTag("$nodeId/readout")) {
        Text(text = listOfNotNull(xLabel, formatChartValue(readout.x)).joinToString(" "), style = READOUT_TEXT, color = ink, modifier = Modifier.nodeTag("$nodeId/readout/x"))
        readout.entries.forEach { entry ->
            val series = plot.series.first { it.def.id == entry.id }
            Text(text = "${entry.label}: ${entry.text}", style = READOUT_TEXT, color = colourOf(plot, series, palette), modifier = Modifier.nodeTag("$nodeId/readout/${entry.id}"))
        }
    }
}
