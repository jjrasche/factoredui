package ai.factoredui.application

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class FakeEngineCounterTest {
    private val engine = FakeEngine()
    private val client = HttpClient.newHttpClient()

    @AfterTest
    fun stopEngine() = engine.stop()

    private fun ask() = client.send(
        HttpRequest.newBuilder(URI("${engine.address}/run")).POST(HttpRequest.BodyPublishers.ofString("""{"command": "load"}""")).build(),
        HttpResponse.BodyHandlers.ofString(),
    )

    @Test
    fun two_calls_made_at_the_same_time_are_counted_as_two_in_flight() {
        engine.runAnswer = { Thread.sleep(300); "{}" }
        val beside = Thread { ask() }.apply { start() }
        ask()
        beside.join()
        assertEquals(2, engine.maxInFlight.get())
    }

    @Test
    fun calls_made_one_after_another_are_counted_as_one_however_quickly() {
        engine.runAnswer = { "{}" }
        repeat(200) { ask() }
        assertEquals(1, engine.maxInFlight.get())
    }
}