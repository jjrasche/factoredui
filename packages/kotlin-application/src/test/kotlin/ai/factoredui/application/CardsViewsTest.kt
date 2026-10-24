package ai.factoredui.application

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

private const val ANSWERS = """{ "fixture": "made up", "title": "Stool",
  "parts": [ { "id": "a", "name": "Board", "x": 0, "y": 0, "z": 0, "dx": 300, "dy": 200, "dz": 20 } ],
  "steps": [ { "title": "Join", "parts": ["a"], "screws": 2, "adult": "x", "child_4_6": "y", "child_7_10": "z", "ask_4_6": "q", "ask_7_10": "r", "shows": "s", "learns": "l" } ],
  "end_questions": ["one", "two"] }"""

private fun worldWithCards(viewWorld: String): Pair<EngineWorld, Path> {
    val directory = Files.createTempDirectory("world")
    val file = directory.resolve("w.json").also { Files.writeString(it, """{ "id": "w", "title": "W", "systems": {} }""") }
    val views = Files.createDirectories(directory.resolve("views"))
    Files.writeString(views.resolve("stool.cards.json"), """{ "title": "Stool", "world": "$viewWorld", "answers": "stool.fixture.json" }""")
    Files.writeString(views.resolve("stool.fixture.json"), ANSWERS)
    return EngineWorld("w", file) to views.resolve("stool.cards.json")
}

class CardsViewsTest {
    @Test
    fun a_cards_file_naming_this_world_gives_its_cards_and_its_blueprint_before_the_overview() {
        val (world, file) = worldWithCards("w")
        val views = viewsOf(world)
        assertEquals(listOf("stool", "stool-blueprint", "overview"), views.map { it.id })
        assertEquals(listOf("Stool", "Blueprint", "Overview"), views.map { it.label })
        assertEquals(file, views.first().cards)
        assertTrue(views[1].blueprint)
    }

    @Test
    fun a_cards_file_naming_another_world_is_not_a_view_of_this_one() {
        assertEquals(listOf("overview"), viewsOf(worldWithCards("elsewhere").first).map { it.id })
    }

    @Test
    fun the_blueprint_view_draws_the_light_page_and_the_cards_view_the_dark_one() {
        val (_, file) = worldWithCards("w")
        assertEquals("light", cardsSpecOfFile(file, true)["theme"].toString().trim('"'))
        assertEquals("dark", cardsSpecOfFile(file, false)["theme"].toString().trim('"'))
    }

    @Test
    fun the_page_key_of_both_views_changes_when_the_answers_file_does() {
        val (world, file) = worldWithCards("w")
        val cards = worldSpecKey("w", "stool", world.path)
        val blueprint = worldSpecKey("w", "stool-blueprint", world.path)
        Files.writeString(file.parent.resolve("stool.fixture.json"), ANSWERS.replace("made up", "changed"))
        assertNotEquals(cards, worldSpecKey("w", "stool", world.path))
        assertNotEquals(blueprint, worldSpecKey("w", "stool-blueprint", world.path))
    }

    @Test
    fun a_blueprint_view_is_the_page_alone_and_a_cards_view_is_framed_by_the_world_bar() {
        val (world, _) = worldWithCards("w")
        val views = viewsOf(world)
        val spec = cardsSpecOfFile(views[0].cards!!, false)
        val bar = worldBar(listOf(labelOf(world)), world, views, views[0])
        assertEquals(spec, framedPage(spec, bar, emptyMap(), views[1]))
        assertNotEquals(spec, framedPage(spec, bar, emptyMap(), views[0]))
    }
}
