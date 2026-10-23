package ai.factoredui.compose.render

import ai.factoredui.compose.crawl.driveScreen
import ai.factoredui.compose.schema.Spec
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val LONG_LABEL = "The Protestant Ethic and the Spirit of Capitalism: Chapter 1: Religious Affiliation and Social Stratification"

private val SPEC = Json.decodeFromString(
    Spec.serializer(),
    """
    { "spec_version": 1, "renderer_min": 1, "root": { "id": "root", "type": "column", "props": {}, "children": [
      { "id": "chapter", "type": "select", "props": { "value": "{pick.chapter}", "options": [ { "value": "c1", "label": "$LONG_LABEL" } ] } } ] } }
    """,
)

class SelectOneLineTest {
    @Test
    fun a_closed_select_with_a_long_label_stays_one_line_tall() {
        driveScreen(SPEC, mapOf("pick" to mapOf("chapter" to "c1")), widthDp = 300, heightDp = 400).use { screen ->
            val pill = assertNotNull(screen.boundsOf("chapter"))
            assertTrue(pill.bottom.value - pill.top.value < 64f, "the select is ${pill.bottom.value - pill.top.value} dp tall")
        }
    }
}
