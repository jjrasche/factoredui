package ai.factoredui.compose.render

import ai.factoredui.compose.adapter.ActionRegistry
import ai.factoredui.compose.renderer.RenderContext
import ai.factoredui.compose.schema.BindingResolver
import ai.factoredui.compose.schema.Spec
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.awt.Point
import kotlin.test.Test
import kotlin.test.assertEquals

private val json = Json { ignoreUnknownKeys = true }

private const val TWO_PARAGRAPHS = """[
  {"t":"In","at":0,"end":1},{"t":"the","at":1,"end":2},{"t":"beginning","at":2,"end":3},
  {"t":"Second","at":5,"end":6,"p":true,"v":"2"},{"t":"paragraph","at":6,"end":7}]"""

private fun node(extra: String = "") =
    """{"id":"text","type":"readalong","props":{"words":$TWO_PARAGRAPHS,"position":"{pos}","size":20$extra}}"""

private class ReadAlongPage(extra: String = "", position: Double = 0.0) : AutoCloseable {
    val tapped = mutableListOf<Map<String, Any?>>()
    private val actions: ActionRegistry = mapOf("tapped" to { params -> tapped += params })
    val context = RenderContext(actions = actions, initialData = mapOf("pos" to position))
    private val spec = """{"spec_version":1,"renderer_min":1,"root":{"id":"page","type":"column","props":{"padding":16},"children":[${node(extra)}]}}"""
    private val surface = SceneSurface(json.decodeFromString(Spec.serializer(), spec), context, 400, 400, ticking = false)

    fun settle() = surface.onScreenThread { surface.onScreenThread { } }

    fun node(): JsonObject = surface.onScreenThread { nodeById(screenJson(screenNodes(context.screen)!!), "text")!! }

    fun shown(key: String): String? = node().getValue("props").jsonObject[key]?.jsonPrimitive?.content

    fun moveTo(seconds: Double) {
        context.setBinding("pos", seconds)
        settle()
    }

    fun tapFirstLine() = tapAt { 8 }

    fun tapLastLine() = tapAt { height -> height - 8 }

    private fun tapAt(yFromTop: (Int) -> Int) {
        val bounds = node().getValue("bounds").jsonObject
        val height = bounds.getValue("h").jsonPrimitive.int
        surface.click(Point(bounds.getValue("x").jsonPrimitive.int + 4, bounds.getValue("y").jsonPrimitive.int + yFromTop(height)))
        settle()
    }

    fun bound(path: String): Any? = BindingResolver.resolveBinding("{$path}", context.data)

    override fun close() = surface.close()
}

class ReadAlongRenderTest {
    @Test
    fun it_reads_back_its_text_and_the_word_the_position_lights() = ReadAlongPage(position = 1.5).use { page ->
        page.settle()
        assertEquals("the", page.shown("lit_text"))
        assertEquals("1", page.shown("lit_index"))
        assertEquals("5", page.shown("word_count"))
    }

    @Test
    fun the_lit_word_follows_the_position_binding() = ReadAlongPage().use { page ->
        page.settle()
        assertEquals("In", page.shown("lit_text"))
        page.moveTo(6.5)
        assertEquals("paragraph", page.shown("lit_text"))
    }

    @Test
    fun a_tap_on_a_word_writes_that_words_start_to_the_position() = ReadAlongPage().use { page ->
        page.settle()
        page.tapFirstLine()
        assertEquals(0.0, page.bound("pos"))
        page.tapLastLine()
        assertEquals(5.0, page.bound("pos"))
        assertEquals("Second", page.shown("lit_text"))
    }

    @Test
    fun a_tap_also_tells_the_word_tap_action_the_index_start_and_verse() = ReadAlongPage(""","on_word_tap":{"action":"tapped","params":{"book":"b1"}}""").use { page ->
        page.settle()
        page.tapLastLine()
        assertEquals(listOf(mapOf<String, Any?>("book" to "b1", "index" to 3.0, "at" to 5.0, "verse" to "2")), page.tapped)
    }
}
