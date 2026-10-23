package ai.factoredui.application

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal const val PIECE_PATH = "piece"
internal const val PICK_ITEMS_PATH = "pick.items"
internal const val PICK_THROUGH_PATH = "pick.through"
internal const val PICK_HIDDEN_PATH = "pick.hidden"
internal const val PICK_STOP_PATH = "pick.stop"
internal const val SOLUTION_OPEN_PATH = "pick.solution_open"
internal const val PICK_INSET_PATH = "pick.inset"
internal const val NOTHING_PICKED = -1

private const val PIECE_RAISED = "raised"
private const val SIDE_WIDTH_DP = 360
private const val STRIP_HEIGHT_DP = 40
private const val GUTTER_DP = 16
private const val CHIP_WIDTH_DP = 168
private const val FOOT_MM = 304.8

private fun bound(path: String) = "{$path}"

internal fun pickState(insetDp: Int): Map<String, Any?> = mapOf(
    "pick" to mapOf("items" to emptyList<Any?>(), "through" to NOTHING_PICKED, "hidden" to emptyList<String>(), "stop" to "peek", "solution_open" to false, "inset" to insetDp),
)

internal fun pickProps(): Map<String, Any?> = mapOf(
    "picked" to bound(PICK_ITEMS_PATH), "through" to bound(PICK_THROUGH_PATH), "hidden" to bound(PICK_HIDDEN_PATH), "detail_record" to bound(PIECE_PATH),
)

private fun slotted(node: JsonObject, slot: String?): JsonObject {
    if (slot == null) return node
    val props = (node["props"] as JsonObject).toMutableMap()
    props["slot"] = JsonPrimitive(slot)
    return JsonObject(node + ("props" to JsonObject(props)))
}

private fun withAction(node: JsonObject, params: Map<String, Any?>): JsonObject = JsonObject(node + ("action" to jsonOf(mapOf("action" to "set_binding", "params" to params))))

private fun checkStat(id: String, label: String, value: String, fraction: String?, status: String?, visible: String? = null): JsonObject =
    specNode(id, "stat", listOfNotNull("label" to label, "value" to value, fraction?.let { "fraction" to it }, status?.let { "status" to it }).toMap(), visible = visible)

private fun peekNodes(p: String, slot: String?): List<JsonObject> = listOf(
    textNode("$p.name", bound("$PIECE_PATH.title"), variant = "heading"),
    textNode("$p.kind", bound("$PIECE_PATH.kind"), variant = "caption", color = MUTED),
    checkStat("$p.binding", bound("$PIECE_PATH.binding.label"), bound("$PIECE_PATH.binding.value"), bound("$PIECE_PATH.binding.fraction"), bound("$PIECE_PATH.binding.status"), visible = bound("$PIECE_PATH.binding")),
).map { slotted(it, slot) }

private fun stepSwipe(): Map<String, Any?> = mapOf(
    "on_swipe" to mapOf(
        "left" to mapOf("action" to "set_binding", "params" to mapOf("path" to PICK_THROUGH_PATH, "add" to 1, "min" to 0)),
        "right" to mapOf("action" to "set_binding", "params" to mapOf("path" to PICK_THROUGH_PATH, "add" to -1, "min" to 0)),
    ),
)

private fun rayChips(p: String): JsonObject {
    val chip = withAction(specNode("$p.chip", "button", mapOf("label" to "{item.label}", "variant" to "outline", "size" to "sm", "width" to CHIP_WIDTH_DP)), mapOf("values" to mapOf(PICK_THROUGH_PATH to "{item.index}")))
    return specNode("$p.rays", "column", stepSwipe(), listOf(specNode("$p.chips", "row", mapOf("gap" to 8, "data" to bound(PICK_ITEMS_PATH), "scroll_to" to bound(PICK_THROUGH_PATH), "itemTemplate" to chip))))
}

private fun factRow(p: String): JsonObject = specNode(
    "$p.facts", "row", mapOf("gap" to 8),
    listOf(
        specNode("$p.weight", "stat", mapOf("label" to "Weight", "value" to bound("$PIECE_PATH.weight_text"), "flex" to 1), visible = bound("$PIECE_PATH.weight_text")),
        specNode("$p.cost", "stat", mapOf("label" to "Cost", "value" to bound("$PIECE_PATH.cost_text"), "flex" to 1), visible = bound("$PIECE_PATH.cost_text")),
    ),
)

