package ai.factoredui.compose.renderer

import ai.factoredui.compose.plan.PlanLabelAlign
import ai.factoredui.compose.plan.PlanLegendEntry
import ai.factoredui.compose.plan.PlanRead
import ai.factoredui.compose.plan.PlanShape
import ai.factoredui.compose.plan.PlanShapeKind
import ai.factoredui.compose.plan.PlanStyle
import ai.factoredui.compose.plan.planWindow
import ai.factoredui.compose.plan.readPlan
import ai.factoredui.compose.schema.SpecNode
import ai.factoredui.compose.stackedscene.CanvasFit
import ai.factoredui.compose.stackedscene.RectMm
import ai.factoredui.compose.stackedscene.fitWindow
import ai.factoredui.compose.stackedscene.mmToCanvas
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.min

private const val PLAN_MARGIN_DP = 28f
private const val PLAN_MAX_ASPECT = 1.5f
private const val PLAN_MAX_WIDTH_DP = 420f
private const val OUTLINE_DP = 1.5f
private const val LINE_DP = 3f
private const val MARKER_DP = 10f
private const val DASH_DP = 6f
private const val LEGEND_SWATCH_DP = 14f
private const val MIN_EXTENT_MM = 1.0
private val LABEL_TEXT = TextStyle(fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
private val LEGEND_TEXT = TextStyle(fontSize = 12.sp, lineHeight = 15.sp)
private val NOTICE_INK = Color(0xFFB3261E)

/** `#rrggbb`, or `#rrggbbaa` with the alpha last. */
internal fun planColour(hex: String?): Color? {
    val body = hex?.removePrefix("#") ?: return null
    val digits = body.toLongOrNull(16) ?: return null
    return when (body.length) {
        6 -> Color(((digits shr 16) and 0xFF).toInt(), ((digits shr 8) and 0xFF).toInt(), (digits and 0xFF).toInt())
        8 -> Color(((digits shr 24) and 0xFF).toInt(), ((digits shr 16) and 0xFF).toInt(), ((digits shr 8) and 0xFF).toInt(), (digits and 0xFF).toInt())
        else -> null
    }
}

private fun paddedWindow(window: RectMm): RectMm {
    val missingWidth = max(0.0, MIN_EXTENT_MM - (window.x1 - window.x0))
    val missingDepth = max(0.0, MIN_EXTENT_MM - (window.y1 - window.y0))
    return RectMm(window.x0 - missingWidth / 2, window.y0 - missingDepth / 2, window.x1 + missingWidth / 2, window.y1 + missingDepth / 2)
}

private fun planAspect(window: RectMm): Float = min(PLAN_MAX_ASPECT.toDouble(), (window.y1 - window.y0) / (window.x1 - window.x0)).toFloat()

private data class PlanBox(val left: Float, val top: Float, val right: Float, val bottom: Float)

private fun boxOf(fit: CanvasFit, shape: PlanShape): PlanBox {
    val (x0, y0) = mmToCanvas(fit, shape.xMm, shape.yMm)
    val (x1, y1) = when (shape.kind) {
        PlanShapeKind.RECT -> mmToCanvas(fit, shape.xMm + shape.widthMm!!, shape.yMm + shape.depthMm!!)
        PlanShapeKind.LINE -> mmToCanvas(fit, shape.x2Mm!!, shape.y2Mm!!)
        PlanShapeKind.MARKER, PlanShapeKind.LABEL -> x0 to y0
    }
    return PlanBox(min(x0, x1).toFloat(), min(y0, y1).toFloat(), max(x0, x1).toFloat(), max(y0, y1).toFloat())
}

@Composable
internal fun RenderPlan(node: SpecNode, resolvedProps: Map<String, Any?>, context: RenderContext) {
    val read = remember(resolvedProps["shapes"], resolvedProps["styles"], resolvedProps["legend"]) {
        readPlan(resolvedProps["shapes"], resolvedProps["styles"], resolvedProps["legend"])
    }
    val window = planWindow(read.shapes)?.let(::paddedWindow)
    Column(modifier = Modifier.fillMaxWidth().nodeTag(node.id)) {
        read.notices.forEach { Text(text = it, style = LEGEND_TEXT, color = NOTICE_INK) }
        if (window == null) {
            Text(text = resolvedProps["empty_text"] as? String ?: "", style = LEGEND_TEXT, color = LocalSpecTheme.current.muted)
        } else {
            PlanDrawing(node.id, read, window)
            PlanLegend(node.id, read.legend, read.styles)
        }
    }
}

@Composable
private fun PlanDrawing(nodeId: String, read: PlanRead, window: RectMm) {
    val theme = LocalSpecTheme.current
    val density = LocalDensity.current
    BoxWithConstraints(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        val widthDp = min(maxWidth, PLAN_MAX_WIDTH_DP.dp)
        val heightDp: Dp = (widthDp - (PLAN_MARGIN_DP * 2).dp) * planAspect(window) + (PLAN_MARGIN_DP * 2).dp
        val fit = fitWindow(window, with(density) { widthDp.toPx() }.toDouble(), with(density) { heightDp.toPx() }.toDouble(), with(density) { PLAN_MARGIN_DP.dp.toPx() }.toDouble())
        Box(modifier = Modifier.width(widthDp).height(heightDp)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                read.shapes.forEach { drawPlanShape(it, read.styles[it.style] ?: PlanStyle(), boxOf(fit, it), theme.ink) }
            }
            read.shapes.forEach { shape ->
                val box = boxOf(fit, shape)
                if (shape.kind == PlanShapeKind.LABEL) PlanLabel(nodeId, shape, box, theme.ink)
                else PlanShapeTag(nodeId, shape, box)
            }
        }
    }
}

