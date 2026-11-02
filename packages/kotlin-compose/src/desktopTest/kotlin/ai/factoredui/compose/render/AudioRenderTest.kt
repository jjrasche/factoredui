package ai.factoredui.compose.render

import ai.factoredui.compose.adapter.ActionRegistry
import ai.factoredui.compose.media.MediaAction
import ai.factoredui.compose.media.MediaHost
import ai.factoredui.compose.renderer.RenderContext
import ai.factoredui.compose.schema.BindingResolver
import ai.factoredui.compose.schema.Spec
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val json = Json { ignoreUnknownKeys = true }

private const val NARRATION = """
    {"id":"narration","type":"audio","props":{
      "source":"/media/ch1.mp3","title":"Chapter 1","artist":"A. Author",
      "position":"{book.position}","playing":"{book.playing}","rate":"{book.rate}","duration":"{book.duration}",
      "markers":[{"at":0,"label":"One"},{"at":600,"label":"Two"},{"at":1200,"label":"Three"}],
      "on_position":{"action":"heard","params":{"book":"b1"}}}}
"""

private class Listening {
    val heard = mutableListOf<Map<String, Any?>>()
    val reacted = mutableListOf<Map<String, Any?>>()
    val actions: ActionRegistry = mapOf("heard" to { params -> heard += params }, "react" to { params -> reacted += params })
}

private class AudioPage(node: String = NARRATION, data: Map<String, Any?> = emptyMap(), engine: FakeAudioEngine? = FakeAudioEngine()) : AutoCloseable {
    val engine: FakeAudioEngine? = engine
    val listening = Listening()
    val context = RenderContext(actions = listening.actions, initialData = data, mediaHost = if (engine == null) MediaHost.NONE else MediaHost({ engine }, null))
    private val surface = SceneSurface(json.decodeFromString(Spec.serializer(), """{"spec_version":1,"renderer_min":1,"root":$node}"""), context, 500, 400, ticking = false)

    fun settle() = surface.onScreenThread { surface.onScreenThread { } }

    fun <T> onScreen(work: () -> T): T = surface.onScreenThread { work() }.also { settle() }

    fun node(): JsonObject = surface.onScreenThread { nodeById(screenJson(screenNodes(context.screen)!!), "narration")!! }

    fun audio(): JsonObject = node().getValue("audio").jsonObject

    fun bound(path: String): Any? = BindingResolver.resolveBinding("{$path}", context.data)

    override fun close() = surface.close()
}

class AudioRenderTest {
    @Test
    fun it_opens_its_source_and_describes_itself_for_the_lock_screen() = AudioPage().use { page ->
        page.settle()
        val engine = checkNotNull(page.engine)
        assertEquals(listOf("/media/ch1.mp3"), engine.opened)
        val description = checkNotNull(engine.description)
        assertEquals(listOf("Chapter 1", "A. Author"), listOf(description.title, description.artist))
        assertEquals(MediaAction.entries.toSet(), description.actions)
    }

    @Test
    fun it_reads_back_loading_until_the_engine_knows_the_length_and_then_the_length() = AudioPage().use { page ->
        page.settle()
        assertEquals("loading", page.audio().getValue("status").jsonPrimitive.content)
        page.onScreen { page.engine!!.listener.onReady(3600.0) }
        assertEquals("ready", page.audio().getValue("status").jsonPrimitive.content)
        assertEquals(3600.0, page.audio().getValue("duration").jsonPrimitive.double)
        assertEquals(3600.0, page.bound("book.duration"))
        assertEquals(listOf("One", "Two", "Three"), page.audio().getValue("markers").jsonArray.map { it.jsonObject.getValue("label").jsonPrimitive.content })
    }

    @Test
    fun time_updates_write_the_position_binding_at_most_twice_a_second() = AudioPage().use { page ->
        page.settle()
        val seen = mutableListOf<Any?>()
        page.onScreen { listOf(1.0, 1.2, 1.6).forEach { page.engine!!.listener.onTime(it); seen += page.bound("book.position") } }
        assertEquals(listOf<Any?>(1.0, 1.0, 1.6), seen)
    }

    @Test
    fun a_write_to_the_position_binding_that_is_not_its_own_seeks_the_engine() = AudioPage().use { page ->
        page.settle()
        page.onScreen { page.engine!!.listener.onTime(10.0) }
        page.onScreen { page.context.setBinding("book.position", 300.0) }
        assertEquals(listOf(300.0), page.engine!!.seeks)
    }

    @Test
    fun its_own_position_writes_do_not_seek() = AudioPage().use { page ->
        page.settle()
        page.onScreen { listOf(1.0, 2.0, 3.0).forEach { page.engine!!.listener.onTime(it) } }
        assertEquals(emptyList(), page.engine!!.seeks)
    }

    @Test
    fun a_position_the_page_already_holds_is_where_it_resumes_once_the_length_is_known() = AudioPage(data = mapOf("book" to mapOf("position" to 300.0))).use { page ->
        page.settle()
        page.onScreen { page.engine!!.listener.onReady(3600.0) }
        assertEquals(listOf(300.0), page.engine!!.seeks)
    }

    @Test
    fun the_playing_binding_plays_and_pauses_the_engine() = AudioPage().use { page ->
        page.settle()
        page.onScreen { page.context.setBinding("book.playing", true) }
        page.onScreen { page.context.setBinding("book.playing", false) }
        assertEquals(listOf(1, 1), listOf(page.engine!!.plays, page.engine.pauses))
    }

