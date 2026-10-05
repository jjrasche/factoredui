package ai.factoredui.compose.renderer

import ai.factoredui.compose.layout.ERASE_BRUSH
import ai.factoredui.compose.layout.FlowView
import ai.factoredui.compose.layout.GroundPoint
import ai.factoredui.compose.layout.ScreenPoint
import ai.factoredui.compose.layout.TileBounds
import ai.factoredui.compose.layout.TileCell
import ai.factoredui.compose.layout.TileCoord
import ai.factoredui.compose.layout.TileShape
import ai.factoredui.compose.layout.TileView
import ai.factoredui.compose.layout.afterGesture
import ai.factoredui.compose.layout.applyBrush
import ai.factoredui.compose.layout.areasOf
import ai.factoredui.compose.layout.countUses
import ai.factoredui.compose.layout.fitFlowView
import ai.factoredui.compose.layout.pickTile
import ai.factoredui.compose.layout.project
import ai.factoredui.compose.layout.tileCenter
import ai.factoredui.compose.layout.tileCorners
import ai.factoredui.compose.layout.tileDrawOrder
import ai.factoredui.compose.layout.tilemapScreenBounds
import ai.factoredui.compose.layout.unproject
import ai.factoredui.compose.schema.ActionRef
import ai.factoredui.compose.schema.SpecNode
import ai.factoredui.compose.schema.SpecValue
import ai.factoredui.compose.schema.TileSprite
import ai.factoredui.compose.schema.TilemapUse
import ai.factoredui.compose.schema.asTilemapProps
import ai.factoredui.compose.schema.assignGraphColors
import ai.factoredui.compose.schema.bindingPath
import ai.factoredui.compose.schema.resolveTileArea
import ai.factoredui.compose.schema.resolveTilemapCells
import ai.factoredui.compose.schema.resolveTilemapShape
import ai.factoredui.compose.schema.resolveTilemapSize
import ai.factoredui.compose.schema.resolveTilemapUses
import ai.factoredui.compose.schema.resolveTilemapView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.exp
import kotlinx.coroutines.launch

internal const val TILEMAP_TILE_WIDTH_DP = 64f
internal const val TILEMAP_HEADROOM = 0.95f
private const val DEFAULT_COLS = 10
private const val DEFAULT_ROWS = 10
private const val FIT_MARGIN_PX = 12f
private const val MAX_FIT_SCALE = 2f
private const val WHEEL_ZOOM_RATE = 0.12f
private const val BLOCK_UNIT_FACTOR = 0.5f
private const val ARCH_DEFAULT_HEIGHT = 0.55f
private const val BLOCK_DEFAULT_HEIGHT = 1f
private const val FENCE_POST_FACTOR = 0.22f
private val GROUND_LIGHT = Color(0xFFCFE0A8)
private val GROUND_DARK = Color(0xFFC3D79B)
private val GRID_LINE = Color(0x33000000)
private val HOVER_LINE = Color(0xFFFFFFFF)
private val TRUNK = Color(0xFF6B4A2B)
private val PALETTE_TEXT = TextStyle(fontSize = 12.sp, lineHeight = 16.sp)
private val FALLBACK_USE_COLOR = Color(0xFF9AA3B2)

private class TileStyle(val use: TilemapUse, val color: Color)

private class TilemapSpace(val view: TileView, val tileWidthPx: Float, val bounds: TileBounds) {
    val originX = -bounds.minX
    val originY = -bounds.minY + TILEMAP_HEADROOM * tileWidthPx
    val contentWidth = bounds.maxX - bounds.minX
    val contentHeight = bounds.maxY - bounds.minY + TILEMAP_HEADROOM * tileWidthPx

    fun toContent(ground: GroundPoint): Offset {
        val screen = project(view, ground, tileWidthPx)
        return Offset(screen.x + originX, screen.y + originY)
    }

