package ai.factoredui.compose.renderer

import ai.factoredui.compose.heatmap.HeatAxis
import ai.factoredui.compose.heatmap.HeatmapProps
import ai.factoredui.compose.heatmap.HeatmapReading
import ai.factoredui.compose.heatmap.heatFrameIndex
import ai.factoredui.compose.heatmap.heatLegendText
import ai.factoredui.compose.heatmap.readHeatmapProps
import ai.factoredui.compose.ramp.ColourRamp
import ai.factoredui.compose.ramp.rampColourFor
import ai.factoredui.compose.schema.SpecNode
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private const val MIN_WIDTH_OVER_HEIGHT = 0.5f
private const val MAX_WIDTH_OVER_HEIGHT = 4f
private const val LEGEND_BAR_DP = 10
private const val CELL_OVERLAP_PX = 0.5f

private fun widthOverHeight(props: HeatmapProps): Float =
    (props.x.extent / props.y.extent).toFloat().coerceIn(MIN_WIDTH_OVER_HEIGHT, MAX_WIDTH_OVER_HEIGHT)


@Composable
internal fun RenderHeatmap(node: SpecNode, resolvedProps: Map<String, Any?>) {
    val theme = LocalSpecTheme.current
    val reading = remember(resolvedProps) { readHeatmapProps(resolvedProps) }
    val props = (reading as? HeatmapReading.Drawn)?.props
    val notices = (reading as? HeatmapReading.Refused)?.notices ?: props?.notices.orEmpty()
    val frame = props?.let { heatFrameIndex(resolvedProps["frame"], it.frames.size) }
    RecordDrawn {
        text = if (props == null) null else "frame ${frame?.plus(1)} of ${props.frames.size}"
        shownProps = if (frame == null) emptyMap() else mapOf("frame_shown" to frame)
        this.notices = notices
    }
    Column(modifier = Modifier.fillMaxWidth().nodeTag(node.id), verticalArrangement = Arrangement.spacedBy(theme.space.xs.dp)) {
        notices.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = theme.warn) }
        if (props == null) {
            (resolvedProps["empty_text"] as? String)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = theme.muted) }
            return@Column
        }
        val cells = props.frames.getOrNull(frame ?: 0)
        if (cells == null) (resolvedProps["empty_text"] as? String)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = theme.muted) }
        else HeatCells(props, cells, widthOverHeight(props), Modifier.nodeTag("${node.id}/cells"))
        HeatAxes(node.id, props)
        RampLegend("${node.id}/legend", props.ramp, props.min, props.max, props.unit)
    }
}

private fun HeatAxis.cut(index: Int): Float = ((edges[index] - first) / extent).toFloat()

@Composable
private fun HeatCells(props: HeatmapProps, cells: List<Double?>, widthOverHeight: Float, modifier: Modifier) {
    Canvas(modifier = modifier.fillMaxWidth().aspectRatio(widthOverHeight)) {
        for (row in 0 until props.y.count) {
            val near = props.y.cut(row) * size.height
            val far = props.y.cut(row + 1) * size.height
            val top = if (props.isYUp) size.height - far else near
            for (column in 0 until props.x.count) {
                val value = cells[row * props.x.count + column] ?: continue
                val left = props.x.cut(column) * size.width
                val right = props.x.cut(column + 1) * size.width
                drawRect(Color(rampColourFor(props.ramp, value)), Offset(left, top), Size(right - left + CELL_OVERLAP_PX, far - near + CELL_OVERLAP_PX))
            }
        }
    }
}

@Composable
private fun HeatAxes(nodeId: String, props: HeatmapProps) {
    val theme = LocalSpecTheme.current
    val style = MaterialTheme.typography.labelSmall
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(heatLegendText(props.x.first, null), style = style, color = theme.muted, modifier = Modifier.nodeTag("$nodeId/x/first"))
        props.x.label?.let { Text(it, style = style, color = theme.muted, modifier = Modifier.nodeTag("$nodeId/x/label")) }
        Text(heatLegendText(props.x.last, null), style = style, color = theme.muted, modifier = Modifier.nodeTag("$nodeId/x/last"))
    }
    props.y.label?.let {
        Text("$it: ${heatLegendText(props.y.first, null)} to ${heatLegendText(props.y.last, null)}", style = style, color = theme.muted, modifier = Modifier.nodeTag("$nodeId/y/label"))
    }
}

/** A legend line: [min], a gradient bar of [ramp] across min..max, and [max], each with [unit]; its parts are tagged `<tag>/min`, `<tag>/bar` and `<tag>/max`. */
@Composable
internal fun RampLegend(tag: String, ramp: ColourRamp, min: Double, max: Double, unit: String?) {
    val theme = LocalSpecTheme.current
    val style = MaterialTheme.typography.labelSmall
    val brush = Brush.horizontalGradient(*ramp.stops.map { ((it.at - min) / (max - min)).toFloat().coerceIn(0f, 1f) to Color(it.colour) }.toTypedArray())
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(theme.space.sm.dp)) {
        Text(heatLegendText(min, unit), style = style, color = theme.ink, modifier = Modifier.nodeTag("$tag/min"))
        Spacer(modifier = Modifier.weight(1f).height(LEGEND_BAR_DP.dp).background(brush).nodeTag("$tag/bar"))
        Text(heatLegendText(max, unit), style = style, color = theme.ink, modifier = Modifier.nodeTag("$tag/max"))
    }
}
