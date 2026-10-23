package ai.factoredui.application

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private fun idOf(node: JsonObject) = (node["id"] as JsonPrimitive).content

private fun everyNode(node: JsonObject): List<JsonObject> = listOf(node) + (node["children"] as? JsonArray).orEmpty().flatMap { everyNode(it as JsonObject) } +
    ((node["props"] as? JsonObject)?.get("itemTemplate") as? JsonObject)?.let(::everyNode).orEmpty()

private fun nodeOf(root: JsonObject, id: String): JsonObject = assertNotNull(everyNode(root).firstOrNull { idOf(it) == id }, "no node '$id'")

private fun propOf(node: JsonObject, key: String): String? = ((node["props"] as JsonObject)[key] as? JsonPrimitive)?.content

private fun visibleOf(node: JsonObject): String? = (node["visible"] as? JsonPrimitive)?.content

private fun valuesOf(node: JsonObject): JsonObject = ((node["action"] as JsonObject)["params"] as JsonObject)["values"] as JsonObject

private val SYSTEMS = mapOf("walls" to listOf("w1", "w2"), "floor" to listOf("f1"))

private val SIP_PIECE = GeoPiece(
    "w1", "walls", 0.0, 0.0, 0.0, 2438.4, 114.3, 1219.2,
    layers = listOf(GeoLayer("outer skin", "OSB", 11.1125, null), GeoLayer("core", "EPS", 88.9, null), GeoLayer("inner skin", "OSB", 11.1125, null)),
    material = "sip_panel",
)

private val GEOMETRY = Geometry(listOf(SIP_PIECE, GeoPiece("f1", "floor", 0.0, 0.0, 0.0, 3000.0, 3000.0, 200.0)), emptyList(), emptyList(), emptyList())

class PiecePanelTest {
    @Test
    fun a_thickness_reads_the_way_a_builder_says_it() {
        assertEquals(listOf("7/16 in", "3 1/2 in", "1 in", "0 in"), listOf(11.1125, 88.9, 25.4, 0.0).map(::thicknessText))
    }

    @Test
    fun the_panel_shows_only_while_a_piece_is_picked_as_a_bottom_sheet_on_a_phone_and_a_side_column_on_a_wider_window() {
        val panel = piecePanelNode(SYSTEMS)
        assertEquals("{piece.title}", visibleOf(panel))
        val sheet = nodeOf(panel, "piece-sheet")
        assertEquals(listOf("sheet", "{window.is_compact}", "{pick.stop}"), listOf((sheet["type"] as JsonPrimitive).content, visibleOf(sheet), propOf(sheet, "stop")))
        val side = nodeOf(panel, "piece-side")
        assertEquals(listOf("{window.is_at_least_medium}", "end"), listOf(visibleOf(side), propOf(side, "anchor")))
    }

    @Test
    fun the_sheets_name_and_kind_are_the_peek_and_the_chips_size_and_sections_are_the_half() {
        val panel = piecePanelNode(SYSTEMS)
        assertEquals(listOf("peek", "peek", "peek"), listOf("sheet.name", "sheet.kind", "sheet.binding").map { propOf(nodeOf(panel, it), "slot") })
        assertEquals(listOf("half", "half", "half"), listOf("sheet.rays", "sheet.facts", "sheet.sections").map { propOf(nodeOf(panel, it), "slot") })
        assertEquals(listOf("full", "full", "full"), listOf("sheet.strip", "sheet.legend", "sheet.checks").map { propOf(nodeOf(panel, it), "slot") })
    }

    @Test
    fun the_side_column_holds_the_same_content_with_no_slots() {
        val panel = piecePanelNode(SYSTEMS)
        assertEquals(null, propOf(nodeOf(panel, "side.rays"), "slot"))
        assertTrue(everyNode(nodeOf(panel, "piece-side")).any { idOf(it) == "side.strip" })
    }

