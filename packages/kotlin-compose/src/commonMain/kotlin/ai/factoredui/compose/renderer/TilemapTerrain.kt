package ai.factoredui.compose.renderer

import ai.factoredui.compose.layout.GroundPoint
import ai.factoredui.compose.layout.TileShape
import ai.factoredui.compose.terrain.ContourLine
import ai.factoredui.compose.terrain.SAMPLES_PER_TILE
import ai.factoredui.compose.terrain.TerrainCosts
import ai.factoredui.compose.terrain.TerrainGrid
import ai.factoredui.compose.terrain.TerrainLegend
import ai.factoredui.compose.terrain.TerrainMode
import ai.factoredui.compose.terrain.TerrainUnits
import ai.factoredui.compose.terrain.bucketScale
import ai.factoredui.compose.terrain.contourLabel
import ai.factoredui.compose.terrain.contourPolylines
import ai.factoredui.compose.terrain.isIndexContour
import ai.factoredui.compose.terrain.contourLabelPlacements
import ai.factoredui.compose.terrain.resolveContourIntervalMm
import ai.factoredui.compose.terrain.resolveContoursShown
import ai.factoredui.compose.terrain.resolveTerrain
import ai.factoredui.compose.terrain.resolveTerrainMode
import ai.factoredui.compose.terrain.resolveTerrainUnits
import ai.factoredui.compose.terrain.terrainCostsFor
import ai.factoredui.compose.terrain.terrainLegendFor
import ai.factoredui.compose.terrain.terrainRaster
import ai.factoredui.compose.terrain.viewScaleBucket
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp

private val HAIRLINE = Stroke(width = 0f)
private const val INDEX_CONTOUR_DP = 2.2f
private const val LABEL_SP = 10f
private const val LABEL_PAD_DP = 2f
private const val MIN_LABELLED_POINTS = 8
private const val LABEL_SPACING_TILES = 1.5f
private val CONTOUR_INK = Color(0xD93B2A1A)
private val CONTOUR_ON_NIGHT = Color(0xCCF2E6CF)
private val LABEL_HALO = Color(0xCCFFFFFF)
private val TERRAIN_GRID_LINE = Color(0x26000000)

internal class ContourLabel(val text: String, val at: Offset)

internal class TerrainContours(val thin: Path, val index: Path, val labels: List<ContourLabel>)

internal class TerrainPass(
    val layer: GraphicsLayer,
    val recorded: RecordedKey,
    val mode: TerrainMode,
    val image: ImageBitmap?,
    val contours: TerrainContours?,
    val legend: TerrainLegend?,
    val look: TileLook,
    val textMeasurer: TextMeasurer,
    val density: Float,
    val costs: TerrainCosts,
) {
    val isActive: Boolean get() = image != null || contours != null
    val contourColour: Color get() = if (look.dark && mode == TerrainMode.OFF) CONTOUR_ON_NIGHT else CONTOUR_INK
}

@Composable
internal fun rememberTerrainPass(resolvedProps: Map<String, Any?>, shape: TileShape, space: TilemapSpace, cols: Int, rows: Int, sideMm: Double, look: TileLook): TerrainPass {
    val raw = resolvedProps["terrain"]
    val field = remember(raw, shape, cols, rows) {
        resolveTerrain(raw)?.takeIf { shape == TileShape.SQUARE && it.heights.cols == cols && it.heights.rows == rows }
    }
    val mode = if (field == null) TerrainMode.OFF else resolveTerrainMode(resolvedProps["terrain_mode"])
    val isContoursShown = field != null && resolveContoursShown(resolvedProps["contours"])
    val intervalMm = resolveContourIntervalMm(resolvedProps["contour_interval_mm"])
    val units = resolveTerrainUnits(resolvedProps["terrain_units"])
    val heightsPrint = field?.heights?.fingerprint
    val cutFillPrint = field?.cutFill?.fingerprint
    val image = remember(heightsPrint, cutFillPrint, mode, sideMm) {
        field?.let { terrainRaster(mode, it, sideMm) }?.let { imageBitmapOfArgb(it.width, it.height, it.values) }
    }
    val contours = remember(heightsPrint, isContoursShown, intervalMm, units, space) {
        if (field != null && isContoursShown) terrainContoursOf(field.heights, intervalMm, units, space) else null
    }
    val legend = remember(heightsPrint, cutFillPrint, mode, isContoursShown, intervalMm, sideMm) {
        field?.let { terrainLegendFor(mode, it, isContoursShown, intervalMm, sideMm) }
    }
    val costs = terrainCostsFor(LocalDeviceProfile.current)
    return TerrainPass(rememberGraphicsLayer(), remember { RecordedKey() }, mode, image, contours, legend, look, rememberTextMeasurer(), LocalDensity.current.density, costs)
}

