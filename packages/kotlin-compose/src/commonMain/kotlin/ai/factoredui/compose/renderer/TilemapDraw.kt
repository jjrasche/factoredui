package ai.factoredui.compose.renderer

import ai.factoredui.compose.layout.FootprintDrawable
import ai.factoredui.compose.layout.GroundPoint
import ai.factoredui.compose.layout.InstanceDrawable
import ai.factoredui.compose.layout.TileCoord
import ai.factoredui.compose.layout.TileDrawable
import ai.factoredui.compose.layout.TileFootprint
import ai.factoredui.compose.layout.TileShape
import ai.factoredui.compose.layout.TileView
import ai.factoredui.compose.layout.footprintCorners
import ai.factoredui.compose.layout.footprintGroundCentre
import ai.factoredui.compose.layout.instanceRadiusTiles
import ai.factoredui.compose.layout.tileCenter
import ai.factoredui.compose.layout.tileCorners
import ai.factoredui.compose.schema.TileSprite
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import kotlin.math.max

internal const val BLOCK_UNIT_FACTOR = 0.5f
internal const val ARCH_DEFAULT_HEIGHT = 0.55f
internal const val BLOCK_DEFAULT_HEIGHT = 1f
internal const val FENCE_POST_FACTOR = 0.22f
internal val TRUNK = Color(0xFF6B4A2B)

private const val TREE_REFERENCE_WIDTH_PIXELS = 7f
private const val ISO_CROWN_WIDTH_PER_RADIUS = 1.4142135f
private const val TOP_CROWN_WIDTH_PER_RADIUS = 2f
private const val MIN_CROWN_PIXEL_FRACTION = 0.5f
private const val INSTANCE_FOOTPRINT_RADIUS_TILES = 0.25f

internal class TilemapScene(
    val shape: TileShape,
    val space: TilemapSpace,
    val cols: Int,
    val rows: Int,
    val drawables: List<TileDrawable>,
    val styles: Map<String, TileStyle>,
    val look: TileLook,
    val phase: Int,
    val density: Float,
    val sideMm: Double,
)

internal class RecordedKey {
    private var parts: List<Any?> = emptyList()

    fun holds(vararg next: Any?): Boolean = parts.size == next.size && parts.indices.all { sameThing(parts[it], next[it]) }

    fun remember(vararg next: Any?) {
        parts = next.toList()
    }

    private fun sameThing(a: Any?, b: Any?): Boolean = a === b || (a is Number && a == b)
}

internal fun DrawScope.drawScene(scene: TilemapScene) {
    for (drawable in scene.drawables) {
        when (drawable) {
            is FootprintDrawable -> drawFootprint(drawable.footprint, scene)
            is InstanceDrawable -> drawInstance(drawable, scene)
        }
    }
}

private fun DrawScope.drawFootprint(footprint: TileFootprint, scene: TilemapScene) {
    val style = scene.styles[footprint.use] ?: return
    val space = scene.space
    val single = footprint.width == 1 && footprint.height == 1
    val ground = if (single) tileCorners(scene.shape, footprint.col, footprint.row) else footprintCorners(footprint)
    val corners = ground.map { space.toContent(it) }
    val centre = space.toContent(footprintGroundCentre(scene.shape, footprint))
    val base = if ((footprint.col + footprint.row) % 2 == 0) scene.look.ground else scene.look.groundAlt
    drawTileSurface(corners, style, base, scene.look, scene.density)
    val extent = max(footprint.width, footprint.height)
    drawStanding(style, TileCoord(footprint.col, footprint.row), corners, centre, space.tileWidthPx, extent, scene.look, scene.phase, scene.density)
    style.use.critter?.let { drawCritters(it, footprint, scene) }
}

private fun DrawScope.drawCritters(critter: String, footprint: TileFootprint, scene: TilemapScene) {
    val pixel = scene.space.tileWidthPx / PIXELS_PER_TILE_WIDTH
    for (row in footprint.row until footprint.row + footprint.height) {
        for (col in footprint.col until footprint.col + footprint.width) {
            if (!critterOnTile(col, row)) continue
            drawCritter(critter, TileCoord(col, row), scene.space.toContent(tileCenter(scene.shape, col, row)), pixel, scene.phase)
        }
    }
}

