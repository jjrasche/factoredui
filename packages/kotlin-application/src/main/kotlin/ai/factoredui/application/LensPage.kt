package ai.factoredui.application

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private const val CARD = "raised"
private const val DOCK = "surface"
private const val WORLD_GROUND = "#2b3c50"
private const val DOCK_CLOSED_DP = 260
private const val DOCK_OPEN_DP = 840
private const val GUTTER_DP = 16
private const val RESET_VIEW_GAP_DP = 100
private const val BLUEPRINT_PATH = "{ui.blueprint}"
private const val SELECTION_CARD = "selection.card"

private fun systemPath(system: String) = "ui.sys.${system.replace(Regex("[^A-Za-z0-9_]"), "_")}"

internal fun inDark(node: JsonObject): JsonObject {
    val type = (node["type"] as JsonPrimitive).content
    val props = (node["props"] as JsonObject).toMutableMap()
    when (type) {
        "text" -> props["color"] = JsonPrimitive(if ((props["variant"] as? JsonPrimitive)?.content == "caption") MUTED else INK)
        "card" -> props["background"] = JsonPrimitive(CARD)
    }
    val children = (node["children"] as JsonArray).map { inDark(it as JsonObject) }
    return JsonObject(node + mapOf("props" to JsonObject(props), "children" to JsonArray(children)))
}

private fun worldProps(node: JsonObject, extra: Map<String, Any?>): JsonObject {
    val props = (node["props"] as JsonObject).toMutableMap()
    props["legend"] = JsonArray(emptyList())
    extra.forEach { (key, value) -> props[key] = jsonOf(value) }
    return JsonObject(node + mapOf("props" to JsonObject(props)))
}

private fun fullWorld(node: JsonObject, extra: Map<String, Any?> = emptyMap(), visible: String? = null): JsonObject {
    val placed = worldProps(node, mapOf("fill" to true, "bottom_inset_dp" to DOCK_CLOSED_DP, "anchor" to "fill", "detail_text" to "{$SELECTION_CARD}") + extra)
    return if (visible == null) placed else JsonObject(placed + ("visible" to JsonPrimitive(visible)))
}

private fun topStrip(hasBlueprint: Boolean): JsonObject = specNode(
    "lens", "row", mapOf("gap" to 8, "align" to "center", "padding" to GUTTER_DP, "anchor" to "top"),
    listOfNotNull(
        specNode("lens.title", "text", mapOf("value" to "", "flex" to 1)),
        if (hasBlueprint) specNode("lens.blueprint", "toggle", mapOf("label" to "Blueprint", "value" to BLUEPRINT_PATH, "width" to 170)) else null,
        specNode("lens.reset-gap", "column", mapOf("width" to RESET_VIEW_GAP_DP)),
    ),
)

private fun selectSystem(chosen: String, all: List<String>): Map<String, Any?> =
    mapOf("action" to "set_binding", "params" to mapOf("values" to all.associate { systemPath(it) to (it == chosen) } + (STEP_PATH to if (chosen == BUILD_VIEW) 0 else null)))

private fun systemRow(name: String, caption: String?, drawn: List<String>): JsonObject =
    JsonObject(
        specNode(
            "systems.$name", "card", mapOf("background" to CARD, "padding" to 12, "cornerRadius" to 12),
            listOf(
                specNode(
                    "systems.$name.row", "row", mapOf("gap" to 8),
                    listOfNotNull(textNode("systems.$name.name", humanize(name), flex = 1), caption?.let { textNode("systems.$name.caption", it, variant = "caption", color = MUTED) }),
                ),
            ),
        ) + ("action" to jsonOf(selectSystem(name, drawn))),
    )

private fun systemList(drawn: List<String>, viewNames: List<String>, describing: Describing?): JsonObject {
    val solutions = describing?.takeIf { it.failure == null }?.systems.orEmpty().associate { it.id to it.solution.takeIf { solution -> solution.isNotEmpty() } }
    val all = drawn + viewNames
    val rows = drawn.map { systemRow(it, solutions[it], all) } + viewNames.map { systemRow(it, "view", all) }
    return specNode("systems", "column", mapOf("gap" to 8), listOf(textNode("systems.hint", "Tap a system to see what it is set to, or a piece in the drawing.", variant = "caption", color = MUTED)) + rows)
}

private const val ODDS_VIEW = "odds"
private const val BOARD_VIEW = "board"
private const val BUILD_VIEW = "build"
private const val TRACE_VIEW = "stocks"

private fun views(boarded: Boarded?, sampled: Sampled?, built: Built?, traced: Traced?): List<String> =
    listOfNotNull(if (built != null) BUILD_VIEW else null, if (sampled != null) ODDS_VIEW else null, if (boarded != null) BOARD_VIEW else null, if (traced != null) TRACE_VIEW else null)

private fun viewPanels(boarded: Boarded?, sampled: Sampled?, built: Built?, traced: Traced?): List<JsonObject> = listOfNotNull(
    built?.let { withVisible(inDark(buildNode(it)), systemPath(BUILD_VIEW)) }, traced?.let { withVisible(inDark(traceNode(it)), systemPath(TRACE_VIEW)) },
    sampled?.let { withVisible(inDark(oddsNode(it)), systemPath(ODDS_VIEW)) }, boarded?.let { withVisible(inDark(boardNode(it)), systemPath(BOARD_VIEW)) },
)

