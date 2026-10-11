package ai.factoredui.compose.renderer

import ai.factoredui.compose.schema.Spec
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import kotlinx.serialization.json.Json
import kotlin.test.Test

private val json = Json { ignoreUnknownKeys = true }

private fun specOf(root: String) = json.decodeFromString(
    Spec.serializer(),
    """{"spec_version":1,"renderer_min":1,"root":$root}""",
)

private const val FORMATTED_FIGURES = """
{
  "id": "figures", "type": "column", "props": {},
  "children": [
    { "id": "cost", "type": "text", "props": { "value": "{line.cost}", "format": "usd" } },
    { "id": "unit", "type": "text", "props": { "value": "{line.unit}", "format": "usd" } },
    { "id": "span", "type": "text", "props": { "value": "{line.span_mm}", "format": "feet_inches" } },
    { "id": "use", "type": "text", "props": { "value": "{line.use}", "format": "percent" } },
    { "id": "mass", "type": "text", "props": { "value": "{line.mass_kg}", "format": "pounds" } },
    { "id": "no-price", "type": "text", "props": { "value": "{line.unpriced}", "format": "usd" } },
    { "id": "no-weight", "type": "text", "props": { "value": "{line.weight_kg}", "format": "pounds", "missing": "weight not known" } },
    { "id": "words", "type": "text", "props": { "value": "{line.label}", "format": "usd" } },
    { "id": "price-text", "type": "text", "props": { "value": "{line.price_text}", "format": "usd" } },
    { "id": "misspelt", "type": "text", "props": { "value": "{line.cost}", "format": "usdd" } }
  ]
}
"""

private val LINE = mapOf(
    "line" to mapOf(
        "cost" to 1108.44,
        "unit" to 62,
        "span_mm" to 2745.0,
        "use" to 0.31938214280064053,
        "mass_kg" to 10.0,
        "unpriced" to null,
        "weight_kg" to null,
        "label" to "Cast pier",
        "price_text" to "92.37",
    ),
)

@OptIn(ExperimentalTestApi::class)
class TextFormatRenderTest {

    @Test
    fun a_bound_number_is_drawn_in_the_format_the_spec_names() = runComposeUiTest {
        setContent { RenderSpec(specOf(FORMATTED_FIGURES), RenderContext(initialData = LINE)) }
        onNodeWithTag("cost").assertIsDisplayed().assertTextEquals("$1,108.44")
        onNodeWithTag("unit").assertTextEquals("$62")
        onNodeWithTag("span").assertTextEquals("9′ 0 1/16″")
        onNodeWithTag("use").assertTextEquals("32%")
        onNodeWithTag("mass").assertTextEquals("22.0 lb")
    }

    @Test
    fun a_null_value_draws_the_dash_or_the_specs_own_missing_words() = runComposeUiTest {
        setContent { RenderSpec(specOf(FORMATTED_FIGURES), RenderContext(initialData = LINE)) }
        onNodeWithTag("no-price").assertTextEquals("–")
        onNodeWithTag("no-weight").assertTextEquals("weight not known")
    }

    @Test
    fun a_text_value_under_a_numeric_format_draws_a_notice_not_the_dash() = runComposeUiTest {
        setContent { RenderSpec(specOf(FORMATTED_FIGURES), RenderContext(initialData = LINE)) }
        onNodeWithTag("words").assertIsDisplayed().assertTextEquals("usd needs a number, not 'Cast pier'")
        onNodeWithTag("price-text").assertTextEquals("usd needs a number, not '92.37'")
    }

    @Test
    fun an_unknown_format_name_is_drawn_on_screen() = runComposeUiTest {
        setContent { RenderSpec(specOf(FORMATTED_FIGURES), RenderContext(initialData = LINE)) }
        onNodeWithTag("misspelt").assertIsDisplayed().assertTextEquals("unknown format 'usdd'")
    }
}