    fun toGround(content: Offset): GroundPoint = unproject(view, ScreenPoint(content.x - originX, content.y - originY), tileWidthPx)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RenderTilemap(node: SpecNode, resolvedProps: Map<String, Any?>, context: RenderContext) {
    val props = node.props.asTilemapProps()
    val cols = resolveTilemapSize(resolvedProps["cols"], DEFAULT_COLS)
    val rows = resolveTilemapSize(resolvedProps["rows"], DEFAULT_ROWS)
    val shape = resolveTilemapShape(resolvedProps["shape"])
    val view = resolveTilemapView(resolvedProps["view"])
    val tileArea = resolveTileArea(resolvedProps["tile_area"])
    val uses = resolveTilemapUses(resolvedProps["uses"])
    val colours = assignGraphColors(uses.map { it.id }, uses.mapNotNull { use -> use.color?.let { use.id to it } }.toMap())
    val styles = remember(uses) { uses.associate { it.id to TileStyle(it, colorOf(colours[it.id], FALLBACK_USE_COLOR)) } }
    val ground = colorOf(resolvedProps["ground"] as? String, GROUND_LIGHT)
    val groundAlt = colorOf(resolvedProps["ground_alt"] as? String, GROUND_DARK)
    val cellsPath = node.props["cells"]?.bindingPath()
    val brushPath = node.props["selected_use"]?.bindingPath()
    val countsPath = node.props["counts"]?.bindingPath()
    val areasPath = node.props["areas"]?.bindingPath()
    var localCells by remember { mutableStateOf(resolveTilemapCells(resolvedProps["cells"])) }
    var localBrush by remember { mutableStateOf(uses.firstOrNull()?.id) }
    var hovered by remember { mutableStateOf<TileCoord?>(null) }
    val cells = if (cellsPath != null) resolveTilemapCells(resolvedProps["cells"]) else localCells
    val brush = if (brushPath != null) resolvedProps["selected_use"] as? String else localBrush
    val density = LocalDensity.current.density
    val space = remember(shape, view, cols, rows, density) {
        val tileWidthPx = TILEMAP_TILE_WIDTH_DP * density
        TilemapSpace(view, tileWidthPx, tilemapScreenBounds(shape, view, cols, rows, tileWidthPx))
    }
    val order = remember(shape, cols, rows) { tileDrawOrder(shape, cols, rows) }
    val byTile = remember(cells) { cells.associateBy { TileCoord(it.col, it.row) } }
    val scope = rememberCoroutineScope()

    LaunchedEffect(cells, tileArea) {
        val counts = uses.associate { it.id to 0 } + countUses(cells)
        if (countsPath != null) context.setBinding(countsPath, counts)
        if (areasPath != null) context.setBinding(areasPath, areasOf(counts, tileArea).mapValues { wholeWhenIntegral(it.value) })
    }

    fun place(tile: TileCoord) {
        val next = applyBrush(cells, cols, rows, tile.col, tile.row, brush)
        if (cellsPath != null) context.setBinding(cellsPath, next.map { cellRecord(it) }) else localCells = next
        val onTileTapped = props.onTileTapped
        if (onTileTapped != null) {
            val placed = next.firstOrNull { it.col == tile.col && it.row == tile.row }?.use
            scope.launch { context.dispatch(node.id, tileTappedAction(onTileTapped, tile, placed)) }
        }
    }

    fun select(use: String) {
        if (brushPath != null) context.setBinding(brushPath, use) else localBrush = use
    }

    Column(modifier = Modifier.fillMaxSize().nodeTag(node.id)) {
        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth().clipToBounds().nodeTag("${node.id}:map")) {
            val viewWidthPx = constraints.maxWidth.toFloat()
            val viewHeightPx = constraints.maxHeight.toFloat()
            val fit = remember(space, viewWidthPx, viewHeightPx) {
                fitFlowView(space.contentWidth, space.contentHeight, viewWidthPx, viewHeightPx, maxScale = MAX_FIT_SCALE, margin = FIT_MARGIN_PX)
            }
            var gestureView by remember(space, viewWidthPx, viewHeightPx) { mutableStateOf<FlowView?>(null) }
            val current = gestureView ?: fit
            val latest by rememberUpdatedState(current)

            fun tileAt(screen: Offset): TileCoord? {
                val content = Offset((screen.x - latest.translateX) / latest.scale, (screen.y - latest.translateY) / latest.scale)
                return pickTile(shape, cols, rows, space.toGround(content))
            }

            Canvas(
                modifier = Modifier.fillMaxSize()
                    .pointerInput(shape, view, cols, rows, cells, brush) {
                        detectTapGestures(onTap = { offset -> tileAt(offset)?.let { place(it) } })
                    }
                    .pointerInput(fit) {
                        detectTransformGestures { centroid, pan, zoom, _ ->
                            gestureView = (gestureView ?: fit).afterGesture(centroid.x, centroid.y, pan.x, pan.y, zoom)
                        }
                    }
                    .pointerInput(shape, view, cols, rows, fit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.first()
                                when (event.type) {
                                    PointerEventType.Move -> hovered = tileAt(change.position)
                                    PointerEventType.Exit -> hovered = null
                                    PointerEventType.Scroll -> {
                                        val zoom = exp(-change.scrollDelta.y * WHEEL_ZOOM_RATE)
                                        gestureView = (gestureView ?: fit).afterGesture(change.position.x, change.position.y, 0f, 0f, zoom)
                                        change.consume()
                                    }
                                }
                            }
                        }
                    },
            ) {
                withTransform({
                    translate(current.translateX, current.translateY)
                    scale(current.scale, current.scale, pivot = Offset.Zero)
                }) {
                    drawTilemap(shape, space, order, byTile, styles, ground, groundAlt, hovered, density)
                }
            }
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            for (use in uses) BrushChip("${node.id}:brush:${use.id}", use.label, styles.getValue(use.id).color, use.id == brush) { select(use.id) }
            BrushChip("${node.id}:brush:$ERASE_BRUSH", ERASE_BRUSH, Color.Transparent, brush == ERASE_BRUSH) { select(ERASE_BRUSH) }
        }
    }
}

