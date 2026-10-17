package ai.factoredui.application

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

class SolvingFormatException(message: String) : IllegalArgumentException(message)

data class SolverAnswer(
    val function: String,
    val domain: String,
    val status: String,
    val detail: String,
    val values: Map<String, Double?>,
)

/** What `we solve` gave, or why it could not: the page says either on screen. */
data class Solving(val answers: List<SolverAnswer>, val failure: String? = null)

private fun JsonObject.text(key: String, where: String): String =
    (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: throw SolvingFormatException("$where lacks a text $key")

private fun valuesOf(entry: JsonObject): Map<String, Double?> =
    (entry["values"] as? JsonObject).orEmpty().mapValues { (_, value) -> (value as? JsonPrimitive)?.takeIf { it !is JsonNull && !it.isString }?.doubleOrNull }

private fun answerOf(entry: JsonObject): SolverAnswer {
    val where = "a solver"
    return SolverAnswer(
        function = entry.text("function", where),
        domain = entry.text("domain", where),
        status = entry.text("status", where),
        detail = (entry["detail"] as? JsonPrimitive)?.content.orEmpty(),
        values = valuesOf(entry),
    )
}

/** Reads the answer of `we solve`; anything that is not a list of solvers is an error. */
fun readSolving(text: String): Solving {
    val root = runCatching { Json.parseToJsonElement(text) }.getOrNull() as? JsonObject
        ?: throw SolvingFormatException("the solving is not a JSON object")
    val solvers = root["solvers"] as? JsonArray ?: throw SolvingFormatException("the solving has no list of solvers: $root")
    return Solving(solvers.map { answerOf(it as? JsonObject ?: throw SolvingFormatException("solvers holds an entry that is not an object")) })
}

private fun title(name: String) = name.replace('-', ' ').replace('_', ' ')

private fun formatQuantity(value: Double): String = if (value % 1.0 == 0.0) value.toLong().toString() else "%.3f".format(value).trimEnd('0').trimEnd('.')

private fun quantityNode(id: String, value: Double?): JsonObject =
    if (value == null) specNode(id, "text", mapOf("value" to null, "missing" to "–", "color" to MUTED_INK))
    else textNode(id, formatQuantity(value), bold = true)

private fun quantityLine(prefix: String, name: String, value: Double?): JsonObject =
    specNode(
        "$prefix.$name.line", "row", mapOf("gap" to 8, "align" to "center", "justify" to "between"),
        listOf(textNode("$prefix.$name.name", title(name), flex = 1), quantityNode("$prefix.$name.value", value)),
    )

private fun solverCard(answer: SolverAnswer): JsonObject {
    val prefix = "solver.${answer.domain}.${answer.function}"
    val heading = specNode(
        "$prefix.heading", "row", mapOf("gap" to 8, "align" to "center"),
        listOf(
            textNode("$prefix.title", title(answer.function), variant = "subheading", bold = true, flex = 1),
            specNode("$prefix.status", "chip", mapOf("label" to answer.status)),
        ),
    )
    val detail = answer.detail.takeIf { it.isNotEmpty() }?.let { textNode("$prefix.detail", it, variant = "caption", color = MUTED_INK) }
    val body = listOfNotNull(heading, textNode("$prefix.domain", answer.domain, variant = "caption", color = MUTED_INK), detail) +
        answer.values.map { (name, value) -> quantityLine(prefix, name, value) }
    return specNode("$prefix.card", "card", mapOf("padding" to 12), listOf(specNode("$prefix.body", "column", mapOf("gap" to 8), body)))
}

/** A card per solver, under one heading; a solve that failed is a line saying why. */
internal fun solvingNode(solving: Solving): JsonObject {
    val failure = solving.failure?.let { textNode("solvers.failure", "The solvers could not be run: $it", variant = "caption", color = MUTED_INK) }
    val heading = textNode("solvers.title", "What the solvers say", variant = "subheading", bold = true)
    return specNode("solvers", "column", mapOf("gap" to 8), listOfNotNull(heading, failure) + solving.answers.map(::solverCard))
}
