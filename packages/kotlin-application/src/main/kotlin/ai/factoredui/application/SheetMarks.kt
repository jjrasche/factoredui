package ai.factoredui.application

import kotlin.math.max
import kotlin.math.min

internal data class Ink(val colour: String, val weightMm: Double, val isDashed: Boolean = false)

internal enum class Anchor { START, MIDDLE, END }

internal sealed interface Mark

internal data class Segment(val x1: Double, val y1: Double, val x2: Double, val y2: Double, val ink: Ink) : Mark

internal data class Frame(val x: Double, val y: Double, val width: Double, val height: Double, val ink: Ink) : Mark

internal data class Words(val x: Double, val y: Double, val text: String, val sizeMm: Double, val anchor: Anchor = Anchor.START, val colour: String = "#000000", val isBold: Boolean = false) : Mark

internal class Sheet(val widthMm: Double, val heightMm: Double, val marks: List<Mark>)

private const val NARROW = "iljtfI.,:;'|!()[] "
private const val WIDE = "mwMW@"

/** The width of text in Helvetica, close enough to lay out lines: narrow letters, digits, capitals and wide letters each have a width. */
internal fun textWidthMm(text: String, sizeMm: Double): Double =
    text.sumOf { char ->
        when {
            char in NARROW -> 0.28
            char in WIDE -> 0.83
            char.isUpperCase() -> 0.68
            char.isDigit() -> 0.556
            else -> 0.52
        }
    } * sizeMm

/** The words wrapped into lines no wider than [widthMm], breaking at spaces. */
internal fun wrapWords(text: String, sizeMm: Double, widthMm: Double): List<String> {
    val lines = mutableListOf<String>()
    var line = ""
    text.split(' ').forEach { word ->
        val candidate = if (line.isEmpty()) word else "$line $word"
        if (line.isNotEmpty() && textWidthMm(candidate, sizeMm) > widthMm) {
            lines.add(line)
            line = word
        } else line = candidate
    }
    return lines + line
}

internal class PaperBounds(val left: Double, val top: Double, val right: Double, val bottom: Double) {
    val width get() = right - left
    val height get() = bottom - top
}

private fun boundsOf(mark: Mark): PaperBounds = when (mark) {
    is Segment -> PaperBounds(min(mark.x1, mark.x2), min(mark.y1, mark.y2), max(mark.x1, mark.x2), max(mark.y1, mark.y2))
    is Frame -> PaperBounds(mark.x, mark.y, mark.x + mark.width, mark.y + mark.height)
    is Words -> {
        val width = textWidthMm(mark.text, mark.sizeMm)
        val left = when (mark.anchor) { Anchor.START -> mark.x; Anchor.MIDDLE -> mark.x - width / 2; Anchor.END -> mark.x - width }
        PaperBounds(left, mark.y - mark.sizeMm, left + width, mark.y)
    }
}

internal fun boundsOfAll(marks: List<Mark>): PaperBounds? =
    marks.map(::boundsOf).takeIf { it.isNotEmpty() }?.let { all -> PaperBounds(all.minOf { it.left }, all.minOf { it.top }, all.maxOf { it.right }, all.maxOf { it.bottom }) }

internal fun moved(mark: Mark, dx: Double, dy: Double): Mark = when (mark) {
    is Segment -> mark.copy(x1 = mark.x1 + dx, y1 = mark.y1 + dy, x2 = mark.x2 + dx, y2 = mark.y2 + dy)
    is Frame -> mark.copy(x = mark.x + dx, y = mark.y + dy)
    is Words -> mark.copy(x = mark.x + dx, y = mark.y + dy)
}
