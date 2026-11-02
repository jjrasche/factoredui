package ai.factoredui.compose.media

import ai.factoredui.compose.schema.ActionRef
import ai.factoredui.compose.schema.BindingResolver
import ai.factoredui.compose.schema.SpecValue
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val json = Json { ignoreUnknownKeys = true }

private fun readFrom(props: String): AudioProps {
    val node = json.decodeFromString(ai.factoredui.compose.schema.SpecNode.serializer(), """{"id":"narration","type":"audio","props":$props}""")
    return readAudioProps(BindingResolver.resolveProps(node.props, emptyMap()), node.props)
}

class AudioPropsTest {
    @Test
    fun the_source_and_what_the_lock_screen_shows_are_read() {
        val props = readFrom("""{"source":"/media/ch1.mp3","title":"Chapter 1","artist":"A. Author","album":"The Book","artwork":"/media/cover.jpg"}""")
        assertEquals(listOf("/media/ch1.mp3", "Chapter 1", "A. Author", "The Book", "/media/cover.jpg"), listOf(props.source, props.title, props.artist, props.album, props.artwork))
        assertEquals(emptyList(), props.notices)
    }

    @Test
    fun the_title_is_shown_on_the_page_unless_the_spec_says_not_to() {
        assertTrue(readFrom("""{"source":"a.mp3","title":"Chapter 1"}""").showsTitle)
        assertEquals(false, readFrom("""{"source":"a.mp3","title":"Chapter 1","show_title":false}""").showsTitle)
    }

    @Test
    fun a_node_with_no_source_says_so_in_its_notices() {
        assertEquals(listOf("audio has no source; there is nothing to play."), readFrom("{}").notices)
    }

    @Test
    fun a_seek_step_of_15_seconds_a_report_every_5_and_built_in_controls_are_the_defaults() {
        val props = readFrom("""{"source":"a.mp3"}""")
        assertEquals(15.0, props.seekStepSeconds)
        assertEquals(5.0, props.reportEverySeconds)
        assertTrue(props.hasControls)
    }

    @Test
    fun a_step_or_interval_that_is_not_above_zero_keeps_its_default_and_is_named() {
        val props = readFrom("""{"source":"a.mp3","seek_step":0,"report_every":"soon"}""")
        assertEquals(15.0, props.seekStepSeconds)
        assertEquals(5.0, props.reportEverySeconds)
        assertEquals(
            listOf("audio seek_step must be a number above zero, not '0.0'; 15 seconds is used.", "audio report_every must be a number above zero, not 'soon'; 5 seconds is used."),
            props.notices,
        )
    }

    @Test
    fun markers_are_kept_in_order_of_their_time_and_a_bad_one_is_named_and_left_out() {
        val props = readFrom("""{"source":"a.mp3","markers":[{"at":600,"label":"Two"},{"at":0,"label":"One"},{"label":"No time"},{"at":-5,"label":"Before"}]}""")
        assertEquals(listOf(AudioMarker(0.0, "One"), AudioMarker(600.0, "Two")), props.markers)
        assertEquals(
            listOf("audio markers[2] needs an 'at' number of seconds from the start; it is left out.", "audio markers[3] needs an 'at' number of seconds from the start; it is left out."),
            props.notices,
        )
    }

    @Test
    fun lock_screen_actions_are_read_as_actions_and_an_unknown_one_is_named() {
        val props = readFrom(
            """{"source":"a.mp3","media_actions":{"previoustrack":{"action":"react","params":{"kind":"lost_me"}},"dance":{"action":"x"},"nexttrack":"react"}}""",
        )
        assertEquals(mapOf(MediaAction.PREVIOUS_TRACK to ActionRef("react", mapOf("kind" to SpecValue.StringValue("lost_me")))), props.mediaActions)
        assertEquals(
            listOf(
                "audio media_actions 'dance' is not a lock-screen action; it reads play, pause, previoustrack, nexttrack, seekbackward, seekforward, seekto, stop.",
                "audio media_actions 'nexttrack' must be an object with an 'action'; it is left out.",
            ),
            props.notices,
        )
    }

    @Test
    fun the_action_that_hears_the_position_is_read() {
        assertEquals(ActionRef("heard", mapOf("book" to SpecValue.StringValue("b1"))), readFrom("""{"source":"a.mp3","on_position":{"action":"heard","params":{"book":"b1"}}}""").onPosition)
    }
}
