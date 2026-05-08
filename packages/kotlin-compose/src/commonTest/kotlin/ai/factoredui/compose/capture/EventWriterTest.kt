package ai.factoredui.compose.capture

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class EventWriterTest {

    private val testSession = Session(
        id = "session-1",
        userId = "user-1",
        startedAt = Instant.fromEpochSeconds(0),
        platform = CapturePlatform.WEB,
    )

    private fun event(type: EventType = EventType.CLICK, path: String = "page/btn") =
        CaptureEvent(eventType = type, componentPath = path)

    private class RecordingTransport(
        private val failTimes: Int = 0,
    ) : EventTransport {
        val sent = mutableListOf<List<CaptureEvent>>()
        private var failuresLeft = failTimes
        override suspend fun send(session: Session, events: List<CaptureEvent>) {
            if (failuresLeft > 0) {
                failuresLeft--
                throw RuntimeException("simulated transport failure")
            }
            sent.add(events)
        }
    }

    @Test
    fun batchSizeReachedTriggersImmediateFlush() = runTest {
        val transport = RecordingTransport()
        val writer = EventWriter(
            transport = transport,
            scope = this,
            flushBatchSize = 3,
        )

        writer.enqueue(testSession, event(path = "a"))
        writer.enqueue(testSession, event(path = "b"))
        writer.enqueue(testSession, event(path = "c"))
        advanceUntilIdle()

        assertEquals(1, transport.sent.size)
        assertEquals(listOf("a", "b", "c"), transport.sent[0].map { it.componentPath })
    }

    @Test
    fun autoFlushDrainsQueueOnTimer() = runTest {
        val transport = RecordingTransport()
        val writer = EventWriter(
            transport = transport,
            scope = this,
            flushInterval = 2.seconds,
            flushBatchSize = 100,
        )

        writer.enqueue(testSession, event(path = "x"))
        writer.startAutoFlush()
        advanceTimeBy(3.seconds)
        writer.stopAutoFlush()
        advanceUntilIdle()

        assertEquals(1, transport.sent.size)
        assertEquals("x", transport.sent[0].first().componentPath)
    }

    @Test
    fun failedFlushReQueuesAtHeadAndRetries() = runTest {
        val transport = RecordingTransport(failTimes = 1)
        val writer = EventWriter(
            transport = transport,
            scope = this,
            flushBatchSize = 2,
        )

        writer.enqueue(testSession, event(path = "first"))
        writer.enqueue(testSession, event(path = "second"))
        advanceUntilIdle()

        // First flush failed — items back in queue
        assertEquals(0, transport.sent.size)
        assertEquals(2, writer.queueSize())

        // Manual retry succeeds
        writer.flush()
        advanceUntilIdle()
        assertEquals(1, transport.sent.size)
        assertEquals(listOf("first", "second"), transport.sent[0].map { it.componentPath })
    }

    @Test
    fun explicitFlushIsIdempotentWhenQueueEmpty() = runTest {
        val transport = RecordingTransport()
        val writer = EventWriter(
            transport = transport,
            scope = this,
            flushBatchSize = 100,
        )

        writer.flush()
        writer.flush()
        advanceUntilIdle()

        assertTrue(transport.sent.isEmpty())
    }
}
