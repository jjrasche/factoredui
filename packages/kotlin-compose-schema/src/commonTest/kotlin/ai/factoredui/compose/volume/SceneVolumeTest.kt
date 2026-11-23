package ai.factoredui.compose.volume

import ai.factoredui.compose.heatmap.heatLegendText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun member(id: String, utilisation: Any?, source: Any? = "row-1") = mapOf(
    "id" to id, "type" to "beam", "system" to "frame",
    "box_mm" to mapOf("x" to 1000, "y" to 2000, "z" to 0, "w" to 100, "d" to 200, "h" to 3000),
    "fields" to mapOf("utilisation" to mapOf("value" to utilisation, "unit" to "ratio", "source" to source)),
)

private fun answer(objects: List<Any?>, limits: Map<String, Any?> = mapOf("utilisation" to mapOf("high" to 1.0, "source" to "setting:frame.limit")), page: Map<String, Any?>? = null) =
    mapOf("objects" to objects, "limits" to limits, "page" to page)

private fun boxesOf(read: SceneVolume): List<Map<*, *>> = (read.props["boxes"] as List<*>).filterIsInstance<Map<*, *>>()

class SceneVolumeTest {

    @Test
    fun a_box_is_drawn_where_the_answer_puts_it_and_scaled_to_the_answers_own_limit() {
        val read = assertNotNull(readScene(answer(listOf(member("m1", 0.4), member("m2", 0.9))), "utilisation"))
        val box = boxesOf(read).first { it["id"] == "m1" }
        assertEquals(listOf(1000, 2000, 0, 100, 200, 3000), listOf(box["x_mm"], box["y_mm"], box["z_mm"], box["width_mm"], box["depth_mm"], box["height_mm"]))
        assertEquals(listOf(0.4), box["values"])
        assertEquals(1.0, read.props["max"])
        assertEquals(0.4, read.props["min"])
        assertEquals("ratio", read.props["unit"])
    }

    @Test
    fun a_value_over_the_limit_is_its_own_style_and_carries_no_colour_value() {
        val read = assertNotNull(readScene(answer(listOf(member("ok", 0.5), member("over", 1.3))), "utilisation"))
        val over = boxesOf(read).first { it["id"] == "over" }
        assertEquals("outside", over["style"])
        assertNull(over["values"])
        assertTrue((read.props["legend"] as List<*>).any { (it as Map<*, *>)["style"] == "outside" })
    }

    @Test
    fun an_unknown_value_is_drawn_hatched_in_the_unknown_style_with_its_reason_in_the_card() {
        val unknown = member("gap", null, mapOf("unknown" to "no load case"))
        val read = assertNotNull(readScene(answer(listOf(member("ok", 0.5), unknown)), "utilisation"))
        val box = boxesOf(read).first { it["id"] == "gap" }
        assertEquals("unknown", box["style"])
        assertEquals(true, box["estimate"])
        val rows = (((read.props["details"] as Map<*, *>)["gap"] as Map<*, *>)["sections"] as List<*>).filterIsInstance<Map<*, *>>().flatMap { (it["rows"] as List<*>).filterIsInstance<Map<*, *>>() }
        assertEquals("unknown: no load case", rows.single()["note"])
        assertNull(rows.single()["value"])
    }

    @Test
    fun a_field_with_no_limit_is_scaled_to_the_range_read_and_a_floor_and_ceiling_both_hold() {
        val noLimit = assertNotNull(readScene(answer(listOf(member("a", 20), member("b", 60)), limits = emptyMap()), "utilisation"))
        assertEquals(20.0, noLimit.props["min"])
        assertEquals(60.0, noLimit.props["max"])
        val floor = assertNotNull(readScene(answer(listOf(member("a", 50), member("b", 70)), limits = mapOf("utilisation" to mapOf("low" to 60, "high" to 90))), "utilisation"))
        assertEquals("outside", boxesOf(floor).first { it["id"] == "a" }["style"])
        assertEquals(60.0, floor.props["min"])
    }

