package ai.factoredui.compose.renderer

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import ai.factoredui.compose.schema.Spec
import kotlinx.serialization.json.Json
import org.junit.Rule
import org.junit.Test
import java.io.File
import kotlin.test.assertTrue

private val json = Json { ignoreUnknownKeys = true }

class VideoPlaybackOnDeviceTest {

    @get:Rule
    val compose = createComposeRule()

    private fun clipOnDevice(): File {
        val context = InstrumentationRegistry.getInstrumentation().context
        val target = File(context.cacheDir, "probe_clip.mp4")
        context.assets.open("probe_clip.mp4").use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        return target
    }

    private fun videoSpec(path: String) = json.decodeFromString(
        Spec.serializer(),
        """
        {
          "spec_version": 1,
          "renderer_min": 1,
          "root": {
            "id": "clip",
            "type": "video",
            "props": { "source": "$path", "autoplay": true, "position": "{playback.positionMs}" }
          }
        }
        """.trimIndent(),
    )

    @Test
    fun the_bound_position_advances_while_a_local_file_plays() {
        val clip = clipOnDevice()
        assertTrue(clip.length() > 0, "the probe clip did not land on the device")
        val context = RenderContext(initialData = mapOf("playback" to mapOf("positionMs" to 0.0)))

        compose.setContent { RenderSpec(videoSpec(clip.absolutePath), context) }

        compose.waitUntil(timeoutMillis = 15_000) { positionOf(context) > 0.0 }
        val early = positionOf(context)
        compose.waitUntil(timeoutMillis = 15_000) { positionOf(context) > early + 500.0 }

        assertTrue(
            positionOf(context) > early,
            "playback position did not advance: started $early, ended ${positionOf(context)}",
        )
    }

    private fun positionOf(context: RenderContext): Double {
        val playback = context.data["playback"] as? Map<*, *> ?: return 0.0
        return (playback["positionMs"] as? Number)?.toDouble() ?: 0.0
    }
}