private fun DrawScope.drawInstance(drawable: InstanceDrawable, scene: TilemapScene) {
    val instance = drawable.instance
    val style = scene.styles[instance.use] ?: return
    val space = scene.space
    val centre = space.toContent(drawable.centre)
    val radiusTiles = instanceRadiusTiles(instance, scene.sideMm)
    val variantKey = TileCoord(instance.id.hashCode() and 0x7fff, (instance.id.hashCode() ushr 15) and 0x7fff)
    if (style.use.sprite == TileSprite.TREE) {
        val pixel = crownPixel(radiusTiles, space.tileWidthPx, space.view)
        drawPixelTree(variantKey, centre, pixel, scene.look.lifted(style.color, scene.look.treeLift), scene.look)
        return
    }
    val half = if (radiusTiles > 0f) radiusTiles else INSTANCE_FOOTPRINT_RADIUS_TILES
    val corners = squareAround(drawable.centre, half).map { space.toContent(it) }
    drawTileSurface(corners, style, scene.look.ground, scene.look, scene.density)
    drawStanding(style, variantKey, corners, centre, space.tileWidthPx, 1, scene.look, scene.phase, scene.density)
}

private fun squareAround(centre: GroundPoint, half: Float): List<GroundPoint> = listOf(
    GroundPoint(centre.x - half, centre.y - half),
    GroundPoint(centre.x + half, centre.y - half),
    GroundPoint(centre.x + half, centre.y + half),
    GroundPoint(centre.x - half, centre.y + half),
)

internal fun crownPixel(radiusTiles: Float, tileWidthPx: Float, view: TileView): Float {
    val standard = tileWidthPx / PIXELS_PER_TILE_WIDTH
    if (radiusTiles <= 0f) return standard
    val widthPerRadius = if (view == TileView.ISO) ISO_CROWN_WIDTH_PER_RADIUS else TOP_CROWN_WIDTH_PER_RADIUS
    return max(radiusTiles * widthPerRadius * tileWidthPx / TREE_REFERENCE_WIDTH_PIXELS, standard * MIN_CROWN_PIXEL_FRACTION)
}

internal fun polygon(points: List<Offset>): Path = Path().apply {
    moveTo(points.first().x, points.first().y)
    points.drop(1).forEach { lineTo(it.x, it.y) }
    close()
}

private fun DrawScope.drawTileSurface(corners: List<Offset>, style: TileStyle, base: Color, look: TileLook, density: Float) {
    val fill = when (style.use.sprite) {
        TileSprite.FLAT -> style.color
        TileSprite.FENCE -> lerp(base, style.color, FENCE_GROUND_BLEND)
        else -> return
    }
    drawPath(polygon(corners), fill, style = Fill)
    drawPath(polygon(corners), look.gridLine, style = Stroke(width = density))
}

internal const val FENCE_GROUND_BLEND = 0.45f
internal const val BRICK_COURSE_PIXELS = 3
internal const val BRICK_SHADE_STEP = 0.1f
internal const val TREE_LIGHT_MIX = 0.35f
internal const val TREE_DARK_MIX = 0.3f
internal const val TREE_OUTLINE_MIX = 0.6f
internal const val TREE_FOOT_PIXELS = 1f
internal const val CRITTER_FOOT_PIXELS = 2f
internal const val SHIMMER_ALPHA = 0.7f
internal const val PHASE_MILLIS = 180L

private fun DrawScope.drawStanding(style: TileStyle, tile: TileCoord, corners: List<Offset>, centre: Offset, tileWidth: Float, extent: Int, look: TileLook, phase: Int, density: Float) {
    val pixel = tileWidth / PIXELS_PER_TILE_WIDTH
    when (style.use.sprite) {
        TileSprite.FLAT -> Unit
        TileSprite.BLOCK -> drawPrism(corners, (style.use.height ?: BLOCK_DEFAULT_HEIGHT) * tileWidth * BLOCK_UNIT_FACTOR, style.color, look, pixel, bricks = true, density)
        TileSprite.ARCH -> drawArch(corners, (style.use.height ?: ARCH_DEFAULT_HEIGHT) * tileWidth * BLOCK_UNIT_FACTOR, style.color, look, pixel, density)
        TileSprite.TREE -> drawPixelTree(tile, centre, pixel * extent, look.lifted(style.color, look.treeLift), look)
        TileSprite.WATER -> drawWater(corners, centre, look.lifted(style.color, look.waterLift), pixel * extent, phase, density)
        TileSprite.FENCE -> drawFence(corners, tileWidth * FENCE_POST_FACTOR, style.color, look, density)
    }
}

private fun shade(color: Color, towardBlack: Float): Color = lerp(color, Color.Black, towardBlack)

