package ai.factoredui.compose.schema

import kotlin.test.Test
import kotlin.test.assertEquals

private fun read(status: Int, body: String) = readActionAnswer("interrupt", status, body)

class ActionAnswerTest {
    @Test
    fun a_set_object_in_a_success_answer_is_the_bindings_to_write() {
        val answer = read(200, """{"set":{"pushback.guide":"Slow down.","pushback.audio":"/x.mp3"}}""")
        assertEquals(mapOf<String, Any?>("pushback.guide" to "Slow down.", "pushback.audio" to "/x.mp3"), answer.writes)
        assertEquals(null, answer.error)
    }

    @Test
    fun values_keep_their_types_numbers_booleans_nulls_objects_and_lists() {
        val answer = read(200, """{"set":{"a.n":2,"a.b":true,"a.z":null,"a.o":{"k":[1,"x"]}}}""")
        assertEquals(mapOf<String, Any?>("a.n" to 2.0, "a.b" to true, "a.z" to null, "a.o" to mapOf("k" to listOf(1.0, "x"))), answer.writes)
    }

    @Test
    fun a_text_that_looks_like_a_binding_is_written_as_text() {
        assertEquals(mapOf<String, Any?>("a.t" to "{not a binding}"), read(200, """{"set":{"a.t":"{not a binding}"}}""").writes)
    }

    @Test
    fun an_empty_answer_or_one_with_no_set_writes_nothing_and_is_not_an_error() {
        listOf("", "  ", """{"handled":true}""").forEach { body ->
            assertEquals(ActionAnswer(emptyMap(), null), read(200, body), body)
        }
        assertEquals(ActionAnswer(emptyMap(), null), read(204, ""))
    }

    @Test
    fun a_refusal_is_an_error_naming_the_status_and_the_reason_the_server_gave() {
        assertEquals(
            "action 'interrupt' was refused with status 500: the model timed out",
            read(500, """{"reason":"the model timed out"}""").error,
        )
        assertEquals("action 'interrupt' was refused with status 404: no such route", read(404, "no such route").error)
        assertEquals("action 'interrupt' was refused with status 502: no reason given", read(502, "").error)
    }

    @Test
    fun a_refusal_never_writes_even_when_it_carries_a_set() {
        assertEquals(emptyMap(), read(500, """{"set":{"a":"b"},"reason":"x"}""").writes)
    }

    @Test
    fun an_answer_that_is_not_json_is_an_error_showing_how_it_began() {
        assertEquals("action 'interrupt' answered something that is not a JSON object: 'ok'", read(200, "ok").error)
        assertEquals("action 'interrupt' answered something that is not a JSON object: '[1,2]'", read(200, "[1,2]").error)
    }

    @Test
    fun a_set_that_is_not_an_object_is_an_error_and_writes_nothing() {
        val answer = read(200, """{"set":["a"]}""")
        assertEquals("action 'interrupt' answered a 'set' that is not an object of paths to values", answer.error)
        assertEquals(emptyMap(), answer.writes)
    }

    @Test
    fun a_set_with_an_empty_path_is_refused_whole() {
        val answer = read(200, """{"set":{"a.b":"ok","":"bad"}}""")
        assertEquals("action 'interrupt' answered a 'set' with an empty path; nothing was written", answer.error)
        assertEquals(emptyMap(), answer.writes)
    }
}
