package ai.factoredui.compose.render

import ai.factoredui.compose.renderer.RenderContext
import ai.factoredui.compose.schema.BindingResolver
import ai.factoredui.compose.schema.Spec
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.awt.Point
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

private val json = Json { ignoreUnknownKeys = true }

private fun slot(id: String, slot: String) = """{"id":"$id","type":"text","props":{"value":"$id","slot":"$slot"}}"""

private const val SHEET = """
  {"id":"sheet","type":"sheet","props":{"stop":"{piece.stop}"},"children":[
    {"id":"name","type":"text","props":{"value":"Wall 3","slot":"peek"}},
    {"id":"size","type":"text","props":{"value":"8 ft 0 in","slot":"half"}},
    {"id":"layers","type":"text","props":{"value":"OSB EPS OSB","slot":"full"}}]}"""

private const val WINDOW_HEIGHT = 700
private const val PEEK_DP = 96.0
private const val HALF_DP = 320.0
private const val HANDLE_DP = 44.0
private const val FULL_FRACTION = 0.92

private class SheetPage(private val width: Int, stop: String = "peek") : AutoCloseable {
    val context = RenderContext(initialData = mapOf("piece" to mapOf("stop" to stop)))
    private val spec = """{"spec_version":1,"renderer_min":1,"root":{"id":"page","type":"stack","props":{"width":"fill","height":"fill"},"children":[$SHEET]}}"""
    private val surface = SceneSurface(json.decodeFromString(Spec.serializer(), spec), context, width, WINDOW_HEIGHT, ticking = false)

    fun settle() = surface.onScreenThread { surface.onScreenThread { } }

    fun node(id: String): JsonObject? = surface.onScreenThread { nodeById(screenJson(screenNodes(context.screen)!!), id) }

    fun bound(path: String): Any? = BindingResolver.resolveBinding("{$path}", context.data)

    fun tapHandle() {
        val sheetHeight = when (bound("piece.stop")) {
            "half" -> HALF_DP
            "full" -> WINDOW_HEIGHT.toDouble() * FULL_FRACTION
            else -> PEEK_DP
        }
        surface.click(Point(width / 2, (WINDOW_HEIGHT - sheetHeight + HANDLE_DP / 2.0).toInt()))
        settle()
    }

    fun shown(key: String): String? = node("sheet")?.getValue("props")?.jsonObject?.get(key)?.jsonPrimitive?.content

    override fun close() = surface.close()
}

class SheetRenderTest {
    @Test
    fun on_a_phone_it_peeks_with_only_the_peek_slot_showing() = SheetPage(width = 400).use { page ->
        page.settle()
        assertEquals("sheet", page.shown("layout"))
        assertNotNull(page.node("name"))
        assertNull(page.node("size"))
        assertNull(page.node("layers"))
    }

    @Test
    fun half_adds_the_half_slot_and_full_adds_the_full_slot() = SheetPage(width = 400).use { page ->
        page.context.setBinding("piece.stop", "half")
        page.settle()
        assertNotNull(page.node("size"))
        assertNull(page.node("layers"))
        page.context.setBinding("piece.stop", "full")
        page.settle()
        assertEquals(true, page.node("layers") != null)
    }

    @Test
    fun a_tap_on_the_handle_steps_up_a_stop_through_the_binding_and_wraps_from_full_to_peek() = SheetPage(width = 400).use { page ->
        page.settle()
        page.tapHandle()
        assertEquals("half", page.bound("piece.stop"))
        page.tapHandle()
        assertEquals("full", page.bound("piece.stop"))
        page.tapHandle()
        assertEquals("peek", page.bound("piece.stop"))
    }

    @Test
    fun on_a_wide_window_it_is_a_side_panel_with_every_slot_and_no_handle() = SheetPage(width = 1000).use { page ->
        page.settle()
        assertEquals("side", page.shown("layout"))
        assertEquals(listOf(true, true, true), listOf("name", "size", "layers").map { page.node(it) != null })
        assertNull(page.node("sheet/handle"))
    }
}