@Composable
private fun PlanShapeTag(nodeId: String, shape: PlanShape, box: PlanBox) {
    val size = IntSize(max(1, (box.right - box.left).toInt()), max(1, (box.bottom - box.top).toInt()))
    PlanPlaced(box.left, box.top, PlanLabelAlign.START, size) { Box(modifier = Modifier.nodeTag("$nodeId/${shape.id}")) }
}

@Composable
private fun PlanLabel(nodeId: String, shape: PlanShape, box: PlanBox, ink: Color) {
    val density = LocalDensity.current.density
    PlanPlaced(box.left + shape.dxDp * density, box.top + shape.dyDp * density, shape.align, null) {
        Text(text = shape.text.orEmpty(), style = LABEL_TEXT, color = ink, modifier = Modifier.nodeTag("$nodeId/${shape.id}"))
    }
}

/** Places its one child at a point of the drawing: [fixed] sizes it with its top-left at the point, otherwise it is measured and centred on the point's y. */
@Composable
internal fun PlanPlaced(x: Float, y: Float, align: PlanLabelAlign, fixed: IntSize?, content: @Composable () -> Unit) {
    Layout(content = content, modifier = Modifier.fillMaxSize()) { measurables, constraints ->
        val bounds = fixed?.let { Constraints.fixed(it.width, it.height) } ?: constraints.copy(minWidth = 0, minHeight = 0)
        val placeable = measurables.first().measure(bounds)
        val left = when (align) {
            PlanLabelAlign.START -> x
            PlanLabelAlign.CENTER -> x - placeable.width / 2f
            PlanLabelAlign.END -> x - placeable.width
        }
        val top = if (fixed == null) y - placeable.height / 2f else y
        layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(left.toInt(), top.toInt()) }
    }
}

private fun DrawScope.drawPlanShape(shape: PlanShape, style: PlanStyle, box: PlanBox, ink: Color) {
    val stroke = planColour(style.stroke) ?: ink
    val fill = planColour(style.fill)
    val effect = if (style.dash) PathEffect.dashPathEffect(floatArrayOf(DASH_DP.dp.toPx(), DASH_DP.dp.toPx())) else null
    when (shape.kind) {
        PlanShapeKind.RECT -> {
            val topLeft = Offset(box.left, box.top)
            val size = Size(box.right - box.left, box.bottom - box.top)
            if (fill != null) drawRect(fill, topLeft, size, style = Fill)
            drawRect(stroke, topLeft, size, style = Stroke(width = (style.weight ?: OUTLINE_DP).dp.toPx(), pathEffect = effect))
        }
        PlanShapeKind.LINE -> {
            val from = lineEnd(shape, box, start = true)
            val to = lineEnd(shape, box, start = false)
            drawLine(stroke, from, to, strokeWidth = (style.weight ?: LINE_DP).dp.toPx(), pathEffect = effect)
        }
        PlanShapeKind.MARKER -> {
            val half = MARKER_DP.dp.toPx() / 2
            val centre = Offset(box.left, box.top)
            drawRect(fill ?: stroke, Offset(centre.x - half, centre.y - half), Size(half * 2, half * 2), style = Fill)
            drawRect(stroke, Offset(centre.x - half, centre.y - half), Size(half * 2, half * 2), style = Stroke(width = (style.weight ?: OUTLINE_DP).dp.toPx()))
        }
        PlanShapeKind.LABEL -> Unit
    }
}

/** A line's two ends in canvas pixels: the box holds them as its opposite corners, so a rising line needs its ends told apart by the shape. */
private fun DrawScope.lineEnd(shape: PlanShape, box: PlanBox, start: Boolean): Offset {
    val goesEast = shape.x2Mm!! >= shape.xMm
    val goesNorth = shape.y2Mm!! >= shape.yMm
    val east = if (start) goesEast else !goesEast
    val north = if (start) goesNorth else !goesNorth
    return Offset(if (east) box.left else box.right, if (north) box.bottom else box.top)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlanLegend(nodeId: String, legend: List<PlanLegendEntry>, styles: Map<String, PlanStyle>) {
    if (legend.isEmpty()) return
    val ink = LocalSpecTheme.current.ink
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        legend.forEachIndexed { index, entry ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val style = styles[entry.style] ?: PlanStyle()
                val stroke = planColour(style.stroke) ?: ink
                val fill = planColour(style.fill)
                Canvas(modifier = Modifier.size(LEGEND_SWATCH_DP.dp)) {
                    if (fill != null) drawRect(fill)
                    drawRect(stroke, style = Stroke(width = (style.weight ?: OUTLINE_DP).coerceAtMost(3f).dp.toPx()))
                }
                Text(text = entry.label, style = LEGEND_TEXT, color = ink, modifier = Modifier.nodeTag("$nodeId/legend/$index"))
            }
        }
    }
}