    @Test
    fun a_next_page_is_said_and_a_card_names_each_field_with_its_source() {
        val read = assertNotNull(readScene(answer(listOf(member("m1", 0.4)), page = mapOf("region" to "frame", "next" to "p2")), "utilisation"))
        assertTrue(read.notices.any { "more follows" in it && "p2" in it })
        val detail = (read.props["details"] as Map<*, *>)["m1"] as Map<*, *>
        assertEquals("m1", detail["title"])
        val row = ((detail["sections"] as List<*>).filterIsInstance<Map<*, *>>().single()["rows"] as List<*>).filterIsInstance<Map<*, *>>().single()
        assertEquals("0.4 ratio", row["value"])
        assertEquals("row-1", row["note"])
    }

    @Test
    fun an_object_the_engine_could_not_measure_is_not_drawn_and_is_listed_and_does_not_widen_the_range() {
        val unmeasured = mapOf("id" to "slab", "type" to "slab", "system" to "frame", "box_mm" to null, "unmeasured" to mapOf("field" to "height", "why" to "no thickness"), "fields" to mapOf("utilisation" to mapOf("value" to 9.0, "unit" to "ratio", "source" to "row-9")))
        val read = assertNotNull(readScene(answer(listOf(member("m1", 0.4), unmeasured), limits = emptyMap()), "utilisation"))
        assertEquals(listOf("m1"), boxesOf(read).map { it["id"] })
        assertTrue(read.notices.any { "'slab' is not measured" in it && "no thickness" in it })
        assertNull(read.props["max"])
    }

    @Test
    fun a_computed_source_is_shown_as_computed_with_what_its_function_reads() {
        val computed = member("m1", 0.4, mapOf("computed" to "structural.utilisation"))
        val withReads = answer(listOf(computed)) + ("computed_by" to mapOf("structural.utilisation" to mapOf("reads" to listOf("row-1", "setting:frame.load"))))
        val read = assertNotNull(readScene(withReads, "utilisation"))
        val row = (((read.props["details"] as Map<*, *>)["m1"] as Map<*, *>)["sections"] as List<*>).filterIsInstance<Map<*, *>>().single()["rows"] as List<*>
        assertEquals("computed: structural.utilisation, which reads row-1, setting:frame.load", (row.single() as Map<*, *>)["note"])
    }

    @Test
    fun a_tilt_about_the_width_axis_is_a_pitch_and_another_axis_is_named_and_drawn_level() {
        fun tilted(id: String, axis: String) = member(id, 0.4).let { it + ("box_mm" to (it["box_mm"] as Map<*, *> + ("tilt" to mapOf("axis" to axis, "deg" to 20)))) }
        val read = assertNotNull(readScene(answer(listOf(tilted("rafter", "width"), tilted("sheet", "depth"))), "utilisation"))
        assertEquals(20, boxesOf(read).first { it["id"] == "rafter" }["pitch_deg"])
        assertNull(boxesOf(read).first { it["id"] == "sheet" }["pitch_deg"])
        assertTrue(read.notices.any { "'sheet'" in it && "depth" in it })
    }

    @Test
    fun the_legend_rounds_to_three_significant_figures() {
        assertEquals("0.0769 ratio", heatLegendText(0.07692307692307693, "ratio"))
        assertEquals("4.03 ratio", heatLegendText(4.03045111619211, "ratio"))
        assertEquals("40 C", heatLegendText(40.0, "C"))
    }

    @Test
    fun without_colour_by_every_object_is_drawn_plain_and_something_that_is_not_an_answer_reads_as_none() {
        val read = assertNotNull(readScene(answer(listOf(member("m1", 0.4))), null))
        assertEquals("object", boxesOf(read).single()["style"])
        assertNull(read.props["min"])
        assertNull(readScene("not an answer", "utilisation"))
    }
}
