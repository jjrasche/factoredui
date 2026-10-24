package ai.factoredui.compose.renderer

import ai.factoredui.compose.detail.SelectionDetail
import ai.factoredui.compose.detail.readSelectionDetails
import ai.factoredui.compose.math.Camera
import ai.factoredui.compose.math.Vec3
import ai.factoredui.compose.math.project
import ai.factoredui.compose.plan.PlanLabelAlign
import ai.factoredui.compose.plan.PlanStyle
import ai.factoredui.compose.schema.SpecNode
import ai.factoredui.compose.schema.bindingPath
import ai.factoredui.compose.volume.VolumeBox
import ai.factoredui.compose.volume.VolumeLegendEntry
import ai.factoredui.compose.volume.VolumeRead
import ai.factoredui.compose.volume.VolumeSpanMm
import ai.factoredui.compose.volume.UP
import ai.factoredui.compose.volume.footprintCorners
import ai.factoredui.compose.volume.shadowOutline
import ai.factoredui.compose.volume.sideNormal
import ai.factoredui.compose.volume.sunShade
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
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.runtime.LaunchedEffect
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
private const val LIT_OUTLINE_DP = 0.8f
private const val BURIED_TOLERANCE_MM = 1.0
private const val GRADE_OPACITY = 0.5f
private val GRADE_COLOUR = Color(0xFF6B5A48)
private const val GHOST_FILL_ALPHA = 0.08f
private const val GHOST_EDGE_ALPHA = 0.3f
private const val CURRENT_OUTLINE_DP = 2.5f
private val CURRENT_INK = Color(0xFFFFD479)
private const val LIT_WHITE_MIX = 0.35f
private const val EDGE_DARKEN = 0.55f
private const val SHADOW_LAYERS = 4
private const val SHADOW_SPREAD = 0.04f
private const val SHADOW_ALPHA = 0.07f
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

private class ProjectedLine(val from: Offset, val to: Offset, val style: String?)

private class ProjectedLabel(val at: Offset, val text: String, val style: String?)

private class ProjectedScene(val ground: List<Offset>?, val boxes: List<ProjectedBox>, val north: Offset?, val lines: List<ProjectedLine>, val labels: List<ProjectedLabel>, val shadows: List<List<Offset>>)

private fun faceShades(ground: List<Pair<Double, Double>>, isLit: Boolean): List<Float> =
    if (!isLit) listOf(1f) + SIDE_SHADES.toList()
    else listOf(sunShade(UP).toFloat()) + ground.indices.map { i -> sideNormal(ground[i], ground[(i + 1) % 4])?.let { sunShade(it).toFloat() } ?: 1f }

private fun projectBox(box: VolumeBox, eye: Vec3, isLit: Boolean, project: (Vec3) -> Offset?): Pair<Float, ProjectedBox>? {
    val ground = footprintCorners(box)
    val low = ground.map { worldOf(it.first, it.second, box.zMm) }
    val high = ground.map { worldOf(it.first, it.second, box.zMm + box.heightMm) }
    val faceCorners = listOf(high) + low.indices.map { i -> listOf(low[i], low[(i + 1) % 4], high[(i + 1) % 4], high[i]) }
    val shades = faceShades(ground, isLit)
    val projected = faceCorners.map { corners -> corners.map(project) }
    if (projected.any { face -> face.any { it == null } }) return null
    val centre = (low + high).reduce { sum, point -> sum + point } * (1f / 8f)
    val ordered = faceCorners.indices.sortedByDescending { i -> ((faceCorners[i].reduce { sum, point -> sum + point } * 0.25f) - eye).length() }
    val faces = ordered.map { ProjectedFace(projected[it].map { point -> point!! }, shades[it]) }
    val points = faces.flatMap { it.corners }
    return (centre - eye).length() to ProjectedBox(box, faces, points.minOf { it.x }, points.minOf { it.y }, points.maxOf { it.x }, points.maxOf { it.y })
}

private fun groundLevelMm(span: VolumeSpanMm, gradeMm: Double?) = gradeMm ?: if (span.floor < 0 && span.ceiling > 0) 0.0 else span.floor

private enum class Phase { PLACED, CURRENT, GHOST }

