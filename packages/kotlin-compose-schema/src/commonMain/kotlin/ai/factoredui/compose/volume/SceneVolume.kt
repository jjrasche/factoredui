package ai.factoredui.compose.volume

import ai.factoredui.compose.schema.plainFigureText

/** The volume props a scene answer stands for: boxes, styles, legend, details and the colour range, plus notices for what could not be drawn. */
data class SceneVolume(val props: Map<String, Any?>, val notices: List<String>)

private const val UNKNOWN_STYLE = "unknown"
private const val OUTSIDE_STYLE = "outside"
private const val OBJECT_STYLE = "object"

private val DEFAULT_STYLES: Map<String, Any?> = mapOf(
    UNKNOWN_STYLE to mapOf("stroke" to "#9aa4ad", "opacity" to 0.5),
    OUTSIDE_STYLE to mapOf("stroke" to "#b91c1c"),
    OBJECT_STYLE to mapOf("stroke" to "#64748b"),
)

private sealed interface Reading {
    data class Scaled(val value: Double) : Reading
    data class Outside(val value: Double, val reason: String) : Reading
    data class Unknown(val reason: String) : Reading
}

private fun fieldsOf(raw: Any?): Map<*, *> = raw as? Map<*, *> ?: emptyMap<Any?, Any?>()

private fun numberOf(raw: Any?): Double? = (raw as? Number)?.toDouble()?.takeIf { it.isFinite() }

private fun computedText(function: Any, computedBy: Map<*, *>): String {
    val reads = ((computedBy[function] as? Map<*, *>)?.get("reads") as? List<*>).orEmpty().joinToString(", ")
    return if (reads.isEmpty()) "computed: $function" else "computed: $function, which reads $reads"
}

private fun sourceText(source: Any?, computedBy: Map<*, *>): String? = when (source) {
    is String -> source
    is Map<*, *> -> source["unknown"]?.let { "unknown: $it" } ?: source["computed"]?.let { computedText(it, computedBy) }
    else -> null
}

private fun unknownReason(field: Map<*, *>): String? = ((field["source"] as? Map<*, *>)?.get("unknown"))?.toString()

private fun readingOf(field: Map<*, *>?, limit: Map<*, *>?): Reading {
    if (field == null) return Reading.Unknown("no such field")
    unknownReason(field)?.let { return Reading.Unknown(it) }
    val value = numberOf(field["value"]) ?: return Reading.Unknown("no number")
    val low = numberOf(limit?.get("low"))
    val high = numberOf(limit?.get("high"))
    if (low != null && value < low) return Reading.Outside(value, "below ${plainFigureText(low)}")
    if (high != null && value > high) return Reading.Outside(value, "above ${plainFigureText(high)}")
    return Reading.Scaled(value)
}

private fun styleNameOf(reading: Reading?): String = when (reading) {
    is Reading.Unknown -> UNKNOWN_STYLE
    is Reading.Outside -> OUTSIDE_STYLE
    else -> OBJECT_STYLE
}

private fun valueText(field: Map<*, *>): String? {
    val value = field["value"] ?: return null
    val figure = numberOf(value)?.let { plainFigureText(it) } ?: value.toString()
    return listOfNotNull(figure, field["unit"] as? String).joinToString(" ")
}

private fun rowNote(name: String, field: Map<*, *>, colourBy: String?, reading: Reading?, computedBy: Map<*, *>): String? {
    val source = sourceText(field["source"], computedBy)
    val state = (reading as? Reading.Outside)?.takeIf { name == colourBy }?.let { "outside the limit: ${it.reason}" }
    return listOfNotNull(state, source).joinToString("; ").ifEmpty { null }
}

private fun detailOf(objectFields: Map<*, *>, colourBy: String?, reading: Reading?, computedBy: Map<*, *>): Map<String, Any?> {
    val rows = fieldsOf(objectFields["fields"]).entries.map { (name, raw) ->
        val field = fieldsOf(raw)
        mapOf("label" to name.toString(), "value" to valueText(field), "note" to rowNote(name.toString(), field, colourBy, reading.takeIf { name == colourBy }, computedBy))
    }
    val heading = listOfNotNull(objectFields["type"] as? String, objectFields["system"] as? String).joinToString(" · ")
    return mapOf("title" to (objectFields["id"] as? String ?: ""), "sections" to listOf(mapOf("heading" to heading, "rows" to rows)))
}

private fun pitchOf(tilt: Any?): Map<String, Any?> {
    val fields = tilt as? Map<*, *> ?: return emptyMap()
    return if (fields["axis"] == "width" && numberOf(fields["deg"]) != null) mapOf("pitch_deg" to fields["deg"]) else emptyMap()
}

