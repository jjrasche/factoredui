package ai.factoredui.compose.renderer

import ai.factoredui.compose.layout.ERASE_BRUSH
import ai.factoredui.compose.layout.FlowView
import ai.factoredui.compose.layout.GroundPoint
import ai.factoredui.compose.layout.ScreenPoint
import ai.factoredui.compose.layout.TileBounds
import ai.factoredui.compose.layout.TileCell
import ai.factoredui.compose.layout.TileCoord
import ai.factoredui.compose.layout.TileInstance
import ai.factoredui.compose.layout.TileShape
import ai.factoredui.compose.layout.TileView
import ai.factoredui.compose.layout.afterGesture
import ai.factoredui.compose.layout.applyBrush
import ai.factoredui.compose.layout.areasOf
import ai.factoredui.compose.layout.countUses
import ai.factoredui.compose.layout.drawOrder
import ai.factoredui.compose.layout.fitFlowView
import ai.factoredui.compose.layout.pickTile
import ai.factoredui.compose.layout.project
import ai.factoredui.compose.layout.tileCorners
import ai.factoredui.compose.layout.tileSideMm
import ai.factoredui.compose.layout.tilemapScreenBounds
import ai.factoredui.compose.layout.unproject
import ai.factoredui.compose.scene.adaptRenderProps
import ai.factoredui.compose.scene.tileFootprintsOf
import ai.factoredui.compose.scene.tileInstancesOf
import ai.factoredui.compose.schema.ActionRef
import ai.factoredui.compose.schema.SpecNode
import ai.factoredui.compose.schema.SpecValue
import ai.factoredui.compose.schema.TilemapUse
import ai.factoredui.compose.schema.asTilemapProps
import ai.factoredui.compose.schema.assignGraphColors
import ai.factoredui.compose.schema.bindingPath
import ai.factoredui.compose.schema.resolveTileArea
import ai.factoredui.compose.schema.resolveTilemapCells
import ai.factoredui.compose.schema.resolveTilemapImages
import ai.factoredui.compose.schema.resolveTilemapShape
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.ceil
import kotlin.math.exp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal const val TILEMAP_TILE_WIDTH_DP = 64f
internal const val TILEMAP_HEADROOM = 0.95f
private const val FIT_MARGIN_PX = 12f
private const val MAX_FIT_SCALE = 2f
private const val WHEEL_ZOOM_RATE = 0.12f
private const val INSTANCE_HIT_PAD_PIXELS = 3f
private val GROUND_LIGHT = Color(0xFFCFE0A8)
private val GROUND_LIGHT_ALT = Color(0xFFC3D79B)
private val GROUND_NIGHT = Color(0xFF2B3A2E)
private val GROUND_NIGHT_ALT = Color(0xFF324537)
private val GRID_LINE = Color(0x33000000)
private val GRID_LINE_NIGHT = Color(0x40FFFFFF)
private val OUTLINE_NIGHT = Color(0x73FFFFFF)
private val HOVER_LINE = Color(0xFFFFFFFF)
private val PALETTE_TEXT = TextStyle(fontSize = 13.sp, lineHeight = 17.sp)
private const val SELECTED_CHIP_ALPHA = 0.16f
private val FALLBACK_USE_COLOR = Color(0xFF9AA3B2)

internal class TileStyle(val use: TilemapUse, val color: Color)

internal class TileLook(val dark: Boolean, val ground: Color, val groundAlt: Color) {
    val gridLine = if (dark) GRID_LINE_NIGHT else GRID_LINE
    val outline: Color? = if (dark) OUTLINE_NIGHT else null
    val leftSide = if (dark) 0.10f else 0.28f
    val rightSide = if (dark) 0.18f else 0.42f
    val brickStep = if (dark) 0.05f else BRICK_SHADE_STEP
    val topLift = if (dark) 0.30f else 0.18f
    val useLift = if (dark) 0.12f else 0f
    val treeLift = if (dark) 0.28f else 0f
    val waterLift = if (dark) 0.25f else 0f

    fun lifted(color: Color, amount: Float): Color = if (amount == 0f) color else lerp(color, Color.White, amount)

    fun rail(color: Color): Color = if (dark) lerp(color, Color.White, 0.45f) else shade(color, 0.35f)
}

