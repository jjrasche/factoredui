package ai.factoredui.compose.pixel

import kotlin.io.encoding.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

enum class PixelSpriteKind { PATTERN, MODEL, TREE }

data class PixelSprite(
    val name: String,
    val kind: PixelSpriteKind,
    val cls: String,
    val size: String,
    val facing: String,
    val frame: Int,
    val sheet: Int,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val anchorX: Int,
    val anchorY: Int,
    val artTileWidth: Int,
    val artTileFeet: Double,
    val variant: String,
    val footprintFt: Pair<Double, Double>,
    val heightFt: Double,
    val swap: String,
)

data class SheetSize(val width: Int, val height: Int)

data class PixelScale(val artTileWidth: Int, val artTileFeet: Double, val sheets: List<SheetSize>, val sprites: List<PixelSprite>) {
    val byName: Map<String, PixelSprite> = sprites.associateBy { it.name }

    fun ofClass(cls: String): List<PixelSprite> = sprites.filter { it.cls == cls }
}

data class PixelSwap(val base: String, val ramps: Map<String, List<Int>>)

data class PixelManifest(val version: Int, val scales: Map<String, PixelScale>, val swaps: Map<String, PixelSwap>)

fun parsePixelManifest(json: String): PixelManifest {
    val root = Json.parseToJsonElement(json).jsonObject
    val scales = root.getValue("scales").jsonObject.entries.associate { (key, value) -> key to scaleOf(value.jsonObject) }
    val swaps = root.getValue("swaps").jsonObject.entries.associate { (key, value) -> key to swapOf(value.jsonObject) }
    return PixelManifest(root.int("version"), scales, swaps)
}

fun decodeEmbeddedManifest(chunks: List<String>): String = Base64.decode(chunks.joinToString("")).decodeToString()

fun argbOfHex(hex: String): Int = hex.removePrefix("#").toLong(16).toInt()

private fun scaleOf(fields: JsonObject) = PixelScale(
    artTileWidth = fields.int("art_tile_width"),
    artTileFeet = fields.double("art_tile_feet"),
    sheets = fields.getValue("sheets").jsonArray.map { SheetSize(it.jsonObject.int("width"), it.jsonObject.int("height")) },
    sprites = fields.getValue("sprites").jsonArray.map { spriteOf(it.jsonObject) },
)

private fun spriteOf(fields: JsonObject): PixelSprite {
    val footprint = fields.getValue("footprint_ft") as JsonArray
    return PixelSprite(
        name = fields.string("name"),
        kind = PixelSpriteKind.valueOf(fields.string("kind").uppercase()),
        cls = fields.string("class"),
        size = fields.string("size"),
        facing = fields.string("facing"),
        frame = fields.int("frame"),
        sheet = fields.int("sheet"),
        x = fields.int("x"),
        y = fields.int("y"),
        width = fields.int("width"),
        height = fields.int("height"),
        anchorX = fields.int("anchor_x"),
        anchorY = fields.int("anchor_y"),
        artTileWidth = fields.int("art_tile_width"),
        artTileFeet = fields.double("art_tile_feet"),
        variant = fields.string("variant"),
        footprintFt = footprint[0].jsonPrimitive.double to footprint[1].jsonPrimitive.double,
        heightFt = fields.double("height_ft"),
        swap = fields.string("swap"),
    )
}

private fun swapOf(fields: JsonObject) = PixelSwap(
    base = fields.string("base"),
    ramps = fields.getValue("ramps").jsonObject.entries.associate { (name, ramp) -> name to ramp.jsonArray.map { argbOfHex(it.jsonPrimitive.content) } },
)

private fun JsonObject.string(key: String): String = getValue(key).jsonPrimitive.content

private fun JsonObject.int(key: String): Int = getValue(key).jsonPrimitive.int

private fun JsonObject.double(key: String): Double = getValue(key).jsonPrimitive.double
