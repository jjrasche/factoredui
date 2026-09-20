package ai.factoredui.compose.crawl

import ai.factoredui.compose.schema.Spec
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val json = Json { ignoreUnknownKeys = true }
private val authoredSpecsDir = File("../kotlin-compose-playground/src/wasmJsMain/resources/specs")

class AuthoredSpecsPropTest {

    private fun authoredSpecs(): List<Pair<String, Spec>> =
        authoredSpecsDir.listFiles { file -> file.extension == "json" }
            .orEmpty()
            .sortedBy { it.name }
            .map { it.name to json.decodeFromString(Spec.serializer(), it.readText()) }

    @Test
    fun the_authored_specs_are_where_this_test_expects_them() {
        assertTrue(authoredSpecsDir.isDirectory, "no specs at ${authoredSpecsDir.absolutePath}")
        assertTrue(authoredSpecs().size >= 9, "found only ${authoredSpecs().size} authored specs to check")
    }

    @Test
    fun every_prop_every_authored_spec_declares_reaches_the_renderer() {
        val ignored = authoredSpecs().flatMap { (name, spec) ->
            unconsumedProps(spec, emptyMap(), 400, 900).map { "$name: ${it.nodeId} (${it.type}) declared '${it.key}'" }
        }
        assertEquals(emptyList(), ignored, "props declared by a real spec that reach nothing")
    }
}
