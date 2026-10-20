package ai.factoredui.application

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun recorded(name: String) = checkNotNull(SheetTest::class.java.getResource("/$name")).readText()

private val GEOMETRY = readGeometry(recorded("live-geometry.json"))

private val EXPANSION = readExpansion(recorded("live-expansion.json"))

private val META = SheetMeta("A house", "2026-10-20", "abc1234", "we deadbee", 6)

private val NOTES = listOf(NoteGroup("NOT MEASURED BY THE ENGINE", listOf("roof: height_mm is unknown (size-unknown)")), NoteGroup("ASSUMED SETTINGS", listOf("floor.thickness_mm = 114 mm: assumed: a 4-1/2 in SIP panel")))

private fun planSheet() = sheetOf("Plan", "A-1 of 6", blueprintPlanShapes(GEOMETRY, EXPANSION), NOTES, META)


class SheetTest {
    @Test
    fun a_wall_line_is_its_length_over_the_scale_on_paper() {
        val shapes = listOf(mapOf("kind" to "line", "style" to "piece", "x_mm" to 0, "y_mm" to 0, "x2_mm" to 2400, "y2_mm" to 0))
        val (scale, marks) = fitDrawing(shapes)
        val line = marks.filterIsInstance<Segment>().single()
        assertEquals(2400.0 / scale, abs(line.x2 - line.x1), 1e-9)
        assertEquals(12, scale)
    }

    @Test
    fun the_scale_is_the_largest_standard_one_at_which_the_house_fits_and_is_stated() {
        val (scale, marks) = fitDrawing(blueprintPlanShapes(GEOMETRY, EXPANSION))
        assertTrue(scale in listOf(8, 12, 16, 24, 32, 48, 64, 96), "scale $scale")
        val bounds = boundsOfAll(marks)!!
        assertTrue(bounds.width < SHEET_WIDTH_MM && bounds.height < SHEET_HEIGHT_MM, "bounds ${bounds.width} x ${bounds.height}")
        assertTrue(sheetSvg(sheetOf("Plan", "A-1 of 6", blueprintPlanShapes(GEOMETRY, EXPANSION), NOTES, META)).contains(scaleName(scale).replace("\"", "&quot;")), scaleName(scale))
    }

    @Test
    fun the_title_block_gives_project_sheet_date_scale_config_commit_and_engine_pin() {
        val svg = sheetSvg(planSheet())
        listOf("A house", "Plan", "A-1 of 6", "2026-10-20", "abc1234", "we deadbee").forEach { assertTrue(svg.contains(">$it<"), it) }
    }

    @Test
    fun the_notes_block_lists_what_was_not_measured_and_what_was_assumed_with_its_reason() {
        val svg = sheetSvg(planSheet())
        assertTrue(svg.contains("roof: height_mm is unknown (size-unknown)"))
        assertTrue(svg.contains("floor.thickness_mm = 114 mm: assumed: a 4-1/2 in SIP panel"))
    }

    @Test
    fun the_dimension_strings_are_in_millimetres_and_feet_and_inches() {
        assertTrue(sheetSvg(planSheet()).contains("2438 mm · 8'-0&quot;") || sheetSvg(planSheet()).contains("2438 mm · 8'-0\""))
    }

    @Test
    fun every_sheet_of_the_house_is_made() {
        val drawings = listOf(blueprintPlanShapes(GEOMETRY, EXPANSION)) + elevationDrawings(GEOMETRY).map { it.shapes } + listOfNotNull(sectionDrawing(GEOMETRY)?.shapes)
        assertEquals(6, drawings.size)
        drawings.forEach { shapes -> assertTrue(sheetSvg(sheetOf("Sheet", "A-n", shapes, NOTES, META)).startsWith("<svg")) }
    }

    @Test
    fun the_pdf_has_a_cross_reference_table_at_the_offset_it_names() {
        val bytes = sheetPdf(planSheet())
        val text = String(bytes, Charsets.ISO_8859_1)
        assertTrue(text.startsWith("%PDF-1.4"))
        val offset = Regex("startxref\\n(\\d+)").find(text)!!.groupValues[1].toInt()
        assertEquals("xref", text.substring(offset, offset + 4))
        assertTrue(text.contains("(A house) Tj"))
    }

