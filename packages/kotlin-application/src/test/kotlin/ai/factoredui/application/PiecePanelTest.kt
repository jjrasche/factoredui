package ai.factoredui.application

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

private fun idOf(node: JsonObject) = (node["id"] as JsonPrimitive).content

private fun everyNode(node: JsonObject): List<JsonObject> = listOf(node) + (node["children"] as? JsonArray).orEmpty().flatMap { everyNode(it as JsonObject) } +
    ((node["props"] as? JsonObject)?.get("itemTemplate") as? JsonObject)?.let(::everyNode).orEmpty()

private fun nodeOf(root: JsonObject, id: String): JsonObject = assertNotNull(everyNode(root).firstOrNull { idOf(it) == id }, "no node '$id'")

private fun propOf(node: JsonObject, key: String): String? = ((node["props"] as JsonObject)[key] as? JsonPrimitive)?.content

private fun visibleOf(node: JsonObject): String? = (node["visible"] as? JsonPrimitive)?.content

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
    fun the_panel_is_a_sheet_that_shows_only_while_a_piece_is_picked() {
        val panel = piecePanelNode(SYSTEMS)
        assertEquals("sheet", (panel["type"] as JsonPrimitive).content)
        assertEquals("{piece.title}", visibleOf(panel))
        assertEquals("{pick.stop}", propOf(panel, "stop"))
    }

    @Test
    fun the_name_and_kind_are_the_peek_and_the_chips_size_and_sections_are_the_half() {
        val panel = piecePanelNode(SYSTEMS)
        assertEquals(listOf("peek", "peek", "peek"), listOf("piece.name", "piece.kind", "piece.binding").map { propOf(nodeOf(panel, it), "slot") })
        assertEquals(listOf("half", "half", "half"), listOf("pick.items", "piece.facts", "piece.sections").map { propOf(nodeOf(panel, it), "slot") })
    }

    @Test
    fun a_chip_steps_the_pick_to_its_index() {
        val chip = nodeOf(piecePanelNode(SYSTEMS), "pick.chip")
        val action = chip["action"] as JsonObject
        assertEquals("set_binding", (action["action"] as JsonPrimitive).content)
        assertEquals("{item.index}", (((action["params"] as JsonObject)["values"] as JsonObject)["pick.through"] as JsonPrimitive).content)
    }

    @Test
    fun open_this_solution_hides_every_piece_of_the_other_systems_and_back_clears_it() {
        val panel = piecePanelNode(SYSTEMS)
        val open = nodeOf(panel, "piece.open.walls")
        assertEquals("{piece.system}=walls", visibleOf(open))
        val hidden = (((open["action"] as JsonObject)["params"] as JsonObject)["values"] as JsonObject)["pick.hidden"] as JsonArray
        assertEquals(listOf("f1"), hidden.map { (it as JsonPrimitive).content })
        val back = nodeOf(panel, "piece.back")
        assertEquals("{pick.solution_open}", visibleOf(back))
        assertEquals(JsonArray(emptyList()), (((back["action"] as JsonObject)["params"] as JsonObject)["values"] as JsonObject)["pick.hidden"])
    }

    @Test
    fun the_engines_layers_reach_the_pieces_card_with_their_system_kind_and_size() {
        val card = withPieceFacts(mapOf("w1" to mapOf("title" to "w1", "sections" to emptyList<Any?>())), GEOMETRY)["w1"] as Map<*, *>
        assertEquals("walls", card["system"])
        assertEquals("Sip panel", card["kind"])
        assertEquals(listOf("OSB", "EPS", "OSB"), (card["layers"] as List<*>).map { (it as Map<*, *>)["label"] })
        assertEquals(listOf("7/16 in", "3 1/2 in", "7/16 in"), (card["layers"] as List<*>).map { (it as Map<*, *>)["size_text"] })
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
