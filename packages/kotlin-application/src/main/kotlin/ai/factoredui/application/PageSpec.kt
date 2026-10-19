package ai.factoredui.application

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private const val PAGE_PADDING = 16
private const val PAGE_GAP = 12

private fun extentCaption(expansion: Expansion): String {
    val overall = expansion.extentMm?.let { (width, depth) -> " ${formatMm(width)} × ${formatMm(depth)} overall." }.orEmpty()
    return "Every length is in millimetres.$overall"
}

private fun hasUnknownSize(expansion: Expansion) = expansion.objects.any { it.widthMm == null || it.depthMm == null }

private const val UNKNOWN_SIZE_NOTE = "A line or a square is drawn where the expansion gives only part of a size."
private const val PLAN_NOTE = "Seen from above, north up."
private const val VOLUME_NOTE = "Drag to turn. Two fingers, or a right-drag, slide it; pinch or scroll to zoom. Each level stands on the one below, and a size the expansion does not give is drawn as a hatched stand-in."
private const val VIEW_PATH = "{view.index}"
private const val TABLES_PATH = "{ui.tables}"

private fun countBySystem(expansion: Expansion): Map<String, Int> =
    (expansion.objects.map { it.system } + expansion.instances.map { it.system }).groupingBy { it }.eachCount()

private fun noteNode(id: String, text: String) = textNode(id, text, variant = "caption", color = MUTED_INK)

private fun planView(file: SystemFile, expansion: Expansion, overlay: Overlay?): JsonObject = specNode(
    "plan-view", "column", mapOf("gap" to 4),
    listOfNotNull(
        noteNode("plan-note", PLAN_NOTE),
        planNode(file, expansion, overlay),
        if (hasUnknownSize(expansion)) noteNode("unknown-size-note", UNKNOWN_SIZE_NOTE) else null,
    ),
)

private fun overlayNote(overlaying: Overlaying): JsonObject =
    noteNode("overlay-note", overlaying.overlay?.let(::overlayCaption) ?: "The overlay could not be run: ${overlaying.failure}")

/** A plan and, when there is something to stand up, a solid view of the same expansion behind a switch. */
private fun viewsNode(file: SystemFile, expansion: Expansion, overlay: Overlay?): JsonObject {
    val plan = planView(file, expansion, overlay)
    val volume = volumeNode(file, expansion, overlay) ?: return plan
    val solid = specNode("volume-view", "column", mapOf("gap" to 4), listOf(volume, noteNode("volume-note", VOLUME_NOTE)))
    return specNode("views", "tabs", mapOf("items" to listOf("Plan", "3D"), "selectedIndex" to VIEW_PATH), listOf(plan, solid))
}

/** The tables of every object, behind a switch that starts off. */
private fun tablesNode(expansion: Expansion): JsonObject {
    val switch = specNode("tables-switch", "toggle", mapOf("label" to "Table", "value" to TABLES_PATH))
    val tables = specNode("tables", "column", mapOf("gap" to PAGE_GAP), listOfNotNull(objectTableNode(expansion), instanceTableNode(expansion)), visible = TABLES_PATH)
    return specNode("tables-section", "column", mapOf("gap" to 4), listOf(switch, tables))
}

/** The page for one expansion: the plan or the solid view, a table of every object, a card per system saying what is given and what is not. */
fun buildPageSpec(file: SystemFile, expansion: Expansion, solving: Solving? = null, overlaying: Overlaying? = null): JsonObject {
    val order = systemOrder(file, expansion)
    val counts = countBySystem(expansion)
    val cards = file.systems.map { systemCard(it, colourOfSystem(order, it.name), counts[it.name] ?: 0) }
    val children = listOfNotNull(
        textNode("title", file.title, variant = "heading", bold = true),
        textNode("caption", extentCaption(expansion), variant = "caption", color = MUTED_INK),
        overlaying?.let(::overlayNote),
        viewsNode(file, expansion, overlaying?.overlay),
        tablesNode(expansion),
        solving?.let(::solvingNode),
        textNode("cards-title", "What each system gives, and what it does not", variant = "subheading", bold = true),
    ) + cards + listOfNotNull(suppliedNode(expansion.supplied))
    val page = specNode("page", "column", mapOf("padding" to PAGE_PADDING, "gap" to PAGE_GAP), children)
    return JsonObject(
        mapOf(
            "spec_version" to JsonPrimitive(1),
            "renderer_min" to JsonPrimitive(1),
            "root" to specNode("root", "scrollview", children = listOf(page)),
        ),
    )
}
