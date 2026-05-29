package ai.factoredui.compose.renderer

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import ai.factoredui.compose.schema.RENDERER_VERSION
import ai.factoredui.compose.schema.Spec
import ai.factoredui.compose.schema.SpecNode
import ai.factoredui.compose.schema.SpecNodeType
import ai.factoredui.compose.schema.SpecValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test

/**
 * The Flow<Spec> entry point hot-swaps the rendered tree in place when the host
 * pushes a new spec — the mechanism behind variant swaps without a navigation
 * boundary.
 */
@OptIn(ExperimentalTestApi::class)
class SpecHotSwapTest {

    private fun specWithText(id: String, value: String) = Spec(
        specVersion = 1,
        rendererMin = RENDERER_VERSION,
        root = SpecNode(
            id = id,
            type = SpecNodeType.TEXT,
            props = mapOf("value" to SpecValue.StringValue(value)),
        ),
    )

    @Test
    fun rendersCurrentSpecAndSwapsOnEmission() = runComposeUiTest {
        val specs = MutableStateFlow(specWithText("control", "Control variant"))
        val context = RenderContext()

        setContent { RenderSpec(specFlow = specs, context = context) }
        waitForIdle()
        onNodeWithText("Control variant").assertIsDisplayed()

        // Host swaps in a new variant's spec — no navigation, same render call.
        specs.value = specWithText("treatment", "Treatment variant")
        waitForIdle()

        onNodeWithText("Treatment variant").assertIsDisplayed()
        onNodeWithText("Control variant").assertDoesNotExist()
    }
}
