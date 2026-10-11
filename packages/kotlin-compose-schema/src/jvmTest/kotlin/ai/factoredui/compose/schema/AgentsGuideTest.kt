package ai.factoredui.compose.schema

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val GUIDE = File("../../AGENTS.md")

private fun listedPrimitives(): Set<String> {
    val line = GUIDE.readLines().first { it.startsWith("column, ") }
    return line.trimEnd('.').split(", ").toSet()
}

class AgentsGuideTest {
    @Test
    fun the_guide_lists_exactly_the_primitives_the_renderer_has() {
        assertEquals(SpecNodeType.entries.map { it.name.lowercase() }.toSet(), listedPrimitives())
    }

    @Test
    fun the_guide_names_the_files_that_are_the_contract() {
        val text = GUIDE.readText()
        listOf("SpecNode.kt", "SpecProps.kt", "RenderNode.kt").forEach { assertTrue(text.contains(it), "the guide must name $it") }
    }
}
