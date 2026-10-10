package ai.factoredui.compose.renderer

import ai.factoredui.compose.scene.SceneFootprint
import ai.factoredui.compose.scene.SceneView
import ai.factoredui.compose.structure.CanvasFit
import ai.factoredui.compose.structure.ColourScale
import ai.factoredui.compose.structure.LegendEntry
import ai.factoredui.compose.structure.PartValue
import ai.factoredui.compose.structure.StructureDrawing
import ai.factoredui.compose.structure.VisiblePart
import ai.factoredui.compose.structure.colourFor
import ai.factoredui.compose.structure.faceShades
import ai.factoredui.compose.structure.flatViewOf
import ai.factoredui.compose.structure.isoProject
import ai.factoredui.compose.structure.mmToCanvas
import ai.factoredui.compose.structure.partBox
import ai.factoredui.compose.structure.partRectInView
import ai.factoredui.compose.structure.projectFaces
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

internal class StructurePicture(
    val scene: SceneView,
    val parts: List<VisiblePart>,
    val drawing: StructureDrawing,
    val fit: CanvasFit,
    val values: Map<String, PartValue>,
    val colourScale: ColourScale?,
    val outlinedIds: Set<String>,
)

private fun StructurePicture.colourOf(part: VisiblePart): Long = colourFor(colourScale, values[part.footprint.id]?.value)

internal class StructurePalette(val edge: Color, val outline: Color, val groundFill: Color, val groundEdge: Color)

private val SELECTED_OUTLINE = Color(0xFF1E90FF)
private const val DIMMED_ALPHA = 0.3f
private const val FLAT_SOLID_ALPHA = 0.92f
private const val ISO_SOLID_ALPHA = 0.97f
private const val FLAT_EDGE_PX = 1.5f
private const val ISO_EDGE_PX = 1f
private const val ISO_EDGE_ALPHA = 0.55f
private const val OUTLINE_PX = 4f
private const val ISO_OUTLINE_PX = 3.5f
private const val DATUM_PX = 2f

/** A part the scene gives no height still has to be seen and found in an elevation, so it is drawn at least this thick. */
private const val MIN_PART_PX = 3f

internal fun structurePalette(theme: SpecTheme) = StructurePalette(
    edge = theme.ground.copy(alpha = 0.85f),
    outline = SELECTED_OUTLINE,
    groundFill = theme.muted.copy(alpha = 0.10f),
    groundEdge = theme.muted.copy(alpha = 0.35f),
)

private fun solidity(part: VisiblePart, solid: Float): Float = if (part.isDimmed) DIMMED_ALPHA else solid

private fun canvasPoint(fit: CanvasFit, xMm: Double, yMm: Double): Offset =
    mmToCanvas(fit, xMm, yMm).let { (x, y) -> Offset(x.toFloat(), y.toFloat()) }

private fun pathThrough(points: List<Offset>): Path = Path().apply {
    points.forEachIndexed { index, point -> if (index == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y) }
    close()
}

internal fun DrawScope.drawStructure(picture: StructurePicture, palette: StructurePalette) {
    if (picture.drawing == StructureDrawing.ISO) drawIsoStructure(picture, palette) else drawFlatStructure(picture, palette)
}

private fun partCanvasRect(picture: StructurePicture, footprint: SceneFootprint): Rect {
    val rect = partRectInView(picture.scene, footprint, flatViewOf(picture.drawing))
    val topLeft = canvasPoint(picture.fit, rect.x0, rect.y1)
    val bottomRight = canvasPoint(picture.fit, rect.x1, rect.y0)
    return Rect(topLeft.x, minOf(topLeft.y, bottomRight.y - MIN_PART_PX), bottomRight.x, bottomRight.y)
}

private fun DrawScope.drawFlatStructure(picture: StructurePicture, palette: StructurePalette) {
    if (picture.drawing != StructureDrawing.PLAN) drawDatum(picture, palette)
    picture.parts.forEach { part ->
        val rect = partCanvasRect(picture, part.footprint)
        val fill = Color(picture.colourOf(part))
        drawRect(fill.copy(alpha = solidity(part, FLAT_SOLID_ALPHA)), rect.topLeft, rect.size)
        drawRect(palette.edge, rect.topLeft, rect.size, style = Stroke(FLAT_EDGE_PX))
    }
    picture.parts.filter { it.footprint.id in picture.outlinedIds }.forEach { part ->
        val rect = partCanvasRect(picture, part.footprint)
        drawRect(palette.outline, rect.topLeft, rect.size, style = Stroke(OUTLINE_PX))
    }
}

