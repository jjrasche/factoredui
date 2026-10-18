package ai.factoredui.compose.renderer

import ai.factoredui.compose.detail.SelectionDetail
import ai.factoredui.compose.detail.readSelectionDetails
import ai.factoredui.compose.math.Camera
import ai.factoredui.compose.math.Vec3
import ai.factoredui.compose.math.project
import ai.factoredui.compose.plan.PlanLabelAlign
import ai.factoredui.compose.plan.PlanStyle
import ai.factoredui.compose.schema.SpecNode
import ai.factoredui.compose.volume.VolumeBox
import ai.factoredui.compose.volume.VolumeLegendEntry
import ai.factoredui.compose.volume.VolumeRead
import ai.factoredui.compose.volume.VolumeSpanMm
import ai.factoredui.compose.volume.footprintCorners
import ai.factoredui.compose.volume.paintOnTop
import ai.factoredui.compose.volume.readVolume
import ai.factoredui.compose.volume.volumeSpan
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

private const val VOLUME_MAX_WIDTH_DP = 560f
private const val PICK_REACH_DP = 12f
private const val SELECTED_OUTLINE_DP = 3.5f
private const val VOLUME_ASPECT = 1.15f
private const val FRAMING_YAW = -(PI / 4).toFloat()
private const val FRAMING_PITCH = (PI / 6).toFloat()
private const val FRAMING_SLACK = 1.1f
private const val MM_PER_UNIT = 1000f
private const val GROUND_MARGIN = 0.15f
private const val HATCH_GAP_DP = 6f
private const val OUTLINE_DP = 1.2f
private const val DASH_DP = 5f
private const val LEGEND_SWATCH_DP = 14f
private const val WHITE_MIX = 0.6f
private const val DRAG_PER_DP = 1f
private val SIDE_SHADES = floatArrayOf(0.86f, 0.7f, 0.8f, 0.64f)
private val GROUND_FILL = Color(0x14, 0x14, 0x14, 0x14)
private val VOLUME_TEXT = TextStyle(fontSize = 12.sp, lineHeight = 15.sp)
private val NOTICE_INK = Color(0xFFB3261E)

/** Engine frame (x east, y north, z up, mm) to the camera's frame (x east, y up, z south, metres). */
private fun worldOf(xMm: Double, yMm: Double, zMm: Double) = Vec3((xMm / MM_PER_UNIT).toFloat(), (zMm / MM_PER_UNIT).toFloat(), (-yMm / MM_PER_UNIT).toFloat())

private fun frameCamera(camera: Camera, span: VolumeSpanMm) {
    camera.target = worldOf((span.west + span.east) / 2, (span.south + span.north) / 2, (span.floor + span.ceiling) / 2)
    val radius = hypot(hypot(span.east - span.west, span.north - span.south), span.ceiling - span.floor).toFloat() / MM_PER_UNIT / 2
    camera.setOrbit(FRAMING_YAW, FRAMING_PITCH, radius / sin(camera.fovYRadians / 2) * FRAMING_SLACK)
}

private data class ProjectedFace(val corners: List<Offset>, val shade: Float)

private data class ProjectedBox(val box: VolumeBox, val faces: List<ProjectedFace>, val left: Float, val top: Float, val right: Float, val bottom: Float)

private class ProjectedScene(val ground: List<Offset>?, val boxes: List<ProjectedBox>, val north: Offset?)

