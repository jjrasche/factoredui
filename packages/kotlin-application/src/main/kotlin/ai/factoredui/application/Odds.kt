package ai.factoredui.application

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

class OddsFormatException(message: String) : IllegalArgumentException(message)

data class Spread(val n: Int, val min: Double, val p5: Double, val p25: Double, val p50: Double, val p75: Double, val p95: Double, val max: Double)

data class Threshold(val path: String, val value: Double, val fraction: Double)

data class Joint(val paths: List<String>, val fraction: Double)

data class Sampling(val runs: Int, val seed: Long, val spreads: Map<String, Spread>, val thresholds: List<Threshold>, val joint: Joint?, val failedRuns: Int, val quality: Map<String, Int> = emptyMap())

data class Sampled(val sampling: Sampling?, val failure: String? = null)

private fun JsonObject.number(key: String, where: String): Double =
    (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull ?: throw OddsFormatException("$where lacks a number $key")

private fun spreadOf(path: String, element: kotlinx.serialization.json.JsonElement): Spread {
    val entry = element as? JsonObject ?: throw OddsFormatException("the output $path is not an object")
    val where = "the output $path"
    return Spread(
        entry.number("n", where).toInt(), entry.number("min", where), entry.number("p5", where), entry.number("p25", where),
        entry.number("p50", where), entry.number("p75", where), entry.number("p95", where), entry.number("max", where),
    )
}

private fun thresholdOf(path: String, element: kotlinx.serialization.json.JsonElement): Threshold {
    val entry = element as? JsonObject ?: throw OddsFormatException("the threshold on $path is not an object")
    return Threshold(path, entry.number("value", "the threshold on $path"), entry.number("fraction", "the threshold on $path"))
}

private fun jointOf(element: kotlinx.serialization.json.JsonElement?): Joint? {
    val entry = element as? JsonObject ?: return null
    val paths = (entry["paths"] as? JsonArray ?: throw OddsFormatException("the joint has no paths")).map { (it as JsonPrimitive).content }
    return Joint(paths, entry.number("fraction", "the joint"))
}

private fun qualityOf(element: kotlinx.serialization.json.JsonElement?): Map<String, Int> =
    (element as? JsonObject).orEmpty().mapNotNull { (label, tally) -> ((tally as? JsonObject)?.get("inputs") as? JsonPrimitive)?.intOrNull?.let { label to it } }.toMap()

fun readSampling(text: String): Sampling {
    val root = runCatching { Json.parseToJsonElement(text) }.getOrNull() as? JsonObject ?: throw OddsFormatException("the sample is not a JSON object")
    (root["refused"] as? JsonObject)?.let { throw OddsFormatException((it["message"] as? JsonPrimitive)?.content ?: "the sample was refused") }
    val outputs = root["outputs"] as? JsonObject ?: throw OddsFormatException("the sample has no outputs")
    val below = (root["below"] as? JsonObject).orEmpty()
    val failed = ((root["failed"] as? JsonObject)?.get("count") as? JsonPrimitive)?.intOrNull ?: 0
    return Sampling(
        root.number("runs", "the sample").toInt(), root.number("seed", "the sample").toLong(),
        outputs.mapValues { (path, element) -> spreadOf(path, element) }, below.map { (path, element) -> thresholdOf(path, element) }, jointOf(root["joint"]), failed, qualityOf(root["quality"]),
    )
}
