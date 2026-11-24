package ai.factoredui.compose.renderer

import ai.factoredui.compose.render.renderSpecToPng
import java.io.File
import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlin.test.Test
import kotlin.test.assertTrue

private const val SCENE_DIRECTORY_PROPERTY = "scene.files.directory"
private const val DEFAULT_SCENE_DIRECTORY = "C:/Users/falcon-ridge-bmax/workspace/world-engine/kotlin/build/scenes"
private const val PHONE_WIDTH_PX = 780
private const val PHONE_HEIGHT_PX = 1560
private const val PHONE_DENSITY = 2f

private fun plainValueOf(element: JsonElement): Any? = when (element) {
    is JsonNull -> null
    is JsonObject -> element.mapValues { plainValueOf(it.value) }
    is JsonArray -> element.map { plainValueOf(it) }
    is JsonPrimitive -> if (element.isString) element.content else element.booleanOrNull ?: element.doubleOrNull
}

private fun sceneSpec(colourBy: String?): String = """
    { "spec_version": 1, "renderer_min": 1,
      "root": { "id": "root", "type": "stack", "props": { "fill": true }, "children": [
        { "id": "scene", "type": "volume", "props": { "fill": true, "anchor": "fill", "scene": "{engine.scene}", ${colourBy?.let { """"colour_by": "$it", "value_ramp": "heat",""" } ?: ""} "lighting": "sun", "fit": true, "fov_deg": 12 } } ] } }
"""

class SceneFileRenderTest {

    @Test
    fun the_scene_answer_files_the_world_engine_wrote_are_drawn_when_they_are_on_this_machine() {
        val directory = File(System.getProperty(SCENE_DIRECTORY_PROPERTY) ?: DEFAULT_SCENE_DIRECTORY)
        val files = listOf("frame" to "utilisation", "greenhouse" to null).map { (name, colourBy) -> Triple(name, File(directory, "$name.scene.json"), colourBy) }
        val present = files.filter { it.second.isFile }
        files.filterNot { it.second.isFile }.forEach { println("SCENE-FILE-ABSENT ${it.second}") }
        present.forEach { (name, file, colourBy) ->
            val scene = plainValueOf(Json.parseToJsonElement(file.readText()))
            val png = renderSpecToPng(sceneSpec(colourBy), PHONE_WIDTH_PX, PHONE_HEIGHT_PX, PHONE_DENSITY, mapOf("engine" to mapOf("scene" to scene)))
            println("SCENE-FILE-PNG $name ${Base64.getEncoder().encodeToString(png)}")
            assertTrue(png.isNotEmpty(), name)
        }
    }
}
