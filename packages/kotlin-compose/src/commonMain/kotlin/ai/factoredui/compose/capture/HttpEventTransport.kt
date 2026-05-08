package ai.factoredui.compose.capture

import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * EventTransport that POSTs JSON batches to a backend ingest endpoint.
 *
 * Wire format:
 *   POST <endpoint>
 *   Content-Type: application/json
 *   { "session": Session, "events": [CaptureEvent, ...] }
 *
 * Failure handling matches the EventTransport contract:
 *   - 2xx response: success.
 *   - 4xx response: permanent failure (malformed batch, auth rejection).
 *     Drop the batch silently — re-queueing would loop forever.
 *   - 5xx response or network error: transient failure. Throw so the
 *     writer re-queues at the head of the queue and retries on the
 *     next flush.
 *
 * The host wires the [HttpClient] (auth headers, timeouts, retry
 * policy, ktor engine selection). We do not configure those here.
 */
class HttpEventTransport(
    private val client: HttpClient,
    private val endpoint: String,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : EventTransport {

    @Serializable
    private data class IngestRequest(
        val session: Session,
        val events: List<CaptureEvent>,
    )

    override suspend fun send(session: Session, events: List<CaptureEvent>) {
        if (events.isEmpty()) return
        val body = json.encodeToString(
            IngestRequest.serializer(),
            IngestRequest(session = session, events = events),
        )
        val response: HttpResponse = client.post(endpoint) {
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        val code = response.status.value
        when {
            code in 200..299 -> Unit
            code in 400..499 -> Unit  // permanent — drop
            else -> error("factoredui ingest failed: HTTP $code")
        }
    }
}
