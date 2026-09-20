package ai.factoredui.compose.schema

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private val json = Json { ignoreUnknownKeys = true }

class VideoPropsTest {

    private fun videoNode(props: String) = json.decodeFromString(
        SpecNode.serializer(),
        """{"id":"clip","type":"video","props":$props}""",
    )

    @Test
    fun the_wire_name_video_reaches_the_video_type() {
        assertEquals(SpecNodeType.VIDEO, videoNode("""{"source":"/tmp/a.mp4"}""").type)
    }

    @Test
    fun a_local_path_is_carried_through_untouched() {
        val props = videoNode("""{"source":"/storage/emulated/0/Download/talk.mp4"}""").props.asVideoProps()
        assertEquals("/storage/emulated/0/Download/talk.mp4", props.source)
    }

    @Test
    fun playback_flags_default_to_not_playing_and_not_looping() {
        val props = videoNode("""{"source":"/tmp/a.mp4"}""").props.asVideoProps()
        assertFalse(props.autoplay, "a video must not start itself unless the spec says so")
        assertFalse(props.loop)
    }

    @Test
    fun playback_flags_are_read_when_the_spec_sets_them() {
        val props = videoNode("""{"source":"/tmp/a.mp4","autoplay":true,"loop":true}""").props.asVideoProps()
        assertTrue(props.autoplay)
        assertTrue(props.loop)
    }

    @Test
    fun a_position_binding_is_reported_as_a_writable_path_like_the_slider() {
        val node = videoNode("""{"source":"/tmp/a.mp4","position":"{playback.positionMs}"}""")
        assertEquals("playback.positionMs", node.props["position"]?.bindingPath())
    }

    @Test
    fun a_literal_position_is_not_a_binding_path() {
        val node = videoNode("""{"source":"/tmp/a.mp4","position":1200}""")
        assertEquals(null, node.props["position"]?.bindingPath())
        assertEquals(1200.0, node.props.asVideoProps().position)
    }
}
