package ai.factoredui.application

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

class RankingFormatException(message: String) : IllegalArgumentException(message)

data class RankedFigure(
    val name: String,
    val value: Double?,
    val unit: String?,
    val good: String?,
    val score: Double?,
    val isMissing: Boolean,
    val source: String?,
    val evidence: String?,
)

data class RankedCandidate(
    val rank: Int,
    val solution: String,
    val title: String,
    val isChosen: Boolean,
    val scoreLow: Double,
    val scoreHigh: Double,
    val isSettled: Boolean,
    val missing: List<String>,
    val figures: List<RankedFigure>,
)

data class Ranking(val system: String, val weights: Map<String, Double>, val candidates: List<RankedCandidate>)

data class Ranked(val ranking: Ranking?, val failure: String? = null)

private fun JsonObject.text(key: String, where: String): String =
    (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: throw RankingFormatException("$where lacks a text $key")

private fun JsonObject.number(key: String, where: String): Double =
    (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull ?: throw RankingFormatException("$where lacks a number $key")

private fun JsonObject.flag(key: String, where: String): Boolean =
    (this[key] as? JsonPrimitive)?.booleanOrNull ?: throw RankingFormatException("$where lacks a true or false $key")

private fun optionalNumber(entry: JsonObject, key: String): Double? = (entry[key] as? JsonPrimitive)?.takeIf { it !is JsonNull && !it.isString }?.doubleOrNull

private fun optionalText(entry: JsonObject, key: String): String? = (entry[key] as? JsonPrimitive)?.takeIf { it !is JsonNull && it.isString }?.content

private fun figureOf(name: String, element: JsonElement): RankedFigure {
    val entry = element as? JsonObject ?: throw RankingFormatException("the figure $name is not an object")
    return RankedFigure(
        name, optionalNumber(entry, "value"), optionalText(entry, "unit"), optionalText(entry, "good"), optionalNumber(entry, "score"),
        entry.flag("missing", "the figure $name"), optionalText(entry, "source"), optionalText(entry, "evidence"),
    )
}

private fun candidateOf(element: JsonElement): RankedCandidate {
    val entry = element as? JsonObject ?: throw RankingFormatException("a candidate is not an object")
    val where = "a candidate"
    return RankedCandidate(
        rank = (entry["rank"] as? JsonPrimitive)?.intOrNull ?: throw RankingFormatException("$where lacks a rank"),
        solution = entry.text("solution", where),
        title = entry.text("title", where),
        isChosen = entry.flag("chosen", where),
        scoreLow = entry.number("score_low", where),
        scoreHigh = entry.number("score_high", where),
        isSettled = entry.flag("settled", where),
        missing = (entry["missing"] as? JsonArray).orEmpty().map { (it as JsonPrimitive).content },
        figures = (entry["figures"] as? JsonObject).orEmpty().map { (name, figure) -> figureOf(name, figure) },
    )
}

fun readRanking(text: String): Ranking {
    val root = runCatching { Json.parseToJsonElement(text) }.getOrNull() as? JsonObject ?: throw RankingFormatException("the ranking is not a JSON object")
    (root["refused"] as? JsonObject)?.let { throw RankingFormatException(it.text("message", "a refusal")) }
    val weights = (root["weights"] as? JsonObject ?: throw RankingFormatException("the ranking has no weights"))
        .mapValues { (_, weight) -> (weight as? JsonPrimitive)?.doubleOrNull ?: throw RankingFormatException("a weight is not a number") }
    val ranked = root["ranked"] as? JsonArray ?: throw RankingFormatException("the ranking has no list of candidates")
    return Ranking(root.text("system", "the ranking"), weights, ranked.map(::candidateOf))
}

private const val SPAN_SEPARATOR = " to "

private fun scoreText(candidate: RankedCandidate): String =
    if (candidate.scoreLow == candidate.scoreHigh) formatFigure(candidate.scoreLow)
    else formatFigure(candidate.scoreLow) + SPAN_SEPARATOR + formatFigure(candidate.scoreHigh)

private fun goodText(good: String?) = when (good) {
    "high" -> "higher is better"
    "low" -> "lower is better"
    else -> null
}

private fun provenance(figure: RankedFigure): String? =
    listOfNotNull(figure.source, figure.evidence).takeIf { it.isNotEmpty() }?.joinToString(", ")

private fun figureLines(prefix: String, ranking: Ranking, figure: RankedFigure): List<JsonObject> {
    val weight = ranking.weights[figure.name]?.let(::formatFigure)
    val gap = figure.isMissing || figure.value == null
    val value = if (gap) specNode("$prefix.${figure.name}.value", "text", mapOf("value" to null, "missing" to "– no figure", "color" to MUTED_INK))
    else textNode("$prefix.${figure.name}.value", listOfNotNull(formatFigure(figure.value!!), figure.unit).joinToString(" "), bold = true)
    val head = specNode(
        "$prefix.${figure.name}.line", "row", mapOf("gap" to 8, "align" to "center", "justify" to "between"),
        listOf(textNode("$prefix.${figure.name}.name", humanize(figure.name), flex = 1), value),
    )
    val explanation = if (gap) "counts as the worst figure for the low score and the best for the high score, weight $weight"
    else listOfNotNull(goodText(figure.good), figure.score?.let { "score ${formatFigure(it)} × weight $weight" }, provenance(figure)).joinToString("; ")
    return listOf(head, textNode("$prefix.${figure.name}.why", explanation, variant = "caption", color = MUTED_INK))
}

private fun missingReason(candidate: RankedCandidate): String =
    if (candidate.missing.isEmpty()) "another candidate has a missing figure" else "its ${candidate.missing.joinToString(", ") { humanize(it) }} figure is missing"

internal fun candidateCard(ranking: Ranking, candidate: RankedCandidate, prefix: String = "rank.${ranking.system}.${candidate.solution}"): JsonObject {
    val chips = listOfNotNull(
        if (candidate.isChosen) specNode("$prefix.chosen", "chip", mapOf("label" to "chosen")) else null,
        if (!candidate.isSettled) specNode("$prefix.unsettled", "chip", mapOf("label" to "rank not settled")) else null,
    )
    val heading = specNode(
        "$prefix.heading", "row", mapOf("gap" to 8, "align" to "center"),
        listOf(textNode("$prefix.title", "#${candidate.rank} ${candidate.title}", variant = "subheading", bold = true, flex = 1)) + chips,
    )
    val score = textNode("$prefix.score", "score ${scoreText(candidate)} (0 worst, 1 best)", variant = "caption", color = MUTED_INK)
    val unsettled = if (candidate.isSettled) null
    else textNode("$prefix.unsettled-why", "Its place changes between the low and the high score, because ${missingReason(candidate)}.", variant = "caption", color = MUTED_INK)
    val body = listOfNotNull(heading, score, unsettled) + candidate.figures.flatMap { figureLines(prefix, ranking, it) }
    return specNode("$prefix.card", "card", mapOf("padding" to 12), listOf(specNode("$prefix.body", "column", mapOf("gap" to 8), body)))
}

internal fun rankingNode(ranked: Ranked): JsonObject {
    val ranking = ranked.ranking
    val heading = textNode("ranking.title", ranking?.let { "How the ${it.system} alternatives rank" } ?: "How the alternatives rank", variant = "subheading", bold = true)
    val failure = ranked.failure?.let { textNode("ranking.failure", "The ranking could not be run: $it", variant = "caption", color = MUTED_INK) }
    val weights = ranking?.let { textNode("ranking.weights", "Weights: " + it.weights.entries.joinToString(", ") { (name, weight) -> "${humanize(name)} ${formatFigure(weight)}" } + ".", variant = "caption", color = MUTED_INK) }
    val cards = ranking?.candidates.orEmpty().map { candidateCard(ranking!!, it) }
    return specNode("ranking", "column", mapOf("gap" to 8), listOfNotNull(heading, failure, weights) + cards)
}
