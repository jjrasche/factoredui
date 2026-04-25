package ai.factoredui.compose.forcegraph.replay

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Single record that the replay scrubber walks across — same shape whether
 * the source is a historical fetch or the live-tail buffer (events that
 * arrived while the user was scrubbed back). Stored here as flat fields so
 * the controller is decoupled from RenderForceGraph's private DTOs.
 */
data class ReplayEvent(
    val id: Long,
    val timestamp: String,
    val type: String,
    val kind: String?,
    val function: String?,
    val producer: String?,
    val consumers: List<String>,
    val label: String,
) {
    /**
     * Empty companion exists so RenderForceGraph can hang private factory
     * extensions (`fromLiveFrame`, `fromHistoryRow`) off the class without
     * leaking those source-private DTO shapes here.
     */
    companion object
}

/**
 * DVR-style cursor over a stream of [ReplayEvent]s, with two modes:
 *
 *  - **Live**: cursor tracks the latest event in the merged buffer; new
 *    events advance both. The renderer pipes SSE frames through both
 *    [appendLive] (always) and the live-mode "fire overlays now" path
 *    (still done in the SSE callback in RenderForceGraph itself).
 *  - **Replay**: cursor sits at a fixed index chosen by the user. Playback
 *    advances at a fixed rate (one event per [DEFAULT_STEP_INTERVAL_MS]).
 *    Live events still flow into the tail buffer so toggling back to live
 *    picks up where reality is, not where the user paused.
 *
 * Why a fixed step rate rather than wall-clock pacing against
 * `created_at`? Bursty signal traffic: a quiet stretch would stall replay
 * for tens of seconds; a burst would flash past faster than the 33ms
 * render frame. Fixed cadence gives the user predictable scrubbing —
 * "one event per tick, ten ticks per second" — which is the only sane
 * default for a debugging primitive. Wall-clock pacing can be added
 * later behind a prop without changing the controller's shape.
 *
 * Mutators ([loadHistory], [appendLive], [setLive], [setPlaying],
 * [seekTo], [advance]) are suspend + Mutex-guarded so the SSE thread on
 * JVM (Dispatchers.Default) and the render-thread playback ticker can
 * both poke at internal collections without races.
 *
 * The controller does NOT own a coroutine; the host composable runs the
 * tick loop and calls these methods so cancellation is tied to
 * composition lifecycle.
 */
class ReplayController(
    private val maxLiveTail: Int = DEFAULT_MAX_LIVE_TAIL,
) {
    private val mutex = Mutex()
    private val historyBuffer: ArrayList<ReplayEvent> = ArrayList()
    private val liveTailBuffer: ArrayList<ReplayEvent> = ArrayList()

    private val _events: MutableStateFlow<List<ReplayEvent>> = MutableStateFlow(emptyList())

    /** Merged history + live-tail buffer, ordered oldest → newest. */
    val events: StateFlow<List<ReplayEvent>> = _events.asStateFlow()

    private val _cursor: MutableStateFlow<Int> = MutableStateFlow(-1)

    /** Index into [events]. -1 means "no events yet"; otherwise 0..size-1. */
    val cursor: StateFlow<Int> = _cursor.asStateFlow()

    private val _isLive: MutableStateFlow<Boolean> = MutableStateFlow(true)
    val isLive: StateFlow<Boolean> = _isLive.asStateFlow()

    private val _isPlaying: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private var historyLoaded: Boolean = false

    /** True iff the host has fetched the history endpoint at least once. */
    fun hasHistory(): Boolean = historyLoaded

    /**
     * Replace the historical prefix with a freshly-fetched batch. Called
     * by the host on first load (or refresh). Live-tail entries are kept
     * in place — they are events that arrived after `since`, regardless
     * of whether they were also re-fetched.
     */
    suspend fun loadHistory(events: List<ReplayEvent>) {
        mutex.withLock {
            historyBuffer.clear()
            historyBuffer.addAll(events)
            historyLoaded = true
            rebuildMerged()
            // After first load, snap cursor to the end if we're in live mode.
            if (_isLive.value) snapCursorToEnd()
        }
    }

    /**
     * Append a live event to the tail. Called from the SSE callback for
     * every frame, regardless of mode — live mode renders it immediately
     * (overlays + log); replay mode sees it once the user toggles back.
     */
    suspend fun appendLive(event: ReplayEvent) {
        mutex.withLock {
            liveTailBuffer.add(event)
            // Bound the tail so a long replay session can't OOM.
            while (liveTailBuffer.size > maxLiveTail) liveTailBuffer.removeAt(0)
            rebuildMerged()
            if (_isLive.value) snapCursorToEnd()
        }
    }

    /** Slice of [events] visible at the current cursor (everything up to and including it). */
    fun visibleSlice(): List<ReplayEvent> {
        val end = _cursor.value
        if (end < 0) return emptyList()
        val all = _events.value
        if (end >= all.size) return all
        return all.subList(0, end + 1)
    }

    suspend fun setLive(live: Boolean) {
        mutex.withLock {
            _isLive.value = live
            if (live) {
                _isPlaying.value = false
                snapCursorToEnd()
            }
        }
    }

    suspend fun setPlaying(playing: Boolean) {
        mutex.withLock {
            if (playing && _isLive.value) {
                // Play in live mode is meaningless; flip to replay first.
                _isLive.value = false
            }
            _isPlaying.value = playing
        }
    }

    /**
     * User dragged the slider. Snap cursor and pause playback. If the
     * user dragged to the end while live mode is active, no-op (already
     * tracking).
     */
    suspend fun seekTo(index: Int) {
        mutex.withLock {
            val total = _events.value.size
            if (total == 0) {
                _cursor.value = -1
                return@withLock
            }
            val clamped = index.coerceIn(0, total - 1)
            _cursor.value = clamped
            if (clamped < total - 1) {
                // Manually scrubbing pulls us out of live + pauses playback.
                _isLive.value = false
                _isPlaying.value = false
            }
        }
    }

    /**
     * Advance cursor by one step during playback. Returns the events
     * crossed (always 0 or 1 for a single advance). Caller fires graph
     * overlays for each returned event.
     *
     * If we hit the end, playback auto-pauses (the user can drag back to
     * replay again, or toggle LIVE).
     */
    suspend fun advance(): List<ReplayEvent> {
        return mutex.withLock {
            if (!_isPlaying.value) return@withLock emptyList()
            val total = _events.value.size
            val current = _cursor.value
            if (current >= total - 1) {
                _isPlaying.value = false
                return@withLock emptyList()
            }
            val next = current + 1
            _cursor.value = next
            listOf(_events.value[next])
        }
    }

    private fun rebuildMerged() {
        val merged = ArrayList<ReplayEvent>(historyBuffer.size + liveTailBuffer.size)
        merged.addAll(historyBuffer)
        merged.addAll(liveTailBuffer)
        _events.value = merged
    }

    private fun snapCursorToEnd() {
        val total = _events.value.size
        _cursor.value = if (total == 0) -1 else total - 1
    }

    companion object {
        /** Default playback cadence — see class kdoc for rationale. */
        const val DEFAULT_STEP_INTERVAL_MS: Long = 100L
        const val DEFAULT_MAX_LIVE_TAIL: Int = 2000
    }
}
