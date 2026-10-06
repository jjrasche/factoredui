package ai.factoredui.compose.pixel

import ai.factoredui.compose.layout.FACING_COUNT
import ai.factoredui.compose.layout.normalisedTurns
import kotlin.math.abs
import kotlin.math.roundToInt

val FACINGS: List<String> = listOf("SE", "SW", "NW", "NE")

const val FACING_ALONG_X = 0
const val FACING_ALONG_Y = 1
const val DEFAULT_TREE_SIZE = "M"

enum class TreeClass(val cls: String) { BROADLEAF("tree-broadleaf"), CONIFER("tree-conifer"), UNKNOWN("tree-unknown") }

private val CONIFER_WORDS = listOf("conifer", "pine", "spruce", "fir", "cedar", "hemlock")
private val BROADLEAF_WORDS = listOf("broadleaf", "oak", "maple", "ash", "birch", "beech", "walnut", "cherry", "apple", "willow")

data class SpriteSize(val forwardFt: Double, val lateralFt: Double)

fun spriteSizeOf(label: String): SpriteSize? {
    val parts = label.split("x")
    if (parts.size != 2) return null
    val forward = parts[0].toDoubleOrNull() ?: return null
    val lateral = parts[1].toDoubleOrNull() ?: return null
    return SpriteSize(forward, lateral)
}

fun worldFacingForExtent(extentXFt: Double, extentYFt: Double): Int = if (extentXFt >= extentYFt) FACING_ALONG_X else FACING_ALONG_Y

fun largestSizeThatFits(sizes: List<String>, longFt: Double, shortFt: Double): String? {
    val parsed = sizes.distinct().mapNotNull { label -> spriteSizeOf(label)?.let { label to it } }
    val fitting = parsed.filter { (_, size) -> size.forwardFt <= longFt && size.lateralFt <= shortFt }
    val chosen = fitting.maxByOrNull { (_, size) -> size.forwardFt * size.lateralFt } ?: parsed.minByOrNull { (_, size) -> size.forwardFt * size.lateralFt }
    return chosen?.first
}

fun treeClassFor(useId: String): TreeClass = when {
    CONIFER_WORDS.any { useId.contains(it) } -> TreeClass.CONIFER
    BROADLEAF_WORDS.any { useId.contains(it) } -> TreeClass.BROADLEAF
    else -> TreeClass.UNKNOWN
}

fun treeSizeFor(scale: PixelScale, crownRadiusFt: Double?): String {
    if (crownRadiusFt == null || crownRadiusFt <= 0.0) return DEFAULT_TREE_SIZE
    val sizes = scale.ofClass(TreeClass.UNKNOWN.cls).map { it.size to it.heightFt }.distinct()
    return sizes.minByOrNull { (_, radius) -> abs(radius - crownRadiusFt) }?.first ?: DEFAULT_TREE_SIZE
}

fun facingForRotation(rotationDeg: Double): Int = normalisedTurns((rotationDeg / 90.0).roundToInt() % FACING_COUNT)

fun nearestRamp(colourHex: String?, swap: PixelSwap): String {
    val wanted = rgbOfHex(colourHex) ?: return swap.base
    return swap.ramps.minByOrNull { (_, ramp) -> rgbDistance(wanted, ramp[ramp.size / 2]) }?.key ?: swap.base
}

fun rgbOfHex(hex: String?): Int? {
    val digits = hex?.removePrefix("#") ?: return null
    if (digits.length != 6) return null
    return digits.toIntOrNull(16)
}

private fun rgbDistance(first: Int, second: Int): Int {
    val red = ((first shr 16) and 0xFF) - ((second shr 16) and 0xFF)
    val green = ((first shr 8) and 0xFF) - ((second shr 8) and 0xFF)
    val blue = (first and 0xFF) - (second and 0xFF)
    return red * red + green * green + blue * blue
}

fun stableHash(text: String, salt: Int): Int {
    var hash = -0x7ee3623b xor salt
    for (char in text) hash = (hash xor char.code) * 0x01000193
    hash = hash xor (hash ushr 15)
    return hash and 0x7fffffff
}
