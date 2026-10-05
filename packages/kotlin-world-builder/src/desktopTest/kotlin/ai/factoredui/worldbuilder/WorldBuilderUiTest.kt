package ai.factoredui.worldbuilder

import ai.factoredui.compose.renderer.RenderContext
import ai.factoredui.compose.renderer.RenderSpec
import ai.factoredui.compose.schema.Spec
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import java.io.File
import kotlin.test.Test
import kotlinx.serialization.json.Json

@OptIn(ExperimentalTestApi::class)
class WorldBuilderUiTest {

    private val spec = Json { ignoreUnknownKeys = true }
        .decodeFromString(Spec.serializer(), File("examples/world-builder-engine.spec.json").readText())

    private fun ComposeUiTest.open(): WorldBuilderHost {
        val host = parcelHost(loadPresentation("examples/parcel.presentation.json"))
        var publish: () -> Unit = {}
        val context = RenderContext(
            actions = host.actions { publish() },
            initialData = host.bindings() + mapOf("theme" to "light", "animate" to false, "brush" to host.initialBrush(), "rename_draft" to ""),
        )
        publish = { context.applyBindings(host.bindings()) }
        setContent { Box(Modifier.size(1100.dp, 800.dp)) { RenderSpec(spec = spec, context = context) } }
        waitForIdle()
        return host
    }

    @Test
    fun theWindowOpensWithTheWorldsScoresAndZeroedUsesInThePanel() = runComposeUiTest {
        open()
        onNodeWithText("Paddock: 0 tiles, 0 sq ft", substring = true).assertIsDisplayed()
        onNodeWithText("Pasture yield", substring = true).assertExists()
        onNodeWithText("Viewing: My plan (1 plan in all)").assertIsDisplayed()
    }

    @Test
    fun clickingTheMapPlacesTheBrushThroughTheEngineAndThePanelReadsTheArea() = runComposeUiTest {
        val host = open()
        onNodeWithTag("world:map").performTouchInput { click(center) }
        waitForIdle()
        assert(host.counts().values.sum() == 1) { "one tile placed through the engine: ${host.counts()}" }
        onNodeWithText("Paddock: 1 tiles, 625 sq ft", substring = true).assertIsDisplayed()
    }

    @Test
    fun aRefusedPlacementShowsTheRulesMessageInThePanel() = runComposeUiTest {
        val host = open()
        onNodeWithTag("world:brush:van_pad").performClick()
        waitForIdle()
        onNodeWithTag("world:map").performTouchInput { click(center) }
        waitForIdle()
        assert(host.counts().values.sum() == 0) { "nothing placed: ${host.counts()}" }
        onNodeWithText("Refused: a van pad needs a path on one of its four sides").assertIsDisplayed()
    }

    @Test
    fun theProposalButtonOpensABranchAndTheDiffLineAppears() = runComposeUiTest {
        val host = open()
        onNodeWithTag("btn-proposal").performClick()
        waitForIdle()
        assert(host.session.currentBranch == "proposal-1")
        onNodeWithText("Viewing: Alternative 1 (2 plans in all)").assertIsDisplayed()
        onNodeWithText("Alternative 1 matches My plan").assertIsDisplayed()
    }
}