private fun projectBox(box: VolumeBox, eye: Vec3, project: (Vec3) -> Offset?): Pair<Float, ProjectedBox>? {
    val ground = footprintCorners(box)
    val low = ground.map { worldOf(it.first, it.second, box.zMm) }
    val high = ground.map { worldOf(it.first, it.second, box.zMm + box.heightMm) }
    val faceCorners = listOf(high) + low.indices.map { i -> listOf(low[i], low[(i + 1) % 4], high[(i + 1) % 4], high[i]) }
    val shades = listOf(1f) + SIDE_SHADES.toList()
    val projected = faceCorners.map { corners -> corners.map(project) }
    if (projected.any { face -> face.any { it == null } }) return null
    val centre = (low + high).reduce { sum, point -> sum + point } * (1f / 8f)
    val ordered = faceCorners.indices.sortedByDescending { i -> ((faceCorners[i].reduce { sum, point -> sum + point } * 0.25f) - eye).length() }
    val faces = ordered.map { ProjectedFace(projected[it].map { point -> point!! }, shades[it]) }
    val points = faces.flatMap { it.corners }
    return (centre - eye).length() to ProjectedBox(box, faces, points.minOf { it.x }, points.minOf { it.y }, points.maxOf { it.x }, points.maxOf { it.y })
}

private fun projectScene(read: VolumeRead, span: VolumeSpanMm, camera: Camera, widthPx: Float, heightPx: Float): ProjectedScene {
    val view = camera.viewMatrix()
    val lens = camera.projectionMatrix(widthPx / heightPx)
    val project = { point: Vec3 -> point.project(view, lens, widthPx, heightPx).takeIf { it.visible }?.let { Offset(it.x, it.y) } }
    val eye = camera.eyePosition()
    val boxes = paintOnTop(read.boxes.mapNotNull { projectBox(it, eye, project) }.sortedByDescending { it.first }.map { it.second }) { it.box }
    val marginX = (span.east - span.west) * GROUND_MARGIN
    val marginY = (span.north - span.south) * GROUND_MARGIN
    val groundCorners = listOf(
        span.west - marginX to span.south - marginY, span.east + marginX to span.south - marginY,
        span.east + marginX to span.north + marginY, span.west - marginX to span.north + marginY,
    ).map { project(worldOf(it.first, it.second, span.floor)) }
    val north = project(worldOf((span.west + span.east) / 2, span.north + marginY, span.floor))
    return ProjectedScene(groundCorners.takeIf { corners -> corners.all { it != null } }?.map { it!! }, boxes, north)
}

private fun pathOf(corners: List<Offset>): Path = Path().apply {
    moveTo(corners.first().x, corners.first().y)
    corners.drop(1).forEach { lineTo(it.x, it.y) }
    close()
}

private fun shaded(colour: Color, shade: Float): Color = Color(colour.red * shade, colour.green * shade, colour.blue * shade, colour.alpha)

private fun DrawScope.drawHatch(corners: List<Offset>, colour: Color) {
    val gap = HATCH_GAP_DP.dp.toPx()
    val left = corners.minOf { it.x }
    val right = corners.maxOf { it.x }
    val top = corners.minOf { it.y }
    val bottom = corners.maxOf { it.y }
    clipPath(pathOf(corners)) {
        var start = left - (bottom - top)
        while (start < right) {
            drawLine(colour, Offset(start, bottom), Offset(start + (bottom - top), top), strokeWidth = 1.dp.toPx())
            start += gap
        }
    }
}

private fun DrawScope.drawProjectedBox(projected: ProjectedBox, style: PlanStyle, ink: Color) {
    val stroke = planColour(style.stroke) ?: ink
    val body = lerp(stroke, Color.White, WHITE_MIX)
    val effect = if (projected.box.isEstimate) PathEffect.dashPathEffect(floatArrayOf(DASH_DP.dp.toPx(), DASH_DP.dp.toPx())) else null
    projected.faces.forEach { face ->
        val path = pathOf(face.corners)
        drawPath(path, shaded(body, face.shade), style = Fill)
        if (projected.box.isEstimate) drawHatch(face.corners, stroke.copy(alpha = 0.55f))
        drawPath(path, stroke, style = Stroke(width = (style.weight ?: OUTLINE_DP).dp.toPx(), pathEffect = effect))
    }
}

private fun DrawScope.drawSelectedBox(projected: ProjectedBox, ink: Color) {
    projected.faces.forEach { face -> drawPath(pathOf(face.corners), ink, style = Stroke(width = SELECTED_OUTLINE_DP.dp.toPx())) }
}

