package ai.factoredui.application

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class SpecCacheTest {
    private val directory = Files.createTempDirectory("specs")

    @Test
    fun a_spec_written_under_a_key_is_read_back_and_another_key_has_none() {
        val cache = SpecCache(directory)
        cache.write("a", "one".toByteArray())
        assertContentEquals("one".toByteArray(), cache.read("a"))
        assertNull(cache.read("b"))
    }

    @Test
    fun only_the_newest_few_specs_are_kept() {
        val cache = SpecCache(directory)
        (1..8).forEach { cache.write("k$it", "v$it".toByteArray()); Files.setLastModifiedTime(directory.resolve("k$it.json"), java.nio.file.attribute.FileTime.fromMillis(it * 1000L)) }
        cache.write("k9", "v9".toByteArray())
        assertEquals(5, Files.list(directory).use { it.filter { file -> file.fileName.toString().endsWith(".json") }.count() })
        assertNull(cache.read("k1"))
        assertContentEquals("v9".toByteArray(), cache.read("k9"))
    }

    @Test
    fun the_key_changes_with_the_world_file_the_options_and_the_domain_files_it_uses() {
        val world = Files.createTempDirectory("world")
        val system = world.resolve("world.json").also { Files.writeString(it, """{ "id": "w", "uses": [ { "domain": "d", "file": "d.jar" } ] }""") }
        fun key(overlay: String? = null) = specCacheKey(Options(system, world.resolve("kotlin/we"), world, "127.0.0.1", 0, overlay = overlay))
        val base = key()
        assertEquals(base, key())
        assertNotEquals(base, key("utilisation"))
        Files.writeString(world.resolve("d.jar"), "jar")
        assertNotEquals(base, key())
        val withJar = key()
        Files.setLastModifiedTime(world.resolve("d.jar"), java.nio.file.attribute.FileTime.fromMillis(12345))
        assertNotEquals(withJar, key())
    }
}
