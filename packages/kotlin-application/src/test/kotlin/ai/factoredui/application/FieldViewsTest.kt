package ai.factoredui.application

import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

private fun view(world: String, answers: String?) = """{
  "title": "Earth battery", "world": "$world", "grid": "grids.g", "inlet": "series.in", "outlet": "series.out", "layouts": ["a"]
  ${answers?.let { """, "answers": "$it"""" } ?: ""}
}"""

private const val ANSWERS = """{ "a": { "fixture": "made up",
  "grids": { "g": { "axes": [ { "name": "y", "unit": "mm", "origin_mm": 0, "step_mm": 1, "count": 1 }, { "name": "z", "unit": "mm", "origin_mm": 0, "step_mm": -1, "count": 1 } ], "times_h": [0], "values": [1] } },
  "series": { "in": { "p": { "unit": "C", "t_h": [0], "v": [2] } }, "out": { "p": { "unit": "C", "t_h": [0], "v": [3] } } } } }"""

private fun worldBeside(worldId: String, viewWorld: String, answers: String?): Pair<EngineWorld, Path> {
    val directory = Files.createTempDirectory("world")
    val file = directory.resolve("w.json").also { Files.writeString(it, """{ "id": "w", "title": "W", "systems": {} }""") }
    val views = Files.createDirectories(directory.resolve("views"))
    Files.writeString(views.resolve("battery.field.json"), view(viewWorld, answers))
    answers?.let { Files.writeString(views.resolve(it), ANSWERS) }
    return EngineWorld(worldId, file) to views.resolve("battery.field.json")
}

class FieldViewsTest {
    @Test
    fun a_field_file_naming_this_world_is_a_view_before_the_overview() {
        val (world, file) = worldBeside("w", "w", "a.fixture.json")
        val views = viewsOf(world)
        assertEquals(listOf("battery", "overview"), views.map { it.id })
        assertEquals(file, views.first().field)
    }

    @Test
    fun a_field_file_naming_another_world_is_not_a_view_of_this_one() {
        val (world, _) = worldBeside("w", "elsewhere", "a.fixture.json")
        assertEquals(listOf("overview"), viewsOf(world).map { it.id })
    }

    @Test
    fun the_view_file_draws_the_answers_file_beside_it() {
        val (_, file) = worldBeside("w", "w", "a.fixture.json")
        val spec = fieldSpecOfFile(file)
        assertTrue(spec.toString().contains("FIXTURE: made up"))
        assertEquals("root", spec["root"]!!.jsonObject["id"].toString().trim('"'))
    }

    @Test
    fun a_view_with_no_answers_file_says_the_engine_solver_is_not_wired_yet() {
        val (_, file) = worldBeside("w", "w", null)
        assertTrue("answers" in assertFailsWith<FieldFormatException> { fieldSpecOfFile(file) }.message!!)
    }

    @Test
    fun the_page_key_changes_when_the_answers_file_does() {
        val (world, file) = worldBeside("w", "w", "a.fixture.json")
        val before = worldSpecKey("w", "battery", world.path)
        Files.writeString(file.parent.resolve("a.fixture.json"), ANSWERS.replace("made up", "changed"))
        assertNotEquals(before, worldSpecKey("w", "battery", world.path))
    }
}
