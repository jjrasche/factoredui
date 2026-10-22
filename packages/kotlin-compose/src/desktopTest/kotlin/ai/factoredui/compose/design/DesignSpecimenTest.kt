package ai.factoredui.compose.design

import ai.factoredui.compose.render.SceneSurface
import ai.factoredui.compose.render.screenJson
import ai.factoredui.compose.render.screenNodes
import ai.factoredui.compose.renderer.RenderContext
import ai.factoredui.compose.renderer.SpecTheme
import ai.factoredui.compose.schema.Spec
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val MODES = mapOf("dark" to SpecTheme.DARK, "light" to SpecTheme.LIGHT)

private fun committed(name: String) = File("../../design/specimen/$name")

private fun noticesIn(element: JsonElement): List<String> = when (element) {
    is JsonObject -> ((element["notices"] as? JsonArray)?.map { (it as JsonPrimitive).content } ?: emptyList()) + (element["children"]?.let(::noticesIn) ?: emptyList())
    is JsonArray -> element.flatMap(::noticesIn)
    else -> emptyList()
}

class DesignSpecimenTest {
    @Test
    fun the_committed_specimen_specs_are_what_the_tokens_generate_so_they_cannot_drift() {
        MODES.forEach { (mode, theme) ->
            assertEquals(designSpecimenSpec(theme, mode), committed("design-system-$mode.spec.json").readText().replace("\r\n", "\n"), "design/specimen/design-system-$mode.spec.json differs: run the designSpecimen task")
        }
    }

    @Test
    fun each_committed_specimen_has_its_screenshot() {
        MODES.keys.forEach { mode -> assertTrue(committed("design-system-$mode.png").length() > 10_000, mode) }
    }

    @Test
    fun no_node_of_the_specimen_reports_a_notice_in_either_theme() {
        MODES.forEach { (mode, theme) ->
            val spec = Json { ignoreUnknownKeys = true }.decodeFromString(Spec.serializer(), designSpecimenSpec(theme, mode))
            val context = RenderContext(theme = theme)
            SceneSurface(spec, context, 430, 900, ticking = false).use { surface ->
                val notices = surface.onScreenThread { noticesIn(screenJson(screenNodes(context.screen)!!)) }
                assertEquals(emptyList(), notices, mode)
            }
        }
    }
}
