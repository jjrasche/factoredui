package ai.factoredui.compose.render

import ai.factoredui.compose.adapter.ActionRegistry
import ai.factoredui.compose.media.MediaHost
import ai.factoredui.compose.media.VoiceCapture
import ai.factoredui.compose.renderer.RenderContext
import ai.factoredui.compose.schema.BindingResolver
import ai.factoredui.compose.schema.Spec
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.awt.Point
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val json = Json { ignoreUnknownKeys = true }

private fun talk(extra: String = "") =
    """{"id":"talk","type":"voice","props":{"label":"Hold to talk","listening":"{mic.listening}","transcript":"{mic.said}","on_capture":{"action":"heard","params":{"book":"b1"}}$extra}}"""

private class VoicePage(node: String, engine: FakeVoiceEngine? = FakeVoiceEngine()) : AutoCloseable {
    val engine: FakeVoiceEngine? = engine
    val heard = mutableListOf<Map<String, Any?>>()
    private val actions: ActionRegistry = mapOf("heard" to { params -> heard += params })
    val context = RenderContext(actions = actions, mediaHost = if (engine == null) MediaHost.NONE else MediaHost(null, { engine }))
    private val surface = SceneSurface(json.decodeFromString(Spec.serializer(), """{"spec_version":1,"renderer_min":1,"root":$node}"""), context, 500, 300, ticking = false)

    fun settle() = surface.onScreenThread { surface.onScreenThread { } }

    fun <T> onScreen(work: () -> T): T = surface.onScreenThread { work() }.also { settle() }

    fun node(): JsonObject = surface.onScreenThread { nodeById(screenJson(screenNodes(context.screen)!!), "talk")!! }

    fun voice(): JsonObject = node().getValue("voice").jsonObject

    fun tap(): Point {
        val bounds = node().getValue("bounds").jsonObject
        val point = Point(bounds.getValue("x").jsonPrimitive.int + bounds.getValue("w").jsonPrimitive.int / 2, bounds.getValue("y").jsonPrimitive.int + bounds.getValue("h").jsonPrimitive.int / 2)
        surface.click(point)
        settle()
        return point
    }

    fun bound(path: String): Any? = BindingResolver.resolveBinding("{$path}", context.data)

    override fun close() = surface.close()
}

class VoiceRenderTest {
    @Test
    fun it_reads_back_its_label_mode_and_capture_and_that_it_is_ready() = VoicePage(talk()).use { page ->
        page.settle()
        assertEquals(listOf("Hold to talk", "hold", "text", "ready"), listOf("label", "mode", "capture", "status").map { page.voice().getValue(it).jsonPrimitive.content })
    }

    @Test
    fun a_press_and_release_starts_and_then_stops_the_engine_for_what_the_node_captures() = VoicePage(talk(""","lang":"en-GB"""")).use { page ->
        page.settle()
        page.tap()
        val request = page.engine!!.requests.single()
        assertEquals(listOf<Any?>(VoiceCapture.TEXT, "en-GB"), listOf(request.capture, request.language))
        assertEquals(1, page.engine.stops, "a release stops what the press started, even before the engine has said it is listening")
    }

    @Test
    fun in_toggle_mode_one_tap_starts_and_the_next_stops() = VoicePage(talk(""","mode":"toggle"""")).use { page ->
        page.settle()
        page.tap()
        assertEquals(listOf(1, 0), listOf(page.engine!!.requests.size, page.engine.stops))
        page.tap()
        assertEquals(listOf(1, 1), listOf(page.engine.requests.size, page.engine.stops))
    }

    @Test
    fun what_is_heard_is_written_to_the_transcript_and_sent_to_the_capture_action_when_listening_ends() = VoicePage(talk()).use { page ->
        page.settle()
        page.tap()
        page.onScreen { page.engine!!.listener.onListening() }
        assertEquals(true, page.bound("mic.listening"))
        assertEquals(true, page.voice().getValue("listening").jsonPrimitive.content.toBoolean())
        page.onScreen {
            page.engine!!.listener.onHeard("lost me", 0.91)
            page.engine.listener.onEnded(1400L)
        }
        assertEquals(false, page.bound("mic.listening"))
        assertEquals("lost me", page.bound("mic.said"))
        assertEquals(1, page.heard.size)
        assertEquals(listOf<Any?>("b1", "lost me", 0.91, 1400.0, false), listOf("book", "text", "confidence", "ms", "uploaded").map { page.heard.single()[it] })
    }

    @Test
    fun listening_that_ends_with_nothing_heard_sends_nothing() = VoicePage(talk()).use { page ->
        page.settle()
        page.tap()
        page.onScreen { page.engine!!.listener.onListening() }
        page.onScreen { page.engine!!.listener.onEnded(300L) }
        assertEquals(emptyList(), page.heard)
    }

    @Test
    fun a_node_that_captures_audio_sends_how_much_went_up_and_no_words() = VoicePage(talk(""","capture":"audio","upload_url":"/v1/voice"""")).use { page ->
        page.settle()
        page.tap()
        page.onScreen { page.engine!!.listener.onListening() }
        page.onScreen {
            page.engine!!.listener.onUploaded(20480L)
            page.engine.listener.onEnded(900L)
        }
        assertEquals(listOf<Any?>(null, true, 20480.0), listOf("text", "uploaded", "bytes").map { page.heard.single()[it] })
    }

    @Test
    fun a_failure_is_read_back_with_its_reason_and_clears_listening() = VoicePage(talk()).use { page ->
        page.settle()
        page.tap()
        page.onScreen { page.engine!!.listener.onListening() }
        page.onScreen { page.engine!!.listener.onFailed("the microphone needs HTTPS") }
        assertEquals("failed", page.voice().getValue("status").jsonPrimitive.content)
        assertEquals(false, page.bound("mic.listening"))
        assertTrue(page.node().getValue("notices").jsonArray.any { it.jsonPrimitive.content.contains("the microphone needs HTTPS") })
    }

    @Test
    fun a_target_with_no_voice_engine_says_so_and_reads_back_unsupported() = VoicePage(talk(), engine = null).use { page ->
        page.settle()
        assertEquals("unsupported", page.voice().getValue("status").jsonPrimitive.content)
        assertTrue(page.node().getValue("notices").jsonArray.any { it.jsonPrimitive.content == "voice is not available on this target." })
    }
}
