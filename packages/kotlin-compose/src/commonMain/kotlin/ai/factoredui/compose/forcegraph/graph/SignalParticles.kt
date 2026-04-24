package ai.factoredui.compose.forcegraph.graph

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock

/**
 * One signal traveling along an edge. Spawned the moment a signal is
 * emitted; it animates from the emitting function to the consuming
 * function over [durationMs] and then expires.
 *
 * The renderer reads `progress(now) ∈ [0, 1]` per frame and interpolates
 * between the endpoints' projected screen positions to draw the traveling
 * dot. Particles are transient — they never mutate the graph topology.
 */
data class SignalParticle(
    val fromFunction: String,
    val toFunction: String,
    val kind: String,
    val spawnedAtMs: Long,
    val durationMs: Long,
) {
    fun progress(nowMs: Long): Float {
        if (durationMs <= 0L) return 1f
        val t = (nowMs - spawnedAtMs).toFloat() / durationMs.toFloat()
        return t.coerceIn(0f, 1f)
    }

    fun isExpired(nowMs: Long): Boolean = nowMs - spawnedAtMs > durationMs
}

/**
 * Active particle set with spawn + sweep. The render loop calls [sweep]
 * alongside the physics step so expired particles drop out every frame.
 */
class SignalParticles(
    private val defaultDurationMs: Long = DEFAULT_DURATION_MS,
    private val clockMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    private val mutex = Mutex()
    private val particles: MutableList<SignalParticle> = mutableListOf()
    private val _active: MutableStateFlow<List<SignalParticle>> = MutableStateFlow(emptyList())

    /** Observable list of currently-traveling particles. */
    val active: StateFlow<List<SignalParticle>> = _active.asStateFlow()

    suspend fun spawn(fromFunction: String, toFunction: String, kind: String, durationMs: Long = defaultDurationMs) {
        mutex.withLock {
            particles += SignalParticle(
                fromFunction = fromFunction,
                toFunction = toFunction,
                kind = kind,
                spawnedAtMs = clockMillis(),
                durationMs = durationMs,
            )
            _active.value = particles.toList()
        }
    }

    /** Called by the render loop. Drops particles past their duration. */
    suspend fun sweep() {
        mutex.withLock {
            val now = clockMillis()
            val before = particles.size
            particles.removeAll { it.isExpired(now) }
            if (particles.size != before) _active.value = particles.toList()
        }
    }

    companion object {
        const val DEFAULT_DURATION_MS: Long = 700L
    }
}
