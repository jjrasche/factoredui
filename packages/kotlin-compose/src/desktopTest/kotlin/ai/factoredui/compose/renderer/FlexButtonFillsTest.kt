package ai.factoredui.compose.renderer

import ai.factoredui.compose.crawl.renderScreen
import ai.factoredui.compose.schema.Spec
import kotlinx.serialization.json.Json
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

private val json = Json { ignoreUnknownKeys = true }

private const val VIEWPORT_WIDTH_DP = 300
private const val GAP_DP = 10

private fun rowOfButtons(shortFlex: String, longFlex: String) = json.decodeFromString(
    Spec.serializer(),
    """
    {
      "spec_version": 1, "renderer_min": 1,
      "root": {
        "id": "root", "type": "row", "props": { "gap": $GAP_DP },
        "children": [
          { "id": "short", "type": "button", "props": { "label": "A"$shortFlex } },
          { "id": "long", "type": "button", "props": { "label": "A much longer label"$longFlex } }
        ]
      }
    }
    """.trimIndent(),
)

private fun widthOf(spec: Spec, id: String): Float {
    val screen = renderScreen(spec, emptyMap(), VIEWPORT_WIDTH_DP, 200)
    val bounds = screen.nodes.first { it.id == id }.bounds!!
    return bounds.right.value - bounds.left.value
}

class FlexButtonFillsTest {

    @Test
    fun buttons_with_equal_flex_in_a_row_are_equally_wide_whatever_their_labels() {
        val spec = rowOfButtons(""", "flex": 1""", """, "flex": 1""")
        val shortWidth = widthOf(spec, "short")
        val longWidth = widthOf(spec, "long")
        assertTrue(abs(shortWidth - longWidth) < 1f, "short $shortWidth dp, long $longWidth dp")
        assertTrue(shortWidth > (VIEWPORT_WIDTH_DP - GAP_DP) / 2f - 1f, "each takes its half: $shortWidth dp")
    }

    @Test
    fun a_button_without_flex_still_wraps_its_label() {
        val spec = rowOfButtons("", "")
        assertTrue(widthOf(spec, "short") < widthOf(spec, "long"))
    }
}
