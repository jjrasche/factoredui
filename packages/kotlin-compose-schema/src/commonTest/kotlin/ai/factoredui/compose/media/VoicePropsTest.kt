package ai.factoredui.compose.media

import ai.factoredui.compose.schema.ActionRef
import ai.factoredui.compose.schema.BindingResolver
import ai.factoredui.compose.schema.SpecNode
import ai.factoredui.compose.schema.SpecValue
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

private val json = Json { ignoreUnknownKeys = true }

private fun readFrom(props: String): VoiceProps {
    val node = json.decodeFromString(SpecNode.serializer(), """{"id":"talk","type":"voice","props":$props}""")
    return readVoiceProps(BindingResolver.resolveProps(node.props, emptyMap()), node.props)
}

private const val HEARD = """"on_capture":{"action":"heard","params":{"book":"b1"}}"""

class VoicePropsTest {
    @Test
    fun hold_to_talk_with_speech_recognition_is_the_default_and_the_capture_action_is_read() {
        val props = readFrom("{$HEARD}")
        assertEquals(VoiceMode.HOLD, props.mode)
        assertEquals(VoiceCapture.TEXT, props.capture)
        assertEquals("Hold to talk", props.label)
        assertEquals(ActionRef("heard", mapOf("book" to SpecValue.StringValue("b1"))), props.onCapture)
        assertEquals(emptyList(), props.notices)
    }

    @Test
    fun toggle_mode_says_tap_to_talk_unless_the_spec_gives_a_label() {
        assertEquals("Tap to talk", readFrom("""{"mode":"toggle",$HEARD}""").label)
        assertEquals("Say it", readFrom("""{"mode":"toggle","label":"Say it",$HEARD}""").label)
    }

    @Test
    fun a_mode_or_capture_that_is_not_known_keeps_its_default_and_is_named() {
        val props = readFrom("""{"mode":"always","capture":"video",$HEARD}""")
        assertEquals(VoiceMode.HOLD, props.mode)
        assertEquals(VoiceCapture.TEXT, props.capture)
        assertEquals(listOf("voice mode must be hold or toggle, not 'always'; hold is used.", "voice capture must be text or audio, not 'video'; text is used."), props.notices)
    }

    @Test
    fun capturing_audio_needs_somewhere_to_send_it() {
        assertEquals(listOf("voice capture 'audio' needs an upload_url; nothing can be sent."), readFrom("""{"capture":"audio",$HEARD}""").notices)
        val sent = readFrom("""{"capture":"audio","upload_url":"/v1/voice",$HEARD}""")
        assertEquals("/v1/voice", sent.uploadUrl)
        assertEquals(emptyList(), sent.notices)
    }

    @Test
    fun a_voice_node_that_names_nothing_to_tell_says_so() {
        assertEquals(listOf("voice has no on_capture; nothing is sent when it hears something."), readFrom("{}").notices)
    }

    @Test
    fun a_capture_carries_what_was_heard_how_sure_how_long_and_whether_audio_went_up() {
        assertEquals(
            mapOf(
                "text" to SpecValue.StringValue("lost me"), "confidence" to SpecValue.NumberValue(0.91), "ms" to SpecValue.NumberValue(1400.0),
                "uploaded" to SpecValue.BooleanValue(false), "bytes" to SpecValue.NullValue,
            ),
            captureEvent("lost me", 0.91, 1400L, null),
        )
        assertEquals(
            mapOf(
                "text" to SpecValue.NullValue, "confidence" to SpecValue.NullValue, "ms" to SpecValue.NumberValue(900.0),
                "uploaded" to SpecValue.BooleanValue(true), "bytes" to SpecValue.NumberValue(20480.0),
            ),
            captureEvent(null, null, 900L, 20480L),
        )
    }
}
