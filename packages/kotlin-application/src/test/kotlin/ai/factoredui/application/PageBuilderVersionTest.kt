package ai.factoredui.application

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PageBuilderVersionTest {
    private val worldFile = Files.createTempFile("world", ".json").also { Files.writeString(it, """{ "id": "w" }""") }

    @Test
    fun the_version_is_a_sha_256_that_is_the_same_each_time_it_is_asked() {
        assertEquals(64, pageBuilderVersion.length)
        assertTrue(pageBuilderVersion.all { it in "0123456789abcdef" })
        assertEquals(pageBuilderVersion, pageBuilderVersion)
    }

    @Test
    fun a_page_built_by_a_different_version_of_the_builders_is_a_different_key() {
        val before = worldSpecKey("w", null, worldFile, builderVersion = "builders-1")
        assertNotEquals(before, worldSpecKey("w", null, worldFile, builderVersion = "builders-2"))
        assertEquals(before, worldSpecKey("w", null, worldFile, builderVersion = "builders-1"))
    }

    @Test
    fun the_key_carries_the_builders_version_by_default() {
        assertEquals(worldSpecKey("w", null, worldFile, builderVersion = pageBuilderVersion), worldSpecKey("w", null, worldFile))
    }
}