/** In a stepped volume a box is a ghost until its stage, current at it, and placed after; a box with no stage is a ghost for ever. Without a step every box is placed. */
private fun phaseOf(box: VolumeBox, step: Double?): Phase {
    val stage = box.stage
    return when {
        step == null -> Phase.PLACED
        stage == null || stage > step -> Phase.GHOST
        stage == step -> Phase.CURRENT
        else -> Phase.PLACED
    }
}

private fun projectShadows(read: VolumeRead, span: VolumeSpanMm, step: Double?, gradeMm: Double?, project: (Vec3) -> Offset?): List<List<Offset>> =
    read.boxes.filter { !it.isEstimate && phaseOf(it, step) != Phase.GHOST }.mapNotNull { box ->
        val outline = shadowOutline(box, groundLevelMm(span, gradeMm)).map { (x, y) -> project(worldOf(x, y, groundLevelMm(span, gradeMm))) }
        outline.takeIf { points -> points.size >= 3 && points.all { it != null } }?.map { it!! }
    }

private fun projectScene(read: VolumeRead, span: VolumeSpanMm, camera: Camera, widthPx: Float, heightPx: Float, isLit: Boolean, step: Double?, gradeMm: Double?): ProjectedScene {
    val view = camera.viewMatrix()
    val lens = camera.projectionMatrix(widthPx / heightPx)
    val project = { point: Vec3 -> point.project(view, lens, widthPx, heightPx).takeIf { it.visible }?.let { Offset(it.x, it.y) } }
    val eye = camera.eyePosition()
    val boxes = paintOnTop(read.boxes.mapNotNull { projectBox(it, eye, isLit, project) }.sortedByDescending { it.first }.map { it.second }) { it.box }
    val marginX = (span.east - span.west) * GROUND_MARGIN
    val marginY = (span.north - span.south) * GROUND_MARGIN
    val groundCorners = listOf(
        span.west - marginX to span.south - marginY, span.east + marginX to span.south - marginY,
        span.east + marginX to span.north + marginY, span.west - marginX to span.north + marginY,
    ).map { project(worldOf(it.first, it.second, gradeMm ?: span.floor)) }
    val north = project(worldOf((span.west + span.east) / 2, span.north + marginY, span.floor))
    val lines = read.lines.mapNotNull { line ->
        val from = project(worldOf(line.from.xMm, line.from.yMm, line.from.zMm))
        val to = project(worldOf(line.to.xMm, line.to.yMm, line.to.zMm))
        if (from != null && to != null) ProjectedLine(from, to, line.style) else null
    }
    val labels = read.labels.mapNotNull { label -> project(worldOf(label.at.xMm, label.at.yMm, label.at.zMm))?.let { ProjectedLabel(it, label.text, label.style) } }
    return ProjectedScene(groundCorners.takeIf { corners -> corners.all { it != null } }?.map { it!! }, boxes, north, lines, labels, if (isLit) projectShadows(read, span, step, gradeMm, project) else emptyList())
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
        drawPath(path, shaded(body, face.shade).copy(alpha = style.opacity), style = Fill)
        if (projected.box.isEstimate) drawHatch(face.corners, stroke.copy(alpha = 0.55f))
        drawPath(path, stroke, style = Stroke(width = (style.weight ?: OUTLINE_DP).dp.toPx(), pathEffect = effect))
    }
}

private fun DrawScope.drawShadow(outline: List<Offset>) {
    val centre = Offset(outline.sumOf { it.x.toDouble() }.toFloat() / outline.size, outline.sumOf { it.y.toDouble() }.toFloat() / outline.size)
    (0 until SHADOW_LAYERS).forEach { layer ->
        val spread = 1f + SHADOW_SPREAD * layer
        drawPath(pathOf(outline.map { centre + (it - centre) * spread }), Color.Black.copy(alpha = SHADOW_ALPHA), style = Fill)
    }
}

