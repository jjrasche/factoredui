package ai.factoredui.application

import kotlinx.serialization.json.JsonObject

internal const val PIECE_PATH = "piece"
internal const val PICK_ITEMS_PATH = "pick.items"
internal const val PICK_THROUGH_PATH = "pick.through"
internal const val PICK_HIDDEN_PATH = "pick.hidden"
internal const val PICK_STOP_PATH = "pick.stop"
internal const val SOLUTION_OPEN_PATH = "pick.solution_open"
internal const val NOTHING_PICKED = -1

private const val PIECE_RAISED = "raised"

private fun bound(path: String) = "{$path}"

/** Everything the page holds before anything is picked: nothing picked through, nothing hidden, the sheet peeking. */
internal fun pickState(): Map<String, Any?> = mapOf(
    "pick" to mapOf("items" to emptyList<Any?>(), "through" to NOTHING_PICKED, "hidden" to emptyList<String>(), "stop" to "peek", "solution_open" to false),
)

/** The props a drawing needs so a tap lists what it crossed, steps along the list and hands the selected entry to the sheet. */
internal fun pickProps(): Map<String, Any?> = mapOf(
    "picked" to bound(PICK_ITEMS_PATH), "through" to bound(PICK_THROUGH_PATH), "hidden" to bound(PICK_HIDDEN_PATH), "detail_record" to bound(PIECE_PATH),
)

private fun slotted(node: JsonObject, slot: String): JsonObject {
    val props = (node["props"] as JsonObject).toMutableMap()
    props["slot"] = kotlinx.serialization.json.JsonPrimitive(slot)
    return JsonObject(node + ("props" to JsonObject(props)))
}

private fun checkStat(id: String, label: String, value: String, fraction: String?, status: String?, visible: String? = null): JsonObject =
    specNode(id, "stat", listOfNotNull("label" to label, "value" to value, fraction?.let { "fraction" to it }, status?.let { "status" to it }).toMap(), visible = visible)

private fun peekSlot(): List<JsonObject> = listOf(
    slotted(textNode("piece.name", bound("$PIECE_PATH.title"), variant = "heading"), "peek"),
    slotted(textNode("piece.kind", bound("$PIECE_PATH.kind"), variant = "caption", color = MUTED), "peek"),
    slotted(checkStat("piece.binding", bound("$PIECE_PATH.binding.label"), bound("$PIECE_PATH.binding.value"), bound("$PIECE_PATH.binding.fraction"), bound("$PIECE_PATH.binding.status"), visible = bound("$PIECE_PATH.binding")), "peek"),
)

private fun rayChips(): JsonObject {
    val chip = specNode(
        "pick.chip", "button", mapOf("label" to "{item.label}", "variant" to "outline", "size" to "sm"),
    ).let { JsonObject(it + ("action" to jsonOf(mapOf("action" to "set_binding", "params" to mapOf("values" to mapOf(PICK_THROUGH_PATH to "{item.index}")))))) }
    return specNode("pick.items", "list", mapOf("data" to bound(PICK_ITEMS_PATH), "direction" to "horizontal", "step" to bound(PICK_THROUGH_PATH), "itemTemplate" to chip))
}

private fun factRow(): JsonObject = specNode(
    "piece.facts", "row", mapOf("gap" to 8),
    listOf(
        specNode("piece.size", "stat", mapOf("label" to "Size", "value" to bound("$PIECE_PATH.size_text"), "flex" to 1), visible = bound("$PIECE_PATH.size_text")),
        specNode("piece.weight", "stat", mapOf("label" to "Weight", "value" to bound("$PIECE_PATH.weight_text"), "flex" to 1), visible = bound("$PIECE_PATH.weight_text")),
        specNode("piece.cost", "stat", mapOf("label" to "Cost", "value" to bound("$PIECE_PATH.cost_text"), "flex" to 1), visible = bound("$PIECE_PATH.cost_text")),
    ),
)

private fun sectionList(): JsonObject {
    val row = specNode(
        "piece.row", "row", mapOf("gap" to 8, "justify" to "between"),
        listOf(textNode("piece.row.label", "{item.label}", flex = 1), specNode("piece.row.value", "text", mapOf("value" to "{item.value}", "variant" to "mono", "color" to MUTED))),
    )
    val section = specNode(
        "piece.section", "column", mapOf("gap" to 4),
        listOf(textNode("piece.section.heading", "{item.heading}", variant = "caption", color = MUTED), specNode("piece.section.rows", "list", mapOf("data" to "{item.rows}", "itemTemplate" to row))),
    )
    return specNode("piece.sections", "list", mapOf("data" to bound("$PIECE_PATH.sections"), "itemTemplate" to section))
}

private fun halfSlot(): List<JsonObject> = listOf(rayChips(), factRow(), sectionList()).map { slotted(it, "half") }