internal class TilemapSpace(val view: TileView, val tileWidthPx: Float, val bounds: TileBounds) {
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
    val shape = resolveTilemapShape(resolvedProps["shape"])
    val view = resolveTilemapView(resolvedProps["view"])
    val tileArea = resolveTileArea(resolvedProps["tile_area"])
    val uses = resolveTilemapUses(resolvedProps["uses"])
    val colours = assignGraphColors(uses.map { it.id }, uses.mapNotNull { use -> use.color?.let { use.id to it } }.toMap())
    val dark = LocalSpecTheme.current.isDark
    val groundHex = resolvedProps["ground"] as? String
    val groundAltHex = resolvedProps["ground_alt"] as? String
    val look = remember(dark, groundHex, groundAltHex) {
        TileLook(
            dark = dark,
            ground = colorOf(groundHex, if (dark) GROUND_NIGHT else GROUND_LIGHT),
            groundAlt = colorOf(groundAltHex, if (dark) GROUND_NIGHT_ALT else GROUND_LIGHT_ALT),
        )
    }
    val styles = remember(uses, look) {
        uses.associate { it.id to TileStyle(it, look.lifted(colorOf(colours[it.id], FALLBACK_USE_COLOR), look.useLift)) }
    }
    val cellsPath = node.props["cells"]?.bindingPath()
    val brushPath = node.props["selected_use"]?.bindingPath()
    val countsPath = node.props["counts"]?.bindingPath()
    val areasPath = node.props["areas"]?.bindingPath()
    val brushLabelPath = node.props["brush_label"]?.bindingPath()
    var localCells by remember { mutableStateOf(resolveTilemapCells(resolvedProps["cells"])) }
    var localBrush by remember { mutableStateOf(uses.firstOrNull()?.id) }
    var hovered by remember { mutableStateOf<TileCoord?>(null) }
    val cells = if (cellsPath != null) resolveTilemapCells(resolvedProps["cells"]) else localCells
    val brush = if (brushPath != null) resolvedProps["selected_use"] as? String else localBrush
    val sceneView = remember(resolvedProps, cells) { adaptRenderProps(resolvedProps + ("cells" to cells.map(::cellRecord))).scene }
    val cols = sceneView.cols
    val rows = sceneView.rows
    val density = LocalDensity.current.density
    val space = remember(shape, view, cols, rows, density) {
        val tileWidthPx = TILEMAP_TILE_WIDTH_DP * density
        TilemapSpace(view, tileWidthPx, tilemapScreenBounds(shape, view, cols, rows, tileWidthPx))
    }
    val sceneFootprints = remember(sceneView) { tileFootprintsOf(sceneView) }
    val imageAliases = resolveTilemapImages(resolvedProps["images"])
    val imageSources = uses.mapNotNull { use -> use.image?.let { use.id to (imageAliases[it] ?: it) } }.toMap()
    val images = rememberLoadedTileImages(imageSources)
    val instances = remember(sceneView) { tileInstancesOf(sceneView) }
    val sideMm = tileSideMm(tileArea)
    val drawables = remember(shape, sceneFootprints, instances, sideMm, rows) {
        drawOrder(shape, sceneFootprints, instances, sideMm, rows)
    }
    val scope = rememberCoroutineScope()
    val animated = resolvedProps["animate"] == true
    val phase = rememberTilemapPhase(animated)
    val groundLayer = rememberGraphicsLayer()
    val sceneLayer = rememberGraphicsLayer()
    val recordedGround = remember { RecordedKey() }
    val recordedScene = remember { RecordedKey() }
    val scenePhase = if (animated) phase else 0
    val scene = remember(shape, space, cols, rows, drawables, styles, look, scenePhase, density, sideMm, images) {
        TilemapScene(shape, space, cols, rows, drawables, styles, look, scenePhase, density, sideMm, images)
    }
    val terrain = rememberTerrainPass(resolvedProps, shape, space, cols, rows, sideMm, look)

    LaunchedEffect(sceneFootprints, tileArea) {
        val counts = uses.associate { it.id to 0 } + countUses(emptyList(), sceneFootprints)
        if (countsPath != null) context.setBinding(countsPath, counts)
        if (areasPath != null) context.setBinding(areasPath, areasOf(counts, tileArea).mapValues { wholeWhenIntegral(it.value) })
    }

    LaunchedEffect(brush, uses) {
        if (brushLabelPath != null) context.setBinding(brushLabelPath, uses.firstOrNull { it.id == brush }?.label ?: brush.orEmpty())
    }

    fun report(tile: TileCoord) {
        val onTileTapped = props.onTileTapped ?: return
        scope.launch { context.dispatch(node.id, tileTappedAction(onTileTapped, tile, brush)) }
    }

    fun reportInstance(instance: TileInstance) {
        val onInstanceTapped = props.onInstanceTapped ?: return
        scope.launch { context.dispatch(node.id, instanceTappedAction(onInstanceTapped, instance)) }
    }

    fun place(tile: TileCoord) {
        if (resolvedProps["controlled"] == true) return report(tile)
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

    val palettePlace = palettePlaceOf(resolvedProps["palette"])
    val palette: @Composable () -> Unit = {
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            for (use in uses) BrushChip("${node.id}:brush:${use.id}", use.label, styles.getValue(use.id).color, use.id == brush) { select(use.id) }
            BrushChip("${node.id}:brush:$ERASE_BRUSH", ERASE_BRUSH, Color.Transparent, brush == ERASE_BRUSH) { select(ERASE_BRUSH) }
        }
    }

