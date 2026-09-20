package ai.factoredui.compose.renderer

import ai.factoredui.compose.crawl.renderScreen
import ai.factoredui.compose.schema.Spec
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals

private val json = Json { ignoreUnknownKeys = true }

private const val DECLARED_GROUND = 0x0B0D0E

private fun rootSpec(type: String, props: String) = json.decodeFromString(
    Spec.serializer(),
    """
    {
      "spec_version": 1, "renderer_min": 1,
      "root": {
        "id": "root", "type": "$type", "props": $props,
        "children": [{ "id": "line", "type": "text", "props": { "value": "one line" } }]
      }
    }
    """.trimIndent(),
)

private fun cornerColours(png: ByteArray): List<Int> {
    val image = ImageIO.read(ByteArrayInputStream(png))
    return listOf(
        image.getRGB(2, 2),
        image.getRGB(image.width - 3, 2),
        image.getRGB(2, image.height - 3),
        image.getRGB(image.width - 3, image.height - 3),
    ).map { it and 0xFFFFFF }
}

class FlexFillsTest {

    @Test
    fun a_root_column_with_flex_fills_the_viewport() {
        val screen = renderScreen(
            rootSpec("column", """{ "padding": 8, "background": "#0B0D0E", "flex": 1 }"""),
            emptyMap(),
            300,
            300,
            theme = SpecTheme.LIGHT,
        )
        assertEquals(List(4) { DECLARED_GROUND }, cornerColours(screen.png), "every corner should be the declared ground")
    }

    @Test
    fun a_root_row_with_flex_fills_the_viewport() {
        val screen = renderScreen(
            rootSpec("row", """{ "padding": 8, "background": "#0B0D0E", "flex": 1 }"""),
            emptyMap(),
            300,
            300,
            theme = SpecTheme.LIGHT,
        )
        assertEquals(List(4) { DECLARED_GROUND }, cornerColours(screen.png), "every corner should be the declared ground")
    }

    @Test
    fun a_root_column_without_flex_still_wraps_its_content() {
        val screen = renderScreen(
            rootSpec("column", """{ "padding": 8, "background": "#0B0D0E" }"""),
            emptyMap(),
            300,
            300,
            theme = SpecTheme.DARK,
        )
        assertEquals(
            SpecTheme.DARK.ground.toRgbInt(),
            cornerColours(screen.png).last(),
            "a container that asked for no flex must not grow to fill the screen",
        )
    }
}
