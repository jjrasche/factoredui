package ai.factoredui.compose.capture

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Batches captured events and flushes them via an [EventTransport] in
 * the background.
 *
 * Why this is a class rather than a suspend function on the hot path:
 * [enqueue] is called from event handlers (click listeners, gesture
 * recognizers) which are not coroutine contexts. The queue write happens
 * inside a launched coroutine so the event handler returns immediately.
 *
 * Flush triggers:
 * - The queue grows past [flushBatchSize].
 * - The auto-flush timer fires every [flushInterval] (when started).
 * - The host calls [flush] explicitly (e.g. on app foreground/background).
 *
 * Failure handling: a failed transport.send re-queues the batch at the
 * head of the queue and the auto-flush will retry next tick. Permanent
 * failures should be absorbed inside the transport, not surfaced as
 * exceptions, to avoid blocking the queue forever on a poison batch.
 */
class EventWriter(
    private val transport: EventTransport,
    private val scope: CoroutineScope,
    private val flushInterval: Duration = 2.seconds,
    private val flushBatchSize: Int = 50,
) {
    private data class Queued(val session: Session, val event: CaptureEvent)

    private val mutex = Mutex()
    private val queue = mutableListOf<Queued>()
    private var flushJob: Job? = null

    /**
     * Queue an event for asynchronous flush. Safe to call from any
     * non-coroutine context — returns immediately.
     */
    fun enqueue(session: Session, event: CaptureEvent) {
        scope.launch {
            val sizeAfter = mutex.withLock {
                queue.add(Queued(session, event))
                queue.size
            }
            if (sizeAfter >= flushBatchSize) flush()
        }
    }

    /** Drain the queue through the transport. Idempotent if queue is empty. */
    suspend fun flush() {
        val taken = mutex.withLock {
            if (queue.isEmpty()) return
            val drained = queue.toList()
            queue.clear()
            drained
        }
        try {
            taken.groupBy { it.session.id }.forEach { (_, sessionGroup) ->
                val session = sessionGroup.first().session
                transport.send(session, sessionGroup.map { it.event })
            }
        } catch (_: Throwable) {
            // Re-queue at head so order is preserved on the next flush.
            mutex.withLock { queue.addAll(0, taken) }
        }
    }

    /** Start a background loop that flushes every [flushInterval]. */
    fun startAutoFlush() {
        if (flushJob?.isActive == true) return
        flushJob = scope.launch {
            while (isActive) {
                delay(flushInterval)
                flush()
            }
        }
    }

    /** Stop the auto-flush loop. Does not flush remaining items. */
    fun stopAutoFlush() {
        flushJob?.cancel()
        flushJob = null
    }

    /** Snapshot of current queue depth — for tests and diagnostics. */
    suspend fun queueSize(): Int = mutex.withLock { queue.size }
}
