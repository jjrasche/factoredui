package ai.factoredui.compose.renderer

import ai.factoredui.compose.crawl.DrivenScreen
import ai.factoredui.compose.crawl.driveScreen
import ai.factoredui.compose.schema.Spec
import androidx.compose.ui.unit.DpRect
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertTrue

private val json = Json { ignoreUnknownKeys = true }

private const val WCAG_TEXT = 4.5
private const val WIDTH = 400
private const val HEIGHT = 900

private fun spec(root: String): Spec = json.decodeFromString(Spec.serializer(), """{"spec_version":1,"renderer_min":1,"root":$root}""")

private val CONTAINERS = spec(
    """
    {"id":"page","type":"column","props":{"gap":8,"padding":8},"children":[
      {"id":"card","type":"card","props":{"padding":12},"children":[{"id":"card-text","type":"text","props":{"value":"Card body text"}}]},
      {"id":"rows","type":"list","props":{"data":"{rows}","stacked":true,"itemTemplate":
        {"id":"row-card","type":"card","props":{"padding":12},"children":[{"id":"row-text","type":"text","props":{"value":"{row.name}"}}]}}},
      {"id":"tabs","type":"tabs","props":{"items":["Tab one","Tab two"]},"children":[{"id":"tab-body","type":"text","props":{"value":"Tab body text"}},{"id":"tab-two","type":"text","props":{"value":"Second"}}]},
      {"id":"chip-off","type":"chip","props":{"label":"Chip off"}},
      {"id":"chip-on","type":"chip","props":{"label":"Chip on","selected":true}},
      {"id":"field","type":"textinput","props":{"value":"{form.name}"}},
      {"id":"switch","type":"toggle","props":{"label":"Toggle label text","value":false}},
      {"id":"picker","type":"select","props":{"placeholder":"Pick one","value":"{form.choice}","options":[{"label":"First option","value":"a"},{"label":"Second option","value":"b"}]}}
    ]}
    """.trimIndent(),
)

private val MODAL = spec(
    """{"id":"dialog","type":"modal","props":{"title":"Modal title"},"children":[{"id":"modal-text","type":"text","props":{"value":"Modal body text"}}]}""",
)

private val STORE = mapOf("rows" to listOf(mapOf("name" to "Row body text")), "form" to mapOf("name" to "Typed value text", "choice" to ""))

private fun linear(channel: Int): Double = (channel / 255.0).let { if (it <= 0.03928) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }

private fun luminance(rgb: Int): Double = 0.2126 * linear((rgb shr 16) and 0xFF) + 0.7152 * linear((rgb shr 8) and 0xFF) + 0.0722 * linear(rgb and 0xFF)

private fun contrastRatio(first: Int, second: Int): Double {
    val (darker, lighter) = listOf(luminance(first), luminance(second)).sorted()
    return (lighter + 0.05) / (darker + 0.05)
}

/** The background is the commonest colour in the text's middle band; the ink is the pixel that differs from it most. */
private fun inkContrastIn(png: ByteArray, box: DpRect): Double {
    val image = ImageIO.read(ByteArrayInputStream(png))
    val height = box.bottom.value - box.top.value
    val width = box.right.value - box.left.value
    val rows = (box.top.value + height * 0.25).toInt()..(box.top.value + height * 0.75).toInt()
    val columns = (box.left.value + width * 0.05).toInt()..(box.right.value - width * 0.05).toInt()
    val pixels = rows.flatMap { y -> columns.map { x -> image.getRGB(x.coerceIn(0, image.width - 1), y.coerceIn(0, image.height - 1)) and 0xFFFFFF } }
    val background = pixels.groupingBy { it }.eachCount().maxBy { it.value }.key
    return pixels.maxOf { contrastRatio(it, background) }
}

private fun unreadableTexts(screen: DrivenScreen, texts: List<String>): List<String> {
    val png = screen.frame().png
    return texts.mapNotNull { text ->
        val box = screen.boundsOfText(text) ?: return@mapNotNull "'$text' was not drawn"
        val ratio = inkContrastIn(png, box)
        if (ratio >= WCAG_TEXT) null else "'$text' ${"%.2f".format(ratio)}:1"
    }
}

private fun assertTextsReadable(screen: DrivenScreen, texts: List<String>, theme: SpecTheme) {
    val failures = unreadableTexts(screen, texts)
    assertTrue(failures.isEmpty(), "on the ${if (theme.isDark) "dark" else "light"} theme, below $WCAG_TEXT:1 against what is behind them: $failures")
}

private val CONTAINER_TEXTS = listOf("Card body text", "Row body text", "Tab one", "Tab body text", "Chip off", "Chip on", "Typed value text", "Toggle label text", "Pick one")

class ThemeContrastTest {

    @Test
    fun every_container_text_is_readable_on_the_light_theme() = assertContainersReadable(SpecTheme.LIGHT)

    @Test
    fun every_container_text_is_readable_on_the_dark_theme() = assertContainersReadable(SpecTheme.DARK)

    @Test
    fun the_select_popup_is_readable_on_the_light_theme() = assertSelectPopupReadable(SpecTheme.LIGHT)

    @Test
    fun the_select_popup_is_readable_on_the_dark_theme() = assertSelectPopupReadable(SpecTheme.DARK)

    @Test
    fun the_modal_is_readable_on_the_light_theme() = assertModalReadable(SpecTheme.LIGHT)

    @Test
    fun the_modal_is_readable_on_the_dark_theme() = assertModalReadable(SpecTheme.DARK)

    private fun assertContainersReadable(theme: SpecTheme) {
        driveScreen(CONTAINERS, STORE, WIDTH, HEIGHT, theme).use { screen ->
            assertTextsReadable(screen, CONTAINER_TEXTS, theme)
        }
    }

    private fun assertSelectPopupReadable(theme: SpecTheme) {
        driveScreen(CONTAINERS, STORE, WIDTH, HEIGHT, theme).use { screen ->
            screen.tapText("Pick one")
            assertTextsReadable(screen, listOf("First option"), theme)
        }
    }

    private fun assertModalReadable(theme: SpecTheme) {
        driveScreen(MODAL, STORE, WIDTH, HEIGHT, theme).use { screen ->
            assertTextsReadable(screen, listOf("Modal title", "Modal body text"), theme)
        }
    }
}
