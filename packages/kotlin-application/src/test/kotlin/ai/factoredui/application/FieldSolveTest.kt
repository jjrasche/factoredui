package ai.factoredui.application

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private const val VIEW = """{ "title": "Tube", "world": "w", "grid": "grids.g", "inlet": "series.in", "outlet": "series.out", "layouts": ["serpentine", "network"],
  "solve": { "system": "earth_tube", "function": "earth-tube-field" } }"""

private fun values(base: Int) = """{ "grids": { "g": { "axes": [ { "name": "y", "unit": "mm", "edges_mm": [0, 500, 1000] }, { "name": "z", "unit": "mm", "edges_mm": [0, 250] } ], "times_h": [0, 1], "values": [$base, 2, 3, 4] } },
  "series": { "in": { "unit": "C", "t_h": [0, 1], "v": [1, 2] }, "out": { "unit": "C", "t_h": [0, 1], "v": [3, 4] } } }"""

private fun solved(status: String, base: Int) = """{ "solvers": [ { "function": "earth-tube-field", "domain": "thermal", "status": "$status", "detail": "d", "values": ${values(base)} } ] }"""

private class RecordingEngine(val status: String = "known") : EngineRuntime {
    val calls = mutableListOf<List<String>>()
    override fun answer(arguments: List<String>, timeoutSeconds: Long): String {
        calls += arguments
        return solved(status, if ("network" in arguments.last()) 20 else 10)
    }
}

private val worldFile = Files.createTempFile("world", ".json").also { Files.writeString(it, """{ "id": "w", "systems": {} }""") }

class FieldSolveTest {
    @Test
    fun each_layout_is_solved_by_the_engine_with_that_layouts_solution_chosen() {
        val engine = RecordingEngine()
        fieldSpecBySolving(engine, worldFile, readFieldScreen(VIEW))
        assertEquals(listOf("solve", worldFile.toAbsolutePath().toString(), "--use", "earth_tube=serpentine"), engine.calls[0])
        assertEquals("earth_tube=network", engine.calls[1].last())
        assertEquals(2, engine.calls.size)
    }

    @Test
    fun the_page_draws_what_the_engine_answered_for_each_layout() {
        val spec = fieldSpecBySolving(RecordingEngine(), worldFile, readFieldScreen(VIEW))
        val heatmap = spec["root"]!!.jsonObject.let { root -> generateSequence(listOf(root)) { level -> level.flatMap { it["children"]?.jsonArray.orEmpty().map { child -> child.jsonObject } }.takeIf { it.isNotEmpty() } }.flatten() }
            .first { it["id"]!!.jsonPrimitive.content == "network.heatmap" }["props"] as JsonObject
        assertEquals(20.0, heatmap["frames"]!!.jsonArray[0].jsonArray[0].jsonPrimitive.content.toDouble())
    }

    @Test
    fun a_solver_that_is_not_known_is_refused_with_the_layout_and_its_status() {
        val failure = assertFailsWith<FieldFormatException> { fieldSpecBySolving(RecordingEngine(status = "unknown"), worldFile, readFieldScreen(VIEW)) }
        assertTrue("serpentine" in failure.message!! && "unknown" in failure.message!!)
    }

    @Test
    fun the_page_names_the_config_the_answers_were_solved_from() {
        val spec = fieldSpecBySolving(RecordingEngine(), worldFile, readFieldScreen(VIEW))
        assertTrue(spec.toString().contains("From the engine"))
    }
}
