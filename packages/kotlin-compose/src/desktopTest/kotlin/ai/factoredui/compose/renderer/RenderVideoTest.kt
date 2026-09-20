package ai.factoredui.compose.renderer

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import ai.factoredui.compose.schema.Spec
import kotlinx.serialization.json.Json
import kotlin.test.Test

private val json = Json { ignoreUnknownKeys = true }

private fun specOf(videoProps: String) = json.decodeFromString(
    Spec.serializer(),
    """
    {
      "spec_version": 1,
      "renderer_min": 1,
      "root": { "id": "clip", "type": "video", "props": $videoProps }
    }
    """.trimIndent(),
)

@OptIn(ExperimentalTestApi::class)
class RenderVideoTest {

    @Test
    fun a_target_with_no_player_says_so_instead_of_drawing_nothing() = runComposeUiTest {
        setContent { RenderSpec(specOf("""{"source":"/tmp/talk.mp4"}"""), RenderContext()) }
        onNodeWithText("Video unsupported on this target", substring = true).assertIsDisplayed()
    }

    @Test
    fun the_unsupported_notice_names_the_source_so_the_spec_can_be_checked() = runComposeUiTest {
        setContent { RenderSpec(specOf("""{"source":"/tmp/talk.mp4"}"""), RenderContext()) }
        onNodeWithText("/tmp/talk.mp4", substring = true).assertIsDisplayed()
    }

    @Test
    fun a_video_node_with_no_source_reports_the_empty_source_rather_than_rendering_blank() = runComposeUiTest {
        setContent { RenderSpec(specOf("""{}"""), RenderContext()) }
        onNodeWithText("no source", substring = true).assertIsDisplayed()
    }
}