private fun unsupportedTiltNotice(item: Map<*, *>): String? {
    val tilt = (item["box_mm"] as? Map<*, *>)?.get("tilt") as? Map<*, *> ?: return null
    return "volume scene: '${item["id"]}' is tilted about its '${tilt["axis"]}' axis; only the width axis is drawn, so it is drawn level.".takeIf { tilt["axis"] != "width" }
}

private fun boxOf(objectFields: Map<*, *>, style: String, reading: Reading?): Map<String, Any?>? {
    val box = objectFields["box_mm"] as? Map<*, *> ?: return null
    val id = objectFields["id"] as? String ?: return null
    return mapOf(
        "id" to id, "x_mm" to box["x"], "y_mm" to box["y"], "z_mm" to box["z"],
        "width_mm" to box["w"], "depth_mm" to box["d"], "height_mm" to box["h"],
        "style" to style, "estimate" to (reading is Reading.Unknown),
    ) + pitchOf(box["tilt"]) + if (reading is Reading.Scaled) mapOf("values" to listOf(reading.value)) else emptyMap()
}

private fun legendFor(styles: Set<String>): List<Map<String, Any?>> = listOfNotNull(
    UNKNOWN_STYLE.takeIf { it in styles }?.let { mapOf("label" to "Unknown", "style" to it, "hatched" to true) },
    OUTSIDE_STYLE.takeIf { it in styles }?.let { mapOf("label" to "Outside the limit", "style" to it, "hatched" to false) },
)

/** Reads a scene answer — objects with a box in millimetres (x, y the footprint centre, z the underside) and named fields that each carry a value, a unit and a source — into volume props. [colourBy] names the field that colours the boxes; its range is the answer's own limit for that field, a side the limit does not state is the extreme read, a value outside the limit is drawn in the `outside` style, and a field with no value is drawn in the `unknown` style. Null when [scene] is not an answer. */
fun readScene(scene: Any?, colourBy: String?, specStyles: Any? = null): SceneVolume? {
    val answer = scene as? Map<*, *> ?: return null
    val everyObject = (answer["objects"] as? List<*>).orEmpty().filterIsInstance<Map<*, *>>()
    val (objects, unmeasured) = everyObject.partition { it["box_mm"] != null || it["unmeasured"] == null }
    val limit = colourBy?.let { fieldsOf(answer["limits"])[it] as? Map<*, *> }
    val readings = objects.map { colourBy?.let { name -> readingOf(fieldsOf(it["fields"])[name] as? Map<*, *>, limit) } }
    val notices = unmeasured.mapTo(mutableListOf()) { item -> "volume scene: '${item["id"]}' is not measured (${(item["unmeasured"] as? Map<*, *>)?.let { "${it["field"]}: ${it["why"]}" }}); it is not drawn." }
    val boxes = objects.zip(readings).mapNotNull { (item, reading) ->
        boxOf(item, styleNameOf(reading), reading) ?: null.also { notices += "volume scene: object '${item["id"]}' has no id or no box_mm; it is not drawn." }
    }
    val scaled = readings.filterIsInstance<Reading.Scaled>().map { it.value }
    val min = numberOf(limit?.get("low")) ?: scaled.minOrNull()
    val max = numberOf(limit?.get("high")) ?: scaled.maxOrNull()
    if (colourBy != null && (min == null || max == null || max <= min)) notices += "volume scene: '$colourBy' has no range to colour by; the boxes are drawn plain."
    notices += objects.mapNotNull { unsupportedTiltNotice(it) }
    val usedStyles = boxes.mapNotNull { it["style"] as? String }.toSet()
    val details = objects.zip(readings).filter { (item, _) -> item["id"] is String && item["box_mm"] is Map<*, *> }.associate { (item, reading) -> item["id"] as String to detailOf(item, colourBy, reading, fieldsOf(answer["computed_by"])) }
    (answer["page"] as? Map<*, *>)?.get("next")?.let { notices += "volume scene: more follows (${(answer["page"] as Map<*, *>)["region"]}, next $it)." }
    val unit = objects.firstNotNullOfOrNull { (fieldsOf(it["fields"])[colourBy] as? Map<*, *>)?.get("unit") as? String }
    val rangeProps = if (min != null && max != null && max > min) mapOf("min" to min, "max" to max, "frame" to 0, "unit" to unit) else emptyMap()
    val styles = DEFAULT_STYLES.filterKeys { it in usedStyles } + ((specStyles as? Map<*, *>)?.entries?.associate { it.key.toString() to it.value } ?: emptyMap())
    return SceneVolume(mapOf("boxes" to boxes, "details" to details, "styles" to styles, "legend" to legendFor(usedStyles)) + rangeProps, notices)
}
