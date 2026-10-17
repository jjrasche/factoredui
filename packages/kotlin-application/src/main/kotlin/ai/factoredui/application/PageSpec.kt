package ai.factoredui.application

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private const val PAGE_PADDING = 16
private const val PAGE_GAP = 12

private fun extentCaption(expansion: Expansion): String {
    val overall = expansion.extentMm?.let { (width, depth) -> " ${formatMm(width)} × ${formatMm(depth)} overall." }.orEmpty()
    return "Seen from above, north up. Every length is in millimetres.$overall"
}

private fun hasUnknownSize(expansion: Expansion) = expansion.objects.any { it.widthMm == null || it.depthMm == null }

private const val UNKNOWN_SIZE_NOTE = "A line or a square is drawn where the expansion gives only part of a size."

private fun countBySystem(expansion: Expansion): Map<String, Int> =
    (expansion.objects.map { it.system } + expansion.instances.map { it.system }).groupingBy { it }.eachCount()

/** The page for one expansion: the plan, a table of every object, a card per system saying what is given and what is not. */
fun buildPageSpec(file: SystemFile, expansion: Expansion): JsonObject {
    val order = systemOrder(file, expansion)
    val counts = countBySystem(expansion)
    val cards = file.systems.map { systemCard(it, colourOfSystem(order, it.name), counts[it.name] ?: 0) }
    val children = listOfNotNull(
        textNode("title", file.title, variant = "heading", bold = true),
        textNode("caption", extentCaption(expansion), variant = "caption", color = MUTED_INK),
        planNode(file, expansion),
        if (hasUnknownSize(expansion)) textNode("unknown-size-note", UNKNOWN_SIZE_NOTE, variant = "caption", color = MUTED_INK) else null,
        objectTableNode(expansion),
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