private fun wholeWhenIntegral(value: Double): Any = if (value % 1.0 == 0.0) value.toLong() else value

private fun cellRecord(cell: TileCell): Map<String, Any?> = mapOf("col" to cell.col, "row" to cell.row, "use" to cell.use)

private fun tileTappedAction(action: String, tile: TileCoord, use: String?) = ActionRef(
    action = action,
    params = mapOf(
        "col" to SpecValue.NumberValue(tile.col.toDouble()),
        "row" to SpecValue.NumberValue(tile.row.toDouble()),
        "use" to (use?.let { SpecValue.StringValue(it) } ?: SpecValue.NullValue),
    ),
)

private fun colorOf(hex: String?, fallback: Color): Color = parseGeomapColor(hex)?.let { Color(it) } ?: fallback

@Composable
private fun BrushChip(tag: String, label: String, swatch: Color, isSelected: Boolean, onSelect: () -> Unit) {
    val theme = LocalSpecTheme.current
    Row(
        modifier = Modifier.nodeTag(tag).clickable(onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            modifier = Modifier.size(22.dp, 14.dp)
                .background(swatch, RoundedCornerShape(3.dp))
                .border(if (isSelected) 2.5.dp else 1.dp, if (isSelected) theme.ink else theme.muted, RoundedCornerShape(3.dp)),
        )
        Text(text = label, style = PALETTE_TEXT.copy(color = theme.ink), maxLines = 1)
    }
}

private fun DrawScope.drawTilemap(
    shape: TileShape,
    space: TilemapSpace,
    order: List<TileCoord>,
    byTile: Map<TileCoord, TileCell>,
    styles: Map<String, TileStyle>,
    ground: Color,
    groundAlt: Color,
    hovered: TileCoord?,
    density: Float,
) {
    for (tile in order) {
        val corners = tileCorners(shape, tile.col, tile.row).map { space.toContent(it) }
        val centre = space.toContent(tileCenter(shape, tile.col, tile.row))
        val base = if ((tile.col + tile.row) % 2 == 0) ground else groundAlt
        val style = byTile[tile]?.let { styles[it.use] }
        drawTileSurface(corners, style, base, density)
        if (style != null) drawSprite(style, corners, centre, space.tileWidthPx, density)
        if (tile == hovered) drawPath(polygon(corners), HOVER_LINE, style = Stroke(width = 2.5f * density))
    }
}

private fun polygon(points: List<Offset>): Path = Path().apply {
    moveTo(points.first().x, points.first().y)
    points.drop(1).forEach { lineTo(it.x, it.y) }
    close()
}

private fun DrawScope.drawTileSurface(corners: List<Offset>, style: TileStyle?, base: Color, density: Float) {
    val fill = when (style?.use?.sprite) {
        TileSprite.FLAT -> style.color
        TileSprite.FENCE -> lerp(base, style.color, FENCE_GROUND_BLEND)
        else -> base
    }
    drawPath(polygon(corners), fill, style = Fill)
    drawPath(polygon(corners), GRID_LINE, style = Stroke(width = density))
}

private const val FENCE_GROUND_BLEND = 0.45f