    @Test
    fun the_engine_starting_or_stopping_on_its_own_writes_the_playing_binding() = AudioPage().use { page ->
        page.settle()
        page.onScreen { page.engine!!.listener.onPlaying(true) }
        assertEquals(true, page.bound("book.playing"))
        page.onScreen { page.engine!!.listener.onEnded() }
        assertEquals(false, page.bound("book.playing"))
        assertEquals(0, page.engine!!.plays + page.engine.pauses, "the engine's own events are not sent back to it as play or pause")
    }

    @Test
    fun the_rate_binding_sets_the_engines_rate() = AudioPage().use { page ->
        page.settle()
        page.onScreen { page.context.setBinding("book.rate", 1.5) }
        assertEquals(listOf(1.5), page.engine!!.rates)
        assertEquals(1.5, page.audio().getValue("rate").jsonPrimitive.double)
    }

    @Test
    fun the_server_is_told_on_play_pause_seek_and_after_each_interval_of_narration() = AudioPage().use { page ->
        page.settle()
        page.onScreen {
            page.engine!!.listener.onPlaying(true)
            page.engine.listener.onTime(2.0)
            page.engine.listener.onTime(6.0)
            page.engine.listener.onPlaying(false)
        }
        page.settle()
        assertEquals(listOf("play", "tick", "pause"), page.listening.heard.map { it["reason"] })
        val tick = page.listening.heard[1]
        assertEquals(listOf<Any?>("b1", 6.0, 1.0, true), listOf(tick["book"], tick["position"], tick["rate"], tick["playing"]))
    }

    @Test
    fun a_lock_screen_previous_goes_to_the_start_of_the_passage_being_heard() = AudioPage().use { page ->
        page.settle()
        page.onScreen { page.engine!!.listener.onTime(700.0) }
        page.onScreen { page.engine!!.listener.onMediaAction(MediaAction.PREVIOUS_TRACK, null) }
        assertEquals(listOf(600.0), page.engine!!.seeks)
        assertEquals(600.0, page.bound("book.position"))
    }

    @Test
    fun a_lock_screen_action_the_spec_maps_is_sent_with_the_position_and_does_not_seek() {
        val mapped = NARRATION.replace(""""on_position"""", """"media_actions":{"previoustrack":{"action":"react","params":{"kind":"lost_me","position":"{book.position}"}}},"on_position"""")
        AudioPage(mapped).use { page ->
            page.settle()
            page.onScreen { page.engine!!.listener.onTime(701.5) }
            page.onScreen { page.engine!!.listener.onMediaAction(MediaAction.PREVIOUS_TRACK, null) }
            assertEquals(listOf<Map<String, Any?>>(mapOf("kind" to "lost_me", "position" to 701.5)), page.listening.reacted)
            assertEquals(emptyList(), page.engine!!.seeks)
        }
    }

    @Test
    fun a_browser_that_will_not_start_sound_reads_back_blocked_and_clears_the_playing_binding() = AudioPage().use { page ->
        page.settle()
        page.onScreen { page.context.setBinding("book.playing", true) }
        page.onScreen { page.engine!!.listener.onBlocked() }
        assertEquals("blocked", page.audio().getValue("status").jsonPrimitive.content)
        assertEquals(false, page.bound("book.playing"))
        assertTrue(page.node().getValue("notices").jsonArray.any { it.jsonPrimitive.content.contains("tap play") })
    }

    @Test
    fun the_length_arriving_after_a_refused_start_does_not_clear_blocked() = AudioPage().use { page ->
        page.settle()
        page.onScreen { page.engine!!.listener.onBlocked() }
        page.onScreen { page.engine!!.listener.onReady(120.0) }
        assertEquals("blocked", page.audio().getValue("status").jsonPrimitive.content)
        assertEquals(120.0, page.audio().getValue("duration").jsonPrimitive.double)
    }

    @Test
    fun a_source_that_changes_is_opened_again() = AudioPage(NARRATION.replace(""""/media/ch1.mp3"""", """"{book.source}""""), mapOf("book" to mapOf("source" to "/media/ch1.mp3"))).use { page ->
        page.settle()
        page.onScreen { page.context.setBinding("book.source", "/media/ch2.mp3") }
        assertEquals(listOf("/media/ch1.mp3", "/media/ch2.mp3"), page.engine!!.opened)
    }

    @Test
    fun a_target_with_no_audio_engine_says_so_and_reads_back_unsupported() = AudioPage(engine = null).use { page ->
        page.settle()
        assertEquals("unsupported", page.audio().getValue("status").jsonPrimitive.content)
        assertTrue(page.node().getValue("notices").jsonArray.any { it.jsonPrimitive.content == "audio is not available on this target." })
    }

    @Test
    fun a_node_with_no_source_names_it_in_its_notices_and_opens_nothing() = AudioPage("""{"id":"narration","type":"audio","props":{}}""").use { page ->
        page.settle()
        assertEquals(emptyList(), page.engine!!.opened)
        assertTrue(page.node().getValue("notices").jsonArray.any { it.jsonPrimitive.content.contains("no source") })
    }

    @Test
    fun leaving_the_screen_closes_the_engine() {
        val page = AudioPage()
        page.settle()
        val engine = checkNotNull(page.engine)
        page.close()
        assertTrue(engine.isClosed)
    }
}
