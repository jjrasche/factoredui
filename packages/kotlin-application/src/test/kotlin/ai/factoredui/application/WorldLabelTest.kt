package ai.factoredui.application

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

private fun labelOfWorldFile(id: String, text: String): WorldLabel {
    val file = Files.createTempDirectory("label").resolve("world.json").also { Files.writeString(it, text) }
    return labelOf(EngineWorld(id, file))
}

class WorldLabelTest {
    @Test
    fun a_world_that_gives_a_short_title_is_named_by_it_and_its_full_title_is_kept() {
        val label = labelOfWorldFile("van-world", """{"id":"van-world","short_title":"The van","title":"Choosing the vehicle that replaces the Civic"}""")
        assertEquals(WorldLabel("van-world", "The van", "Choosing the vehicle that replaces the Civic"), label)
    }

    @Test
    fun a_world_with_no_short_title_is_named_by_its_id_made_readable() {
        assertEquals("Van", labelOfWorldFile("van-world", "{}").short)
        assertEquals("Arkopia greenhouse", labelOfWorldFile("arkopia-greenhouse", "{}").short)
        assertEquals("Small house", labelOfWorldFile("small-house", "{}").short)
        assertEquals("Folder/odd", labelOfWorldFile("folder/odd", "{}").short)
    }

    @Test
    fun a_file_that_cannot_be_read_still_has_a_name_and_no_full_title() {
        val label = labelOf(EngineWorld("gone-world", java.nio.file.Path.of("no-such-dir", "world.json")))
        assertEquals(WorldLabel("gone-world", "Gone", null), label)
    }

    @Test
    fun a_short_title_that_is_blank_or_not_text_is_ignored() {
        assertEquals("Van", labelOfWorldFile("van-world", """{"short_title":"  "}""").short)
        assertEquals("Van", labelOfWorldFile("van-world", """{"short_title":3}""").short)
    }
}