private fun checkList(): JsonObject {
    val check = checkStat("piece.check", "{item.label}", "{item.value}", "{item.fraction}", "{item.status}")
    return specNode("piece.checks", "list", mapOf("data" to bound("$PIECE_PATH.checks"), "itemTemplate" to check), visible = bound("$PIECE_PATH.checks"))
}

private fun openSolution(system: String, hiddenIds: List<String>): JsonObject =
    JsonObject(
        specNode("piece.open.$system", "button", mapOf("label" to "Open this solution", "variant" to "primary", "size" to "md"), visible = "${bound("$PIECE_PATH.system")}=$system") +
            ("action" to jsonOf(mapOf("action" to "set_binding", "params" to mapOf("values" to mapOf(PICK_HIDDEN_PATH to hiddenIds, SOLUTION_OPEN_PATH to true))))),
    )

private fun backToAll(): JsonObject =
    JsonObject(
        specNode("piece.back", "button", mapOf("label" to "Back to all", "variant" to "outline", "size" to "md"), visible = bound(SOLUTION_OPEN_PATH)) +
            ("action" to jsonOf(mapOf("action" to "set_binding", "params" to mapOf("values" to mapOf(PICK_HIDDEN_PATH to emptyList<String>(), SOLUTION_OPEN_PATH to false))))),
    )

private fun fullSlot(idsBySystem: Map<String, List<String>>): List<JsonObject> {
    val everyId = idsBySystem.values.flatten()
    val solutions = idsBySystem.map { (system, ids) -> openSolution(system, everyId - ids.toSet()) }
    val layers = specNode("piece.layers", "layers", mapOf("layers" to bound("$PIECE_PATH.layers")), visible = bound("$PIECE_PATH.layers"))
    return (listOf(layers, checkList()) + solutions + backToAll()).map { slotted(it, "full") }
}

/** The panel for what a tap picked: the piece's name, kind and binding check at a glance; the pieces the tap crossed, its size, weight and cost at half; its layers, every check against its limit and "open this solution" at full. It shows only while something is picked. */
internal fun piecePanelNode(idsBySystem: Map<String, List<String>>): JsonObject =
    specNode(
        "piece-sheet", "sheet", mapOf("stop" to bound(PICK_STOP_PATH), "background" to PIECE_RAISED),
        peekSlot() + halfSlot() + fullSlot(idsBySystem), visible = bound("$PIECE_PATH.title"),
    )

private const val SIXTEENTHS_PER_INCH = 16
private const val MM_PER_INCH_EXACT = 25.4

/** A thickness the way a builder reads it: 7/16 in, 3 1/2 in. */
internal fun thicknessText(mm: Double): String {
    val sixteenths = Math.round(mm / MM_PER_INCH_EXACT * SIXTEENTHS_PER_INCH).toInt()
    val whole = sixteenths / SIXTEENTHS_PER_INCH
    val remainder = sixteenths % SIXTEENTHS_PER_INCH
    val divisor = if (remainder == 0) 1 else gcdOf(remainder, SIXTEENTHS_PER_INCH)
    val fraction = if (remainder == 0) "" else "${remainder / divisor}/${SIXTEENTHS_PER_INCH / divisor}"
    return listOf(if (whole > 0 || fraction.isEmpty()) whole.toString() else "", fraction).filter { it.isNotEmpty() }.joinToString(" ") + " in"
}

private fun gcdOf(first: Int, second: Int): Int = if (second == 0) first else gcdOf(second, first % second)

private fun layerEntry(layer: GeoLayer): Map<String, Any?> =
    mapOf("label" to layer.material, "size" to layer.thicknessMm / MM_PER_INCH_EXACT, "size_text" to thicknessText(layer.thicknessMm), "note" to layer.name)

private fun factsOf(piece: GeoPiece): Map<String, Any?> {
    val sides = listOf(piece.dxMm, piece.dyMm, piece.dzMm).sortedDescending()
    return mapOf(
        "system" to piece.system, "kind" to sentenceCase(piece.material ?: piece.system), "size_text" to sides.joinToString(" × ") { feetInches(it) },
    ) + (if (piece.layers.isEmpty()) emptyMap() else mapOf("layers" to piece.layers.map(::layerEntry)))
}

/** What the engine's geometry says about each piece, added to its card for the sheet: its system, kind, size and the layers it is made of. */
internal fun withPieceFacts(details: Map<String, Any?>, geometry: Geometry): Map<String, Any?> =
    details + geometry.pieces.associate { piece ->
        @Suppress("UNCHECKED_CAST")
        val card = details[piece.id] as? Map<String, Any?> ?: mapOf("title" to piece.id, "sections" to emptyList<Any?>())
        piece.id to (card + factsOf(piece))
    }