/** A box lit from the sun in its style's colour, with a thin edge in a deeper tone of it. */
private fun DrawScope.drawLitBox(projected: ProjectedBox, style: PlanStyle, ink: Color) {
    val stroke = planColour(style.stroke) ?: ink
    val body = lerp(stroke, Color.White, LIT_WHITE_MIX)
    val effect = if (projected.box.isEstimate) PathEffect.dashPathEffect(floatArrayOf(DASH_DP.dp.toPx(), DASH_DP.dp.toPx())) else null
    projected.faces.forEach { face ->
        val path = pathOf(face.corners)
        val faceColour = shaded(body, face.shade).copy(alpha = style.opacity)
        drawPath(path, faceColour, style = Fill)
        if (projected.box.isEstimate) drawHatch(face.corners, stroke.copy(alpha = 0.55f))
        val edge = shaded(stroke, EDGE_DARKEN)
        drawPath(path, edge, style = Stroke(width = (style.weight ?: LIT_OUTLINE_DP).dp.toPx(), pathEffect = effect))
    }
}

private fun DrawScope.drawGhostBox(projected: ProjectedBox, style: PlanStyle, ink: Color) {
    val stroke = planColour(style.stroke) ?: ink
    projected.faces.forEach { face ->
        val path = pathOf(face.corners)
        drawPath(path, stroke.copy(alpha = GHOST_FILL_ALPHA), style = Fill)
        drawPath(path, stroke.copy(alpha = GHOST_EDGE_ALPHA), style = Stroke(width = LIT_OUTLINE_DP.dp.toPx()))
    }
}

private fun DrawScope.drawCurrentOutline(projected: ProjectedBox) {
    projected.faces.forEach { face -> drawPath(pathOf(face.corners), CURRENT_INK, style = Stroke(width = CURRENT_OUTLINE_DP.dp.toPx())) }
}

/** A box as its edges only: each face is filled with the ground, so a nearer face hides the edges behind it, and the edges are stroked in the style's line. */
private fun DrawScope.drawBlueprintBox(projected: ProjectedBox, style: PlanStyle, ground: Color, ink: Color) {
    val stroke = planColour(style.stroke) ?: ink
    val isDashed = projected.box.isEstimate || style.dash
    val effect = if (isDashed) PathEffect.dashPathEffect(floatArrayOf(DASH_DP.dp.toPx(), DASH_DP.dp.toPx())) else null
    projected.faces.forEach { face ->
        val path = pathOf(face.corners)
        drawPath(path, ground.copy(alpha = ground.alpha * style.opacity), style = Fill)
        drawPath(path, stroke, style = Stroke(width = (style.weight ?: OUTLINE_DP).dp.toPx(), pathEffect = effect))
    }
}

