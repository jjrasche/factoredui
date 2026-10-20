package ai.factoredui.application

import java.io.ByteArrayOutputStream

private const val POINTS_PER_MM = 72.0 / 25.4
private const val DASH_POINTS = "[4.5 2.8] 0 d"

private fun points(value: Double) = "%.2f".format(java.util.Locale.ROOT, value * POINTS_PER_MM)

private fun colourOperands(hex: String): String =
    listOf(1, 3, 5).joinToString(" ") { "%.3f".format(java.util.Locale.ROOT, hex.substring(it, it + 2).toInt(16) / 255.0) }

private fun pdfText(text: String) = text.map { if (it.code > 255) '?' else it }.joinToString("").replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")

private fun operatorsOf(mark: Mark, heightMm: Double): String = when (mark) {
    is Segment -> strokeOperators(mark.ink) + "${points(mark.x1)} ${points(heightMm - mark.y1)} m ${points(mark.x2)} ${points(heightMm - mark.y2)} l S\n"
    is Frame -> strokeOperators(mark.ink) + "${points(mark.x)} ${points(heightMm - mark.y - mark.height)} ${points(mark.width)} ${points(mark.height)} re S\n"
    is Words -> {
        val width = textWidthMm(mark.text, mark.sizeMm)
        val left = when (mark.anchor) { Anchor.START -> mark.x; Anchor.MIDDLE -> mark.x - width / 2; Anchor.END -> mark.x - width }
        "${colourOperands(mark.colour)} rg BT /${if (mark.isBold) "F2" else "F1"} ${points(mark.sizeMm)} Tf ${points(left)} ${points(heightMm - mark.y)} Td (${pdfText(mark.text)}) Tj ET\n"
    }
}

private fun strokeOperators(ink: Ink) = "${colourOperands(ink.colour)} RG ${points(ink.weightMm)} w ${if (ink.isDashed) DASH_POINTS else "[] 0 d"}\n"

/** The sheet as a one-page PDF of vector lines and Helvetica text, at the paper size it states. */
internal fun sheetPdf(sheet: Sheet): ByteArray {
    val content = sheet.marks.joinToString("") { operatorsOf(it, sheet.heightMm) }.toByteArray(Charsets.ISO_8859_1)
    val bodies = listOf(
        "<< /Type /Catalog /Pages 2 0 R >>",
        "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
        "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${points(sheet.widthMm)} ${points(sheet.heightMm)}] /Contents 4 0 R /Resources << /Font << /F1 5 0 R /F2 6 0 R >> >> >>",
        null,
        "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>",
        "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>",
    )
    val out = ByteArrayOutputStream()
    fun write(text: String) = out.write(text.toByteArray(Charsets.ISO_8859_1))
    val offsets = mutableListOf<Int>()
    write("%PDF-1.4\n")
    bodies.forEachIndexed { index, body ->
        offsets.add(out.size())
        write("${index + 1} 0 obj\n")
        if (body != null) write("$body\n") else {
            write("<< /Length ${content.size} >>\nstream\n")
            out.write(content)
            write("endstream\n")
        }
        write("endobj\n")
    }
    val xref = out.size()
    write("xref\n0 ${bodies.size + 1}\n0000000000 65535 f \n")
    offsets.forEach { write("%010d 00000 n \n".format(it)) }
    write("trailer\n<< /Size ${bodies.size + 1} /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n")
    return out.toByteArray()
}
