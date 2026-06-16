package ai.factoredui.compose.renderer

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import ai.factoredui.compose.schema.ActionRef
import ai.factoredui.compose.schema.SpecNode
import ai.factoredui.compose.schema.SpecNodeType
import ai.factoredui.compose.schema.SpecValue
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class RenderNodeTagTest {

    @Test
    fun buttonIsFindableByNodeIdTagAndTappable() = runComposeUiTest {
        var fired = false
        val context = RenderContext(actions = mapOf("send" to { _ -> fired = true }))
        val button = SpecNode(
            id = "send-btn",
            type = SpecNodeType.BUTTON,
            props = mapOf("label" to SpecValue.StringValue("Send")),
            action = ActionRef(action = "send"),
        )
        setContent { RenderSpec(root = button, context = context) }
        waitForIdle()

        onNodeWithTag("send-btn").assertIsDisplayed()
        onNodeWithTag("send-btn").performClick()
        waitForIdle()
        assertTrue(fired, "a button found by its node-id testTag should dispatch its action")
    }

    @Test
    fun textInputIsFindableByNodeIdTag() = runComposeUiTest {
        val field = SpecNode(
            id = "compose-field",
            type = SpecNodeType.TEXTINPUT,
            props = mapOf("placeholder" to SpecValue.StringValue("Message")),
        )
        setContent { RenderSpec(root = field, context = RenderContext()) }
        waitForIdle()

        onNodeWithTag("compose-field").assertIsDisplayed()
    }
}
