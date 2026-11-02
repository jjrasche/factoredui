package ai.factoredui.application

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

class OverlayFormatException(message: String) : IllegalArgumentException(message)

data class PieceReading(val value: Double?, val position: Double?, val colour: String?, val reason: String?)

data class Overlay(
    val id: String,
    val label: String,
    val unit: String,
    val min: Double,
    val max: Double,
    val good: String,
    val stops: List<Pair<Double, String>>,
    val unknownColour: String,
    val pieces: Map<String, PieceReading>,
)

data class Overlaying(val overlay: Overlay?, val failure: String? = null)

private fun JsonObject.text(key: String, where: String): String =
    (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: throw OverlayFormatException("$where lacks a text $key")

private fun JsonObject.number(key: String, where: String): Double =
    (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull ?: throw OverlayFormatException("$where lacks a number $key")

private fun optionalNumber(entry: JsonObject, key: String): Double? = (entry[key] as? JsonPrimitive)?.takeIf { it !is JsonNull && !it.isString }?.doubleOrNull

private fun optionalText(entry: JsonObject, key: String): String? = (entry[key] as? JsonPrimitive)?.takeIf { it !is JsonNull && it.isString }?.content

private fun stopOf(entry: kotlinx.serialization.json.JsonElement): Pair<Double, String> {
    val pair = entry as? JsonArray ?: throw OverlayFormatException("a stop is not a pair")
    val position = (pair.getOrNull(0) as? JsonPrimitive)?.doubleOrNull ?: throw OverlayFormatException("a stop lacks its position")
    val colour = (pair.getOrNull(1) as? JsonPrimitive)?.takeIf { it.isString }?.content ?: throw OverlayFormatException("a stop lacks its colour")
    return position to colour
}

private fun readingOf(entry: JsonObject) =
    PieceReading(optionalNumber(entry, "value"), optionalNumber(entry, "t"), optionalText(entry, "color"), optionalText(entry, "reason"))

fun readOverlay(text: String): Overlay {
    val root = runCatching { Json.parseToJsonElement(text) }.getOrNull() as? JsonObject
        ?: throw OverlayFormatException("the overlay is not a JSON object")
    (root["refused"] as? JsonObject)?.let { throw OverlayFormatException(it.text("message", "a refusal")) }
    val head = root["overlay"] as? JsonObject ?: throw OverlayFormatException("the answer has no overlay: $root")
    val pieces = root["pieces"] as? JsonObject ?: throw OverlayFormatException("the answer has no pieces")
    return Overlay(
        id = head.text("id", "the overlay"),
        label = head.text("label", "the overlay"),
        unit = head.text("unit", "the overlay"),
        min = head.number("min", "the overlay"),
        max = head.number("max", "the overlay"),
        good = head.text("good", "the overlay"),
        stops = (head["stops"] as? JsonArray ?: throw OverlayFormatException("the overlay has no stops")).map(::stopOf),
        unknownColour = head.text("unknown_color", "the overlay"),
        pieces = pieces.mapValues { (_, entry) -> readingOf(entry as? JsonObject ?: throw OverlayFormatException("a piece is not an object")) },
    )
}

private const val NOT_APPLICABLE_STYLE = "overlay: not applicable"
private const val UNKNOWN_STYLE = "overlay: unknown"
private const val NOT_APPLICABLE_INK = "#8a8a8a"
private const val CLEAR_FILL = "#00000000"
private const val OVERLAY_FILL_ALPHA = "B0"

internal fun isUnknown(reading: PieceReading) = reading.reason != null

internal fun overlayStyleOf(overlay: Overlay, id: String): String {
    val reading = overlay.pieces[id] ?: return NOT_APPLICABLE_STYLE
    return when {
        isUnknown(reading) -> UNKNOWN_STYLE
        reading.colour != null -> "overlay: ${reading.colour}"
        else -> NOT_APPLICABLE_STYLE
    }
}

internal fun overlayStyles(overlay: Overlay, ids: Collection<String>): Map<String, Map<String, Any?>> =
    ids.map { overlayStyleOf(overlay, it) }.distinct().associateWith { key ->
        when (key) {
            NOT_APPLICABLE_STYLE -> mapOf("stroke" to NOT_APPLICABLE_INK, "fill" to CLEAR_FILL, "dash" to true)
            UNKNOWN_STYLE -> mapOf("stroke" to overlay.unknownColour, "fill" to overlay.unknownColour + OVERLAY_FILL_ALPHA)
            else -> key.removePrefix("overlay: ").let { colour -> mapOf("stroke" to colour, "fill" to colour + OVERLAY_FILL_ALPHA) }
        }
    }

private fun stopValue(overlay: Overlay, position: Double): Double {
    val span = overlay.max - overlay.min
    return if (overlay.good == "high") overlay.max - position * span else overlay.min + position * span
}

private fun stopLabel(overlay: Overlay, position: Double): String {
    val figure = "${formatFigure(stopValue(overlay, position))} ${overlay.unit}"
    return when (position) {
        overlay.stops.first().first -> "$figure, best"
        overlay.stops.last().first -> "$figure, worst"
        else -> figure
    }
}

internal fun formatFigure(value: Double): String = if (value % 1.0 == 0.0) value.toLong().toString() else "%.2f".format(value).trimEnd('0').trimEnd('.')

internal fun overlayLegend(overlay: Overlay, ids: Collection<String>): List<Map<String, Any?>> {
    val used = ids.map { overlayStyleOf(overlay, it) }.toSet()
    val stopEntries = overlay.stops.map { (position, colour) -> mapOf("label" to stopLabel(overlay, position), "style" to "overlay: $colour") }
    val extras = listOfNotNull(
        if (UNKNOWN_STYLE in used) mapOf("label" to "unknown, tap for why", "style" to UNKNOWN_STYLE) else null,
        if (NOT_APPLICABLE_STYLE in used) mapOf("label" to "does not apply", "style" to NOT_APPLICABLE_STYLE) else null,
    )
    return stopEntries + extras
}

internal fun stopStyles(overlay: Overlay): Map<String, Map<String, Any?>> =
    overlay.stops.associate { (_, colour) -> "overlay: $colour" to mapOf("stroke" to colour, "fill" to colour + OVERLAY_FILL_ALPHA) }

internal fun overlayCaption(overlay: Overlay): String {
    val direction = if (overlay.good == "high") "higher" else "lower"
    return "Coloured by ${overlay.label} (${overlay.unit}, ${formatFigure(overlay.min)} to ${formatFigure(overlay.max)}); $direction is better."
}
