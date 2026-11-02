package ai.factoredui.engine.layout

import ai.factoredui.compose.schema.DeviceClass
import ai.factoredui.compose.schema.LayoutThresholdSet
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val SHEET_FILL = "#202028"
private const val GROUND = "#ffffff"
private const val PHONE_WIDTH = 390.0
private const val PHONE_HEIGHT = 844.0

private fun node(
    id: String,
    type: String,
    x: Double,
    y: Double,
    w: Double,
    h: Double,
    children: List<JsonObject> = emptyList(),
    fill: String? = null,
    ink: String? = null,
    action: Boolean = false,
    stop: String? = null,
): JsonObject = buildJsonObject {
    put("id", id)
    put("type", type)
    put("visible", true)
    put("composed", true)
    put("bounds", buildJsonObject { put("x", x); put("y", y); put("w", w); put("h", h) })
    put("action", if (action) JsonPrimitive("go") else JsonNull)
    fill?.let { put("fill", it) }
    ink?.let { put("ink", it) }
    put("props", buildJsonObject { stop?.let { put("stop", it) } })
    put("children", JsonArray(children))
}

private fun page(vararg children: JsonObject): JsonObject = node("page", "stack", 0.0, 0.0, PHONE_WIDTH, PHONE_HEIGHT, children.toList())

private fun map(height: Double = PHONE_HEIGHT) = node("map", "geomap", 0.0, 0.0, PHONE_WIDTH, height)

private fun sheetFrom(top: Double, vararg inside: JsonObject, stop: String = "peek") =
    node("sheet", "sheet", 0.0, top, PHONE_WIDTH, PHONE_HEIGHT - top, inside.toList(), fill = SHEET_FILL, stop = stop)

private class SheetPixels(private val sheetTop: Int) : PixelGrid {
    override val width = PHONE_WIDTH.toInt()
    override val height = PHONE_HEIGHT.toInt()
    override fun rgbAt(x: Int, y: Int): Int = if (y >= sheetTop) 0x202028 else 0x88aa66
}

private fun phone(vararg captures: LayoutCapture) =
    LayoutPage("plan", DeviceClass.COMPACT, PHONE_WIDTH, PHONE_HEIGHT, GROUND, captures.toList())

private fun readings(page: LayoutPage, device: DeviceClass = page.device) =
    readLayout(page, LayoutThresholdSet().forDevice(device), "2026-11-02T00:00:00Z").associateBy { it.factor.factorName }

private fun closedSheetPhone(): LayoutPage =
    phone(LayoutCapture("peek", page(map(796.0), sheetFrom(796.0)), SheetPixels(796)))

class LayoutFactorsTest {
    @Test
    fun a_phone_page_whose_map_leaves_only_a_handle_to_the_sheet_passes_the_main_share() {
        val share = readings(closedSheetPhone()).getValue("main_share_peek")
        assertEquals(LayoutVerdict.PASS, share.verdict)
        assertEquals(796.0 / 844.0, share.factor.value, 1e-9)
    }

    @Test
    fun a_main_view_covered_by_a_tall_sheet_fails_the_main_share() {
        val covered = phone(LayoutCapture("peek", page(map(), sheetFrom(300.0)), SheetPixels(300)))
        val share = readings(covered).getValue("main_share_peek")
        assertEquals(LayoutVerdict.FAIL, share.verdict)
        assertEquals(300.0 / 844.0, share.factor.value, 1e-9)
    }

    @Test
    fun a_half_open_sheet_lowers_the_main_share_instead_of_reading_the_whole_screen() {
        val stops = phone(
            LayoutCapture("peek", page(map(), sheetFrom(796.0)), SheetPixels(796)),
            LayoutCapture("half", page(map(), sheetFrom(524.0, stop = "half")), SheetPixels(524)),
        )
        val found = readings(stops)
        assertTrue(found.getValue("main_share_half").factor.value < found.getValue("main_share_peek").factor.value)
    }

    @Test
    fun a_sheet_the_tree_places_where_the_pixels_show_none_reads_blind_not_pass() {
        val disagreeing = phone(LayoutCapture("peek", page(map(796.0), sheetFrom(796.0)), SheetPixels(500)))
        assertEquals(LayoutVerdict.BLIND, readings(disagreeing).getValue("main_share_peek").verdict)
    }

