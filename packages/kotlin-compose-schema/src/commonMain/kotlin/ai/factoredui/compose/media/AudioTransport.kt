package ai.factoredui.compose.media

import ai.factoredui.compose.schema.ActionRef
import ai.factoredui.compose.schema.SpecValue
import kotlin.math.abs

data class AudioState(val position: Double, val duration: Double?, val isPlaying: Boolean, val rate: Double)

sealed interface AudioEffect {
    data class SetPlaying(val isPlaying: Boolean) : AudioEffect
    data class SeekTo(val seconds: Double) : AudioEffect
    data class Dispatch(val ref: ActionRef) : AudioEffect
}

private const val RESTART_WITHIN_SECONDS = 3.0
private const val NEXT_AFTER_SECONDS = 0.5

private fun seekBy(state: AudioState, seconds: Double): AudioEffect.SeekTo =
    AudioEffect.SeekTo((state.position + seconds).coerceIn(0.0, state.duration ?: Double.POSITIVE_INFINITY))

private fun markerBefore(position: Double, markers: List<AudioMarker>): Double {
    val heard = markers.lastOrNull { it.at <= position } ?: return 0.0
    if (position - heard.at > RESTART_WITHIN_SECONDS) return heard.at
    return markers.lastOrNull { it.at < heard.at }?.at ?: 0.0
}

private fun markerAfter(position: Double, markers: List<AudioMarker>): Double? = markers.firstOrNull { it.at > position + NEXT_AFTER_SECONDS }?.at

private fun goPrevious(state: AudioState, props: AudioProps): AudioEffect.SeekTo =
    if (props.markers.isEmpty()) seekBy(state, -props.seekStepSeconds) else AudioEffect.SeekTo(markerBefore(state.position, props.markers))

private fun goNext(state: AudioState, props: AudioProps): AudioEffect.SeekTo =
    markerAfter(state.position, props.markers)?.let { AudioEffect.SeekTo(it) } ?: seekBy(state, props.seekStepSeconds)

fun planMediaAction(action: MediaAction, seekTime: Double?, state: AudioState, props: AudioProps): List<AudioEffect> {
    props.mediaActions[action]?.let { return listOf(AudioEffect.Dispatch(it)) }
    return when (action) {
        MediaAction.PLAY -> listOf(AudioEffect.SetPlaying(true))
        MediaAction.PAUSE, MediaAction.STOP -> listOf(AudioEffect.SetPlaying(false))
        MediaAction.SEEK_BACKWARD -> listOf(seekBy(state, -props.seekStepSeconds))
        MediaAction.SEEK_FORWARD -> listOf(seekBy(state, props.seekStepSeconds))
        MediaAction.SEEK_TO -> listOfNotNull(seekTime?.let { AudioEffect.SeekTo(it) })
        MediaAction.PREVIOUS_TRACK -> listOf(goPrevious(state, props))
        MediaAction.NEXT_TRACK -> listOf(goNext(state, props))
    }
}

fun positionEvent(reason: String, state: AudioState): Map<String, SpecValue> = mapOf(
    "position" to SpecValue.NumberValue(state.position),
    "rate" to SpecValue.NumberValue(state.rate),
    "playing" to SpecValue.BooleanValue(state.isPlaying),
    "duration" to (state.duration?.let { SpecValue.NumberValue(it) } ?: SpecValue.NullValue),
    "reason" to SpecValue.StringValue(reason),
)

class ReportClock(private val everySeconds: Double) {
    private var lastReported: Double? = null

    fun isDue(position: Double): Boolean = lastReported?.let { abs(position - it) >= everySeconds } ?: true

    fun markReported(position: Double) {
        lastReported = position
    }
}
