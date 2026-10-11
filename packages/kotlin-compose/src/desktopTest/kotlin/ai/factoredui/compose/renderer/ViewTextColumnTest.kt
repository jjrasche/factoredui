package ai.factoredui.compose.renderer

import ai.factoredui.compose.crawl.driveScreen
import ai.factoredui.compose.scene.readReports
import ai.factoredui.compose.scene.reportsState
import ai.factoredui.compose.schema.Spec
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

private val json = Json { ignoreUnknownKeys = true }

private val VIEWS = reportsState(
    readReports("""{"views":[{"id":"stock","label":"Stock","columns":[{"id":"species","label":"Species","type":"text"}],"rows":[{"id":"r","label":"R","object_ids":[],"values":{"species":"pine"}}]}]}"""),
)

private fun cellSpec(format: String) = json.decodeFromString(
    Spec.serializer(),
    """
    {"spec_version":1,"renderer_min":1,"root":{"id":"page","type":"column","children":[{"id":"rows","type":"list","props":{"data":"{views.stock.rows}","itemTemplate":
      {"id":"cell","type":"text","props":{"value":"{row.values.species}"$format}}}}]}}
    """.trimIndent(),
)

class ViewTextColumnTest {

    @Test
    fun a_text_value_from_the_engines_views_response_draws_as_its_text() {
        driveScreen(cellSpec(""), mapOf("views" to VIEWS)).use { screen ->
            assertEquals(listOf("pine"), screen.textsUnder("page"))
        }
    }

    @Test
    fun a_number_format_on_a_text_value_draws_the_needs_a_number_notice() {
        driveScreen(cellSpec(""","format":"percent""""), mapOf("views" to VIEWS)).use { screen ->
            assertEquals(listOf("percent needs a number, not 'pine'"), screen.textsUnder("page"))
        }
    }
}
