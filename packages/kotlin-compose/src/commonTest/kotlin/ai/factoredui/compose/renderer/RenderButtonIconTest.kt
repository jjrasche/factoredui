package ai.factoredui.compose.renderer

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import ai.factoredui.compose.schema.ActionRef
import ai.factoredui.compose.schema.SpecNode
import ai.factoredui.compose.schema.SpecNodeType
import ai.factoredui.compose.schema.SpecValue
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class RenderButtonIconTest {

    @Test
    fun iconOnlyButtonStaysFindableAndDispatches() = runComposeUiTest {
        var sent = false
        val context = RenderContext(actions = mapOf("send" to { _ -> sent = true }))
        val button = SpecNode(
            id = "send",
            type = SpecNodeType.BUTTON,
            props = mapOf("icon" to SpecValue.StringValue("lucide:arrow-up")),
            action = ActionRef(action = "send"),
        )
        setContent { RenderSpec(root = button, context = context) }
        waitForIdle()

        onNodeWithContentDescription("send").performClick()
        waitForIdle()

        assertTrue(sent, "an icon-only button should still dispatch its action")
    }

    @Test
    fun iconWithLabelStillRendersLabel() = runComposeUiTest {
        val button = SpecNode(
            id = "labeled",
            type = SpecNodeType.BUTTON,
            props = mapOf(
                "icon" to SpecValue.StringValue("lucide:check"),
                "label" to SpecValue.StringValue("Send"),
            ),
        )
        setContent { RenderSpec(root = button, context = RenderContext()) }
        waitForIdle()

        onNodeWithText("Send").assertIsDisplayed()
    }
}