    @Test
    fun the_title_block_states_the_worlds_engine_pin_and_the_checkout_only_when_it_differs() {
        assertEquals("we 02d17b7", pinText("02d17b75af58653882c928803df4660d0a5c78e3", "02d17b7"))
        assertEquals("we 02d17b7; rendered with we 073b001", pinText("02d17b75af58653882c928803df4660d0a5c78e3", "073b001"))
        assertEquals("no engine.pin beside the world file; rendered with we 073b001", pinText(null, "073b001"))
    }

    @Test
    fun no_text_on_any_sheet_is_smaller_than_eight_points() {
        val drawings = listOf(blueprintPlanShapes(GEOMETRY, EXPANSION)) + elevationDrawings(GEOMETRY).map { it.shapes } + listOfNotNull(sectionDrawing(GEOMETRY)?.shapes)
        val sheets = drawings.map { sheetOf("Sheet", "A-n", it, NOTES, META) } + textSheets("Summary", { page, pages -> "S-1 ($page of $pages)" }, listOf(NoteGroup("H", (1..400).map { "a line of the summary number $it" })), META)
        sheets.forEach { sheet -> sheet.marks.filterIsInstance<Words>().forEach { assertTrue(it.sizeMm * 72 / 25.4 >= 8.0, "'${it.text}' is ${it.sizeMm * 72 / 25.4} pt") } }
    }

    @Test
    fun the_summary_runs_to_as_many_sheets_as_it_needs() {
        val sheets = textSheets("Summary", { page, pages -> "S-1 ($page of $pages)" }, listOf(NoteGroup("H", (1..400).map { "a line of the summary number $it" })), META)
        assertTrue(sheets.size > 2, "sheets ${sheets.size}")
        assertTrue(sheetSvg(sheets.last()).contains(">S-1 (${sheets.size} of ${sheets.size})<"))
    }

    private fun assumptions(count: Int) = NoteGroup("ASSUMED SETTINGS", (1..count).map { "setting$it.value = $it unit: assumed, because this reason runs on over a few words so that it wraps onto a second line of the notes" })

    private fun drawnSet() = listOf(Drawn("Plan", blueprintPlanShapes(GEOMETRY, EXPANSION)), Drawn("South elevation", elevationDrawings(GEOMETRY).first().shapes))

    private fun titleTop() = SHEET_HEIGHT_MM - 8.0 - 36.0

    @Test
    fun notes_that_do_not_fit_continue_on_a_sheet_each_drawing_points_to_and_nothing_overlaps_the_title_block() {
        val sheets = buildSheets(drawnSet(), listOf(assumptions(60)), emptyList(), META)
        val drawings = sheets.filter { it.first.startsWith("A-") }
        val continuation = sheets.filter { it.first.startsWith("N-1") }
        assertTrue(continuation.isNotEmpty(), "a continuation sheet")
        drawings.forEach { (_, sheet) ->
            val notes = sheet.marks.filterIsInstance<Words>().filter { it.x > SHEET_WIDTH_MM - 8.0 - 150.0 && it.y < titleTop() + 1.0 }
            assertTrue(notes.all { it.y <= titleTop() - 2.0 }, "a note line reaches the title block")
            assertTrue(sheetSvg(sheet).contains("Notes continue on sheet N-1"))
        }
        continuation.forEach { (_, sheet) -> assertTrue(sheet.marks.filterIsInstance<Words>().all { it.y <= SHEET_HEIGHT_MM - 8.0 }) }
        assertTrue(sheetSvg(continuation.first().second).contains(">Notes, continued<"))
    }

    @Test
    fun every_note_is_on_a_drawing_or_a_continuation_sheet_exactly_once() {
        val sheets = buildSheets(drawnSet(), listOf(assumptions(60)), emptyList(), META)
        fun count(sheet: Sheet, prefix: String) = sheet.marks.filterIsInstance<Words>().count { it.text.startsWith(prefix) }
        (1..60).forEach { n ->
            val onDrawing = count(sheets.first { it.first.startsWith("A-1") }.second, "setting$n.value =")
            val onContinuation = sheets.filter { it.first.startsWith("N-1") }.sumOf { count(it.second, "setting$n.value =") }
            assertEquals(1, onDrawing + onContinuation, "setting$n")
        }
    }

    @Test
    fun notes_that_fit_stay_on_the_sheet_with_no_pointer_and_no_continuation() {
        val sheets = buildSheets(drawnSet(), NOTES, emptyList(), META)
        assertTrue(sheets.none { it.first.startsWith("N-1") })
        assertTrue(sheets.none { sheetSvg(it.second).contains("Notes continue") })
    }
}
