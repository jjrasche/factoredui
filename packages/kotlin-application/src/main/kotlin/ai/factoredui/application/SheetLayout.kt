package ai.factoredui.application


internal const val SHEET_WIDTH_MM = 432.0
internal const val SHEET_HEIGHT_MM = 279.0
private const val MARGIN_MM = 8.0
private const val TITLE_HEIGHT_MM = 36.0
private const val NOTES_WIDTH_MM = 150.0
private const val PAD_MM = 6.0
private const val DP_MM = 0.26
private const val HEIGHT_LABEL_GAP_MM = 3.0
private const val DIMENSION_TEXT_MM = 2.9
private const val NOTE_HEADING_MM = 3.4
private const val NOTE_SIZE_MM = 2.9
private val SCALES = listOf(8, 12, 16, 24, 32, 48, 64, 96, 128, 192)
private const val BLACK = "#000000"

private val SCALE_NAMES = mapOf(
    8 to "1:8 (1-1/2\" = 1'-0\")", 12 to "1:12 (1\" = 1'-0\")", 16 to "1:16 (3/4\" = 1'-0\")", 24 to "1:24 (1/2\" = 1'-0\")", 32 to "1:32 (3/8\" = 1'-0\")",
    48 to "1:48 (1/4\" = 1'-0\")", 64 to "1:64 (3/16\" = 1'-0\")", 96 to "1:96 (1/8\" = 1'-0\")",
)

internal fun scaleName(scale: Int): String = SCALE_NAMES[scale] ?: "1:$scale"

internal class SheetMeta(val project: String, val date: String, val configSource: String, val enginePin: String, val sheetCount: Int)

internal class NoteGroup(val heading: String, val lines: List<String>)

private val INKS = mapOf(
    "piece" to Ink(BLACK, 0.35), "seam" to Ink("#555555", 0.15), "hole" to Ink(BLACK, 0.25, true), "unknown" to Ink(BLACK, 0.25, true),
    "dimension" to Ink("#333333", 0.15), "cut" to Ink(BLACK, 0.7),
)

private fun inkOf(shape: Map<String, Any?>) = INKS[shape["style"]] ?: Ink(BLACK, 0.25)

private fun number(shape: Map<String, Any?>, key: String) = (shape[key] as Number).toDouble()

/** A drawing shape in the engine's millimetres as a mark on paper at 1:[scale], its corner at the origin; y runs down the page. */
private fun markOf(shape: Map<String, Any?>, scale: Int, west: Double, north: Double): List<Mark> {
    fun x(mm: Double) = (mm - west) / scale
    fun y(mm: Double) = (north - mm) / scale
    return when (shape["kind"]) {
        "rect" -> listOf(Frame(x(number(shape, "x_mm")), y(number(shape, "y_mm") + number(shape, "depth_mm")), number(shape, "width_mm") / scale, number(shape, "depth_mm") / scale, inkOf(shape)))
        "line" -> listOf(Segment(x(number(shape, "x_mm")), y(number(shape, "y_mm")), x(number(shape, "x2_mm")), y(number(shape, "y2_mm")), inkOf(shape)))
        "label" -> {
            val isHeight = (shape["id"] as? String)?.startsWith("dim:height/text") == true
            val align = if (isHeight) Anchor.END else when (shape["align"]) { "start" -> Anchor.START; "end" -> Anchor.END; else -> Anchor.MIDDLE }
            val beside = if (isHeight) -HEIGHT_LABEL_GAP_MM else 0.0
            val size = DIMENSION_TEXT_MM
            listOf(Words(x(number(shape, "x_mm")) + beside + number(shape, "dx_dp") * DP_MM, y(number(shape, "y_mm")) + number(shape, "dy_dp") * DP_MM + size / 2, shape["text"] as String, size, align))
        }
        "marker" -> {
            val cx = x(number(shape, "x_mm"))
            val cy = y(number(shape, "y_mm"))
            listOf(Segment(cx - 1.2, cy, cx + 1.2, cy, inkOf(shape)), Segment(cx, cy - 1.2, cx, cy + 1.2, inkOf(shape)))
        }
        else -> emptyList()
    }
}

