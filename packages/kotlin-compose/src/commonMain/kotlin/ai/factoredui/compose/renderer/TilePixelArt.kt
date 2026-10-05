package ai.factoredui.compose.renderer

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

internal const val TREE_VARIANT_COUNT = 3
internal const val SHIMMER_CYCLE = 8
internal const val SHIMMER_DASHES = 3
internal const val PIXELS_PER_TILE_WIDTH = 22f
private const val BRICK_COURSE_PIXELS = 3
private const val CRITTER_SHARE_MODULUS = 3
private val SHIMMER_ROWS = listOf(-1, 1, -2)
private const val PIXEL_SEAM = 0.5f

internal class PixelSprite(val rows: List<String>) {
    val width: Int get() = rows.first().length
    val height: Int get() = rows.size
}

internal fun tileHash(col: Int, row: Int): Int {
    var hash = col * 73856093 xor row * 19349663
    hash = hash xor (hash ushr 13)
    hash *= 0x5bd1e995
    hash = hash xor (hash ushr 15)
    return hash and 0x7fffffff
}

internal fun treeVariantFor(col: Int, row: Int): Int = tileHash(col, row) % TREE_VARIANT_COUNT

internal fun critterOnTile(col: Int, row: Int): Boolean = tileHash(col * 3 + 1, row * 5 + 2) % CRITTER_SHARE_MODULUS == 0

internal fun critterFrameFor(phase: Int): Int = (phase / 2) % 2

internal fun brickTone(pixelRow: Int): Int = (pixelRow / BRICK_COURSE_PIXELS) % 2

internal fun shimmerOffsets(phase: Int): List<Pair<Int, Int>> =
    (0 until SHIMMER_DASHES).map { dash -> ((phase + dash * 3) % SHIMMER_CYCLE - SHIMMER_CYCLE / 2) to SHIMMER_ROWS[dash] }

private fun pad(solid: String, canvasWidth: Int): String {
    val left = (canvasWidth - solid.length) / 2
    return ".".repeat(left) + solid + ".".repeat(canvasWidth - left - solid.length)
}

private fun trunkRows(canvasWidth: Int, count: Int): List<String> {
    val centre = (canvasWidth - 1) / 2
    return List(count) { ".".repeat(centre) + "W" + ".".repeat(canvasWidth - 1 - centre) }
}

private fun conifer(): PixelSprite {
    val widths = listOf(1, 3, 3, 5, 5, 7, 7, 9, 9)
    val rows = widths.map { width ->
        val solid = (0 until width).joinToString("") { x ->
            when {
                width == 1 -> "L"
                x == 0 || x == width - 1 -> "O"
                x < (width - 1) / 2 -> "L"
                x > (width - 1) / 2 -> "D"
                else -> "M"
            }
        }
        pad(solid, 9)
    }
    return PixelSprite(rows + trunkRows(9, 3))
}

private fun roundTree(): PixelSprite {
    val widths = listOf(3, 5, 7, 7, 7, 5, 3)
    val rows = widths.mapIndexed { y, width ->
        val solid = (0 until width).joinToString("") { x ->
            val centre = (width - 1) / 2
            when {
                x == 0 || x == width - 1 -> "O"
                x < centre && y < 3 -> "L"
                x > centre || y > 4 -> "D"
                else -> "M"
            }
        }
        pad(solid, 7)
    }
    return PixelSprite(rows + trunkRows(7, 3))
}

private fun tallTree(): PixelSprite {
    val widths = listOf(3, 5, 5, 5, 5, 3)
    val rows = widths.mapIndexed { y, width ->
        val solid = (0 until width).joinToString("") { x ->
            when {
                x == 0 || x == width - 1 -> "O"
                x == 1 && y < 4 -> "L"
                x == width - 2 -> "D"
                else -> "M"
            }
        }
        pad(solid, 5)
    }
    return PixelSprite(rows + trunkRows(5, 2))
}

private val TREE_SPRITES = listOf(conifer(), roundTree(), tallTree())

internal fun treeSprite(variant: Int): PixelSprite = TREE_SPRITES[variant % TREE_VARIANT_COUNT]

private val SHEEP_FRAMES = listOf(
    PixelSprite(listOf(".wwwww..", "wwwwwwkk", "wvvvvvkk", ".O.O.O..")),
    PixelSprite(listOf(".wwwww..", "wwwwwwkk", "wvvvvvkk", "..O.O.O.")),
)

internal fun critterSpriteOrNull(name: String, frame: Int): PixelSprite? = when (name) {
    "sheep" -> SHEEP_FRAMES[frame % SHEEP_FRAMES.size]
    else -> null
}

internal fun critterSprite(name: String, frame: Int): PixelSprite = critterSpriteOrNull(name, frame) ?: error("no sprite for critter $name")

internal class PixelPalette(val light: Color, val mid: Color, val dark: Color, val outline: Color, val wood: Color) {
    fun colorOf(symbol: Char): Color? = when (symbol) {
        'L' -> light
        'M' -> mid
        'D' -> dark
        'O' -> outline
        'W' -> wood
        else -> null
    }
}

private val WOOL = Color(0xFFF4F1E8)
private val WOOL_SHADE = Color(0xFFCFCABD)
private val CRITTER_DARK = Color(0xFF3A3A3A)

internal fun critterColorOf(symbol: Char): Color? = when (symbol) {
    'w' -> WOOL
    'v' -> WOOL_SHADE
    'k' -> CRITTER_DARK
    'O' -> CRITTER_DARK
    else -> null
}

internal fun DrawScope.drawPixelSprite(sprite: PixelSprite, anchorX: Float, bottomY: Float, pixel: Float, colorOf: (Char) -> Color?) {
    val left = anchorX - sprite.width * pixel / 2f
    val top = bottomY - sprite.height * pixel
    sprite.rows.forEachIndexed { rowIndex, row ->
        row.forEachIndexed { columnIndex, symbol ->
            colorOf(symbol)?.let { color ->
                drawRect(
                    color = color,
                    topLeft = Offset(left + columnIndex * pixel, top + rowIndex * pixel),
                    size = Size(pixel + PIXEL_SEAM, pixel + PIXEL_SEAM),
                )
            }
        }
    }
}

internal fun DrawScope.drawShimmer(centre: Offset, pixel: Float, phase: Int, color: Color) {
    shimmerOffsets(phase).forEach { (dx, dy) ->
        drawRect(color, topLeft = Offset(centre.x + dx * pixel, centre.y + dy * pixel), size = Size(2 * pixel, pixel))
    }
}