    @Test
    fun the_chips_are_a_row_over_what_a_tap_crossed_and_a_chip_steps_the_pick_to_its_index() {
        val panel = piecePanelNode(SYSTEMS)
        val chips = nodeOf(panel, "sheet.chips")
        assertEquals(listOf("row", "{pick.items}", "{pick.through}"), listOf((chips["type"] as JsonPrimitive).content, propOf(chips, "data"), propOf(chips, "scroll_to")))
        assertEquals("{item.index}", (valuesOf(nodeOf(panel, "sheet.chip"))["pick.through"] as JsonPrimitive).content)
    }

    @Test
    fun a_swipe_on_the_chips_steps_the_pick_one_way_or_the_other_and_never_below_the_first() {
        val swipe = (nodeOf(piecePanelNode(SYSTEMS), "sheet.rays")["props"] as JsonObject)["on_swipe"] as JsonObject
        fun paramsOf(side: String) = ((swipe[side] as JsonObject)["params"] as JsonObject).mapValues { (it.value as JsonPrimitive).content }
        assertEquals(mapOf("path" to "pick.through", "add" to "1", "min" to "0"), paramsOf("left"))
        assertEquals(mapOf("path" to "pick.through", "add" to "-1", "min" to "0"), paramsOf("right"))
    }

    @Test
    fun open_this_solution_hides_every_piece_of_the_other_systems_and_back_clears_it() {
        val panel = piecePanelNode(SYSTEMS)
        val open = nodeOf(panel, "sheet.open.walls")
        assertEquals("{piece.system}=walls", visibleOf(open))
        assertEquals(listOf("f1"), (valuesOf(open)["pick.hidden"] as JsonArray).map { (it as JsonPrimitive).content })
        val back = nodeOf(panel, "sheet.back")
        assertEquals("{pick.solution_open}", visibleOf(back))
        assertEquals(JsonArray(emptyList()), valuesOf(back)["pick.hidden"])
    }

    @Test
    fun the_layer_strip_is_a_row_of_cells_whose_width_and_colour_come_from_each_layer() {
        val strip = nodeOf(piecePanelNode(SYSTEMS), "sheet.strip")
        assertEquals(listOf("row", "{piece.layers}"), listOf((strip["type"] as JsonPrimitive).content, propOf(strip, "data")))
        val cell = nodeOf(strip, "sheet.layer")
        assertEquals(listOf("{item.share}", "{item.color}"), listOf(propOf(cell, "flex"), propOf(cell, "background")))
    }

    @Test
    fun layer_shares_follow_thickness_add_up_to_one_and_keep_a_thin_layer_visible() {
        val shares = layerShares(listOf(11.1125, 88.9, 11.1125))
        assertEquals(1.0, shares.sum(), 1e-9)
        assertEquals(shares[0], shares[2], 1e-9)
        val thin = layerShares(listOf(0.01, 10.0))
        assertEquals(1.0, thin.sum(), 1e-9)
        assertTrue(thin[0] >= 0.04 - 1e-9)
    }

    @Test
    fun the_engines_layers_reach_the_pieces_card_with_their_system_kind_size_share_and_colour() {
        val card = withPieceFacts(mapOf("w1" to mapOf("title" to "w1", "sections" to emptyList<Any?>())), GEOMETRY)["w1"] as Map<*, *>
        assertEquals("walls", card["system"])
        assertEquals("Sip panel", card["kind"])
        val layers = (card["layers"] as List<*>).map { it as Map<*, *> }
        assertEquals(listOf("OSB", "EPS", "OSB"), layers.map { it["label"] })
        assertEquals(listOf("7/16 in", "3 1/2 in", "7/16 in"), layers.map { it["size_text"] })
        assertEquals(layers[0]["color"], layers[2]["color"])
        assertEquals("w1", card["title"])
    }

    @Test
    fun a_piece_with_no_card_gets_one_named_for_its_id_and_no_layers_when_it_has_none() {
        val card = withPieceFacts(emptyMap(), GEOMETRY)["f1"] as Map<*, *>
        assertEquals("f1", card["title"])
        assertEquals(false, "layers" in card)
    }

    @Test
    fun the_page_state_starts_with_nothing_picked_and_the_sheet_peeking() {
        val pick = pickState()["pick"] as Map<*, *>
        assertEquals(listOf(NOTHING_PICKED, "peek", false), listOf(pick["through"], pick["stop"], pick["solution_open"]))
    }
}
