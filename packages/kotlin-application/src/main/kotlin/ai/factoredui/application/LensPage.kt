package ai.factoredui.application

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private const val HUD = "#0b1220"
private const val PANEL = "#162033"
private const val WORLD_GROUND = "#2b3c50"
private const val INK = "#e6e8eb"
private const val MUTED = "#9aa3ad"
private const val DOCK_CLOSED_DP = 520
private const val DOCK_OPEN_DP = 840
private const val SYSTEMS_PER_ROW = 4
private const val BLUEPRINT_PATH = "{ui.blueprint}"
private const val SELECTION_CARD = "selection.card"

private fun systemPath(system: String) = "ui.sys.${system.replace(Regex("[^A-Za-z0-9_]"), "_")}"

/** The same node with its words, toggles and cards in the inks of a dark page. */
internal fun inDark(node: JsonObject): JsonObject {
    val type = (node["type"] as JsonPrimitive).content
    val props = (node["props"] as JsonObject).toMutableMap()
    when (type) {
        "text" -> props["color"] = JsonPrimitive(if ((props["variant"] as? JsonPrimitive)?.content == "caption") MUTED else INK)
        "toggle" -> props["color"] = JsonPrimitive(INK)
        "card" -> props["background"] = JsonPrimitive(PANEL)
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

private fun topStrip(hasBlueprint: Boolean, title: String): JsonObject = specNode(
    "lens", "row", mapOf("gap" to 8, "align" to "center", "padding" to 8, "background" to "${HUD}d9", "anchor" to "top"),
    listOfNotNull(
        textNode("lens.title", title, variant = "subheading", bold = true, color = INK, flex = 1),
        if (hasBlueprint) specNode("lens.blueprint", "toggle", mapOf("label" to "Blueprint", "value" to BLUEPRINT_PATH, "color" to INK, "width" to 170)) else null,
    ),
)

private fun selectSystem(chosen: String, all: List<String>): Map<String, Any?> =
    mapOf("action" to "set_binding", "params" to mapOf("values" to all.associate { systemPath(it) to (it == chosen) }))

private fun systemButtons(drawn: List<String>): JsonObject {
    val rows = drawn.chunked(SYSTEMS_PER_ROW).mapIndexed { index, names ->
        specNode(
            "systems.row.$index", "row", mapOf("gap" to 4),
            names.map { name ->
                JsonObject(specNode("systems.$name", "button", mapOf("label" to humanize(name), "variant" to "secondary", "size" to "sm", "flex" to 1)) + ("action" to jsonOf(selectSystem(name, drawn))))
            },
        )
    }
    return specNode("systems", "column", mapOf("gap" to 4, "padding" to 8), listOf(textNode("systems.hint", "Tap a system to see what it is set to, or a piece in the drawing.", variant = "caption", color = MUTED)) + rows)
}

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

/** The page as one world that fills the screen: lens strip on top, a draggable dock below holding the system buttons and what each system is set to, and a switch between the shaded solid and the blueprint. */
fun buildLensPageSpec(
    file: SystemFile,
    expansion: Expansion,
    solving: Solving? = null,
    overlaying: Overlaying? = null,
    ranked: Ranked? = null,
    describing: Describing? = null,
    geometried: Geometried? = null,
): JsonObject {
    val geometry = geometried?.geometry
    val shaded = volumeNode(file, expansion, overlaying?.overlay, geometry)?.let { fullWorld(it, mapOf("ground" to WORLD_GROUND, "lighting" to "sun")) }
    val blueprint = geometry?.let { fullWorld(blueprintVolumeNode(it, expansion), visible = BLUEPRINT_PATH) }
    val drawn = systemOrder(file, expansion).filter { name -> expansion.objects.any { it.system == name } || expansion.instances.any { it.system == name } }
    val notes = listOfNotNull(
        overlaying?.let(::overlayNote), geometried?.failure?.let { textNode("blueprint-failure", "The geometry could not be read: $it", variant = "caption", color = MUTED) },
    ).map(::inDark)
    val drawings = geometry?.let { listOf(withVisible(inDark(blueprintDrawingsNode(it, expansion)), "ui.blueprint")) }.orEmpty()
    val body = specNode(
        "dock.body", "column", mapOf("gap" to 10, "padding" to 12),
        listOf(withVisible(textNode("dock.tapped", "{$SELECTION_CARD}", color = INK), SELECTION_CARD)) + notes + drawings + selectedPanels(describing, file, drawn) + elsewhere(describing, file, expansion, solving, ranked),
    )
    val dock = specNode("dock", "column", mapOf("anchor" to "bottom", "sheet" to mapOf("closed_dp" to DOCK_CLOSED_DP, "open_dp" to DOCK_OPEN_DP), "background" to PANEL), listOf(systemButtons(drawn), body))
    val world = listOfNotNull(shaded, blueprint)
    val root = specNode("root", "stack", mapOf("fill" to true, "background" to HUD), world + listOf(topStrip(blueprint != null, file.title), dock))
    return JsonObject(mapOf("spec_version" to JsonPrimitive(1), "renderer_min" to JsonPrimitive(1), "root" to root))
}
