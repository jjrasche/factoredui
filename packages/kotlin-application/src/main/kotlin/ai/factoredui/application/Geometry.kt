package ai.factoredui.application

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

class GeometryFormatException(message: String) : IllegalArgumentException(message)

data class GeoPiece(val id: String, val system: String, val xMm: Double, val yMm: Double, val zMm: Double, val dxMm: Double, val dyMm: Double, val dzMm: Double)

data class GeoRegion(val minMm: List<Double>, val maxMm: List<Double>)

data class GeoContact(val a: String, val b: String, val normal: String, val region: GeoRegion)

data class GeoCut(val host: String, val opening: String, val region: GeoRegion)

data class GeoUnmeasured(val id: String, val field: String, val why: String)

data class Geometry(val pieces: List<GeoPiece>, val contacts: List<GeoContact>, val cuts: List<GeoCut>, val unmeasured: List<GeoUnmeasured>)

data class Geometried(val geometry: Geometry?, val failure: String? = null)

private fun JsonObject.text(key: String, where: String): String =
    (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: throw GeometryFormatException("$where lacks a text $key")

private fun JsonObject.number(key: String, where: String): Double =
    (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull ?: throw GeometryFormatException("$where lacks a number $key")

private fun entries(root: JsonObject, key: String): List<JsonObject> =
    (root[key] as? JsonArray ?: throw GeometryFormatException("the geometry has no list $key")).map { it as? JsonObject ?: throw GeometryFormatException("$key holds an entry that is not an object") }

private fun pointOf(element: JsonElement?, where: String): List<Double> =
    (element as? JsonArray)?.map { (it as? JsonPrimitive)?.doubleOrNull ?: throw GeometryFormatException("$where holds a point that is not numbers") }?.takeIf { it.size == 3 }
        ?: throw GeometryFormatException("$where lacks a point of three numbers")

private fun regionOf(entry: JsonObject, where: String): GeoRegion {
    val region = entry["region"] as? JsonObject ?: throw GeometryFormatException("$where lacks a region")
    return GeoRegion(pointOf(region["min_mm"], where), pointOf(region["max_mm"], where))
}

private fun pieceOf(entry: JsonObject): GeoPiece {
    val id = entry.text("id", "a piece")
    val where = "piece '$id'"
    return GeoPiece(id, entry.text("system", where), entry.number("x", where), entry.number("y", where), entry.number("z", where), entry.number("dx", where), entry.number("dy", where), entry.number("dz", where))
}

fun readGeometry(text: String): Geometry {
    val root = runCatching { Json.parseToJsonElement(text) }.getOrNull() as? JsonObject ?: throw GeometryFormatException("the geometry is not a JSON object")
    (root["refused"] as? JsonObject)?.let { throw GeometryFormatException(it.text("message", "a refusal")) }
    return Geometry(
        pieces = entries(root, "pieces").map(::pieceOf),
        contacts = entries(root, "contacts").map { GeoContact(it.text("a", "a contact"), it.text("b", "a contact"), it.text("normal", "a contact"), regionOf(it, "a contact")) },
        cuts = entries(root, "cuts").map { GeoCut(it.text("host", "a cut"), it.text("opening", "a cut"), regionOf(it, "a cut")) },
        unmeasured = entries(root, "unmeasured").map { GeoUnmeasured(it.text("id", "an unmeasured piece"), it.text("field", "an unmeasured piece"), it.text("why", "an unmeasured piece")) },
    )
}