private fun DrawScope.drawPrism(corners: List<Offset>, lift: Float, color: Color, look: TileLook, pixel: Float, bricks: Boolean, density: Float) {
    val centreX = corners.map { it.x }.average().toFloat()
    val centreY = corners.map { it.y }.average().toFloat()
    corners.indices.forEach { index ->
        val a = corners[index]
        val b = corners[(index + 1) % corners.size]
        if ((a.y + b.y) / 2f > centreY + 0.5f) {
            val darker = if ((a.x + b.x) / 2f < centreX) look.leftSide else look.rightSide
            val face = Path().apply {
                moveTo(a.x, a.y)
                lineTo(b.x, b.y)
                lineTo(b.x, b.y - lift)
                lineTo(a.x, a.y - lift)
                close()
            }
            drawPath(face, shade(color, darker), style = Fill)
            if (bricks) drawBrickCourses(a, b, lift, shade(color, darker + look.brickStep), pixel)
        }
    }
    val roof = polygon(corners.map { Offset(it.x, it.y - lift) })
    drawPath(roof, lerp(color, Color.White, look.topLift), style = Fill)
    look.outline?.let { drawPath(roof, it, style = Stroke(width = density)) }
}

private fun DrawScope.drawArch(corners: List<Offset>, lift: Float, color: Color, look: TileLook, pixel: Float, density: Float) {
    drawPrism(corners, lift, color, look, pixel, bricks = false, density)
    val top = corners.map { Offset(it.x, it.y - lift) }
    val ridgeStart = Offset((top[0].x + top[3].x) / 2f, (top[0].y + top[3].y) / 2f)
    val ridgeEnd = Offset((top[1].x + top[2].x) / 2f, (top[1].y + top[2].y) / 2f)
    drawLine(Color.White.copy(alpha = 0.7f), ridgeStart, ridgeEnd, strokeWidth = 2f * density)
}

private fun DrawScope.drawBrickCourses(a: Offset, b: Offset, lift: Float, color: Color, pixel: Float) {
    val course = BRICK_COURSE_PIXELS * pixel
    var rowIndex = 0
    var start = 0f
    while (start < lift) {
        val end = minOf(lift, start + course)
        if (brickTone(rowIndex * BRICK_COURSE_PIXELS) == 1) {
            val band = Path().apply {
                moveTo(a.x, a.y - start)
                lineTo(b.x, b.y - start)
                lineTo(b.x, b.y - end)
                lineTo(a.x, a.y - end)
                close()
            }
            drawPath(band, color, style = Fill)
        }
        start = end
        rowIndex++
    }
}

private fun DrawScope.drawPixelTree(tile: TileCoord, centre: Offset, pixel: Float, color: Color, look: TileLook) {
    val palette = PixelPalette(
        light = lerp(color, Color.White, TREE_LIGHT_MIX),
        mid = color,
        dark = shade(color, TREE_DARK_MIX),
        outline = look.outline ?: shade(color, TREE_OUTLINE_MIX),
        wood = look.lifted(TRUNK, look.treeLift),
    )
    drawPixelSprite(treeSprite(treeVariantFor(tile.col, tile.row)), centre.x, centre.y + TREE_FOOT_PIXELS * pixel, pixel, palette::colorOf)
}

private fun DrawScope.drawCritter(name: String, tile: TileCoord, centre: Offset, pixel: Float, phase: Int) {
    val sprite = critterSpriteOrNull(name, critterFrameFor(phase)) ?: return
    val hash = tileHash(tile.col + 11, tile.row + 17)
    val dx = ((hash % 7) - 3) * pixel
    val dy = (((hash / 7) % 5) - 2) * pixel
    drawPixelSprite(sprite, centre.x + dx, centre.y + CRITTER_FOOT_PIXELS * pixel + dy, pixel, ::critterColorOf)
}

private fun DrawScope.drawWater(corners: List<Offset>, centre: Offset, color: Color, pixel: Float, phase: Int, density: Float) {
    val inset = corners.map { Offset(centre.x + (it.x - centre.x) * 0.78f, centre.y + (it.y - centre.y) * 0.78f) }
    drawPath(polygon(inset), color, style = Fill)
    drawPath(polygon(inset), shade(color, 0.25f), style = Stroke(width = 1.5f * density))
    drawShimmer(centre, pixel, phase, Color.White.copy(alpha = SHIMMER_ALPHA))
}

private fun DrawScope.drawFence(corners: List<Offset>, postHeight: Float, color: Color, look: TileLook, density: Float) {
    val rail = look.rail(color)
    val raised = corners.map { Offset(it.x, it.y - postHeight) }
    corners.zip(raised).forEach { (foot, top) -> drawLine(rail, foot, top, strokeWidth = 2.5f * density) }
    drawPath(polygon(raised), rail, style = Stroke(width = 1.8f * density))
}