private fun northWest(shapes: List<Map<String, Any?>>): Pair<Double, Double> {
    val geometric = shapes.filter { it["kind"] != "label" }
    val xs = geometric.flatMap { shape -> listOfNotNull(number(shape, "x_mm"), (shape["x2_mm"] as? Number)?.toDouble(), (shape["width_mm"] as? Number)?.let { number(shape, "x_mm") + it.toDouble() }) }
    val ys = geometric.flatMap { shape -> listOfNotNull(number(shape, "y_mm"), (shape["y2_mm"] as? Number)?.toDouble(), (shape["depth_mm"] as? Number)?.let { number(shape, "y_mm") + it.toDouble() }) }
    return (xs.minOrNull() ?: 0.0) to (ys.maxOrNull() ?: 0.0)
}

private class DrawingArea(val left: Double, val top: Double, val width: Double, val height: Double)

private val AREA = DrawingArea(MARGIN_MM + PAD_MM, MARGIN_MM + PAD_MM, SHEET_WIDTH_MM - 2 * MARGIN_MM - NOTES_WIDTH_MM - 2 * PAD_MM, SHEET_HEIGHT_MM - 2 * MARGIN_MM - TITLE_HEIGHT_MM - 2 * PAD_MM)

private fun marksAt(shapes: List<Map<String, Any?>>, scale: Int): List<Mark> {
    val (west, north) = northWest(shapes)
    return shapes.flatMap { markOf(it, scale, west, north) }
}

private fun fits(marks: List<Mark>) = boundsOfAll(marks)?.let { it.width <= AREA.width && it.height <= AREA.height } ?: true

/** The largest standard scale at which the whole drawing fits its area, and the drawing placed in the middle of that area. */
internal fun fitDrawing(shapes: List<Map<String, Any?>>): Pair<Int, List<Mark>> {
    val scale = SCALES.firstOrNull { fits(marksAt(shapes, it)) } ?: SCALES.last()
    val marks = marksAt(shapes, scale)
    val bounds = boundsOfAll(marks) ?: return scale to marks
    val dx = AREA.left + (AREA.width - bounds.width) / 2 - bounds.left
    val dy = AREA.top + (AREA.height - bounds.height) / 2 - bounds.top
    return scale to marks.map { moved(it, dx, dy) }
}

private fun frame(x: Double, y: Double, width: Double, height: Double, weight: Double = 0.35) = Frame(x, y, width, height, Ink(BLACK, weight))

private fun cell(x: Double, y: Double, width: Double, label: String, value: String, bold: Boolean = false): List<Mark> = listOf(
    Words(x + 2, y + 5, label.uppercase(), 2.9, colour = "#666666"),
    Words(x + 2, y + 13, value, 3.4, isBold = bold),
)

private fun titleBlock(sheetName: String, sheetNumber: String, scaleText: String, meta: SheetMeta): List<Mark> {
    val left = MARGIN_MM
    val top = SHEET_HEIGHT_MM - MARGIN_MM - TITLE_HEIGHT_MM
    val width = SHEET_WIDTH_MM - 2 * MARGIN_MM
    val column = width / 4
    val half = TITLE_HEIGHT_MM / 2
    val cells = listOf(
        listOf("Project" to meta.project, "Sheet" to sheetName, "Sheet number" to sheetNumber, "Date" to meta.date),
        listOf("Config commit" to meta.configSource, "Engine pin" to meta.enginePin, "Scale" to scaleText, "Source" to "world-engine geometry; no line is drawn by hand"),
    )
    val marks = cells.flatMapIndexed { row, entries -> entries.flatMapIndexed { index, (label, value) -> cell(left + index * column, top + row * half, column, label, value, bold = label == "Sheet") } }
    val lines = listOf(Segment(left, top, left + width, top, Ink(BLACK, 0.7)), Segment(left, top + half, left + width, top + half, Ink(BLACK, 0.25))) +
        (1 until 4).map { Segment(left + it * column, top, left + it * column, top + TITLE_HEIGHT_MM, Ink(BLACK, 0.25)) }
    return marks + lines
}

private fun noteLines(groups: List<NoteGroup>, sizeMm: Double): List<Pair<String, Double>> = groups.flatMap { group ->
    listOf(group.heading to NOTE_HEADING_MM) + group.lines.flatMap { line -> wrapWords(line, sizeMm, NOTES_WIDTH_MM - 2 * PAD_MM).mapIndexed { index, text -> (if (index == 0) text else "    $text") to sizeMm } } + ("" to sizeMm)
}

