package ai.factoredui.worldengine.ground

import ai.factoredui.worldengine.expression.compensatedSum
import ai.factoredui.worldengine.json.MalformedDataException
import ai.factoredui.worldengine.json.isBooleanLiteral
import ai.factoredui.worldengine.json.missingKey
import ai.factoredui.worldengine.json.requiredText
import ai.factoredui.worldengine.state.Tile
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.hypot

const val GROUND_FLOOR_MM: Long = -500_000L
const val GROUND_CEILING_MM: Long = 5_000_000L
const val MAX_GROUND_CHANGE_MM: Long = 50_000L
val GROUND_VERBS: List<String> = listOf("dig", "raise")
val GROUND_AMOUNT_FIELDS: Map<String, String> = linkedMapOf("dig" to "depth_mm", "raise" to "height_mm")

class GroundSpec(val raw: JsonObject) {
    val heights: JsonArray get() = raw["heights_mm"] as? JsonArray ?: throw missingKey("heights_mm")
    val datum: String get() = raw.requiredText("datum")
    val source: String get() = raw.requiredText("source")
    val verticalError: JsonElement get() = (raw["error"] as? JsonObject)?.get("vertical_mm") ?: throw missingKey("vertical_mm")

    fun seedHeightsMm(): List<Double> = heights.map { heightOf(it) }
}

data class GroundState(val heightsMm: List<Double>, val version: Long) {
    fun reshaped(corners: List<Int>, changeMm: Double): GroundState {
        val heights = heightsMm.toMutableList()
        corners.forEach { heights[it] = heights[it] + changeMm }
        return GroundState(heights, version + 1)
    }

    fun cutFillMm(baseMm: List<Double>): List<Double> = heightsMm.zip(baseMm) { now, seed -> now - seed }

    fun lowestMm(): Double = heightsMm.minOrNull() ?: throw MalformedDataException("min() arg is an empty sequence")

    fun highestMm(): Double = heightsMm.maxOrNull() ?: throw MalformedDataException("max() arg is an empty sequence")
}

fun isGroundNumber(element: JsonElement): Boolean = element is JsonPrimitive && element !is JsonNull && !element.isString && !isBooleanLiteral(element)

private fun heightOf(element: JsonElement): Double {
    if (!isGroundNumber(element)) throw MalformedDataException("ground height $element is not a number")
    return (element as JsonPrimitive).content.toDouble()
}

fun isWithinGroundRange(heightMm: Double): Boolean = heightMm >= GROUND_FLOOR_MM && heightMm <= GROUND_CEILING_MM

fun expectedGroundLength(cols: Int, rows: Int): Int = (cols + 1) * (rows + 1)

fun outOfRangeVertices(heights: JsonArray): List<Int> =
    heights.indices.filter { isGroundNumber(heights[it]) && !isWithinGroundRange(heightOf(heights[it])) }

fun findGroundProblems(ground: GroundSpec?, cols: Int, rows: Int): List<String> {
    val heights = ground?.heights ?: return emptyList()
    val expected = expectedGroundLength(cols, rows)
    val problems = mutableListOf<String>()
    if (heights.size != expected) problems += "${heights.size} heights, but a $cols x $rows grid has (cols + 1) x (rows + 1) = $expected vertices"
    val outside = outOfRangeVertices(heights)
    if (outside.isNotEmpty()) problems += "heights at vertex indices $outside lie outside $GROUND_FLOOR_MM to $GROUND_CEILING_MM mm"
    return problems
}

fun tileCornerIndices(cols: Int, tile: Tile): List<Int> {
    val vertexCols = cols + 1
    val north = tile.row * vertexCols + tile.col
    val south = (tile.row + 1) * vertexCols + tile.col
    return listOf(north, north + 1, south, south + 1)
}

fun tileCornerTouches(tile: Tile): List<String> =
    listOf(tile.row, tile.row + 1).flatMap { vertexRow -> listOf(tile.col, tile.col + 1).map { vertexCol -> "ground:$vertexCol,$vertexRow" } }

fun tileMeanMm(heightsMm: List<Double>, cols: Int, tile: Tile): Double = compensatedSum(tileCornerIndices(cols, tile).map { heightsMm[it] }) / 4

fun tileSlopePct(heightsMm: List<Double>, cols: Int, tile: Tile, sideMm: Double): Double {
    val (northWest, northEast, southWest, southEast) = tileCornerIndices(cols, tile).map { heightsMm[it] }
    val southEastTriangle = hypot(southEast - southWest, northEast - southEast)
    val northWestTriangle = hypot(northEast - northWest, northWest - southWest)
    return 100 * maxOf(southEastTriangle, northWestTriangle) / sideMm
}