    Column(modifier = Modifier.fillMaxSize().nodeTag(node.id)) {
        if (palettePlace == PalettePlace.TOP) palette()
        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth().clipToBounds().nodeTag("${node.id}:map")) {
            val viewWidthPx = constraints.maxWidth.toFloat()
            val viewHeightPx = constraints.maxHeight.toFloat()
            val fit = remember(space, viewWidthPx, viewHeightPx) {
                fitFlowView(space.contentWidth, space.contentHeight, viewWidthPx, viewHeightPx, maxScale = MAX_FIT_SCALE, margin = FIT_MARGIN_PX)
            }
            var gestureView by remember(space, viewWidthPx, viewHeightPx) { mutableStateOf<FlowView?>(null) }
            val current = gestureView ?: fit
            val latest by rememberUpdatedState(current)

            fun contentAt(screen: Offset): Offset = Offset((screen.x - latest.translateX) / latest.scale, (screen.y - latest.translateY) / latest.scale)

            fun groundAt(screen: Offset): GroundPoint = space.toGround(contentAt(screen))

            fun tileAt(screen: Offset): TileCoord? = pickTile(shape, cols, rows, groundAt(screen))

            fun instanceAt(screen: Offset): TileInstance? =
                if (props.onInstanceTapped == null) null else pickDrawnInstance(scene, contentAt(screen), INSTANCE_HIT_PAD_PIXELS / latest.scale)

            fun tapAt(screen: Offset) {
                val instance = instanceAt(screen)
                if (instance != null) reportInstance(instance) else tileAt(screen)?.let { place(it) }
            }

            Canvas(
                modifier = Modifier.fillMaxSize()
                    .pointerInput(scene, brush, props.onInstanceTapped) {
                        detectTapGestures(onTap = { offset -> tapAt(offset) })
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
                val contentSize = IntSize(ceil(space.contentWidth).toInt(), ceil(space.contentHeight).toInt())
                val withChecker = current.scale * space.tileWidthPx >= CHECKER_MIN_TILE_PIXELS
                if (!recordedGround.holds(shape, space, look, density, withChecker)) {
                    groundLayer.record(size = contentSize) { drawGround(shape, space, cols, rows, look, density, withChecker) }
                    recordedGround.remember(shape, space, look, density, withChecker)
                }
                if (!recordedScene.holds(scene)) {
                    sceneLayer.record(size = contentSize) { drawScene(scene) }
                    recordedScene.remember(scene)
                }
                recordTerrainLayer(terrain, space, cols, rows, current.scale, contentSize)
                withTransform({
                    translate(current.translateX, current.translateY)
                    scale(current.scale, current.scale, pivot = Offset.Zero)
                }) {
                    drawLayer(groundLayer)
                    drawTerrainLayer(terrain)
                    drawLayer(sceneLayer)
                    hovered?.let { drawPath(polygon(tileCorners(shape, it.col, it.row).map(space::toContent)), HOVER_LINE, style = Stroke(width = 2.5f * density)) }
                }
            }
            TerrainLegendCard(terrain, "${node.id}:terrain-legend", Modifier.align(Alignment.BottomStart))
        }
        if (palettePlace == PalettePlace.BOTTOM) palette()
    }
}

private fun wholeWhenIntegral(value: Double): Any = if (value % 1.0 == 0.0) value.toLong() else value

@Composable
private fun rememberTilemapPhase(animate: Boolean): Int {
    var phase by remember { mutableStateOf(0) }
    if (animate) LaunchedEffect(Unit) { while (true) { delay(PHASE_MILLIS); phase++ } }
    return phase
}

private fun cellRecord(cell: TileCell): Map<String, Any?> = mapOf("col" to cell.col, "row" to cell.row, "use" to cell.use)

private fun instanceTappedAction(action: String, instance: TileInstance) = ActionRef(
    action = action,
    params = mapOf(
        "id" to SpecValue.StringValue(instance.id),
        "use" to SpecValue.StringValue(instance.use),
        "x_mm" to SpecValue.NumberValue(instance.xMm),
        "y_mm" to SpecValue.NumberValue(instance.yMm),
    ),
)

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
    val chipShape = RoundedCornerShape(8.dp)
    Row(
        modifier = Modifier.nodeTag(tag)
            .background(if (isSelected) theme.ink.copy(alpha = SELECTED_CHIP_ALPHA) else Color.Transparent, chipShape)
            .border(if (isSelected) 2.dp else 1.dp, if (isSelected) theme.ink else theme.muted, chipShape)
            .clickable(onClick = onSelect)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier.size(24.dp, 16.dp)
                .background(swatch, RoundedCornerShape(3.dp))
                .border(1.dp, theme.muted, RoundedCornerShape(3.dp)),
        )
        Text(text = label, style = PALETTE_TEXT.copy(color = theme.ink, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal), maxLines = 1)
    }
}

internal enum class PalettePlace { TOP, BOTTOM, NONE }

internal fun palettePlaceOf(raw: Any?): PalettePlace = PalettePlace.entries.firstOrNull { it.name.equals(raw as? String, ignoreCase = true) } ?: PalettePlace.BOTTOM

