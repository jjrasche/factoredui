package ai.factoredui.application

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private const val SYSTEM_FILE = """{ "id": "w", "title": "W", "systems": {} }"""

private fun worldWith(config: String, vararg screens: String): EngineWorld {
    val directory = Files.createTempDirectory("world")
    val file = directory.resolve("world.json").also { Files.writeString(it, config) }
    if (screens.isNotEmpty()) Files.createDirectories(directory.resolve("views")).let { views -> screens.forEach { Files.writeString(views.resolve(it), "{}") } }
    return EngineWorld("w", file)
}

class WorldViewsTest {
    @Test
    fun a_world_whose_file_reads_as_a_system_has_an_overview() {
        val views = viewsOf(worldWith(SYSTEM_FILE))
        assertEquals(listOf("overview"), views.map { it.id })
        assertNull(views.single().screen)
    }

    @Test
    fun a_file_that_is_not_a_system_file_has_no_overview() {
        assertEquals(emptyList(), viewsOf(worldWith("""{ "parts": [] }""")).map { it.id })
    }

    @Test
    fun each_screen_file_beside_the_world_is_a_view_named_for_the_file_and_listed_before_the_overview() {
        val world = worldWith(SYSTEM_FILE, "week.screen.json", "day.screen.json", "geometry.json")
        val views = viewsOf(world)
        assertEquals(listOf("day", "week", "overview"), views.map { it.id })
        assertEquals(listOf("Day", "Week", "Overview"), views.map { it.label })
        assertEquals(world.path.parent.resolve("views").resolve("day.screen.json"), views.first().screen)
    }

    @Test
    fun a_screen_names_its_view_with_spaces_for_underscores() {
        assertEquals("Heat flow", viewsOf(worldWith("{}", "heat_flow.screen.json")).single().label)
    }

    @Test
    fun a_world_that_cannot_be_read_has_no_views() {
        assertEquals(emptyList(), viewsOf(EngineWorld("gone", Path.of("no-such-directory", "world.json"))))
    }
}
