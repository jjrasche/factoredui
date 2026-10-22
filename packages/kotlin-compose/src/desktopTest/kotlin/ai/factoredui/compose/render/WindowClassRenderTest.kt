package ai.factoredui.compose.render

import ai.factoredui.compose.renderer.RenderContext
import ai.factoredui.compose.schema.BindingResolver
import ai.factoredui.compose.schema.Spec
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

private val json = Json { ignoreUnknownKeys = true }

private const val ADAPTIVE = """
    {"spec_version":1,"renderer_min":1,"root":{"id":"root","type":"column","props":{},"children":[
      {"id":"phone","type":"text","props":{"value":"phone layout"},"visible":"{window.is_compact}"},
      {"id":"tablet","type":"text","props":{"value":"tablet layout"},"visible":"{window.is_medium}"},
      {"id":"desktop","type":"text","props":{"value":"desktop layout"},"visible":"{window.is_expanded}"}]}}
"""

private fun drawnAt(width: Int): Pair<List<String>, Any?> {
    val context = RenderContext()
    SceneSurface(json.decodeFromString(Spec.serializer(), ADAPTIVE), context, width, 600, ticking = false).use { surface ->
        val shown = surface.onScreenThread { listOf("phone", "tablet", "desktop").filter { nodeById(screenJson(screenNodes(context.screen)!!), it)!!.getValue("visible").jsonPrimitive.boolean } }
        return shown to BindingResolver.resolveBinding("{window.class}", context.data)
    }
}

class WindowClassRenderTest {
    @Test
    fun one_spec_shows_the_phone_tablet_or_desktop_layout_by_the_windows_width() {
        assertEquals(listOf(listOf("phone"), listOf("tablet"), listOf("desktop")), listOf(drawnAt(400).first, drawnAt(700).first, drawnAt(1000).first))
    }

    @Test
    fun the_class_is_a_binding_a_spec_can_read() {
        assertEquals(listOf<Any?>("compact", "medium", "expanded"), listOf(drawnAt(400).second, drawnAt(700).second, drawnAt(1000).second))
    }
}
