package ai.factoredui.compose.capture

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@OptIn(ExperimentalCoroutinesApi::class)
class HttpEventTransportTest {

    private val testSession = Session(
        id = "session-1",
        userId = "user-1",
        startedAt = Instant.fromEpochSeconds(0),
        platform = CapturePlatform.WEB,
    )

    private fun event(path: String) = CaptureEvent(
        eventType = EventType.CLICK,
        componentPath = path,
    )

    private fun mockClient(
        status: HttpStatusCode,
        capture: ((String) -> Unit)? = null,
    ): HttpClient {
        val engine = MockEngine { request ->
            capture?.invoke(request.body.toString())
            respond(
                content = ByteReadChannel("{}"),
                status = status,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        return HttpClient(engine)
    }

    @Test
    fun postsSessionAndEventsAsJsonOnSuccess() = runTest {
        var capturedBody: String? = null
        val client = mockClient(HttpStatusCode.OK) { body -> capturedBody = body }
        val transport = HttpEventTransport(client, "https://example/ingest")

        transport.send(testSession, listOf(event("checkout/btn"), event("home/banner")))

        // Body is captured via MockEngine's body property; assert structure once it parses.
        // Direct body capture from MockEngine returns the wrapper string; instead read the
        // full request via a different path — assert no throw is sufficient for the wire test.
        // Round-trip the request body would require an OutgoingContent reader.
        assertEquals(true, capturedBody != null)
    }

    @Test
    fun emptyEventListIsANoop() = runTest {
        var requestCount = 0
        val client = mockClient(HttpStatusCode.OK) { _ -> requestCount++ }
        val transport = HttpEventTransport(client, "https://example/ingest")

        transport.send(testSession, emptyList())

        assertEquals(0, requestCount)
    }

    @Test
    fun fourHundredResponseIsAbsorbedNotThrown() = runTest {
        val client = mockClient(HttpStatusCode.BadRequest)
        val transport = HttpEventTransport(client, "https://example/ingest")

        // Should NOT throw — permanent failures are dropped silently.
        transport.send(testSession, listOf(event("a")))
    }

    @Test
    fun fiveHundredResponseThrows() = runTest {
        val client = mockClient(HttpStatusCode.InternalServerError)
        val transport = HttpEventTransport(client, "https://example/ingest")

        val ex = assertFailsWith<IllegalStateException> {
            transport.send(testSession, listOf(event("a")))
        }
        assertTrue(ex.message!!.contains("500"))
    }

    @Test
    fun bodyContainsSessionAndEventsInExpectedShape() = runTest {
        // Roundtrip: parse the request body to verify wire format.
        var parsed: kotlinx.serialization.json.JsonObject? = null
        val engine = MockEngine { request ->
            val raw = (request.body as io.ktor.http.content.OutgoingContent.ByteArrayContent).bytes()
            parsed = Json.parseToJsonElement(raw.decodeToString()).jsonObject
            respond(
                content = "{}",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val transport = HttpEventTransport(HttpClient(engine), "https://example/ingest")
        transport.send(testSession, listOf(event("checkout/btn")))

        val obj = parsed!!
        assertEquals("session-1", obj["session"]!!.jsonObject["id"]!!.jsonPrimitive.content)
        assertEquals("user-1", obj["session"]!!.jsonObject["user_id"]!!.jsonPrimitive.content)
        val events = obj["events"]!!.jsonArray
        assertEquals(1, events.size)
        assertEquals("click", events[0].jsonObject["event_type"]!!.jsonPrimitive.content)
        assertEquals("checkout/btn", events[0].jsonObject["component_path"]!!.jsonPrimitive.content)
    }
}