private fun notesBlock(groups: List<NoteGroup>): List<Mark> {
    val left = SHEET_WIDTH_MM - MARGIN_MM - NOTES_WIDTH_MM
    val size = NOTE_SIZE_MM
    var y = MARGIN_MM + PAD_MM + NOTE_HEADING_MM
    val words = noteLines(groups, size).map { (text, height) -> Words(left + PAD_MM, y, text, height, isBold = height == NOTE_HEADING_MM).also { y += height * 1.35 } }
    return words + Segment(left, MARGIN_MM, left, SHEET_HEIGHT_MM - MARGIN_MM - TITLE_HEIGHT_MM, Ink(BLACK, 0.35))
}

private const val TEXT_SIZE_MM = 2.9
private const val TEXT_PITCH = 1.3
private const val HEADING_SIZE_MM = 3.4
private const val COLUMN_GAP_MM = 8.0
private const val COLUMNS_PER_SHEET = 2

private class TextRow(val text: String, val sizeMm: Double, val isBold: Boolean)

private fun rowsOf(groups: List<NoteGroup>, widthMm: Double): List<TextRow> = groups.flatMap { group ->
    listOf(TextRow(group.heading, HEADING_SIZE_MM, true)) +
        group.lines.flatMap { line -> wrapWords(line, TEXT_SIZE_MM, widthMm).mapIndexed { index, text -> TextRow(if (index == 0) text else "      $text", TEXT_SIZE_MM, false) } } + TextRow("", TEXT_SIZE_MM, false)
}

/** Text flowed down columns and over sheets, each with the title block; a sheet holds [COLUMNS_PER_SHEET] columns. */
internal fun textSheets(sheetName: String, numberOf: (page: Int, pages: Int) -> String, groups: List<NoteGroup>, meta: SheetMeta): List<Sheet> {
    val left = MARGIN_MM + PAD_MM
    val width = (SHEET_WIDTH_MM - 2 * MARGIN_MM - 2 * PAD_MM - COLUMN_GAP_MM * (COLUMNS_PER_SHEET - 1)) / COLUMNS_PER_SHEET
    val height = SHEET_HEIGHT_MM - 2 * MARGIN_MM - TITLE_HEIGHT_MM - 2 * PAD_MM
    val columns = mutableListOf<MutableList<Words>>()
    var y = height
    rowsOf(groups, width).forEach { row ->
        val pitch = row.sizeMm * TEXT_PITCH
        if (columns.isEmpty() || y + pitch > height) { columns.add(mutableListOf()); y = 0.0 }
        y += pitch
        val index = columns.size - 1
        columns[index].add(Words(left + (index % COLUMNS_PER_SHEET) * (width + COLUMN_GAP_MM), MARGIN_MM + PAD_MM + y, row.text, row.sizeMm, isBold = row.isBold))
    }
    val pages = columns.chunked(COLUMNS_PER_SHEET)
    return pages.mapIndexed { page, group ->
        val border = frame(MARGIN_MM, MARGIN_MM, SHEET_WIDTH_MM - 2 * MARGIN_MM, SHEET_HEIGHT_MM - 2 * MARGIN_MM, 0.7)
        Sheet(SHEET_WIDTH_MM, SHEET_HEIGHT_MM, listOf<Mark>(border) + group.flatten() + titleBlock(sheetName, numberOf(page + 1, pages.size), "not to scale", meta))
    }
}

/** A sheet: the drawing to scale, a notes column, and a title block, inside a border. */
internal fun sheetOf(sheetName: String, sheetNumber: String, shapes: List<Map<String, Any?>>, notes: List<NoteGroup>, meta: SheetMeta): Sheet {
    val (scale, drawing) = fitDrawing(shapes)
    val border = frame(MARGIN_MM, MARGIN_MM, SHEET_WIDTH_MM - 2 * MARGIN_MM, SHEET_HEIGHT_MM - 2 * MARGIN_MM, 0.7)
    return Sheet(SHEET_WIDTH_MM, SHEET_HEIGHT_MM, listOf<Mark>(border) + drawing + notesBlock(notes) + titleBlock(sheetName, sheetNumber, scaleName(scale), meta))
}