internal fun terrainContoursOf(grid: TerrainGrid, intervalMm: Int, units: TerrainUnits, space: TilemapSpace): TerrainContours {
    val lines = contourPolylines(grid, intervalMm)
    val (index, thin) = lines.partition { isIndexContour(it.levelMm, intervalMm) }
    val labels = contourLabelPlacements(lines, intervalMm, MIN_LABELLED_POINTS, LABEL_SPACING_TILES)
        .map { ContourLabel(contourLabel(it.line.levelMm, units), space.toContent(it.anchor)) }
    return TerrainContours(pathOf(thin, space), pathOf(index, space), labels)
}

private fun pathOf(lines: List<ContourLine>, space: TilemapSpace): Path = Path().apply {
    for (line in lines) {
        val points = line.points.map(space::toContent)
        moveTo(points.first().x, points.first().y)
        for (index in 1 until points.size) lineTo(points[index].x, points[index].y)
        if (line.isClosed) close()
    }
}

internal fun DrawScope.recordTerrainLayer(pass: TerrainPass, space: TilemapSpace, cols: Int, rows: Int, viewScale: Float, contentSize: IntSize) {
    if (!pass.isActive) return
    val bucket = viewScaleBucket(viewScale)
    if (pass.recorded.holds(pass.image, pass.contours, pass.look, pass.mode, space, bucket, pass.costs)) return
    pass.layer.record(size = contentSize) { drawTerrain(pass, space, cols, rows, bucketScale(bucket)) }
    pass.recorded.remember(pass.image, pass.contours, pass.look, pass.mode, space, bucket, pass.costs)
}

internal fun DrawScope.drawTerrainLayer(pass: TerrainPass) {
    if (pass.isActive) drawLayer(pass.layer)
}

private fun DrawScope.drawTerrain(pass: TerrainPass, space: TilemapSpace, cols: Int, rows: Int, viewScale: Float) {
    pass.image?.let { image ->
        drawTerrainImage(image, space)
        if (pass.costs.isGridShown(space.tileWidthPx * viewScale)) drawPath(gridLines(space, cols, rows), TERRAIN_GRID_LINE, style = HAIRLINE)
    }
    pass.contours?.let { drawContours(it, pass, viewScale, pass.costs.areThinContoursShown(space.tileWidthPx * viewScale)) }
}

private fun DrawScope.drawTerrainImage(image: ImageBitmap, space: TilemapSpace) {
    val origin = space.toContent(GroundPoint(0f, 0f))
    val east = space.toContent(GroundPoint(1f, 0f)) - origin
    val south = space.toContent(GroundPoint(0f, 1f)) - origin
    val sampleToContent = Matrix().apply {
        this[0, 0] = east.x / SAMPLES_PER_TILE
        this[0, 1] = east.y / SAMPLES_PER_TILE
        this[1, 0] = south.x / SAMPLES_PER_TILE
        this[1, 1] = south.y / SAMPLES_PER_TILE
        this[3, 0] = origin.x
        this[3, 1] = origin.y
    }
    val size = IntSize(image.width, image.height)
    withTransform({ transform(sampleToContent) }) {
        drawImage(image, IntOffset.Zero, size, IntOffset.Zero, size, filterQuality = FilterQuality.Low)
    }
}

private fun DrawScope.drawContours(contours: TerrainContours, pass: TerrainPass, viewScale: Float, isThinShown: Boolean) {
    val colour = pass.contourColour
    if (isThinShown) drawPath(contours.thin, colour, style = HAIRLINE)
    drawPath(contours.index, colour, style = Stroke(width = INDEX_CONTOUR_DP * pass.density / viewScale, cap = StrokeCap.Round, join = StrokeJoin.Round))
    val style = TextStyle(fontSize = (LABEL_SP / viewScale).sp, color = CONTOUR_INK)
    val pad = LABEL_PAD_DP * pass.density / viewScale
    for (label in contours.labels) {
        val measured = pass.textMeasurer.measure(label.text, style)
        val topLeft = Offset(label.at.x - measured.size.width / 2f, label.at.y - measured.size.height / 2f)
        drawRect(LABEL_HALO, topLeft - Offset(pad, 0f), Size(measured.size.width + 2 * pad, measured.size.height.toFloat()))
        drawText(measured, topLeft = topLeft)
    }
}
