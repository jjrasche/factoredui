package ai.factoredui.application

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// split-audit-2026-10-20 item 9: a reading is found by its reason text; it waits for a status field
private const val READING_PREFIX = "reading:"
private const val MILLIMETRE_FORMAT_SUFFIX = " mm"
private val MILLIMETRE_FORMAT = mapOf("scale" to 1, "decimals" to 0, "suffix" to MILLIMETRE_FORMAT_SUFFIX)

private fun isMillimetres(setting: SystemSetting) = setting.name.endsWith("_mm") && setting.value is JsonPrimitive && !(setting.value as JsonPrimitive).isString

private fun isOpenReading(setting: SystemSetting) = setting.value !is JsonNull && setting.reason?.startsWith(READING_PREFIX) == true

private fun valueNode(id: String, setting: SystemSetting): JsonObject = when {
    setting.value is JsonArray -> textNode(id, (setting.value as JsonArray).joinToString(", ") { (it as? JsonPrimitive)?.content ?: it.toString() }, bold = true)
    setting.value is JsonNull -> specNode(id, "text", mapOf("value" to null, "missing" to "–", "color" to MUTED_INK))
    isMillimetres(setting) -> specNode(id, "text", mapOf("value" to setting.value, "format" to MILLIMETRE_FORMAT, "bold" to true))
    else -> textNode(id, (setting.value as JsonPrimitive).content, bold = true)
}

private fun reasonText(setting: SystemSetting): String? = setting.reason?.removePrefix(READING_PREFIX)?.trim()?.takeIf { it.isNotEmpty() }

private fun settingNode(system: String, setting: SystemSetting): JsonObject {
    val id = "$system.${setting.name}"
    val line = specNode(
        "$id.line", "row", mapOf("gap" to 8, "align" to "center", "justify" to "between"),
        listOfNotNull(
            textNode("$id.name", humanize(setting.name), flex = 1),
            valueNode("$id.value", setting),
            if (isOpenReading(setting)) specNode("$id.open", "chip", mapOf("label" to "open reading")) else null,
        ),
    )
    val reason = reasonText(setting)
    return specNode("$id.setting", "column", mapOf("gap" to 2), listOfNotNull(line, reason?.let { textNode("$id.reason", it, variant = "caption", color = MUTED_INK) }))
}

private const val SWATCH_DP = 14

private fun countLabel(count: Int) = "$count ${if (count == 1) "object" else "objects"}"

internal fun systemCard(entry: SystemEntry, colour: String, objectCount: Int): JsonObject {
    val heading = specNode(
        "${entry.name}.heading", "row", mapOf("gap" to 8, "align" to "center"),
        listOfNotNull(
            specNode("${entry.name}.swatch", "row", mapOf("background" to colour, "width" to SWATCH_DP, "height" to SWATCH_DP)),
            textNode("${entry.name}.title", entry.name, variant = "subheading", bold = true, flex = 1),
            textNode("${entry.name}.count", countLabel(objectCount), variant = "caption", color = MUTED_INK),
        ),
    )
    val solution = entry.solution?.let { textNode("${entry.name}.solution", it, variant = "caption", color = MUTED_INK) }
    return specNode("${entry.name}.card", "card", mapOf("padding" to 12), listOf(specNode("${entry.name}.body", "column", mapOf("gap" to 8), listOfNotNull(heading, solution) + entry.settings.map { settingNode(entry.name, it) })))
}

internal fun suppliedNode(figures: List<SuppliedFigure>): JsonObject? {
    if (figures.isEmpty()) return null
    val rows = figures.map { figure ->
        specNode(
            "supplied.${figure.name}", "column", mapOf("gap" to 2),
            listOfNotNull(
                textNode("supplied.${figure.name}.value", "${formatMm(figure.value)} ${humanize(figure.unit)}", bold = true),
                figure.reason?.let { textNode("supplied.${figure.name}.reason", it, variant = "caption", color = MUTED_INK) },
            ),
        )
    }
    return specNode("supplied", "column", mapOf("gap" to 8), listOf(textNode("supplied.title", "Supplied", variant = "subheading", bold = true)) + rows)
}
