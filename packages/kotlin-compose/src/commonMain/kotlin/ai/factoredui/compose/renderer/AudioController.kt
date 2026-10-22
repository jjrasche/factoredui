package ai.factoredui.compose.renderer

import ai.factoredui.compose.media.AudioDescription
import ai.factoredui.compose.media.AudioEffect
import ai.factoredui.compose.media.AudioEngine
import ai.factoredui.compose.media.AudioListener
import ai.factoredui.compose.media.AudioProps
import ai.factoredui.compose.media.AudioState
import ai.factoredui.compose.media.MediaAction
import ai.factoredui.compose.media.ReportClock
import ai.factoredui.compose.media.planMediaAction
import ai.factoredui.compose.media.positionEvent
import ai.factoredui.compose.schema.ActionRef
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

internal data class AudioPaths(val position: String?, val playing: String?, val rate: String?, val duration: String?)

internal data class AudioWanted(val position: Double?, val isPlaying: Boolean?, val rate: Double?)

internal data class AudioSnapshot(val status: String, val position: Double, val duration: Double?, val isPlaying: Boolean, val rate: Double)

private const val POSITION_WRITE_EVERY_SECONDS = 0.5
private const val SEEK_TOLERANCE_SECONDS = 1e-6

internal class AudioController(
    private val engine: AudioEngine?,
    private val context: RenderContext,
    private val nodeId: String,
    private val scope: CoroutineScope,
    private val paths: AudioPaths,
    private val publish: (AudioRecord, List<String>) -> Unit,
) : AudioListener {
    var snapshot by mutableStateOf(AudioSnapshot(if (engine == null) "unsupported" else "idle", 0.0, null, false, 1.0))
        private set

    private var props: AudioProps? = null
    private var openedSource: String? = null
    private var described: AudioDescription? = null
    private var lastWrittenPosition: Double? = null
    private var appliedPlaying: Boolean? = null
    private var appliedRate = 1.0
    private var pendingSeek: Double? = null
    private var clock = ReportClock(1.0)
    private var failures: List<String> = emptyList()

    val canPlay: Boolean get() = engine != null

    fun sync(props: AudioProps, wanted: AudioWanted) {
        this.props = props
        if (engine == null) return announce(listOf("audio is not available on this target."))
        openSource(props, wanted)
        describe(props)
        applyPlaying(wanted.isPlaying)
        applyRate(wanted.rate)
        applyPosition(wanted.position)
        announce()
    }

    fun togglePlay() = setPlaying(!snapshot.isPlaying)

    fun skip(seconds: Double) = seekTo(clamped(snapshot.position + seconds))

    fun seekToSeconds(seconds: Double) = seekTo(clamped(seconds))

    fun setRateTo(rate: Double) {
        appliedRate = rate
        engine?.setRate(rate)
        write(paths.rate, rate)
        snapshot = snapshot.copy(rate = rate)
        report("rate")
        announce()
    }

    fun close() {
        engine?.close()
    }

    override fun onReady(durationSeconds: Double) {
        snapshot = snapshot.copy(status = if (snapshot.status == "loading") "ready" else snapshot.status, duration = durationSeconds)
        write(paths.duration, durationSeconds)
        pendingSeek?.takeIf { it > 0.0 }?.let { seekTo(clamped(it)) }
        pendingSeek = null
        announce()
    }

    override fun onTime(positionSeconds: Double) {
        snapshot = snapshot.copy(position = positionSeconds)
        val written = lastWrittenPosition
        if (written == null || abs(positionSeconds - written) >= POSITION_WRITE_EVERY_SECONDS) {
            lastWrittenPosition = positionSeconds
            write(paths.position, positionSeconds)
        }
        if (snapshot.isPlaying && clock.isDue(positionSeconds)) report("tick")
        announce()
    }

    override fun onPlaying(isPlaying: Boolean) {
        appliedPlaying = isPlaying
        snapshot = snapshot.copy(status = "ready", isPlaying = isPlaying)
        write(paths.playing, isPlaying)
        report(if (isPlaying) "play" else "pause")
        announce()
    }

    override fun onEnded() {
        appliedPlaying = false
        snapshot = snapshot.copy(isPlaying = false)
        write(paths.playing, false)
        report("ended")
        announce()
    }

    override fun onFailed(message: String) {
        failures = listOf("audio failed: $message")
        stopped("failed")
    }

    override fun onBlocked() {
        failures = listOf("audio was not started: the browser plays sound only after a tap; tap play.")
        stopped("blocked")
    }

    override fun onMediaAction(action: MediaAction, seekTimeSeconds: Double?) {
        val current = props ?: return
        planMediaAction(action, seekTimeSeconds, stateNow(), current).forEach(::applyEffect)
    }

    private fun stopped(status: String) {
        appliedPlaying = false
        snapshot = snapshot.copy(status = status, isPlaying = false)
        write(paths.playing, false)
        announce()
    }

    private fun stateNow() = AudioState(snapshot.position, snapshot.duration, snapshot.isPlaying, snapshot.rate)

    private fun clamped(seconds: Double) = seconds.coerceIn(0.0, snapshot.duration ?: Double.POSITIVE_INFINITY)

    private fun openSource(props: AudioProps, wanted: AudioWanted) {
        if (props.source == openedSource) return
        openedSource = props.source
        clock = ReportClock(props.reportEverySeconds)
        lastWrittenPosition = null
        appliedPlaying = null
        pendingSeek = wanted.position
        if (props.source.isEmpty()) {
            snapshot = snapshot.copy(status = "idle")
            return
        }
        snapshot = snapshot.copy(status = "loading", position = wanted.position ?: 0.0, duration = null, isPlaying = false)
        engine?.open(props.source, this)
    }

    private fun describe(props: AudioProps) {
        val now = AudioDescription(props.title, props.artist, props.album, props.artwork, MediaAction.entries.toSet())
        if (now == described) return
        described = now
        engine?.describe(now)
    }

    private fun applyPlaying(wanted: Boolean?) {
        if (wanted == null || wanted == appliedPlaying) return
        appliedPlaying = wanted
        if (wanted) engine?.play() else engine?.pause()
    }

    private fun applyRate(wanted: Double?) {
        if (wanted == null || wanted <= 0.0 || wanted == appliedRate) return
        appliedRate = wanted
        engine?.setRate(wanted)
        snapshot = snapshot.copy(rate = wanted)
        report("rate")
    }

    private fun applyPosition(wanted: Double?) {
        val written = lastWrittenPosition
        if (wanted == null || written == null || abs(wanted - written) <= SEEK_TOLERANCE_SECONDS) return
        seekTo(clamped(wanted))
    }

    private fun setPlaying(isPlaying: Boolean) {
        appliedPlaying = isPlaying
        if (isPlaying) engine?.play() else engine?.pause()
        write(paths.playing, isPlaying)
    }

    private fun seekTo(seconds: Double) {
        engine?.seekTo(seconds)
        lastWrittenPosition = seconds
        snapshot = snapshot.copy(position = seconds)
        write(paths.position, seconds)
        report("seek")
        announce()
    }

    private fun applyEffect(effect: AudioEffect) {
        when (effect) {
            is AudioEffect.SetPlaying -> setPlaying(effect.isPlaying)
            is AudioEffect.SeekTo -> seekTo(clamped(effect.seconds))
            is AudioEffect.Dispatch -> scope.launch { context.dispatch(nodeId, effect.ref) }
        }
    }

    private fun report(reason: String) {
        val heard = props?.onPosition ?: return
        clock.markReported(snapshot.position)
        val event = ActionRef(heard.action, heard.params + positionEvent(reason, stateNow()))
        scope.launch { context.dispatch(nodeId, event) }
    }

    private fun write(path: String?, value: Any?) {
        if (path != null) context.setBinding(path, value)
    }

    private fun announce(notices: List<String> = emptyList()) {
        val shown = props
        publish(
            AudioRecord(
                source = shown?.source.orEmpty(), status = snapshot.status, position = snapshot.position, duration = snapshot.duration, isPlaying = snapshot.isPlaying, rate = snapshot.rate,
                markers = shown?.markers.orEmpty().map { AudioMarkerRecord(it.at, it.label) }, mediaActions = shown?.mediaActions?.keys.orEmpty().map { it.wire },
            ),
            (shown?.notices.orEmpty()) + failures + notices,
        )
    }
}