private fun isInside(corners: List<Offset>, at: Offset): Boolean {
    var inside = false
    corners.indices.forEach { i ->
        val a = corners[i]
        val b = corners[(i + 1) % corners.size]
        if ((a.y > at.y) != (b.y > at.y) && at.x < (b.x - a.x) * (at.y - a.y) / (b.y - a.y) + a.x) inside = !inside
    }
    return inside
}

private fun edgeDistance(corners: List<Offset>, at: Offset): Float =
    corners.indices.minOf { i ->
        val a = corners[i]
        val b = corners[(i + 1) % corners.size]
        val dx = b.x - a.x
        val dy = b.y - a.y
        val lengthSquared = dx * dx + dy * dy
        val along = if (lengthSquared == 0f) 0f else (((at.x - a.x) * dx + (at.y - a.y) * dy) / lengthSquared).coerceIn(0f, 1f)
        hypot(at.x - (a.x + along * dx), at.y - (a.y + along * dy))
    }

/** Every box the tap could mean: those whose faces hold it, nearest the eye first, then those with an edge within [reachPx] (a plane seen edge-on), nearest first. Boxes are given back to front. */
private fun pickProjectedBoxes(boxes: List<ProjectedBox>, at: Offset, reachPx: Float): List<String> {
    val front = boxes.asReversed()
    val holding = front.filter { projected -> projected.faces.any { isInside(it.corners, at) } }.map { it.box.id }
    val near = front.map { projected -> projected to projected.faces.minOf { edgeDistance(it.corners, at) } }.filter { it.second <= reachPx }.sortedBy { it.second }.map { it.first.box.id }
    return (holding + near).distinct()
}

@Composable
internal fun RenderVolume(node: SpecNode, resolvedProps: Map<String, Any?>, context: RenderContext) {
    val read = remember(resolvedProps["boxes"], resolvedProps["styles"], resolvedProps["legend"]) {
        readVolume(resolvedProps["boxes"], resolvedProps["styles"], resolvedProps["legend"])
    }
    val details = remember(resolvedProps["details"]) { readSelectionDetails(resolvedProps["details"]) }
    val selection = rememberDrawingSelection(node, resolvedProps["selected"], context)
    val span = volumeSpan(read.boxes)
    Column(modifier = Modifier.fillMaxWidth().nodeTag(node.id)) {
        read.notices.forEach { Text(text = it, style = VOLUME_TEXT, color = NOTICE_INK) }
        if (span == null) {
            Text(text = resolvedProps["empty_text"] as? String ?: "", style = VOLUME_TEXT, color = LocalSpecTheme.current.muted)
        } else {
            VolumeDrawing(node.id, read, span, selection, details)
            SelectionDetailCard(node.id, selection.ids, details)
            VolumeLegend(node.id, read.legend, read.styles)
        }
    }
}

@Composable
private fun VolumeDrawing(nodeId: String, read: VolumeRead, span: VolumeSpanMm, selection: DrawingSelection, details: Map<String, SelectionDetail>) {
    val theme = LocalSpecTheme.current
    val camera = remember(span) { Camera().also { frameCamera(it, span) } }
    var revision by remember(camera) { mutableStateOf(0) }
    BoxWithConstraints(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        val widthDp = min(maxWidth, VOLUME_MAX_WIDTH_DP.dp)
        val heightDp = widthDp * VOLUME_ASPECT
        val density = LocalDensity.current
        val widthPx = with(density) { widthDp.toPx() }
        val heightPx = with(density) { heightDp.toPx() }
        val scene = remember(revision, read, span, widthPx, heightPx) { projectScene(read, span, camera, widthPx, heightPx) }
        val reachPx = with(density) { PICK_REACH_DP.dp.toPx() }
        Box(
            modifier = Modifier.width(widthDp).height(heightDp).orbitGestures(camera) { revision += 1 }.pointerInput(scene, selection.ids) {
                detectTapGestures(onTap = { at -> selection.tap(pickProjectedBoxes(scene.boxes.filter { isPickable(it.box.id, details) }, at, reachPx)) })
            },
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                scene.ground?.let { drawPath(pathOf(it), GROUND_FILL, style = Fill) }
                scene.boxes.forEach { drawProjectedBox(it, read.styles[it.box.style] ?: PlanStyle(), theme.ink) }
                scene.boxes.filter { it.box.id in selection.ids }.forEach { drawSelectedBox(it, theme.ink) }
            }
            scene.boxes.forEach { projected ->
                val size = IntSize(max(1, (projected.right - projected.left).toInt()), max(1, (projected.bottom - projected.top).toInt()))
                PlanPlaced(projected.left, projected.top, PlanLabelAlign.START, size) { Box(modifier = Modifier.nodeTag("$nodeId/${projected.box.id}")) }
            }
            scene.north?.let { PlanPlaced(it.x, it.y, PlanLabelAlign.CENTER, null) { Text(text = "N", style = VOLUME_TEXT, color = theme.ink, modifier = Modifier.nodeTag("$nodeId/north")) } }
        }
    }
    Text(
        text = "Reset view",
        style = VOLUME_TEXT,
        color = theme.ink,
        modifier = Modifier.padding(top = 4.dp).clickable { frameCamera(camera, span); revision += 1 }.nodeTag("$nodeId/reset"),
    )
}

