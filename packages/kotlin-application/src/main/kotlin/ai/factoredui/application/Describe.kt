package ai.factoredui.application

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class DescribeFormatException(message: String) : IllegalArgumentException(message)

data class DescribedSetting(val id: String, val unit: String?, val type: String?, val value: JsonElement, val reason: String?)

data class DescribedAlternative(val id: String, val title: String, val settings: List<DescribedSetting>)

data class DescribedSystem(
    val id: String,
    val title: String,
    val solution: String,
    val parts: List<String>,
    val settings: List<DescribedSetting>,
    val alternatives: List<DescribedAlternative>,
    val solvers: List<String>,
    val rules: List<String>,
)

data class Describing(val systems: List<DescribedSystem>, val rankings: Map<String, Ranked> = emptyMap(), val hasRankingWeights: Boolean = false, val failure: String? = null)

private fun JsonObject.text(key: String, where: String): String =
    (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: throw DescribeFormatException("$where lacks a text $key")

private fun optionalText(entry: JsonObject, key: String): String? = (entry[key] as? JsonPrimitive)?.takeIf { it !is JsonNull && it.isString }?.content

private fun names(entry: JsonObject, key: String): List<String> = (entry[key] as? JsonArray).orEmpty().map { (it as JsonPrimitive).content }

private fun settingOf(element: JsonElement): DescribedSetting {
    val entry = element as? JsonObject ?: throw DescribeFormatException("a setting is not an object")
    return DescribedSetting(entry.text("id", "a setting"), optionalText(entry, "unit"), optionalText(entry, "type"), entry["value"] ?: JsonNull, optionalText(entry, "reason"))
}

private fun alternativeOf(element: JsonElement): DescribedAlternative {
    val entry = element as? JsonObject ?: throw DescribeFormatException("an alternative is not an object")
    val id = entry.text("id", "an alternative")
    return DescribedAlternative(id, optionalText(entry, "title") ?: id, (entry["settings"] as? JsonArray).orEmpty().map(::settingOf))
}

private fun systemOf(element: JsonElement): DescribedSystem {
    val entry = element as? JsonObject ?: throw DescribeFormatException("a system is not an object")
    val id = entry.text("id", "a system")
    return DescribedSystem(
        id = id,
        title = optionalText(entry, "title") ?: id,
        solution = optionalText(entry, "solution_title") ?: optionalText(entry, "solution") ?: "",
        parts = names(entry, "parts"),
        settings = (entry["settings"] as? JsonArray).orEmpty().map(::settingOf),
        alternatives = (entry["alternatives"] as? JsonArray).orEmpty().map(::alternativeOf),
        solvers = names(entry, "solvers"),
        rules = names(entry, "rules"),
    )
}

fun readDescribed(text: String): List<DescribedSystem> {
    val root = runCatching { Json.parseToJsonElement(text) }.getOrNull() as? JsonObject ?: throw DescribeFormatException("the description is not a JSON object")
    (root["refused"] as? JsonObject)?.let { throw DescribeFormatException(it.text("message", "a refusal")) }
    return (root["systems"] as? JsonArray ?: throw DescribeFormatException("the description has no systems")).map(::systemOf)
}

private fun valueText(setting: DescribedSetting): String? = when (val value = setting.value) {
    is JsonNull -> null
    is JsonArray -> value.joinToString(", ") { (it as? JsonPrimitive)?.content ?: it.toString() }
    is JsonPrimitive -> if (value.isString) value.content else listOfNotNull(value.content.toDoubleOrNull()?.let(::formatFigure) ?: value.content, setting.unit?.takeIf { it in SHOWN_UNITS }).joinToString(" ")
    else -> value.toString()
}

private val SHOWN_UNITS = setOf("mm", "ratio", "percent", "btu", "years", "usd", "degrees", "kg", "m")

private fun valueNode(id: String, setting: DescribedSetting): JsonObject {
    val text = valueText(setting) ?: return specNode(id, "text", mapOf("value" to null, "missing" to "– not given", "color" to MUTED_INK))
    return textNode(id, text, bold = true)
}

private fun settingNode(prefix: String, setting: DescribedSetting): JsonObject {
    val id = "$prefix.${setting.id}"
    val line = specNode(
        "$id.line", "row", mapOf("gap" to 8, "align" to "center", "justify" to "between"),
        listOf(textNode("$id.name", humanize(setting.id), flex = 1), valueNode("$id.value", setting)),
    )
    val reason = setting.reason?.removePrefix("reading:")?.trim()?.takeIf { it.isNotEmpty() }
    return specNode("$id.setting", "column", mapOf("gap" to 2), listOfNotNull(line, reason?.let { textNode("$id.reason", it, variant = "caption", color = MUTED_INK) }))
}

private fun settingsTakenBy(prefix: String, alternative: DescribedAlternative): List<JsonObject> =
    alternative.settings.map { setting -> textNode("$prefix.${setting.id}", "${humanize(setting.id)}${setting.unit?.let { " ($it)" }.orEmpty()}", variant = "caption", color = MUTED_INK) }

private const val NO_PRIORITIES = "No priorities set: the config gives no ranking weights, so the solutions are not ranked."

/** The solutions of a system with alternatives, best first, each with its score bounds, whether its place is settled, its figures with their sources and gaps, and the chosen one marked. */
private fun rankedSolutions(system: DescribedSystem, describing: Describing): List<JsonObject> {
    if (system.alternatives.isEmpty()) return emptyList()
    val prefix = "describe.${system.id}.ranked"
    val ranked = describing.rankings[system.id]
    val ranking = ranked?.ranking
    val heading = textNode("$prefix.title", "Solutions, ranked", variant = "subheading", bold = true)
    return when {
        ranking != null -> listOf(
            heading,
            textNode("$prefix.weights", "Weights: " + ranking.weights.entries.joinToString(", ") { (name, weight) -> "${humanize(name)} ${formatFigure(weight)}" } + ".", variant = "caption", color = MUTED_INK),
        ) + ranking.candidates.map { candidateCard(ranking, it, "$prefix.${it.solution}") }
        ranked?.failure != null -> listOf(heading, textNode("$prefix.failure", "The ranking could not be run: ${ranked.failure}", variant = "caption", color = MUTED_INK))
        !describing.hasRankingWeights -> listOf(heading, textNode("$prefix.none", NO_PRIORITIES, variant = "caption", color = MUTED_INK))
        else -> emptyList()
    }
}

private fun bindingKey(id: String): String = id.replace(Regex("[^A-Za-z0-9_]"), "_")

private fun alternativeNode(system: DescribedSystem, alternative: DescribedAlternative): JsonObject {
    val prefix = "describe.${system.id}.alt.${alternative.id}"
    val path = "{ui.alt.${bindingKey(system.id)}.${bindingKey(alternative.id)}}"
    val switch = specNode("$prefix.switch", "toggle", mapOf("label" to alternative.title, "value" to path))
    val detail = specNode(
        "$prefix.detail", "column", mapOf("gap" to 6),
        listOfNotNull(alternative.settings.takeIf { it.isNotEmpty() }?.let { textNode("$prefix.needs", "It would ask for these settings:", variant = "caption", color = MUTED_INK) }) +
            settingsTakenBy("$prefix.need", alternative),
        visible = path,
    )
    return specNode("$prefix.item", "column", mapOf("gap" to 4), listOf(switch, detail))
}

private fun alsoLine(system: DescribedSystem): String? =
    listOfNotNull(
        system.solvers.takeIf { it.isNotEmpty() }?.let { "Read by ${it.joinToString(", ") { name -> humanize(name) }}." },
        system.rules.takeIf { it.isNotEmpty() }?.let { "Checked by ${it.joinToString(", ") { name -> humanize(name) }}." },
    ).joinToString(" ").ifEmpty { null }

private fun systemCard(system: DescribedSystem, describing: Describing): JsonObject {
    val prefix = "describe.${system.id}"
    val heading = specNode(
        "$prefix.heading", "column", mapOf("gap" to 4),
        listOf(textNode("$prefix.name", humanize(system.id), variant = "subheading", bold = true), specNode("$prefix.solution", "chip", mapOf("label" to system.solution))),
    )
    val body = listOfNotNull(
        heading,
        textNode("$prefix.title", system.title, variant = "caption", color = MUTED_INK),
        system.parts.takeIf { it.isNotEmpty() }?.let { textNode("$prefix.parts", "Made of ${it.joinToString(", ") { part -> humanize(part) }}.", variant = "caption", color = MUTED_INK) },
    ) + system.settings.map { settingNode(prefix, it) } + rankedSolutions(system, describing) +
        listOfNotNull(
            system.alternatives.takeIf { it.isNotEmpty() }?.let { textNode("$prefix.alternatives", "Alternatives: tap one to see what it asks for.", variant = "caption", color = MUTED_INK) },
        ) + system.alternatives.map { alternativeNode(system, it) } +
        listOfNotNull(alsoLine(system)?.let { textNode("$prefix.also", it, variant = "caption", color = MUTED_INK) })
    return specNode("$prefix.card", "card", mapOf("padding" to 12), listOf(specNode("$prefix.body", "column", mapOf("gap" to 8), body)))
}

internal fun describeNode(describing: Describing): JsonObject {
    val heading = textNode("describe.title", "The systems, and what each one is set to", variant = "subheading", bold = true)
    return specNode("describe", "column", mapOf("gap" to 8), listOf(heading) + describing.systems.map { systemCard(it, describing) })
}
