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
      {"id":"small","type":"button","props":{"label":"Small","size":"sm"}},
      {"id":"plain","type":"button","props":{"label":"Plain"}},
      {"id":"large","type":"button","props":{"label":"Large","size":"lg"}},
      {"id":"huge","type":"button","props":{"label":"Lost me","size":"xl"}}]}
"""

private fun heightsOfButtons(): Map<String, Int> {
    val spec = json.decodeFromString(Spec.serializer(), """{"spec_version":1,"renderer_min":1,"root":$BUTTONS}""")
    val context = RenderContext()
    SceneSurface(spec, context, 400, 600, ticking = false).use { surface ->
        return surface.onScreenThread {
            listOf("small", "plain", "large", "huge").associateWith { id -> nodeById(screenJson(screenNodes(context.screen)!!), id)!!.getValue("bounds").jsonObject.getValue("h").jsonPrimitive.int }
        }
    }
}

class ButtonSizeRenderTest {
    @Test
    fun the_sizes_md_lg_and_xl_are_48_56_and_72_dp_tall() {
        val heights = heightsOfButtons()
        assertEquals(listOf(48, 56, 72), listOf("plain", "large", "huge").map { heights.getValue(it) })
    }

    @Test
    fun a_small_button_keeps_the_48_dp_touch_target_the_platform_gives_every_button() {
        val heights = heightsOfButtons()
        assertEquals(48, heights.getValue("small"))
        assertTrue(heights.getValue("plain") < heights.getValue("large") && heights.getValue("large") < heights.getValue("huge"), heights.toString())
    }
}
