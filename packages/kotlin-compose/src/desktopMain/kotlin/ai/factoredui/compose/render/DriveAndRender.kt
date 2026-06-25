package ai.factoredui.compose.render

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import ai.factoredui.compose.renderer.RenderContext
import ai.factoredui.compose.renderer.RenderSpec
import ai.factoredui.compose.schema.Spec
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.skia.EncodedImageFormat

class DriveResult(val png: ByteArray, val finalStateJson: String)

// The DRIVE half of the headless loop: render a spec with initial state, fire REAL pointer gestures at
// the live scene (not a simulated before/after), re-render, and return the post-interaction PNG + the
// resulting state — so a judge can verify the interaction HANDLER worked (e.g. a curation drag moved
// position while relevance stayed put), not just that a hypothetical state renders.
@OptIn(ExperimentalComposeUiApi::class)
fun driveAndRender(
    specJson: String,
    initialStateJson: String,
    gestureJson: String,
    width: Int = 800,
    height: Int = 1280,
    density: Float = 2f,
): DriveResult {
    val spec = decoder.decodeFromString(Spec.serializer(), specJson)
    val initialData = jsonObjectToMap(decoder.parseToJsonElement(initialStateJson).jsonObject)
    val context = RenderContext(initialData = initialData)
    val scene = ImageComposeScene(width = width, height = height, density = Density(density)) {
        RenderSpec(spec = spec, context = context)
    }
    try {
        scene.render()
        for (step in decoder.parseToJsonElement(gestureJson).jsonArray) {
            val fields = step.jsonObject
            val type = pointerType(fields["type"]?.jsonPrimitive?.contentOrNull)
            val position = Offset(
                (fields["x"]?.jsonPrimitive?.doubleOrNull ?: 0.0).toFloat(),
                (fields["y"]?.jsonPrimitive?.doubleOrNull ?: 0.0).toFloat(),
            )
            scene.sendPointerEvent(eventType = type, position = position)
            scene.render()
        }
        val png = scene.render().encodeToData(EncodedImageFormat.PNG)?.bytes ?: ByteArray(0)
        return DriveResult(png = png, finalStateJson = mapToJson(context.data).toString())
    } finally {
        scene.close()
    }
}

private val decoder = Json { ignoreUnknownKeys = true }

private fun pointerType(name: String?): PointerEventType = when (name) {
    "down", "press" -> PointerEventType.Press
    "up", "release" -> PointerEventType.Release
    else -> PointerEventType.Move
}

private fun jsonObjectToMap(obj: JsonObject): Map<String, Any?> =
    obj.mapValues { (_, value) -> jsonToAny(value) }

private fun jsonToAny(element: JsonElement): Any? = when (element) {
    is JsonNull -> null
    is JsonObject -> jsonObjectToMap(element)
    is JsonArray -> element.map { jsonToAny(it) }
    is JsonPrimitive ->
        if (element.isString) element.content
        else element.booleanOrNull ?: element.doubleOrNull ?: element.content
}

private fun mapToJson(data: Map<String, Any?>): JsonObject = buildJsonObject {
    for ((key, value) in data) put(key, anyToJson(value))
}

private fun anyToJson(value: Any?): JsonElement = when (value) {
    null -> JsonNull
    is String -> JsonPrimitive(value)
    is Boolean -> JsonPrimitive(value)
    is Number -> JsonPrimitive(value)
    is Map<*, *> -> JsonObject(value.entries.associate { (k, v) -> k.toString() to anyToJson(v) })
    is List<*> -> JsonArray(value.map { anyToJson(it) })
    else -> JsonPrimitive(value.toString())
}
