package ai.factoredui.compose.render

import ai.factoredui.compose.renderer.RenderContext
import ai.factoredui.compose.schema.Spec
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val json = Json { ignoreUnknownKeys = true }

private const val BUTTONS = """
    {"id":"root","type":"column","props":{"gap":8},"children":[
      {"id":"plain","type":"button","props":{"label":"Plain"}},
      {"id":"large","type":"button","props":{"label":"Large","size":"lg"}},
      {"id":"huge","type":"button","props":{"label":"Lost me","size":"xl"}}]}
"""

private fun heightsOfButtons(): Map<String, Int> {
    val spec = json.decodeFromString(Spec.serializer(), """{"spec_version":1,"renderer_min":1,"root":$BUTTONS}""")
    val context = RenderContext()
    SceneSurface(spec, context, 400, 600, ticking = false).use { surface ->
        return surface.onScreenThread {
            listOf("plain", "large", "huge").associateWith { id -> nodeById(screenJson(screenNodes(context.screen)!!), id)!!.getValue("bounds").jsonObject.getValue("h").jsonPrimitive.int }
        }
    }
}

class ButtonSizeRenderTest {
    @Test
    fun an_xl_button_is_at_least_72_dp_tall_for_a_thumb() {
        assertTrue(heightsOfButtons().getValue("huge") >= 72)
    }

    @Test
    fun the_other_sizes_are_drawn_as_they_were_before() {
        val heights = heightsOfButtons()
        assertEquals(heights.getValue("plain"), heights.getValue("large"))
        assertTrue(heights.getValue("plain") < 72, heights.toString())
    }
}
