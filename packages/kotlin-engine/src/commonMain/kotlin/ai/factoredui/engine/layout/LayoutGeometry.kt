package ai.factoredui.engine.layout

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

private const val PIXEL_CHANNEL_TOLERANCE = 8
private const val FILL_ROW_MIN_SHARE = 0.5
private const val ROWS_THAT_END_THE_FILL = 4
private const val INTERSECTION_SLACK_DP = 1.0

fun intersectionArea(first: Box, second: Box): Double {
    val across = min(first.right, second.right) - max(first.x, second.x)
    val deep = min(first.bottom, second.bottom) - max(first.y, second.y)
    return if (across > 0 && deep > 0) across * deep else 0.0
}

fun overlapsByMoreThanSlack(first: Box, second: Box): Boolean =
    min(first.right, second.right) - max(first.x, second.x) > INTERSECTION_SLACK_DP &&
        min(first.bottom, second.bottom) - max(first.y, second.y) > INTERSECTION_SLACK_DP

fun contains(outer: Box, inner: Box): Boolean =
    inner.x >= outer.x - INTERSECTION_SLACK_DP && inner.y >= outer.y - INTERSECTION_SLACK_DP &&
        inner.right <= outer.right + INTERSECTION_SLACK_DP && inner.bottom <= outer.bottom + INTERSECTION_SLACK_DP

fun parseRgb(hex: String): Int = hex.removePrefix("#").take(6).toInt(16)

private fun channelsOf(rgb: Int): List<Int> = listOf((rgb shr 16) and 0xFF, (rgb shr 8) and 0xFF, rgb and 0xFF)

private fun matchesFill(pixel: Int, fill: Int): Boolean =
    channelsOf(pixel).zip(channelsOf(fill)).all { (drawn, wanted) -> abs(drawn - wanted) <= PIXEL_CHANNEL_TOLERANCE }

private fun fillShareOfRow(grid: PixelGrid, y: Int, fromX: Int, toX: Int, fill: Int): Double {
    val span = (fromX until toX).toList()
    return span.count { matchesFill(grid.rgbAt(it, y), fill) }.toDouble() / span.size
}

/** The top edge of a bottom-anchored panel as drawn: the first run of rows, going up, where its fill stops holding the row. */
fun findFillTopEdge(grid: PixelGrid, fromX: Int, toX: Int, fill: Int): Int {
    var lowRows = 0
    var edge = grid.height
    for (y in grid.height - 1 downTo 0) {
        val isFilled = fillShareOfRow(grid, y, fromX.coerceAtLeast(0), toX.coerceAtMost(grid.width), fill) >= FILL_ROW_MIN_SHARE
        if (isFilled) {
            edge = y
            lowRows = 0
        } else {
            lowRows += 1
            if (lowRows >= ROWS_THAT_END_THE_FILL) break
        }
    }
    return edge
}

private fun linearChannel(channel: Int): Double {
    val unit = channel / 255.0
    return if (unit <= 0.03928) unit / 12.92 else ((unit + 0.055) / 1.055).pow(2.4)
}

private fun luminanceOf(rgb: Int): Double {
    val (red, green, blue) = channelsOf(rgb).map(::linearChannel)
    return 0.2126 * red + 0.7152 * green + 0.0722 * blue
}

/** WCAG 2 contrast ratio of two #rrggbb colours. */
fun contrastRatio(firstHex: String, secondHex: String): Double {
    val lighter = max(luminanceOf(parseRgb(firstHex)), luminanceOf(parseRgb(secondHex)))
    val darker = min(luminanceOf(parseRgb(firstHex)), luminanceOf(parseRgb(secondHex)))
    return (lighter + 0.05) / (darker + 0.05)
}
