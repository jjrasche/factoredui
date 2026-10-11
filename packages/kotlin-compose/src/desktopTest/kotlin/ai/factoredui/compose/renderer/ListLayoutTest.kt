package ai.factoredui.compose.renderer

import ai.factoredui.compose.crawl.driveScreen
import ai.factoredui.compose.schema.Spec
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

private val json = Json { ignoreUnknownKeys = true }

private fun scrolledList(listProps: String) = json.decodeFromString(
    Spec.serializer(),
    """
    {"spec_version":1,"renderer_min":1,"root":{"id":"page","type":"scrollview","children":[
      {"id":"rows","type":"list","props":{"data":"{rows}",$listProps"itemTemplate":{"id":"row","type":"text","props":{"value":"{row.name}"}}}}
    ]}}
    """.trimIndent(),
)

private val NAMES = listOf("first", "second", "third")

private val STORE = mapOf("rows" to NAMES.map { mapOf("name" to it) })

class ListLayoutTest {

    @Test
    fun a_stacked_list_inside_a_scrollview_draws_every_row() {
        driveScreen(scrolledList(""""stacked":true,"""), STORE).use { screen ->
            assertEquals(NAMES, screen.textsUnder("page"))
        }
    }
}
