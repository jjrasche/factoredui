package ai.factoredui.application

private const val DASH = "1.6 1"

private fun escaped(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

private fun number(value: Double) = "%.3f".format(java.util.Locale.ROOT, value)

private fun strokeOf(ink: Ink) = "stroke=\"${ink.colour}\" stroke-width=\"${number(ink.weightMm)}\"" + if (ink.isDashed) " stroke-dasharray=\"$DASH\"" else ""

private fun elementOf(mark: Mark): String = when (mark) {
    is Segment -> "<line x1=\"${number(mark.x1)}\" y1=\"${number(mark.y1)}\" x2=\"${number(mark.x2)}\" y2=\"${number(mark.y2)}\" ${strokeOf(mark.ink)}/>"
    is Frame -> "<rect x=\"${number(mark.x)}\" y=\"${number(mark.y)}\" width=\"${number(mark.width)}\" height=\"${number(mark.height)}\" fill=\"none\" ${strokeOf(mark.ink)}/>"
    is Words -> {
        val anchor = when (mark.anchor) { Anchor.START -> "start"; Anchor.MIDDLE -> "middle"; Anchor.END -> "end" }
        "<text x=\"${number(mark.x)}\" y=\"${number(mark.y)}\" font-size=\"${number(mark.sizeMm)}\" text-anchor=\"$anchor\" fill=\"${mark.colour}\"" +
            (if (mark.isBold) " font-weight=\"bold\"" else "") + ">${escaped(mark.text)}</text>"
    }
}

/** The sheet as SVG in millimetres of paper, so it prints at the size it states. */
internal fun sheetSvg(sheet: Sheet): String = buildString {
    appendLine("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"${number(sheet.widthMm)}mm\" height=\"${number(sheet.heightMm)}mm\" viewBox=\"0 0 ${number(sheet.widthMm)} ${number(sheet.heightMm)}\" font-family=\"Helvetica, Arial, sans-serif\">")
    appendLine("<rect width=\"${number(sheet.widthMm)}\" height=\"${number(sheet.heightMm)}\" fill=\"#ffffff\"/>")
    sheet.marks.forEach { appendLine(elementOf(it)) }
    appendLine("</svg>")
}
