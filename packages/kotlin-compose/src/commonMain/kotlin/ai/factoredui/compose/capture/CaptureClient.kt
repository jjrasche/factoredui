package ai.factoredui.compose.capture

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/**
 * Front-end facing capture orchestrator.
 *
 * Bundles a [SessionManager], an [EventWriter], and an [EventTransport]
 * into a single object the host app constructs once at boot. Auto-flush
 * starts on construction.
 *
 * Usage:
 *
 * ```kotlin
 * val capture = CaptureClient(
 *     transport = HttpEventTransport(httpClient, "/factoredui/ingest"),
 *     sessionManager = SessionManager(userId = { authedUserId }, platform = CapturePlatform.WEB),
 *     scope = appScope,
 * )
 * // ... in event handlers:
 * capture.trackClick("checkout/btn")
 * ```
 */
class CaptureClient(
    transport: EventTransport,
    private val sessionManager: SessionManager,
    scope: CoroutineScope,
    flushInterval: Duration = 2.seconds,
    flushBatchSize: Int = 50,
) {
    private val writer = EventWriter(
        transport = transport,
        scope = scope,
        flushInterval = flushInterval,
        flushBatchSize = flushBatchSize,
    )

    /**
     * Begin the periodic auto-flush loop. Call once at app boot (e.g. on
     * Activity.onCreate or root composable mount). Not called from the
     * constructor on purpose — auto-flush is a background coroutine that
     * keeps the host's scope alive, so the host should opt in explicitly.
     */
    fun start() { writer.startAutoFlush() }

    /** Emit a fully-formed event. Use [trackClick] etc. for the common shapes. */
    fun track(event: CaptureEvent) {
        val session = sessionManager.ensureSession()
        writer.enqueue(session, event)
    }

    fun trackClick(componentPath: String, payload: Map<String, JsonElement> = emptyMap()) {
        track(CaptureEvent(
            eventType = EventType.CLICK,
            componentPath = componentPath,
            payload = payload,
        ))
    }

    fun trackImpression(componentPath: String) {
        track(CaptureEvent(
            eventType = EventType.IMPRESSION,
            componentPath = componentPath,
        ))
    }

    fun trackNavigation(componentPath: String, action: String) {
        track(CaptureEvent(
            eventType = EventType.NAVIGATION,
            componentPath = componentPath,
            payload = mapOf("action" to JsonPrimitive(action)),
        ))
    }

    /** Force-drain the queue. Call on app foreground/background transitions. */
    suspend fun flush() = writer.flush()

    /** Stop auto-flush + clear the active session. Call on app shutdown. */
    fun shutdown() {
        writer.stopAutoFlush()
        sessionManager.endSession()
    }

    val sessionId: String? get() = sessionManager.sessionId
}