/** One finger or the left button turns the view, two fingers or the right button or Ctrl slides it, a pinch or the wheel zooms. */
private fun Modifier.orbitGestures(camera: Camera, changed: () -> Unit): Modifier =
    pointerInput(camera) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            do {
                val event = awaitPointerEvent()
                val held = event.changes.filter { it.pressed }
                if (held.size == 1) {
                    val shift = (held[0].position - held[0].previousPosition) / density
                    val isSliding = event.keyboardModifiers.isCtrlPressed || event.buttons.isSecondaryPressed
                    if (isSliding) camera.pan(shift.x, shift.y) else camera.drag(shift.x * DRAG_PER_DP, shift.y * DRAG_PER_DP)
                    held[0].consume()
                    changed()
                } else if (held.size >= 2) {
                    val centre = held.map { it.position }.reduce { sum, point -> sum + point } / held.size.toFloat()
                    val before = held.map { it.previousPosition }.reduce { sum, point -> sum + point } / held.size.toFloat()
                    val spread = held.map { (it.position - centre).getDistance() }.average().toFloat()
                    val spreadBefore = held.map { (it.previousPosition - before).getDistance() }.average().toFloat()
                    if (spreadBefore > 0f && spread > 0f) camera.setOrbit(camera.yawRadians, camera.pitchRadians, camera.distance * spreadBefore / spread)
                    val shift = (centre - before) / density
                    camera.pan(shift.x, shift.y)
                    held.forEach { it.consume() }
                    changed()
                }
            } while (event.changes.any { it.pressed })
        }
    }.pointerInput(camera) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                if (event.type != PointerEventType.Scroll) continue
                val change = event.changes.first()
                camera.zoom(-change.scrollDelta.y)
                change.consume()
                changed()
            }
        }
    }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VolumeLegend(nodeId: String, legend: List<VolumeLegendEntry>, styles: Map<String, PlanStyle>) {
    if (legend.isEmpty()) return
    val ink = LocalSpecTheme.current.ink
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        legend.forEachIndexed { index, entry ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val stroke = planColour(styles[entry.style]?.stroke) ?: ink
                Canvas(modifier = Modifier.size(LEGEND_SWATCH_DP.dp)) {
                    val corners = listOf(Offset(0f, 0f), Offset(size.width, 0f), Offset(size.width, size.height), Offset(0f, size.height))
                    drawRect(lerp(stroke, Color.White, WHITE_MIX))
                    if (entry.isHatched) drawHatch(corners, stroke.copy(alpha = 0.55f))
                    drawRect(stroke, style = Stroke(width = OUTLINE_DP.dp.toPx()))
                }
                Text(text = entry.label, style = VOLUME_TEXT, color = ink, modifier = Modifier.nodeTag("$nodeId/legend/$index"))
            }
        }
    }
}
