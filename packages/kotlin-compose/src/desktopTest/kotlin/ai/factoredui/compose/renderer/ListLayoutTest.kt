package ai.factoredui.compose.renderer

import ai.factoredui.compose.crawl.driveScreen
import ai.factoredui.compose.schema.Spec
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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

private val SCROLLED_CHILD_LIST = json.decodeFromString(
    Spec.serializer(),
    """
    {"spec_version":1,"renderer_min":1,"root":{"id":"page","type":"scrollview","children":[
      {"id":"rows","type":"list","children":[
        {"id":"a","type":"text","props":{"value":"first"}},{"id":"b","type":"text","props":{"value":"second"}},{"id":"c","type":"text","props":{"value":"third"}}
      ]}
    ]}}
    """.trimIndent(),
)

private val FLEX_LIST = json.decodeFromString(
    Spec.serializer(),
    """
    {"spec_version":1,"renderer_min":1,"root":{"id":"page","type":"column","children":[
      {"id":"rows","type":"list","props":{"flex":1,"data":"{rows}","itemTemplate":{"id":"row","type":"text","props":{"value":"{row.name}"}}}}
    ]}}
    """.trimIndent(),
)

class ListLayoutTest {

    @Test
    fun an_unstacked_template_list_inside_a_scrollview_draws_every_row_instead_of_failing() {
        driveScreen(scrolledList(""), STORE).use { screen ->
            assertEquals(NAMES, screen.textsUnder("page"))
        }
    }

    @Test
    fun a_list_of_children_inside_a_scrollview_draws_every_child_instead_of_failing() {
        driveScreen(SCROLLED_CHILD_LIST, STORE).use { screen ->
            assertEquals(NAMES, screen.textsUnder("page"))
        }
    }

    @Test
    fun a_list_given_a_bounded_height_by_flex_stays_lazy_and_draws_only_the_rows_that_fit() {
        val manyRows = mapOf("rows" to (1..200).map { mapOf("name" to "row $it") })
        driveScreen(FLEX_LIST, manyRows, 400, 300).use { screen ->
            val drawn = screen.textsUnder("page")
            assertEquals("row 1", drawn.first())
            assertTrue(drawn.size in 2..60, "a lazy list composes the rows in view, not all 200: ${drawn.size}")
        }
    }

    @Test
    fun a_stacked_list_inside_a_scrollview_draws_every_row() {
        driveScreen(scrolledList(""""stacked":true,"""), STORE).use { screen ->
            assertEquals(NAMES, screen.textsUnder("page"))
        }
    }
}