    @Test
    fun a_covering_sheet_with_no_screenshot_reads_blind() {
        val unshot = phone(LayoutCapture("peek", page(map(796.0), sheetFrom(796.0)), null))
        assertEquals(LayoutVerdict.BLIND, readings(unshot).getValue("main_share_peek").verdict)
    }

    @Test
    fun the_same_main_share_passes_on_a_desktop_and_fails_on_a_phone() {
        val tree = page(node("map", "geomap", 0.0, 0.0, PHONE_WIDTH, 540.0))
        val onPhone = phone(LayoutCapture(null, tree, null))
        assertEquals(LayoutVerdict.FAIL, readings(onPhone).getValue("main_share").verdict)
        assertEquals(LayoutVerdict.PASS, readings(onPhone, DeviceClass.EXPANDED).getValue("main_share").verdict)
    }

    @Test
    fun a_thirty_dp_tap_target_fails_on_a_phone() {
        val small = phone(LayoutCapture(null, page(map(700.0), node("go", "button", 10.0, 720.0, 120.0, 30.0, action = true)), null))
        val found = readings(small).getValue("smallest_tap_target_dp")
        assertEquals(LayoutVerdict.FAIL, found.verdict)
        assertEquals(30.0, found.factor.value, 1e-9)
    }

    @Test
    fun ten_dp_text_fails_the_smallest_text_line() {
        val tiny = phone(LayoutCapture(null, page(map(700.0), node("note", "text", 10.0, 720.0, 200.0, 10.0, ink = "#000000")), null))
        assertEquals(LayoutVerdict.FAIL, readings(tiny).getValue("smallest_text_line_dp").verdict)
    }

    @Test
    fun two_buttons_drawn_over_each_other_are_counted_as_overlapping() {
        val stacked = phone(LayoutCapture(null, page(
            map(600.0),
            node("a", "button", 10.0, 650.0, 150.0, 48.0, action = true),
            node("b", "button", 80.0, 660.0, 150.0, 48.0, action = true),
        ), null))
        assertEquals(1.0, readings(stacked).getValue("overlapping_controls").factor.value)
    }

    @Test
    fun a_sheet_over_the_map_is_not_an_overlap_of_controls() {
        val found = readings(closedSheetPhone()).getValue("overlapping_controls")
        assertEquals(LayoutVerdict.PASS, found.verdict)
    }

    @Test
    fun a_control_that_only_the_half_stop_holds_costs_one_tap() {
        val slider = node("weight", "slider", 10.0, 540.0, 300.0, 48.0)
        val stops = phone(
            LayoutCapture("peek", page(map(), sheetFrom(796.0)), SheetPixels(796)),
            LayoutCapture("half", page(map(), sheetFrom(524.0, slider, stop = "half")), SheetPixels(524)),
        )
        assertEquals(1.0, readings(stops).getValue("taps_to_reach_every_control").factor.value)
    }

    @Test
    fun grey_text_on_a_white_page_below_four_and_a_half_to_one_fails_contrast() {
        val faint = phone(LayoutCapture(null, page(map(700.0), node("note", "text", 10.0, 720.0, 200.0, 20.0, ink = "#bbbbbb")), null))
        assertEquals(LayoutVerdict.FAIL, readings(faint).getValue("min_contrast_ratio").verdict)
    }

    @Test
    fun a_node_drawn_past_the_window_edge_is_counted() {
        val wide = phone(LayoutCapture(null, page(map(700.0), node("wide", "button", 300.0, 720.0, 200.0, 48.0, action = true)), null))
        assertEquals(1.0, readings(wide).getValue("nodes_past_the_window").factor.value)
    }

    @Test
    fun a_page_with_no_controls_has_no_above_the_fold_reading_to_fail() {
        val readonly = phone(LayoutCapture(null, page(map(700.0)), null))
        assertTrue("controls_above_the_fold" !in readings(readonly))
    }

    @Test
    fun every_reading_carries_its_page_and_device() {
        val factor = readings(closedSheetPhone()).getValue("main_share_peek").factor
        assertEquals("plan", factor.page)
        assertEquals("compact", factor.device)
    }
}