private fun DrawScope.drawSprite(style: TileStyle, corners: List<Offset>, centre: Offset, tileWidth: Float, density: Float) {
    when (style.use.sprite) {
        TileSprite.FLAT -> Unit
        TileSprite.BLOCK -> drawPrism(corners, (style.use.height ?: BLOCK_DEFAULT_HEIGHT) * tileWidth * BLOCK_UNIT_FACTOR, style.color)
        TileSprite.ARCH -> drawArch(corners, (style.use.height ?: ARCH_DEFAULT_HEIGHT) * tileWidth * BLOCK_UNIT_FACTOR, style.color, density)
        TileSprite.TREE -> drawTree(centre, tileWidth, style.color)
        TileSprite.WATER -> drawWater(corners, centre, style.color, density)
        TileSprite.FENCE -> drawFence(corners, tileWidth * FENCE_POST_FACTOR, style.color, density)
    }
}

private fun shade(color: Color, towardBlack: Float): Color = lerp(color, Color.Black, towardBlack)

private fun DrawScope.drawPrism(corners: List<Offset>, lift: Float, color: Color) {
    val centreX = corners.map { it.x }.average().toFloat()
    val centreY = corners.map { it.y }.average().toFloat()
    corners.indices.forEach { index ->
        val a = corners[index]
        val b = corners[(index + 1) % corners.size]
        if ((a.y + b.y) / 2f > centreY + 0.5f) {
            val darker = if ((a.x + b.x) / 2f < centreX) 0.28f else 0.42f
            val face = Path().apply {
                moveTo(a.x, a.y)
                lineTo(b.x, b.y)
                lineTo(b.x, b.y - lift)
                lineTo(a.x, a.y - lift)
                close()
            }
            drawPath(face, shade(color, darker), style = Fill)
        }
    }
    drawPath(polygon(corners.map { Offset(it.x, it.y - lift) }), lerp(color, Color.White, 0.18f), style = Fill)
}

private fun DrawScope.drawArch(corners: List<Offset>, lift: Float, color: Color, density: Float) {
    drawPrism(corners, lift, color)
    val top = corners.map { Offset(it.x, it.y - lift) }
    val ridgeStart = Offset((top[0].x + top[3].x) / 2f, (top[0].y + top[3].y) / 2f)
    val ridgeEnd = Offset((top[1].x + top[2].x) / 2f, (top[1].y + top[2].y) / 2f)
    drawLine(Color.White.copy(alpha = 0.7f), ridgeStart, ridgeEnd, strokeWidth = 2f * density)
}

private fun DrawScope.drawTree(centre: Offset, tileWidth: Float, color: Color) {
    drawRect(TRUNK, topLeft = Offset(centre.x - 0.03f * tileWidth, centre.y - 0.16f * tileWidth), size = androidx.compose.ui.geometry.Size(0.06f * tileWidth, 0.2f * tileWidth))
    val tiers = listOf(
        Triple(0.40f, 0.18f, 0.12f),
        Triple(0.58f, 0.14f, 0.28f),
    )
    tiers.forEachIndexed { index, (apexRise, halfWidth, baseRise) ->
        val tier = Path().apply {
            moveTo(centre.x, centre.y - apexRise * tileWidth - 0.2f * tileWidth)
            lineTo(centre.x + halfWidth * tileWidth, centre.y - baseRise * tileWidth)
            lineTo(centre.x - halfWidth * tileWidth, centre.y - baseRise * tileWidth)
            close()
        }
        drawPath(tier, shade(color, if (index == 0) 0.2f else 0f), style = Fill)
    }
}

private fun DrawScope.drawWater(corners: List<Offset>, centre: Offset, color: Color, density: Float) {
    val inset = corners.map { Offset(centre.x + (it.x - centre.x) * 0.78f, centre.y + (it.y - centre.y) * 0.78f) }
    drawPath(polygon(inset), color, style = Fill)
    drawPath(polygon(inset), shade(color, 0.25f), style = Stroke(width = 1.5f * density))
    drawLine(Color.White.copy(alpha = 0.6f), Offset(centre.x - 0.12f * (inset[1].x - inset[0].x), centre.y), Offset(centre.x + 0.1f * (inset[1].x - inset[0].x), centre.y), strokeWidth = 2f * density)
}

private fun DrawScope.drawFence(corners: List<Offset>, postHeight: Float, color: Color, density: Float) {
    val rail = shade(color, 0.35f)
    val raised = corners.map { Offset(it.x, it.y - postHeight) }
    corners.zip(raised).forEach { (foot, top) -> drawLine(rail, foot, top, strokeWidth = 2.5f * density) }
    drawPath(polygon(raised), rail, style = Stroke(width = 1.8f * density))
}
