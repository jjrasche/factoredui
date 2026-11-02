package ai.factoredui.engine.layout

import ai.factoredui.compose.schema.DeviceClass
import ai.factoredui.compose.schema.LayoutThresholdSet
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.awt.image.BufferedImage
import java.io.File
import java.time.Instant
import javax.imageio.ImageIO
import kotlin.system.exitProcess

private val json = Json { prettyPrint = true }

private class ImagePixels(private val image: BufferedImage) : PixelGrid {
    override val width: Int get() = image.width
    override val height: Int get() = image.height
    override fun rgbAt(x: Int, y: Int): Int = image.getRGB(x, y) and 0xFFFFFF
}

private fun readCapture(directory: File, capture: JsonObject): LayoutCapture {
    val tree = Json.parseToJsonElement(File(directory, capture.getValue("tree").jsonPrimitive.content).readText()).jsonObject
    val shot = capture["shot"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.content?.let { ImageIO.read(File(directory, it)) }
    return LayoutCapture(capture["stop"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.content, tree, shot?.let(::ImagePixels))
}

private fun readPage(directory: File, entry: JsonObject): LayoutPage = LayoutPage(
    page = entry.getValue("page").jsonPrimitive.content,
    device = DeviceClass.fromWire(entry.getValue("device").jsonPrimitive.content),
    widthDp = entry.getValue("width").jsonPrimitive.double,
    heightDp = entry.getValue("height").jsonPrimitive.double,
    groundHex = entry.getValue("ground").jsonPrimitive.content,
    captures = entry.getValue("captures").jsonArray.map { readCapture(directory, it.jsonObject) },
)

private fun readingJson(reading: LayoutReading): JsonElement = buildJsonObject {
    put("page", reading.factor.page)
    put("device", reading.factor.device)
    put("factor", reading.factor.factorName)
    put("value", if (reading.factor.value.isNaN()) null else reading.factor.value)
    put("threshold", reading.threshold)
    put("higher_is_better", reading.higherIsBetter)
    put("verdict", reading.verdict.name)
    put("note", reading.note)
}

/** args: the capture directory (holding manifest.json and the trees and screenshots it names) and the file to write the table to. */
fun main(args: Array<String>) {
    if (args.size != 2) {
        System.err.println("usage: LayoutScoreCli <capture directory> <out.json>")
        exitProcess(2)
    }
    val directory = File(args[0])
    val manifest = Json.parseToJsonElement(File(directory, "manifest.json").readText()).jsonArray
    val computedAt = Instant.now().toString()
    val readings = manifest.flatMap { entry ->
        val page = readPage(directory, entry.jsonObject)
        readLayout(page, LayoutThresholdSet().forDevice(page.device), computedAt)
    }
    File(args[1]).writeText(json.encodeToString(JsonElement.serializer(), JsonArray(readings.map(::readingJson))))
    val failing = readings.count { it.verdict != LayoutVerdict.PASS }
    println("layout scores: ${readings.size} readings, $failing not PASS")
}
