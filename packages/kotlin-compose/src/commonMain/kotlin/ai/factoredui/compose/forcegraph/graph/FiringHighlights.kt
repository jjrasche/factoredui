package ai.factoredui.compose.forcegraph.graph

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock

/**
 * Tracks which function nodes are currently "active" — i.e. have a
 * handler actively processing a signal (or were very recently processing
 * one). The architectural graph is static; this is the only thing that
 * changes at runtime.
 *
 * The router fires [ai.agentplatform.router.FiringEvent.Started] when a
 * handler is about to run; JVM adapters call [mark] with the function
 * name. Each marked name gets an expiry `holdMillis` into the future. A
 * sweeper coroutine (the render loop) calls [sweep] periodically to drop
 * expired names.
 *
 * Why hold? Function firings often complete in well under one animation
 * frame — without a deliberate minimum hold duration, the eye never sees
 * the highlight. 400 ms is long enough to register, short enough that a
 * steady firing stream still reads as motion.
 */
class FiringHighlights(
    private val holdMillis: Long = DEFAULT_HOLD_MILLIS,
    private val clockMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    private val mutex = Mutex()
    private val expireAt: HashMap<String, Long> = HashMap()

    private val _active: MutableStateFlow<Set<String>> = MutableStateFlow(emptySet())

    /** Observable set of currently-active function names. */
    val active: StateFlow<Set<String>> = _active.asStateFlow()

    suspend fun mark(functionName: String) {
        mutex.withLock {
            expireAt[functionName] = clockMillis() + holdMillis
            _active.value = expireAt.keys.toSet()
        }
    }

    /** Called by the render loop. Removes names whose expiry is in the past. */
    suspend fun sweep() {
        mutex.withLock {
            val now = clockMillis()
            var changed = false
            val iter = expireAt.entries.iterator()
            while (iter.hasNext()) {
                if (iter.next().value <= now) {
                    iter.remove()
                    changed = true
                }
            }
            if (changed) _active.value = expireAt.keys.toSet()
        }
    }

    companion object {
        const val DEFAULT_HOLD_MILLIS: Long = 400L
    }
}
