package ai.factoredui.compose.render

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import ai.factoredui.compose.renderer.RenderContext
import ai.factoredui.compose.renderer.RenderSpec
import ai.factoredui.compose.schema.Spec
import kotlinx.serialization.json.Json
import org.jetbrains.skia.EncodedImageFormat

private val specDecoder = Json { ignoreUnknownKeys = true }

// The headless render seam: any SDUI spec JSON -> a real factored-ui render -> PNG bytes, no device,
// no test runtime. agent-platform/CI calls this; it drives the ux-optimizer + BDD-visual loops.
@OptIn(ExperimentalComposeUiApi::class)
fun renderSpecToPng(
    specJson: String,
    width: Int = 800,
    height: Int = 1280,
    density: Float = 2f,
): ByteArray {
    val spec = specDecoder.decodeFromString(Spec.serializer(), specJson)
    val scene = ImageComposeScene(width = width, height = height, density = Density(density)) {
        RenderSpec(spec = spec, context = RenderContext())
    }
    try {
        return scene.render().encodeToData(EncodedImageFormat.PNG)?.bytes ?: ByteArray(0)
    } finally {
        scene.close()
    }
}
