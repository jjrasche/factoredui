package ai.factoredui.application

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

private fun row(label: String, value: String?, note: String? = null) = mapOf("label" to label, "value" to value, "note" to note)

private fun section(heading: String, rows: List<Map<String, Any?>>) = mapOf("heading" to heading, "rows" to rows)

private fun textOf(value: JsonElement): String? = when (value) {
    is JsonNull -> null
    is JsonArray -> value.joinToString(", ") { textOf(it) ?: "–" }
    is JsonPrimitive -> if (value.isString) value.content else value.content
    else -> value.toString()
}

private fun figureText(value: Double?): String? = value?.let(::formatMm)

private fun settingRows(entry: SystemEntry): List<Map<String, Any?>> =
    entry.settings.map { row(humanize(it.name), textOf(it.value), it.reason) }

private fun ruleRows(file: SystemFile, system: String): List<Map<String, Any?>> =
    file.binds.filter { (_, systems) -> system in systems }.map { (role, systems) -> row("bound as", role, "with ${systems.filterNot { it == system }.joinToString(", ").ifEmpty { "nothing else" }}") }

private fun fieldRows(fields: Map<String, Double?>) = fields.map { (name, value) -> row(humanize(name), figureText(value)) }

private fun identityRows(entry: SystemEntry?, system: String, type: String) =
    listOf(row("system", system), row("solution", entry?.solution), row("type", type))

private fun detailOf(file: SystemFile, id: String, system: String, type: String, placeRows: List<Map<String, Any?>>, fields: Map<String, Double?>): Map<String, Any?> {
    val entry = file.systems.firstOrNull { it.name == system }
    val sections = listOfNotNull(
        section("What it is", identityRows(entry, system, type)),
        section("Where", placeRows),
        section("Its fields", fieldRows(fields)).takeIf { fields.isNotEmpty() },
        entry?.let { section("The $system system's settings", settingRows(it)) }?.takeIf { entry.settings.isNotEmpty() },
        section("Rules naming $system", ruleRows(file, system)).takeIf { ruleRows(file, system).isNotEmpty() },
    )
    return mapOf("title" to id, "sections" to sections)
}

private fun objectPlaceRows(item: ExpandedObject) = listOf(
    row("x (mm)", figureText(item.xMm)), row("y (mm)", figureText(item.yMm)),
    row("width (mm)", figureText(item.widthMm)), row("depth (mm)", figureText(item.depthMm)), row("level", item.level?.toString()),
)

private fun instancePlaceRows(item: ExpandedInstance) = listOf(
    row("x (mm)", figureText(item.xMm)), row("y (mm)", figureText(item.yMm)), row("length (mm)", figureText(item.lengthMm)),
    row("turned (deg)", figureText(item.rotationDeg)), row("elevation (mm)", figureText(item.elevationMm)),
)

/** What a card says when one object or placed length is selected: what it is, where, its fields, its system's settings with the reasons the file gives, and the rules that name its system. */
internal fun detailsOf(file: SystemFile, expansion: Expansion): Map<String, Any?> =
    expansion.objects.associate { it.id to detailOf(file, it.id, it.system, it.type, objectPlaceRows(it), it.fields) } +
        expansion.instances.associate { it.id to detailOf(file, it.id, it.system, it.type, instancePlaceRows(it), it.fields) }
