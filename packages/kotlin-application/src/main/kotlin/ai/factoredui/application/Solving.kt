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

private fun quantityNode(id: String, value: Double?, unit: String?): JsonObject =
    if (value == null) specNode(id, "text", mapOf("value" to null, "missing" to "–", "color" to MUTED_INK))
    else specNode(id, "text", mapOf("value" to figureText(value, unit), "variant" to "mono"))

private fun quantityLine(prefix: String, name: String, value: Double?): JsonObject {
    val label = figureLabelOf(name)
    return specNode(
        "$prefix.$name.line", "row", mapOf("gap" to 8, "justify" to "between"),
        listOf(textNode("$prefix.$name.name", label.text, flex = 1), quantityNode("$prefix.$name.value", value, label.unit)),
    )
}

private fun bindingKey(text: String): String = text.replace(Regex("[^A-Za-z0-9_]"), "_")

private fun solverCard(answer: SolverAnswer): JsonObject {
    val prefix = "solver.${answer.domain}.${answer.function}"
    val path = "{ui.solver.${bindingKey(answer.domain)}.${bindingKey(answer.function)}}"
    val heading = specNode(
        "$prefix.heading", "row", mapOf("gap" to 8),
        listOf(
            textNode("$prefix.title", sentenceCase(answer.function), variant = "subheading", flex = 1),
            textNode("$prefix.status", answer.status, variant = "caption", color = MUTED_INK),
        ),
    )
    val switch = specNode("$prefix.figures.switch", "toggle", mapOf("label" to "Show figures", "value" to path))
    val detail = answer.detail.takeIf { it.isNotEmpty() }?.let { textNode("$prefix.detail", it, variant = "caption", color = MUTED_INK) }
    val figures = specNode("$prefix.figures", "column", mapOf("gap" to 8), answer.values.map { (name, value) -> quantityLine(prefix, name, value) }, visible = path)
    val body = listOfNotNull(heading, textNode("$prefix.domain", sentenceCase(answer.domain), variant = "caption", color = MUTED_INK), detail, switch, figures)
    return specNode("$prefix.card", "card", mapOf("padding" to 16), listOf(specNode("$prefix.body", "column", mapOf("gap" to 8), body)))
}

internal fun solvingNode(solving: Solving): JsonObject {
    val failure = solving.failure?.let { textNode("solvers.failure", "The solvers could not be run: $it", variant = "caption", color = MUTED_INK) }
    val heading = textNode("solvers.title", "What the solvers say", variant = "subheading")
    return specNode("solvers", "column", mapOf("gap" to 8), listOfNotNull(heading, failure) + solving.answers.map(::solverCard))
}