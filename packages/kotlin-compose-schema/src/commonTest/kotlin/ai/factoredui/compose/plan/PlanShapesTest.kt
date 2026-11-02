package ai.factoredui.compose.plan

import ai.factoredui.compose.stackedscene.RectMm
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private fun rect(id: String, x: Number, y: Number, width: Number, depth: Number, style: String? = null): Map<String, Any?> =
    mapOf("id" to id, "kind" to "rect", "x_mm" to x, "y_mm" to y, "width_mm" to width, "depth_mm" to depth, "style" to style)

class PlanShapesTest {
    @Test
    fun a_rect_a_line_a_marker_and_a_label_are_read_with_their_millimetres() {
        val read = readPlan(
            listOf(
                rect("a", 610, 2438, 2438, 2438, "base"),
                mapOf("id" to "b", "kind" to "line", "x_mm" to 0, "y_mm" to 0, "x2_mm" to 100.5, "y2_mm" to 0),
                mapOf("id" to "c", "kind" to "marker", "x_mm" to 5, "y_mm" to 6),
                mapOf("id" to "d", "kind" to "label", "x_mm" to 1, "y_mm" to 2, "text" to "north", "align" to "start", "dx_dp" to 4, "dy_dp" to 10.5),
            ),
            mapOf("base" to mapOf("fill" to "#aabbcc", "dash" to true, "weight" to 2.5)),
            listOf(mapOf("label" to "Base", "style" to "base")),
        )
        assertEquals(listOf("a", "b", "c", "d"), read.shapes.map { it.id })
        assertEquals(2438.0, read.shapes[0].depthMm)
        assertEquals(100.5, read.shapes[1].x2Mm)
        assertEquals(PlanLabelAlign.START, read.shapes[3].align)
        assertEquals(4f to 10.5f, read.shapes[3].dxDp to read.shapes[3].dyDp)
        assertEquals(PlanStyle(fill = "#aabbcc", weight = 2.5f, dash = true), read.styles["base"])
        assertEquals(listOf(PlanLegendEntry("Base", "base")), read.legend)
        assertEquals(emptyList(), read.notices)
    }

    @Test
    fun a_shape_that_cannot_be_drawn_is_dropped_and_named() {
        val read = readPlan(
            listOf(
                mapOf("id" to "no-size", "kind" to "rect", "x_mm" to 0, "y_mm" to 0, "width_mm" to null, "depth_mm" to 5),
                mapOf("id" to "odd", "kind" to "blob", "x_mm" to 0, "y_mm" to 0),
                mapOf("id" to "no-point", "kind" to "marker", "x_mm" to "0", "y_mm" to 0),
                "not an object",
            ),
            null,
            null,
        )
        assertEquals(emptyList(), read.shapes)
        assertEquals(
            listOf(
                "plan shape 'no-size': a rect needs width_mm and depth_mm as numbers; it is not drawn.",
                "plan shape 'odd': kind must be one of rect, line, marker, label, not 'blob'; it is not drawn.",
                "plan shape 'no-point': x_mm and y_mm must be numbers; it is not drawn.",
                "plan shape 3 is not an object; it is not drawn.",
            ),
            read.notices,
        )
    }

    @Test
    fun a_style_the_styles_do_not_hold_is_named_once() {
        val read = readPlan(listOf(rect("a", 0, 0, 1, 1, "ghost"), rect("b", 0, 0, 1, 1, "ghost")), emptyMap<String, Any?>(), null)
        assertEquals(listOf("plan style 'ghost' is not in styles; its shapes are drawn plain."), read.notices)
    }

    @Test
    fun the_window_spans_every_shape_and_is_absent_for_none() {
        val read = readPlan(
            listOf(
                rect("a", 610, 2438, 2438, 2438),
                mapOf("id" to "b", "kind" to "line", "x_mm" to 4000, "y_mm" to 100, "x2_mm" to 4000, "y2_mm" to -50),
            ),
            null,
            null,
        )
        assertEquals(RectMm(610.0, -50.0, 4000.0, 4876.0), planWindow(read.shapes))
        assertNull(planWindow(emptyList()))
    }
}
