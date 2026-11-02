package ai.factoredui.application

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private const val PAGE_PADDING = 16
private const val PAGE_GAP = 12

private fun extentCaption(expansion: Expansion): String {
    val overall = expansion.extentMm?.let { (width, depth) -> " ${formatMm(width)} × ${formatMm(depth)} overall." }.orEmpty()
    return "Every length is in millimetres.$overall"
}

private const val PLAN_NOTE = "Seen from above, north up."
private const val VOLUME_NOTE = "Drag to turn. Two fingers, or a right-drag, slide it; pinch or scroll to zoom. Each level stands on the one below, and a size the expansion does not give is drawn as a hatched stand-in."
private const val VIEW_PATH = "{view.index}"
private const val TABLES_PATH = "{ui.tables}"

private fun countBySystem(expansion: Expansion): Map<String, Int> =
    (expansion.objects.map { it.system } + expansion.instances.map { it.system }).groupingBy { it }.eachCount()

private fun noteNode(id: String, text: String) = textNode(id, text, variant = "caption", color = MUTED_INK)

private fun planView(file: SystemFile, expansion: Expansion, geometry: Geometry, overlay: Overlay?): JsonObject = specNode(
    "plan-view", "column", mapOf("gap" to 4),
    listOf(noteNode("plan-note", PLAN_NOTE), planNode(file, expansion, geometry, overlay)),
)

internal fun overlayNote(overlaying: Overlaying): JsonObject =
    noteNode("overlay-note", overlaying.overlay?.let(::overlayCaption) ?: "The overlay could not be run: ${overlaying.failure}")

private fun solidView(volume: JsonObject): JsonObject = specNode("volume-view", "column", mapOf("gap" to 4), listOf(volume, noteNode("volume-note", VOLUME_NOTE)))

/** The plan and the solid view, both from the engine's geometry, behind a switch; either alone when the other has nothing to show. */
private fun viewsNode(file: SystemFile, expansion: Expansion, overlay: Overlay?, geometry: Geometry?): JsonObject? {
    val plan = geometry?.let { planView(file, expansion, it, overlay) }
    val solid = volumeNode(file, expansion, overlay, geometry)?.let(::solidView)
    return if (plan != null && solid != null) specNode("views", "tabs", mapOf("items" to listOf("Plan", "3D"), "selectedIndex" to VIEW_PATH), listOf(plan, solid)) else plan ?: solid
}

/** The tables of every object, behind a switch that starts off. */
internal fun tablesNode(expansion: Expansion): JsonObject {
    val switch = specNode("tables-switch", "toggle", mapOf("label" to "Table", "value" to TABLES_PATH))
    val tables = specNode("tables", "column", mapOf("gap" to PAGE_GAP), listOfNotNull(objectTableNode(expansion), instanceTableNode(expansion)), visible = TABLES_PATH)
    return specNode("tables-section", "column", mapOf("gap" to 4), listOf(switch, tables))
}

/** The page for one expansion: the plan or the solid view, a table of every object, a card per system saying what is given and what is not. */
fun buildPageSpec(file: SystemFile, expansion: Expansion, solving: Solving? = null, overlaying: Overlaying? = null, ranked: Ranked? = null, describing: Describing? = null, geometried: Geometried? = null): JsonObject {
    val order = systemOrder(file, expansion)
    val counts = countBySystem(expansion)
    val isDescribed = describing != null && describing.failure == null
    val cards = if (isDescribed) listOf(describeNode(describing!!)) else file.systems.map { systemCard(it, colourOfSystem(order, it.name), counts[it.name] ?: 0) }
    val describeFailure = describing?.failure?.let { noteNode("describe-failure", "The systems could not be described: $it") }
    val children = listOfNotNull(
        textNode("title", file.title, variant = "heading", bold = true),
        textNode("caption", extentCaption(expansion), variant = "caption", color = MUTED_INK),
        overlaying?.let(::overlayNote),
        viewsNode(file, expansion, overlaying?.overlay, geometried?.geometry),
        geometried?.let { blueprintNode(it, expansion) },
        tablesNode(expansion),
        solving?.let(::solvingNode),
        ranked?.let(::rankingNode),
        describeFailure,
        if (isDescribed) null else textNode("cards-title", "What each system gives, and what it does not", variant = "subheading", bold = true),
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
