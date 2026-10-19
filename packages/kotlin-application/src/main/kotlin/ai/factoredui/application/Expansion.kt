package ai.factoredui.application

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

class ExpansionFormatException(message: String) : IllegalArgumentException(message)

data class ExpandedObject(
    val id: String,
    val system: String,
    val type: String,
    val xMm: Double,
    val yMm: Double,
    val widthMm: Double?,
    val depthMm: Double?,
    val level: Int?,
    val fields: Map<String, Double?> = emptyMap(),
    val elevationMm: Double? = null,
)

data class ExpandedInstance(
    val id: String,
    val system: String,
    val type: String,
    val xMm: Double,
    val yMm: Double,
    val rotationDeg: Double,
    val lengthMm: Double,
    val elevationMm: Double = 0.0,
    val fields: Map<String, Double?> = emptyMap(),
)

data class SuppliedFigure(val name: String, val value: Double, val unit: String, val reason: String?)

data class Expansion(
    val extentMm: Pair<Double, Double>?,
    val objects: List<ExpandedObject>,
    val instances: List<ExpandedInstance>,
    val supplied: List<SuppliedFigure>,
)

private fun JsonObject.number(key: String): Double? = (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull && !it.isString }?.doubleOrNull

private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull && it.isString }?.content

private fun JsonObject.requiredNumber(key: String, where: String): Double = number(key) ?: throw ExpansionFormatException("$where lacks a number $key")

private fun JsonObject.requiredText(key: String, where: String): String = text(key) ?: throw ExpansionFormatException("$where lacks a text $key")

private fun fieldsOf(entry: JsonObject): Map<String, Double?> =
    (entry["fields"] as? JsonObject).orEmpty().mapNotNull { (name, value) ->
        when {
            value is JsonNull -> name to null
            value is JsonPrimitive && !value.isString -> value.doubleOrNull?.let { name to it }
            else -> null
        }
    }.toMap()

private fun objectOf(entry: JsonObject): ExpandedObject {
    val id = entry.requiredText("id", "an object")
    return ExpandedObject(
        id = id,
        system = entry.requiredText("system", "object '$id'"),
        type = entry.requiredText("type", "object '$id'"),
        xMm = entry.requiredNumber("x_mm", "object '$id'"),
        yMm = entry.requiredNumber("y_mm", "object '$id'"),
        widthMm = entry.number("width_mm"),
        depthMm = entry.number("depth_mm"),
        level = entry.number("level")?.toInt(),
        fields = fieldsOf(entry),
        elevationMm = entry.number("elevation_mm"),
    )
}

private fun instanceOf(entry: JsonObject): ExpandedInstance {
    val id = entry.requiredText("id", "an instance")
    return ExpandedInstance(
        id = id,
        system = entry.requiredText("system", "instance '$id'"),
        type = entry.requiredText("type", "instance '$id'"),
        xMm = entry.requiredNumber("x_mm", "instance '$id'"),
        yMm = entry.requiredNumber("y_mm", "instance '$id'"),
        rotationDeg = entry.number("rotation_deg") ?: 0.0,
        lengthMm = entry.requiredNumber("length_mm", "instance '$id'"),
        elevationMm = entry.number("elevation_mm") ?: 0.0,
        fields = fieldsOf(entry),
    )
}

private fun suppliedOf(name: String, entry: JsonObject): SuppliedFigure =
    SuppliedFigure(name, entry.requiredNumber("value", "supplied '$name'"), entry.requiredText("unit", "supplied '$name'"), entry.text("reason"))

private fun objectsIn(root: JsonObject, key: String): List<JsonObject> =
    (root[key] as? JsonArray).orEmpty().map { it as? JsonObject ?: throw ExpansionFormatException("$key holds an entry that is not an object") }

private fun extentOf(root: JsonObject): Pair<Double, Double>? {
    val pair = (root["extent_mm"] as? JsonArray)?.map { (it as? JsonPrimitive)?.doubleOrNull } ?: return null
    return if (pair.size == 2 && pair.all { it != null }) pair[0]!! to pair[1]!! else null
}

/** The engine may give two things one id; a selection and a card are keyed by id, so the second and later get a number. */
private fun <T> withUniqueIds(items: List<T>, taken: MutableSet<String>, idOf: (T) -> String, rename: (T, String) -> T): List<T> =
    items.map { item ->
        val base = idOf(item)
        var candidate = base
        var count = 1
        while (!taken.add(candidate)) candidate = "$base (${++count})"
        if (candidate == base) item else rename(item, candidate)
    }

/** Reads the answer of `we expand`; a refused answer is an error carrying the engine's reason. */
fun readExpansion(text: String): Expansion {
    val root = runCatching { Json.parseToJsonElement(text) }.getOrNull() as? JsonObject
        ?: throw ExpansionFormatException("the expansion is not a JSON object")
    root["refused"]?.let { throw ExpansionFormatException("the engine refused the world: $it") }
    val taken = mutableSetOf<String>()
    return Expansion(
        extentMm = extentOf(root),
        objects = withUniqueIds(objectsIn(root, "objects").map(::objectOf), taken, { it.id }) { item, id -> item.copy(id = id) },
        instances = withUniqueIds(objectsIn(root, "instances").map(::instanceOf), taken, { it.id }) { item, id -> item.copy(id = id) },
        supplied = (root["supplied"] as? JsonObject).orEmpty().map { (name, entry) -> suppliedOf(name, entry as? JsonObject ?: throw ExpansionFormatException("supplied '$name' is not an object")) },
    )
}