private fun DrawScope.drawDatum(picture: StructurePicture, palette: StructurePalette) {
    val datumY = canvasPoint(picture.fit, 0.0, 0.0).y
    drawLine(palette.groundEdge, Offset(0f, datumY), Offset(size.width, datumY), strokeWidth = DATUM_PX)
}

private fun isoPolygon(fit: CanvasFit, polygon: List<Pair<Double, Double>>): Path = pathThrough(polygon.map { (u, v) -> canvasPoint(fit, u, v) })

private fun DrawScope.drawIsoGround(picture: StructurePicture, palette: StructurePalette) {
    val window = picture.scene.window
    val corners = listOf(window.x0 to window.y0, window.x1 to window.y0, window.x1 to window.y1, window.x0 to window.y1)
        .map { (x, y) -> isoProject(x.toDouble(), y.toDouble(), 0.0).let { canvasPoint(picture.fit, it.u, it.v) } }
    val ground = pathThrough(corners)
    drawPath(ground, palette.groundFill)
    drawPath(ground, palette.groundEdge, style = Stroke(1f))
}

private fun DrawScope.drawIsoStructure(picture: StructurePicture, palette: StructurePalette) {
    drawIsoGround(picture, palette)
    picture.parts.forEach { part ->
        val faces = projectFaces(partBox(picture.scene, part.footprint))
        val shades = faceShades(picture.colourOf(part))
        val alpha = solidity(part, ISO_SOLID_ALPHA)
        listOf(faces.top to shades.top, faces.west to shades.west, faces.south to shades.south).forEach { (polygon, shade) ->
            val face = isoPolygon(picture.fit, polygon)
            drawPath(face, Color(shade).copy(alpha = alpha))
            drawPath(face, palette.edge.copy(alpha = ISO_EDGE_ALPHA * alpha), style = Stroke(ISO_EDGE_PX))
        }
    }
    picture.parts.filter { it.footprint.id in picture.outlinedIds }.forEach { part ->
        val faces = projectFaces(partBox(picture.scene, part.footprint))
        listOf(faces.top, faces.west, faces.south).forEach { drawPath(isoPolygon(picture.fit, it), palette.outline, style = Stroke(ISO_OUTLINE_PX)) }
    }
}

private val POPUP_BACKGROUND = Color(0xF7FFFFFF)
private val POPUP_INK = Color(0xFF14171A)
private val POPUP_SUBTLE_INK = Color(0xFF44505A)
private const val POPUP_GAP_PX = 12
private const val POPUP_EDGE_PX = 4

internal fun popupOrigin(anchorX: Float, anchorY: Float, width: Int, height: Int, boundsWidth: Int): Pair<Int, Int> {
    val x = (anchorX - width / 2f).roundToInt().coerceAtMost(boundsWidth - width - POPUP_EDGE_PX).coerceAtLeast(POPUP_EDGE_PX)
    val above = anchorY.roundToInt() - POPUP_GAP_PX - height
    val y = if (above < POPUP_EDGE_PX) anchorY.roundToInt() + POPUP_GAP_PX else above
    return x to y
}

@Composable
internal fun PartPopup(tag: String, lines: List<String>, anchorX: Float, anchorY: Float) {
    Column(
        modifier = Modifier
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                val (x, y) = popupOrigin(anchorX, anchorY, placeable.width, placeable.height, constraints.maxWidth)
                layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(x, y) }
            }
            .testTag(tag)
            .background(POPUP_BACKGROUND, RoundedCornerShape(7.dp))
            .border(2.dp, SELECTED_OUTLINE, RoundedCornerShape(7.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp),
    ) {
        lines.forEachIndexed { index, line ->
            val isTitle = index == 0
            Text(
                line,
                color = if (isTitle) POPUP_INK else POPUP_SUBTLE_INK,
                fontSize = if (isTitle) 14.sp else 13.sp,
                fontWeight = if (isTitle) FontWeight.Bold else null,
            )
        }
    }
}

@Composable
internal fun ColourLegend(tag: String, entries: List<LegendEntry>, modifier: Modifier) {
    if (entries.isEmpty()) return
    Column(
        modifier = modifier.padding(8.dp).testTag(tag).background(POPUP_BACKGROUND, RoundedCornerShape(5.dp)).padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        entries.forEach { entry ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(modifier = Modifier.size(12.dp).background(Color(entry.colour)))
                Text(entry.label, color = POPUP_INK, fontSize = 12.sp)
            }
        }
    }
}
