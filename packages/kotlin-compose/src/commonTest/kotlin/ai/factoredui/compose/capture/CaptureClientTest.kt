package ai.factoredui.compose.capture

import ai.factoredui.compose.schema.ActionRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonPrimitive

@OptIn(ExperimentalCoroutinesApi::class)
class CaptureClientTest {

    private class Recording : EventTransport {
        val sent = mutableListOf<Pair<Session, List<CaptureEvent>>>()
        override suspend fun send(session: Session, events: List<CaptureEvent>) {
            sent.add(session to events)
        }
    }

    private fun newClient(scope: kotlinx.coroutines.CoroutineScope, transport: EventTransport): CaptureClient {
        val sessions = SessionManager(
            userId = { "user-1" },
            platform = CapturePlatform.WEB,
            now = { Instant.fromEpochSeconds(1000) },
            newId = { "fixed-session" },
        )
        return CaptureClient(
            transport = transport,
            sessionManager = sessions,
            scope = scope,
            flushBatchSize = 1,
        )
    }

    @Test
    fun trackClickEnqueuesClickEvent() = runTest {
        val transport = Recording()
        val client = newClient(this, transport)

        client.trackClick("checkout/btn")
        client.flush()
        advanceUntilIdle()
        client.shutdown()

        assertEquals(1, transport.sent.size)
        val (session, events) = transport.sent[0]
        assertEquals("fixed-session", session.id)
        assertEquals(EventType.CLICK, events[0].eventType)
        assertEquals("checkout/btn", events[0].componentPath)
    }

    @Test
    fun trackNavigationIncludesActionInPayload() = runTest {
        val transport = Recording()
        val client = newClient(this, transport)

        client.trackNavigation("home", "mount")
        client.flush()
        advanceUntilIdle()
        client.shutdown()

        val event = transport.sent[0].second[0]
        assertEquals(EventType.NAVIGATION, event.eventType)
        assertEquals(JsonPrimitive("mount"), event.payload["action"])
    }

    @Test
    fun trackImpressionEnqueuesImpressionEvent() = runTest {
        val transport = Recording()
        val client = newClient(this, transport)

        client.trackImpression("hero/banner")
        client.flush()
        advanceUntilIdle()
        client.shutdown()

        assertEquals(EventType.IMPRESSION, transport.sent[0].second[0].eventType)
    }

    @Test
    fun captureObservabilityRecordsInteractionsAsClicks() = runTest {
        val transport = Recording()
        val client = newClient(this, transport)
        val observability = CaptureObservability(client)

        observability.onInteraction(
            nodeId = "checkout-btn",
            action = ActionRef(action = "submit-order"),
        )
        client.flush()
        advanceUntilIdle()
        client.shutdown()

        val event = transport.sent[0].second[0]
        assertEquals(EventType.CLICK, event.eventType)
        assertEquals("checkout-btn", event.componentPath)
        assertEquals(JsonPrimitive("submit-order"), event.payload["action"])
    }

    @Test
    fun sessionIdIsAvailableAfterFirstTrack() = runTest {
        val transport = Recording()
        val client = newClient(this, transport)

        client.trackClick("any")
        advanceUntilIdle()

        assertNotNull(client.sessionId)
        client.shutdown()
    }
}
