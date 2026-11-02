package ai.factoredui.application

import kotlinx.serialization.json.JsonObject

private const val NO_HOURS_HEADING = "No hours yet"

private fun runSpan(unit: String, card: FlowCard): String? {
    val first = card.firstRunAt ?: return null
    val last = card.lastRunAt ?: first
    return if (first == last) "ran at $unit ${formatFigure(first)}" else "ran ${card.runs} times, $unit ${formatFigure(first)} to ${formatFigure(last)}"
}

private fun crewLine(card: FlowCard): String? {
    if (card.people.isEmpty()) return null
    val learning = if (card.learners.isEmpty()) "" else "; learning: ${card.learners.joinToString(", ")}"
    return "people: ${card.people.joinToString(", ")}$learning"
}

private fun needsLine(card: FlowCard): String? =
    if (card.needs.isEmpty()) null else "needs: " + card.needs.entries.joinToString(", ") { "${it.key} ${formatFigure(it.value)}" }

private fun cardNode(unit: String, card: FlowCard): JsonObject {
    val lines = listOfNotNull(
        needsLine(card), runSpan(unit, card), crewLine(card), card.places.takeIf { it.isNotEmpty() }?.let { "placed: ${it.joinToString(", ")}" },
        card.blockedBy?.let { "blocked by: $it" },
    )
    val body = lines.mapIndexed { index, line -> textNode("board.${card.name}.line.$index", line, variant = "caption", color = MUTED_INK) }
    return specNode("board.${card.name}", "card", mapOf("padding" to 10, "gap" to 4), listOf(textNode("board.${card.name}.title", humanize(card.name.replace('-', '_')), bold = true)) + body)
}

private fun section(id: String, heading: String, unit: String, cards: List<FlowCard>): List<JsonObject> =
    if (cards.isEmpty()) emptyList() else listOf(textNode("board.$id.heading", heading, variant = "caption", color = MUTED_INK)) + cards.map { cardNode(unit, it) }

/** A card for each flow of the simulation, in the order they first ran; flows that never ran are grouped as having no hours yet. */
internal fun boardNode(boarded: Boarded): JsonObject {
    val title = textNode("board.title", "Task board", variant = "subheading", bold = true)
    val board = boarded.board ?: return specNode("board", "column", mapOf("gap" to 8), listOf(title, textNode("board.failure", "The simulation could not be run: ${boarded.failure}", variant = "caption", color = MUTED_INK)))
    val (ran, idle) = board.cards.partition { it.runs > 0 }
    val ordered = ran.sortedBy { it.firstRunAt }
    val note = textNode("board.note", "The simulation ended ${board.endedAs}; times are in ${board.unit}s.", variant = "caption", color = MUTED_INK)
    return specNode("board", "column", mapOf("gap" to 8), listOf(title, note) + section("ran", "Ran", board.unit, ordered) + section("idle", NO_HOURS_HEADING, board.unit, idle))
}
