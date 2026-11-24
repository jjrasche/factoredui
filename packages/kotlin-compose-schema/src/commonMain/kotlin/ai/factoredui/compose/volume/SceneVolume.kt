package ai.factoredui.compose.volume

import ai.factoredui.compose.heatmap.heatLegendText
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
    val unit = field["unit"] as? String
    return numberOf(value)?.let { heatLegendText(it, unit) } ?: listOfNotNull(value.toString(), unit).joinToString(" ")
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

private fun legendFor(styleCounts: Map<String, Int>): List<Map<String, Any?>> = listOfNotNull(
    styleCounts[UNKNOWN_STYLE]?.let { mapOf("label" to "Unknown ($it)", "style" to UNKNOWN_STYLE, "hatched" to true) },
    styleCounts[OUTSIDE_STYLE]?.let { mapOf("label" to "Outside the limit ($it)", "style" to OUTSIDE_STYLE, "hatched" to false) },
)

private fun readValueOf(reading: Reading?): Double? = when (reading) {
    is Reading.Scaled -> reading.value
    is Reading.Outside -> reading.value
    else -> null
}

/** One label over the object whose [colourBy] value is highest, naming its id, type, value and unit, and its text field `governing` when it has one. */
private fun topLabelOf(objects: List<Map<*, *>>, readings: List<Reading?>, colourBy: String?): List<Map<String, Any?>> {
    val top = objects.zip(readings)
        .mapNotNull { (item, reading) -> readValueOf(reading)?.let { value -> Triple(item, value, item["box_mm"] as? Map<*, *>) } }
        .filter { it.third != null && it.first["id"] is String }
        .maxByOrNull { it.second } ?: return emptyList()
    val (item, value, box) = top
    val fields = fieldsOf(item["fields"])
    val unit = fieldsOf(fields[colourBy])["unit"] as? String
    val governing = fieldsOf(fields["governing"])["value"] as? String
    val text = listOfNotNull(item["id"] as? String, item["type"] as? String, heatLegendText(value, unit), governing).joinToString(" · ")
    val at = listOf(box!!["x"], box["y"], numberOf(box["z"])?.let { z -> z + (numberOf(box["h"]) ?: 0.0) })
    return listOf(mapOf("id" to "highest", "at" to at, "text" to text))
}

private const val LIGHTING_OFF = "none"

/** The light an answer gives a spec that asks for sun lighting: the answer's `sun: {direction: [east, north, up]}` (toward the sun) as the light, or lighting off with a notice when the answer gives no sun, no three numbers, or one at or below the horizon. A spec that asks for no sun lighting is left alone. */
private fun sunPropsOf(answer: Map<*, *>, specLighting: String?, notices: MutableList<String>): Map<String, Any?> {
    if (specLighting != "sun") return emptyMap()
    fun off(why: String) = mapOf<String, Any?>("lighting" to LIGHTING_OFF).also { notices += "volume scene: $why; no sunlight or shadows are drawn." }
    val direction = fieldsOf(answer["sun"])["direction"] as? List<*> ?: return off("no sun was given")
    val parts = direction.map { numberOf(it) }
    if (parts.size != 3 || parts.any { it == null }) return off("sun.direction is not [east, north, up] numbers")
    if (parts[2]!! <= 0.0) return off("the sun is at or below the horizon")
    return mapOf("sun" to parts)
}

private const val MM_PER_INCH = 25.4
private const val INCHES_PER_FOOT = 12

private fun lengthText(mm: Double, lengthUnit: String?): String {
    if (lengthUnit == "mm") return "${plainFigureText(kotlin.math.round(mm))} mm"
    val inches = kotlin.math.round(mm / MM_PER_INCH).toInt()
    return "${inches / INCHES_PER_FOOT} ft ${inches % INCHES_PER_FOOT} in"
}

private fun extentsLine(boxes: List<Map<String, Any?>>, lengthUnit: String?): String? {
    fun low(centre: String, size: String) = boxes.minOfOrNull { numberOf(it[centre])!! - numberOf(it[size])!! / 2 }
    fun high(centre: String, size: String) = boxes.maxOfOrNull { numberOf(it[centre])!! + numberOf(it[size])!! / 2 }
    val east = high("x_mm", "width_mm") ?: return null
    val north = high("y_mm", "depth_mm") ?: return null
    val floor = boxes.minOf { numberOf(it["z_mm"])!! }
    val ceiling = boxes.maxOf { numberOf(it["z_mm"])!! + numberOf(it["height_mm"])!! }
    val width = east - low("x_mm", "width_mm")!!
    val depth = north - low("y_mm", "depth_mm")!!
    return "Overall: ${lengthText(width, lengthUnit)} east to west, ${lengthText(depth, lengthUnit)} north to south, ${lengthText(ceiling - floor, lengthUnit)} high."
}

/** Reads a scene answer — objects with a box in millimetres (x, y the footprint centre, z the underside) and named fields that each carry a value, a unit and a source — into volume props. [colourBy] names the field that colours the boxes; its range is the answer's own limit for that field, a side the limit does not state is the extreme read, a value outside the limit is drawn in the `outside` style, and a field with no value is drawn in the `unknown` style. Null when [scene] is not an answer. */
fun readScene(scene: Any?, colourBy: String?, specStyles: Any? = null, colourCaption: String? = null, lengthUnit: String? = null, specLighting: String? = null): SceneVolume? {
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
    val styleCounts = boxes.mapNotNull { it["style"] as? String }.groupingBy { it }.eachCount()
    val usedStyles = styleCounts.keys
    val details = objects.zip(readings).filter { (item, _) -> item["id"] is String && item["box_mm"] is Map<*, *> }.associate { (item, reading) -> item["id"] as String to detailOf(item, colourBy, reading, fieldsOf(answer["computed_by"])) }
    (answer["page"] as? Map<*, *>)?.get("next")?.let { notices += "volume scene: more follows (${(answer["page"] as Map<*, *>)["region"]}, next $it)." }
    val unit = objects.firstNotNullOfOrNull { (fieldsOf(it["fields"])[colourBy] as? Map<*, *>)?.get("unit") as? String }
    val rangeProps = if (min != null && max != null && max > min) mapOf("min" to min, "max" to max, "frame" to 0, "unit" to unit) else emptyMap()
    val styles = DEFAULT_STYLES.filterKeys { it in usedStyles } + ((specStyles as? Map<*, *>)?.entries?.associate { it.key.toString() to it.value } ?: emptyMap())
    val labels = if (colourBy != null) topLabelOf(objects, readings, colourBy) else emptyList()
    return SceneVolume(mapOf("boxes" to boxes, "labels" to labels,"details" to details, "styles" to styles, "legend" to legendFor(styleCounts), "scene_info" to listOfNotNull(colourCaption?.let { "Colour: $it." }, extentsLine(boxes, lengthUnit))) + rangeProps + sunPropsOf(answer, specLighting, notices), notices)
}
