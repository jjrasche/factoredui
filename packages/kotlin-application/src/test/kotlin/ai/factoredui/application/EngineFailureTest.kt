package ai.factoredui.application

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

private fun pageSaying(text: String): JsonObject = JsonObject(mapOf("root" to JsonObject(mapOf("children" to JsonArray(listOf(JsonObject(mapOf("props" to JsonObject(mapOf("value" to JsonPrimitive(text)))))))))))

class EngineFailureTest {
    @Test
    fun a_section_the_engine_could_not_be_reached_for_is_a_failure() {
        val transport = listOf(
            "The geometry could not be read: the engine at http://127.0.0.1:8197 did not answer: request timed out",
            "The solvers could not be run: the runtime did not answer",
            "The simulation could not be run: connection refused",
            "The sample could not be run: the engine refused solve: status 503",
            "The systems could not be described: the engine at http://127.0.0.1:8766 listed no worlds: status 502",
        )
        assertEquals(listOf(true, true, true, true, true), transport.map { hasEngineFailure(pageSaying(it)) })
    }

    @Test
    fun an_answer_the_engine_gave_about_the_domain_is_not_a_failure() {
        val domain = listOf(
            "The ranking could not be run: the systems' against relation has a cycle",
            "The ranking could not be run: no weights",
            "The geometry could not be read: no geometry",
            "loft_floor: its solution is placed at its own position",
        )
        assertEquals(listOf(false, false, false, false), domain.map { hasEngineFailure(pageSaying(it)) })
    }

    @Test
    fun a_page_with_both_is_a_failure_and_a_page_with_neither_is_not() {
        val both = JsonArray(listOf(JsonPrimitive("The ranking could not be run: a cycle"), JsonPrimitive("The solvers could not be run: the engine did not answer")))
        assertEquals(true, hasEngineFailure(both))
        assertEquals(false, hasEngineFailure(JsonArray(listOf(JsonPrimitive("Heat loss"), JsonPrimitive("1,163 lb")))))
    }
}