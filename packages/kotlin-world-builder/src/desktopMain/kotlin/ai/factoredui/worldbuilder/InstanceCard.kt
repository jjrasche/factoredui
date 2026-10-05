package ai.factoredui.worldbuilder

import ai.factoredui.worldengine.state.InstanceRecord
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

private const val MM_PER_METRE = 1000.0
private const val SOURCE_TAG_LIMIT = 140
private const val NOT_MEASURED = "not measured"
private const val NOT_RECORDED = "not recorded"
private val SOURCE_KEYS = listOf("twin", "row", "file", "tag")
private val ERROR_FIGURES = listOf("position_mm" to "Position error", "height_mm" to "Height error", "crown_radius_mm" to "Crown radius error")

internal fun instanceTitle(record: InstanceRecord, label: String): String = "$label ${record.id} (${record.provenance})"

internal fun instanceCardLines(record: InstanceRecord): List<String> =
    listOf(
        "Position: ${metres(record.xMm)} east, ${metres(record.yMm)} north of the south-west corner",
        "Height: ${metres(record.heightMm)}",
        "Crown radius: ${metres(record.crownRadiusMm)}",
    ) + provenanceLines(record)

private fun provenanceLines(record: InstanceRecord): List<String> {
    if (record.provenance != "measured") return listOf("Proposed on this branch; no measurement behind it.")
    return listOf("Source: ${sourceText(record.source)}") + ERROR_FIGURES.map { (key, label) -> "$label: ${errorText(record.error, key)}" }
}

internal fun metres(element: JsonElement): String = millimetres(element)?.let { "${formatNumber(it / MM_PER_METRE)} m" } ?: NOT_RECORDED

private fun millimetres(element: JsonElement): Double? = (element as? JsonPrimitive)?.takeIf { it !is JsonNull && !it.isString }?.doubleOrNull

private fun sourceText(source: JsonElement): String {
    val fields = source as? JsonObject ?: return NOT_RECORDED
    val parts = SOURCE_KEYS.mapNotNull { key -> (fields[key] as? JsonPrimitive)?.content?.let { "$key ${shortened(it)}" } }
    return parts.joinToString("; ").ifEmpty { NOT_RECORDED }
}

private fun shortened(text: String): String = if (text.length <= SOURCE_TAG_LIMIT) text else text.take(SOURCE_TAG_LIMIT).trimEnd() + "..."

private fun errorText(error: JsonElement, key: String): String {
    val figure = (error as? JsonObject)?.get(key) as? JsonObject ?: return NOT_MEASURED
    val value = millimetres(figure["value"] ?: JsonNull) ?: return "$NOT_MEASURED (${reasonOf(figure)})"
    return "plus or minus ${formatNumber(value / MM_PER_METRE)} m"
}

private fun reasonOf(figure: JsonObject): String = (figure["null_reason"] as? JsonPrimitive)?.content?.let { shortened(it) } ?: "no reason given"