private fun DrawScope.drawProjectedLine(line: ProjectedLine, style: PlanStyle, ink: Color) {
    val effect = if (style.dash) PathEffect.dashPathEffect(floatArrayOf(DASH_DP.dp.toPx(), DASH_DP.dp.toPx())) else null
    drawLine(planColour(style.stroke) ?: ink, line.from, line.to, strokeWidth = (style.weight ?: OUTLINE_DP).dp.toPx(), pathEffect = effect)
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

private const val NOTHING_PICKED = -1

private fun pickedEntries(crossed: List<String>, details: Map<String, SelectionDetail>): List<Map<String, Any?>> =
    crossed.mapIndexed { index, id -> mapOf("id" to id, "index" to index, "label" to (details[id]?.title ?: id)) }

private fun pickedIdsOf(raw: Any?): List<String> = (raw as? List<*>).orEmpty().mapNotNull { (it as? Map<*, *>)?.get("id") as? String }

/** With `through` bound, the one picked at that index is the selection, an index of -1 clears it, and one past the last picked is brought back to the last. */
@Composable
private fun PublishPickedThrough(node: SpecNode, through: Int?, pickedIds: List<String>, context: RenderContext) {
    val selectedPath = node.props["selected"]?.bindingPath()
    val throughPath = node.props["through"]?.bindingPath()
    LaunchedEffect(selectedPath, throughPath, through, pickedIds) {
        if (throughPath == null || through == null) return@LaunchedEffect
        if (pickedIds.isNotEmpty() && through >= pickedIds.size) return@LaunchedEffect context.setBinding(throughPath, pickedIds.lastIndex)
        selectedPath?.let { context.setBinding(it, pickedIds.getOrNull(through)?.let { id -> listOf(id) } ?: emptyList<String>()) }
    }
}

/** Writes what `details` holds for the one selected thing, as given, to the path `detail_record` binds; nothing when none or several are selected. */
@Composable
private fun PublishDetailRecord(node: SpecNode, selection: Set<String>, rawDetails: Any?, context: RenderContext) {
    val path = node.props["detail_record"]?.bindingPath() ?: return
    val record = selection.singleOrNull()?.let { (rawDetails as? Map<*, *>)?.get(it) }
    LaunchedEffect(path, record) { context.setBinding(path, record) }
}
@Composable
internal fun RenderVolume(node: SpecNode, resolvedProps: Map<String, Any?>, context: RenderContext) {
    val read = remember(resolvedProps["boxes"], resolvedProps["styles"], resolvedProps["legend"], resolvedProps["lines"], resolvedProps["labels"]) {
        readVolume(resolvedProps["boxes"], resolvedProps["styles"], resolvedProps["legend"], resolvedProps["lines"], resolvedProps["labels"])
    }
    val details = remember(resolvedProps["details"]) { readSelectionDetails(resolvedProps["details"]) }
    val selection = rememberDrawingSelection(node, resolvedProps["selected"], context)
    PublishSelectionText(node, selection.ids, details, context)
    val throughPath = node.props["through"]?.bindingPath()
    val through = (resolvedProps["through"] as? Number)?.toInt()
    val pickedIds = pickedIdsOf(resolvedProps["picked"])
    val inFront = if (throughPath != null && through != null && through > 0) pickedIds.take(through) else emptyList()
    val hidden = remember(resolvedProps["hidden"], inFront) { (resolvedProps["hidden"] as? List<*>).orEmpty().filterIsInstance<String>().toSet() + inFront }
    PublishPickedThrough(node, through, pickedIds, context)
    PublishDetailRecord(node, selection.ids, resolvedProps["details"], context)
    val shown = remember(read, hidden) { if (hidden.isEmpty()) read else read.copy(boxes = read.boxes.filter { it.id !in hidden }) }
    val pickedPath = node.props["picked"]?.bindingPath()
    val span = volumeSpan(read.boxes)
    val fill = resolvedProps["fill"] == true
    val bottomInsetDp = (resolvedProps["bottom_inset_dp"] as? Number)?.toFloat() ?: 0f
    val blueprint = resolvedProps["mode"] == "blueprint"
    val ground = planColour(resolvedProps["ground"] as? String)
    val gradeTint = (planColour(resolvedProps["grade_color"] as? String) ?: GRADE_COLOUR).let { it.copy(alpha = (resolvedProps["grade_opacity"] as? Number)?.toFloat()?.coerceIn(0f, 1f) ?: GRADE_OPACITY) }
    Column(modifier = (if (fill) Modifier.fillMaxSize() else Modifier.fillMaxWidth()).nodeTag(node.id)) {
        read.notices.forEach { Text(text = it, style = VOLUME_TEXT, color = NOTICE_INK) }
        if (span == null) {
            Text(text = resolvedProps["empty_text"] as? String ?: "", style = VOLUME_TEXT, color = LocalSpecTheme.current.muted)
        } else {
            VolumeDrawing(node.id, shown, span, selection, details, if (fill) bottomInsetDp else null, blueprint, ground, resolvedProps["lighting"] == "sun", (resolvedProps["step"] as? Number)?.toDouble(), (resolvedProps["grade_mm"] as? Number)?.toDouble(), gradeTint, pickedPath, throughPath, context)
            if (!fill) SelectionDetailCard(node.id, selection.ids, details)
            VolumeLegend(node.id, read.legend, read.styles)
        }
    }
}

@Composable
private fun ColumnScope.VolumeDrawing(nodeId: String, read: VolumeRead, span: VolumeSpanMm, selection: DrawingSelection, details: Map<String, SelectionDetail>, fillInsetDp: Float?, blueprint: Boolean, ground: Color?, isLit: Boolean, step: Double?, gradeMm: Double?, gradeTint: Color, pickedPath: String?, throughPath: String?, context: RenderContext) {
    val theme = LocalSpecTheme.current
    val camera = remember(span) { Camera().also { frameCamera(it, span) } }
    var revision by remember(camera) { mutableStateOf(0) }
    val fits = fillInsetDp != null
    BoxWithConstraints(modifier = if (fits) Modifier.fillMaxWidth().weight(1f) else Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        val widthDp = if (fits) maxWidth else min(maxWidth, VOLUME_MAX_WIDTH_DP.dp)
        val heightDp = if (fits) (maxHeight - (fillInsetDp ?: 0f).dp).coerceAtLeast(1.dp) else widthDp * VOLUME_ASPECT
        val density = LocalDensity.current
        val widthPx = with(density) { widthDp.toPx() }
        val heightPx = with(density) { heightDp.toPx() }
        val scene = remember(revision, read, span, widthPx, heightPx, isLit, step, gradeMm) { projectScene(read, span, camera, widthPx, heightPx, isLit, step, gradeMm) }
        val reachPx = with(density) { PICK_REACH_DP.dp.toPx() }
        Box(
            modifier = Modifier.width(widthDp).height(heightDp).orbitGestures(camera) { revision += 1 }.pointerInput(scene, selection.ids, pickedPath, throughPath) {
                detectTapGestures(onTap = { at ->
                    val crossed = pickProjectedBoxes(scene.boxes.filter { isPickable(it.box.id, details) }, at, reachPx)
                    pickedPath?.let { context.setBinding(it, pickedEntries(crossed, details)) }
                    if (throughPath != null) context.setBinding(throughPath, if (crossed.isEmpty()) NOTHING_PICKED else 0) else selection.tap(crossed)
                })
            },
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                ground?.let { drawRect(it) }
                if (!blueprint) scene.ground?.let { drawPath(pathOf(it), GROUND_FILL, style = Fill) }
                fun drawBox(it: ProjectedBox) {
                    val style = read.styles[it.box.style] ?: PlanStyle()
                    val phase = phaseOf(it.box, step)
                    when {
                        blueprint -> drawBlueprintBox(it, style, ground ?: Color.Transparent, theme.ink)
                        phase == Phase.GHOST -> drawGhostBox(it, style, theme.ink)
                        isLit -> drawLitBox(it, style, theme.ink)
                        else -> drawProjectedBox(it, style, theme.ink)
                    }
                    if (phase == Phase.CURRENT && !blueprint) drawCurrentOutline(it)
                }
                val isBuried = { projected: ProjectedBox -> gradeMm != null && !blueprint && projected.box.zMm + projected.box.heightMm < gradeMm - BURIED_TOLERANCE_MM }
                scene.boxes.filter(isBuried).forEach(::drawBox)
                if (gradeMm != null && !blueprint) scene.ground?.let { drawPath(pathOf(it), gradeTint, style = Fill) }
                scene.shadows.forEach { drawShadow(it) }
                scene.boxes.filterNot(isBuried).forEach(::drawBox)
                scene.lines.forEach { drawProjectedLine(it, read.styles[it.style] ?: PlanStyle(), theme.ink) }
                scene.boxes.filter { it.box.id in selection.ids }.forEach { drawSelectedBox(it, if (blueprint) Color.White else theme.ink) }
            }
            scene.labels.forEach { label ->
                PlanPlaced(label.at.x, label.at.y, PlanLabelAlign.CENTER, null) {
                    Text(text = label.text, style = VOLUME_TEXT, color = planColour(read.styles[label.style]?.stroke) ?: theme.ink)
                }
            }
            scene.boxes.forEach { projected ->
                val size = IntSize(max(1, (projected.right - projected.left).toInt()), max(1, (projected.bottom - projected.top).toInt()))
                PlanPlaced(projected.left, projected.top, PlanLabelAlign.START, size) { Box(modifier = Modifier.nodeTag("$nodeId/${projected.box.id}")) }
            }
            scene.north?.let { PlanPlaced(it.x, it.y, PlanLabelAlign.CENTER, null) { Text(text = "N", style = VOLUME_TEXT, color = theme.ink, modifier = Modifier.nodeTag("$nodeId/north")) } }
            if (fits) {
                Text(
                    text = "Reset view",
                    style = VOLUME_TEXT,
                    color = theme.ink,
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).clickable { frameCamera(camera, span); revision += 1 }.nodeTag("$nodeId/reset"),
                )
            }
        }
    }
    if (!fits) {
        Text(
            text = "Reset view",
            style = VOLUME_TEXT,
            color = theme.ink,
            modifier = Modifier.padding(top = 4.dp).clickable { frameCamera(camera, span); revision += 1 }.nodeTag("$nodeId/reset"),
        )
    }
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
