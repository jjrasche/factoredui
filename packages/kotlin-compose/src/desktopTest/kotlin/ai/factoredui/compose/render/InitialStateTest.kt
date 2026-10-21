package ai.factoredui.compose.render

import ai.factoredui.compose.renderer.RenderContext
import ai.factoredui.compose.schema.Spec
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

private val json = Json { ignoreUnknownKeys = true }

private val SPEC = json.decodeFromString(
    Spec.serializer(),
    """{"spec_version":1,"renderer_min":1,"state":{"day.from":2757,"day.length":23},"root":{"id":"n","type":"text","props":{"value":"from {day.from}"}}}""",
)

private fun dataAfterDrawing(context: RenderContext): Map<String, Any?> {
    SceneSurface(SPEC, context, 300, 100, ticking = false).use { surface -> surface.onScreenThread { } }
    return context.data
}

private fun mapAt(data: Map<String, Any?>, key: String) = data[key] as Map<*, *>

class InitialStateTest {
    @Test
    fun a_path_with_no_value_starts_at_the_value_the_spec_gives() {
        val data = dataAfterDrawing(RenderContext())
        assertEquals(mapOf("from" to 2757.0, "length" to 23.0), mapAt(data, "day"))
    }

    @Test
    fun a_path_that_already_has_a_value_keeps_it() {
        val data = dataAfterDrawing(RenderContext(initialData = mapOf("day" to mapOf("from" to 100.0))))
        assertEquals(mapOf("from" to 100.0, "length" to 23.0), mapAt(data, "day"))
    }

    @Test
    fun the_first_frame_already_shows_the_started_value() {
        val context = RenderContext()
        SceneSurface(SPEC, context, 300, 100, ticking = false).use { surface ->
            val text = surface.onScreenThread { screenNodes(context.screen)!!.record.text }
            assertEquals("from 2757", text)
        }
    }
}
