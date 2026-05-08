package ai.factoredui.compose.capture

/**
 * Sends a batch of captured events somewhere — typically an HTTP POST to
 * the backend ingest endpoint, but may also be an in-process
 * implementation for tests or for embedding the server target in the
 * same JVM as the frontend.
 *
 * Implementations MUST throw on transient failure so the writer can
 * re-queue the batch and retry on the next flush. Implementations MUST
 * NOT throw on permanent failure (malformed data, auth rejection) —
 * those events are dropped to prevent a poison batch from blocking
 * the queue forever.
 */
interface EventTransport {
    suspend fun send(session: Session, events: List<CaptureEvent>)
}

/** Drops every event. Useful as a default and for tests. */
object NoOpEventTransport : EventTransport {
    override suspend fun send(session: Session, events: List<CaptureEvent>) {
        // intentionally empty
    }
}
