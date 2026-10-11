package ai.factoredui.compose.renderer

import ai.factoredui.compose.crawl.driveScreen
import ai.factoredui.compose.format.MISSING_FIGURE
import ai.factoredui.compose.schema.Spec
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

private val json = Json { ignoreUnknownKeys = true }

private fun rowsSpec(format: String, missing: String = "") = json.decodeFromString(
    Spec.serializer(),
    """
    {"spec_version":1,"renderer_min":1,"root":{"id":"rows","type":"column","children":[{"id":"rows-list","type":"list","props":{
      "data": "{rows}",
      "itemTemplate": {"id":"row-figure","type":"text","props":{"value":"{row.values.x}","format":$format $missing}}
    }}]}}
    """.trimIndent(),
)

private fun rowsOf(vararg figures: Any?) = mapOf("rows" to figures.map { mapOf("values" to mapOf("x" to it)) })

private const val REQUESTED = """{"scale":0.000224809,"suffix":" lbf","decimals":0}"""

private const val HALF_POINTS = """{"scale":0.5,"suffix":" pts","decimals":2}"""

class ScaledTextFormatTest {

    @Test
    fun a_list_row_draws_its_bound_figure_through_an_object_format() {
        driveScreen(rowsSpec(REQUESTED), rowsOf(11126025.8)).use { screen ->
            assertEquals(listOf("2,501 lbf"), screen.textsUnder("rows"))
        }
    }

    @Test
    fun each_row_draws_its_scaled_figure_its_missing_text_or_its_notice() {
        driveScreen(rowsSpec(HALF_POINTS, missing = """, "missing": "not measured""""), rowsOf(0.256, -2469, null, "abc")).use { screen ->
            assertEquals(listOf("0.13 pts", "-1,234.50 pts", "not measured", "format needs a number, not 'abc'"), screen.textsUnder("rows"))
        }
    }

    @Test
    fun a_missing_figure_with_no_missing_text_draws_the_dash() {
        driveScreen(rowsSpec(HALF_POINTS), rowsOf(null)).use { screen ->
            assertEquals(listOf(MISSING_FIGURE), screen.textsUnder("rows"))
        }
    }

    @Test
    fun a_scale_written_as_text_draws_the_notice_in_the_row_not_a_figure() {
        driveScreen(rowsSpec("""{"scale":"0.0002"}"""), rowsOf(10)).use { screen ->
            assertEquals(listOf("format scale needs a number, not '0.0002'"), screen.textsUnder("rows"))
        }
    }

    @Test
    fun fractional_decimals_draw_the_notice_in_the_row_not_a_figure() {
        driveScreen(rowsSpec("""{"scale":0.5,"decimals":1.5}"""), rowsOf(10)).use { screen ->
            assertEquals(listOf("format decimals needs a whole number from 0 to 12, not '1.5'"), screen.textsUnder("rows"))
        }
    }

    @Test
    fun the_named_string_form_still_draws_beside_it() {
        driveScreen(rowsSpec("\"usd\""), rowsOf(6490.97)).use { screen ->
            assertEquals(listOf("$6,490.97"), screen.textsUnder("rows"))
        }
    }
}
