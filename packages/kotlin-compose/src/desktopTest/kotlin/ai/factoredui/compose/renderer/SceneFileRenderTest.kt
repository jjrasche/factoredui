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
private const val SCENE_FILE_SUFFIX = ".scene.json"
private const val PHONE_WIDTH_PX = 780
private const val PHONE_HEIGHT_PX = 1560
private const val PHONE_DENSITY = 2f

private fun plainValueOf(element: JsonElement): Any? = when (element) {
    is JsonNull -> null
    is JsonObject -> element.mapValues { plainValueOf(it.value) }
    is JsonArray -> element.map { plainValueOf(it) }
    is JsonPrimitive -> if (element.isString) element.content else element.booleanOrNull ?: element.doubleOrNull
}

private fun onlyLimitOf(scene: Any?): String? = ((scene as? Map<*, *>)?.get("limits") as? Map<*, *>)?.keys?.singleOrNull()?.toString()

private fun sceneSpec(colourBy: String?): String = """
    { "spec_version": 1, "renderer_min": 1,
      "root": { "id": "root", "type": "stack", "props": { "fill": true }, "children": [
        { "id": "scene", "type": "volume", "props": { "fill": true, "anchor": "fill", "scene": "{engine.scene}", ${colourBy?.let { """"colour_by": "$it", "value_ramp": "heat",""" } ?: ""} "lighting": "sun", "fit": true, "fov_deg": 12 } } ] } }
"""

class SceneFileRenderTest {

    @Test
    fun every_scene_answer_file_in_the_folder_is_drawn_coloured_by_its_one_limit_and_an_empty_folder_says_so() {
        val directory = File(System.getProperty(SCENE_DIRECTORY_PROPERTY) ?: DEFAULT_SCENE_DIRECTORY)
        val files = directory.listFiles { file -> file.name.endsWith(SCENE_FILE_SUFFIX) }.orEmpty().sortedBy { it.name }
        if (files.isEmpty()) println("SCENE-FILE-ABSENT $directory")
        files.forEach { file ->
            val scene = plainValueOf(Json.parseToJsonElement(file.readText()))
            val png = renderSpecToPng(
                specJson = sceneSpec(onlyLimitOf(scene)),
                width = PHONE_WIDTH_PX,
                height = PHONE_HEIGHT_PX,
                density = PHONE_DENSITY,
                data = mapOf("engine" to mapOf("scene" to scene)),
            )
            println("SCENE-FILE-PNG ${file.name.removeSuffix(SCENE_FILE_SUFFIX)} ${Base64.getEncoder().encodeToString(png)}")
            assertTrue(png.isNotEmpty(), file.name)
        }
    }
}
