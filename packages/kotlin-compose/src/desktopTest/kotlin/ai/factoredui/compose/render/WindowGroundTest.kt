package ai.factoredui.compose.render

import ai.factoredui.compose.renderer.RenderContext
import ai.factoredui.compose.schema.Spec
import androidx.compose.ui.graphics.toArgb
import kotlinx.serialization.json.Json
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals

private val json = Json { ignoreUnknownKeys = true }

private fun pixelBelowContent(root: String, context: RenderContext): Int {
    val spec = json.decodeFromString(Spec.serializer(), """{"spec_version":1,"renderer_min":1,"root":$root}""")
    val file = File.createTempFile("ground", ".png")
    SceneSurface(spec, context, 300, 400, ticking = false).use { surface -> surface.shot(file) }
    return ImageIO.read(file).getRGB(150, 395) and 0xFFFFFF
}

private fun page(background: String) =
    """{"id":"root","type":"column","props":{"padding":16$background},"children":[{"id":"hello","type":"text","props":{"value":"short"}}]}"""

class WindowGroundTest {
    @Test
    fun a_root_that_names_a_background_paints_the_whole_window_with_it_below_its_content() {
        assertEquals(0x0b1220, pixelBelowContent(page(""","background":"#0b1220""""), RenderContext()))
    }

    @Test
    fun a_root_with_no_background_leaves_the_window_the_themes_ground() {
        val context = RenderContext()
        assertEquals(context.theme.ground.toArgb() and 0xFFFFFF, pixelBelowContent(page(""), context))
    }
}
