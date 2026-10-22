package ai.factoredui.compose.media

import ai.factoredui.compose.schema.ActionRef
import ai.factoredui.compose.schema.SpecValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private val MARKERS = listOf(AudioMarker(0.0, "One"), AudioMarker(600.0, "Two"), AudioMarker(1200.0, "Three"))

private fun propsWith(markers: List<AudioMarker> = emptyList(), mediaActions: Map<MediaAction, ActionRef> = emptyMap()) = AudioProps(
    "a.mp3", null, null, null, null, markers, 15.0, 5.0, true, mediaActions, null, emptyList(),
)

private fun stateAt(position: Double, duration: Double? = 3600.0, isPlaying: Boolean = true, rate: Double = 1.0) = AudioState(position, duration, isPlaying, rate)

private fun plan(action: MediaAction, state: AudioState, props: AudioProps = propsWith(), seekTime: Double? = null) = planMediaAction(action, seekTime, state, props)

class AudioTransportTest {
    @Test
    fun play_pause_and_stop_set_whether_it_plays() {
        assertEquals(listOf(AudioEffect.SetPlaying(true)), plan(MediaAction.PLAY, stateAt(10.0, isPlaying = false)))
        assertEquals(listOf(AudioEffect.SetPlaying(false)), plan(MediaAction.PAUSE, stateAt(10.0)))
        assertEquals(listOf(AudioEffect.SetPlaying(false)), plan(MediaAction.STOP, stateAt(10.0)))
    }

    @Test
    fun seeking_backward_and_forward_moves_by_the_step_and_stops_at_the_ends() {
        assertEquals(listOf(AudioEffect.SeekTo(85.0)), plan(MediaAction.SEEK_BACKWARD, stateAt(100.0)))
        assertEquals(listOf(AudioEffect.SeekTo(0.0)), plan(MediaAction.SEEK_BACKWARD, stateAt(5.0)))
        assertEquals(listOf(AudioEffect.SeekTo(115.0)), plan(MediaAction.SEEK_FORWARD, stateAt(100.0)))
        assertEquals(listOf(AudioEffect.SeekTo(3600.0)), plan(MediaAction.SEEK_FORWARD, stateAt(3595.0)))
    }

    @Test
    fun seeking_forward_with_no_known_length_is_not_capped() {
        assertEquals(listOf(AudioEffect.SeekTo(3715.0)), plan(MediaAction.SEEK_FORWARD, stateAt(3700.0, duration = null)))
    }

    @Test
    fun seeking_to_a_time_goes_there_and_with_no_time_does_nothing() {
        assertEquals(listOf(AudioEffect.SeekTo(742.5)), plan(MediaAction.SEEK_TO, stateAt(10.0), seekTime = 742.5))
        assertEquals(emptyList(), plan(MediaAction.SEEK_TO, stateAt(10.0)))
    }

    @Test
    fun previous_goes_to_the_start_of_the_passage_being_heard_unless_just_begun_and_then_to_the_one_before() {
        val props = propsWith(MARKERS)
        assertEquals(listOf(AudioEffect.SeekTo(600.0)), plan(MediaAction.PREVIOUS_TRACK, stateAt(700.0), props))
        assertEquals(listOf(AudioEffect.SeekTo(0.0)), plan(MediaAction.PREVIOUS_TRACK, stateAt(601.0), props))
        assertEquals(listOf(AudioEffect.SeekTo(0.0)), plan(MediaAction.PREVIOUS_TRACK, stateAt(2.0), props))
    }

    @Test
    fun next_goes_to_the_next_passage_and_with_none_left_seeks_forward_by_the_step() {
        val props = propsWith(MARKERS)
        assertEquals(listOf(AudioEffect.SeekTo(600.0)), plan(MediaAction.NEXT_TRACK, stateAt(10.0), props))
        assertEquals(listOf(AudioEffect.SeekTo(1215.0)), plan(MediaAction.NEXT_TRACK, stateAt(1200.0), props))
    }

    @Test
    fun with_no_markers_previous_and_next_seek_by_the_step() {
        assertEquals(listOf(AudioEffect.SeekTo(85.0)), plan(MediaAction.PREVIOUS_TRACK, stateAt(100.0)))
        assertEquals(listOf(AudioEffect.SeekTo(115.0)), plan(MediaAction.NEXT_TRACK, stateAt(100.0)))
    }

    @Test
    fun an_action_the_spec_names_for_a_lock_screen_button_is_dispatched_and_the_default_is_not_done() {
        val lostMe = ActionRef("react", mapOf("kind" to SpecValue.StringValue("lost_me")))
        val props = propsWith(MARKERS, mapOf(MediaAction.PREVIOUS_TRACK to lostMe))
        assertEquals(listOf(AudioEffect.Dispatch(lostMe)), plan(MediaAction.PREVIOUS_TRACK, stateAt(700.0), props))
        assertEquals(listOf(AudioEffect.SeekTo(1215.0)), plan(MediaAction.NEXT_TRACK, stateAt(1200.0), props))
    }

    @Test
    fun a_report_carries_where_it_is_how_fast_whether_it_plays_how_long_it_is_and_why() {
        assertEquals(
            mapOf(
                "position" to SpecValue.NumberValue(742.5), "rate" to SpecValue.NumberValue(1.25), "playing" to SpecValue.BooleanValue(true),
                "duration" to SpecValue.NumberValue(3600.0), "reason" to SpecValue.StringValue("seek"),
            ),
            positionEvent("seek", stateAt(742.5, rate = 1.25)),
        )
        assertEquals(SpecValue.NullValue, positionEvent("tick", stateAt(1.0, duration = null))["duration"])
    }

    @Test
    fun a_report_is_due_after_the_interval_of_narration_in_either_direction() {
        val clock = ReportClock(5.0)
        assertTrue(clock.isDue(100.0), "nothing has been reported yet")
        clock.markReported(100.0)
        assertFalse(clock.isDue(104.9))
        assertTrue(clock.isDue(105.0))
        assertTrue(clock.isDue(94.0), "a seek back is a change too")
    }
}