private fun withVisible(node: JsonObject, path: String): JsonObject = JsonObject(node + ("visible" to JsonPrimitive("{$path}")))

private fun selectedPanels(describing: Describing?, file: SystemFile, drawn: List<String>): List<JsonObject> {
    val cards = describing?.takeIf { it.failure == null }?.let { describeNode(it).let { node -> (node["children"] as JsonArray).map { child -> child as JsonObject } } }.orEmpty()
    return drawn.mapNotNull { name ->
        cards.firstOrNull { (it["id"] as JsonPrimitive).content == "describe.$name.card" }?.let { withVisible(inDark(it), systemPath(name)) }
    }
}

private fun elsewhere(describing: Describing?, file: SystemFile, expansion: Expansion, solving: Solving?, ranked: Ranked?): List<JsonObject> {
    val order = systemOrder(file, expansion)
    val counts = (expansion.objects.map { it.system } + expansion.instances.map { it.system }).groupingBy { it }.eachCount()
    val oldCards = if (describing == null || describing.failure != null) file.systems.map { systemCard(it, colourOfSystem(order, it.name), counts[it.name] ?: 0) } else emptyList()
    val failure = describing?.failure?.let { textNode("describe-failure", "The systems could not be described: $it", variant = "caption", color = MUTED) }
    return listOfNotNull(
        tablesNode(expansion), solving?.let(::solvingNode), ranked?.let(::rankingNode), failure, suppliedNode(expansion.supplied),
    ).map(::inDark) + oldCards.map(::inDark)
}

private fun pageSpec(root: JsonObject): JsonObject =
    JsonObject(mapOf("spec_version" to JsonPrimitive(1), "renderer_min" to JsonPrimitive(1), "theme" to JsonPrimitive("dark"), "state" to jsonOf(pickState()), "root" to root))

private fun idsBySystem(expansion: Expansion, geometry: Geometry?): Map<String, List<String>> =
    geometry?.pieces?.groupBy({ it.system }, { it.id })
        ?: (expansion.objects.map { it.system to it.id } + expansion.instances.map { it.system to it.id }).groupBy({ it.first }, { it.second })

private fun noDrawingPage(body: List<JsonObject>, note: String): JsonObject =
    specNode("root", "scrollview", mapOf("background" to HUD), listOf(specNode("page", "column", mapOf("gap" to GUTTER_DP, "padding" to GUTTER_DP), listOf(textNode("drawing.none", note, variant = "caption", color = MUTED)) + body)))

fun buildLensPageSpec(
    file: SystemFile,
    expansion: Expansion,
    solving: Solving? = null,
    overlaying: Overlaying? = null,
    ranked: Ranked? = null,
    describing: Describing? = null,
    geometried: Geometried? = null,
    boarded: Boarded? = null,
    sampled: Sampled? = null,
    built: Built? = null,
    traced: Traced? = null,
): JsonObject {
    val geometry = geometried?.geometry
    val shaded = volumeNode(file, expansion, overlaying?.overlay, geometry, built?.build?.stageOf)?.let { fullWorld(it, mapOf("ground" to WORLD_GROUND, "lighting" to "sun") + pickProps()) }
    val blueprint = geometry?.let { fullWorld(blueprintVolumeNode(it, expansion), visible = BLUEPRINT_PATH) }
    val drawn = systemOrder(file, expansion).filter { name -> expansion.objects.any { it.system == name } || expansion.instances.any { it.system == name } }
    val notes = listOfNotNull(
        overlaying?.let(::overlayNote), geometried?.failure?.let { textNode("blueprint-failure", "The geometry could not be read: $it", variant = "caption", color = MUTED) },
    ).map(::inDark)
    val drawings = geometry?.let { listOf(withVisible(inDark(blueprintDrawingsNode(it)), "ui.blueprint")) }.orEmpty()
    val bodyContent = notes + drawings + selectedPanels(describing, file, drawn) + viewPanels(boarded, sampled, built, traced) + systemList(drawn, views(boarded, sampled, built, traced), describing) + elsewhere(describing, file, expansion, solving, ranked)
    if (shaded == null && blueprint == null) return pageSpec(noDrawingPage(bodyContent, geometried?.failure?.let { "No drawing: the geometry could not be read." } ?: "This world has no drawing."))
    val body = specNode(
        "dock.body", "column", mapOf("gap" to 12, "padding" to GUTTER_DP),
        listOf(withVisible(textNode("dock.tapped", "{$SELECTION_CARD}", color = INK), SELECTION_CARD)) + bodyContent,
    )
    val dock = specNode("dock", "column", mapOf("anchor" to "bottom", "sheet" to mapOf("closed_dp" to DOCK_CLOSED_DP, "open_dp" to DOCK_OPEN_DP), "background" to DOCK), listOf(body), visible = if (shaded != null) "{$PICK_THROUGH_PATH}=$NOTHING_PICKED" else null)
    val piecePanel = if (shaded != null) listOf(piecePanelNode(idsBySystem(expansion, geometry))) else emptyList()
    val world = listOfNotNull(shaded, blueprint)
    return pageSpec(specNode("root", "stack", mapOf("fill" to true, "background" to HUD), world + listOf(topStrip(blueprint != null), dock) + piecePanel))
}