private fun sizeLine(p: String): JsonObject = specNode(
    "$p.size", "column", mapOf("gap" to 2),
    listOf(textNode("$p.size.label", "Size", variant = "caption", color = MUTED), specNode("$p.size.value", "text", mapOf("value" to bound("$PIECE_PATH.size_text"), "variant" to "mono"))),
    visible = bound("$PIECE_PATH.size_text"),
)

private fun sectionList(p: String): JsonObject {
    val row = specNode(
        "$p.row", "row", mapOf("gap" to 8, "justify" to "between"),
        listOf(textNode("$p.row.label", "{item.label}", flex = 1), specNode("$p.row.value", "text", mapOf("value" to "{item.value}", "variant" to "mono", "color" to MUTED))),
    )
    val section = specNode(
        "$p.section", "column", mapOf("gap" to 4),
        listOf(textNode("$p.section.heading", "{item.heading}", variant = "caption", color = MUTED), specNode("$p.section.rows", "list", mapOf("data" to "{item.rows}", "stacked" to true, "itemTemplate" to row))),
    )
    return specNode("$p.sections", "list", mapOf("data" to bound("$PIECE_PATH.sections"), "stacked" to true, "itemTemplate" to section))
}

private fun layerStrip(p: String): JsonObject {
    val cell = specNode("$p.layer", "column", mapOf("flex" to "{item.share}", "background" to "{item.color}"))
    return specNode("$p.strip", "row", mapOf("gap" to 0, "justify" to "between", "height" to STRIP_HEIGHT_DP, "data" to bound("$PIECE_PATH.layers"), "itemTemplate" to cell), visible = bound("$PIECE_PATH.layers"))
}

private fun layerLegend(p: String): JsonObject {
    val row = specNode(
        "$p.layer.row", "row", mapOf("gap" to 8),
        listOf(
            textNode("$p.layer.label", "{item.label}", flex = 1),
            specNode("$p.layer.size", "text", mapOf("value" to "{item.size_text}", "variant" to "mono", "color" to MUTED)),
            specNode("$p.layer.note", "text", mapOf("value" to "{item.note}", "variant" to "caption", "color" to MUTED)),
        ),
    )
    return specNode("$p.legend", "list", mapOf("data" to bound("$PIECE_PATH.layers"), "stacked" to true, "itemTemplate" to row), visible = bound("$PIECE_PATH.layers"))
}

private fun checkList(p: String): JsonObject {
    val check = checkStat("$p.check", "{item.label}", "{item.value}", "{item.fraction}", "{item.status}")
    return specNode("$p.checks", "list", mapOf("data" to bound("$PIECE_PATH.checks"), "stacked" to true, "itemTemplate" to check), visible = bound("$PIECE_PATH.checks"))
}

private fun openSolution(p: String, system: String, hiddenIds: List<String>): JsonObject =
    withAction(
        specNode("$p.open.$system", "button", mapOf("label" to "Open this solution", "variant" to "primary", "size" to "md"), visible = "${bound("$PIECE_PATH.system")}=$system"),
        mapOf("values" to mapOf(PICK_HIDDEN_PATH to hiddenIds, SOLUTION_OPEN_PATH to true)),
    )

private fun backToAll(p: String): JsonObject =
    withAction(
        specNode("$p.back", "button", mapOf("label" to "Back to all", "variant" to "outline", "size" to "md"), visible = bound(SOLUTION_OPEN_PATH)),
        mapOf("values" to mapOf(PICK_HIDDEN_PATH to emptyList<String>(), SOLUTION_OPEN_PATH to false)),
    )

private fun halfNodes(p: String, slot: String?): List<JsonObject> = listOf(rayChips(p), sizeLine(p), factRow(p), sectionList(p)).map { slotted(it, slot) }

private fun fullNodes(p: String, idsBySystem: Map<String, List<String>>, slot: String?): List<JsonObject> {
    val everyId = idsBySystem.values.flatten()
    val solutions = idsBySystem.map { (system, ids) -> openSolution(p, system, everyId - ids.toSet()) }
    return (listOf(layerStrip(p), layerLegend(p), checkList(p)) + solutions + backToAll(p)).map { slotted(it, slot) }
}

private fun bottomSheet(idsBySystem: Map<String, List<String>>): JsonObject =
    specNode(
        "piece-sheet", "sheet", mapOf("stop" to bound(PICK_STOP_PATH), "peek_to" to bound(PICK_INSET_PATH)),
        peekNodes("sheet", "peek") + halfNodes("sheet", "half") + fullNodes("sheet", idsBySystem, "full"),
        visible = bound("window.is_compact"),
    )

private fun sidePanel(idsBySystem: Map<String, List<String>>): JsonObject =
    specNode(
        "piece-side", "column", mapOf("anchor" to "end", "width" to SIDE_WIDTH_DP, "gap" to 12, "padding" to GUTTER_DP, "background" to PIECE_RAISED),
        peekNodes("side", null) + halfNodes("side", null) + fullNodes("side", idsBySystem, null),
        visible = bound("window.is_at_least_medium"),
    )

/** What a tap picked, as a bottom sheet on a compact window and a panel on the right on a wider one; it shows only while something is picked. */
internal fun piecePanelNode(idsBySystem: Map<String, List<String>>): JsonObject =
    specNode("piece-panel", "stack", mapOf("fill" to true, "anchor" to "fill"), listOf(bottomSheet(idsBySystem), sidePanel(idsBySystem)), visible = bound("$PIECE_PATH.title"))

private const val SIXTEENTHS_PER_INCH = 16
private const val MM_PER_INCH_EXACT = 25.4
private const val MIN_LAYER_SHARE = 0.04
private val LAYER_COLOURS = listOf("#c9a66b", "#8fb8d8", "#9ccc9c", "#d08a8a", "#b39ddb", "#e0c36a")

/** A length the way a builder reads it: inches with their fraction below a foot (4 1/2 in), feet and inches from there. */
internal fun lengthText(mm: Double): String = if (mm < FOOT_MM) thicknessText(mm) else feetInches(mm)

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

private fun colourOfLayer(material: String): String = LAYER_COLOURS[Math.floorMod(material.hashCode(), LAYER_COLOURS.size)]

/** Each layer's share of the strip: in proportion to its thickness, none below [MIN_LAYER_SHARE], adding up to one. */
internal fun layerShares(thicknessesMm: List<Double>): List<Double> {
    val total = thicknessesMm.sum()
    if (total <= 0.0) return thicknessesMm.map { 0.0 }
    val proportional = thicknessesMm.map { it / total }
    val thin = proportional.indices.filter { proportional[it] < MIN_LAYER_SHARE }
    if (thin.isEmpty()) return proportional
    val thickTotal = proportional.indices.filter { it !in thin }.sumOf { proportional[it] }
    val spare = 1.0 - MIN_LAYER_SHARE * thin.size
    return proportional.indices.map { if (it in thin) MIN_LAYER_SHARE else proportional[it] / thickTotal * spare }
}

private fun layerEntries(layers: List<GeoLayer>): List<Map<String, Any?>> {
    val shares = layerShares(layers.map { it.thicknessMm })
    return layers.mapIndexed { index, layer ->
        mapOf("label" to layer.material, "size_text" to thicknessText(layer.thicknessMm), "note" to layer.name, "share" to shares[index], "color" to colourOfLayer(layer.material))
    }
}

private fun factsOf(piece: GeoPiece): Map<String, Any?> {
    val sides = listOf(piece.dxMm, piece.dyMm, piece.dzMm).sortedDescending()
    return mapOf("system" to piece.system, "kind" to sentenceCase(piece.material ?: piece.system), "size_text" to sides.joinToString(" × ") { lengthText(it) }) + (piece.label?.let { mapOf("title" to it) } ?: emptyMap()) +
        (if (piece.layers.isEmpty()) emptyMap() else mapOf("layers" to layerEntries(piece.layers)))
}

/** What the engine's geometry says about each piece, added to its card for the sheet: its system, kind, size and the layers it is made of. */
internal fun withPieceFacts(details: Map<String, Any?>, geometry: Geometry): Map<String, Any?> =
    details + geometry.pieces.associate { piece ->
        @Suppress("UNCHECKED_CAST")
        val card = details[piece.id] as? Map<String, Any?> ?: mapOf("title" to piece.id, "sections" to emptyList<Any?>())
        piece.id to (card + factsOf(piece))
    }
