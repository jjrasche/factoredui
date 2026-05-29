package ai.factoredui.compose.renderer

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import ai.factoredui.compose.schema.ActionRef
import ai.factoredui.compose.schema.RENDERER_VERSION
import ai.factoredui.compose.schema.ShortcutKey
import ai.factoredui.compose.schema.Spec
import ai.factoredui.compose.schema.SpecNode
import ai.factoredui.compose.schema.SpecNodeType
import ai.factoredui.compose.schema.SpecValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalTestApi::class)
class KeybindingTest {

    @Test
    fun mapsComposeKeysToShortcutKeys() {
        assertEquals(ShortcutKey.Y, toShortcutKey(Key.Y))
        assertEquals(ShortcutKey.N, toShortcutKey(Key.N))
        assertEquals(ShortcutKey.SPACE, toShortcutKey(Key.Spacebar))
        assertEquals(ShortcutKey.ENTER, toShortcutKey(Key.Enter))
        assertEquals(ShortcutKey.ESCAPE, toShortcutKey(Key.Escape))
        assertEquals(ShortcutKey.ARROW_UP, toShortcutKey(Key.DirectionUp))
        assertEquals(ShortcutKey.TAB, toShortcutKey(Key.Tab))
        assertNull(toShortcutKey(Key.Q), "unbound key maps to null")
    }

    @Test
    fun keyPressDispatchesBoundAction() = runComposeUiTest {
        var verdictValue: Any? = null
        val context = RenderContext(
            actions = mapOf("verdict" to { params -> verdictValue = params["value"] }),
        )
        val spec = Spec(
            specVersion = 1,
            rendererMin = RENDERER_VERSION,
            root = SpecNode(
                id = "root",
                type = SpecNodeType.TEXT,
                props = mapOf("value" to SpecValue.StringValue("Label this frame")),
            ),
            keybindings = mapOf(
                ShortcutKey.Y to ActionRef(action = "verdict", params = mapOf("value" to SpecValue.StringValue("Y"))),
            ),
        )

        setContent { RenderSpec(spec = spec, context = context) }
        waitForIdle()
        onNodeWithText("Label this frame").assertIsDisplayed()

        onRoot().performKeyInput { pressKey(Key.Y) }
        waitForIdle()

        assertEquals("Y", verdictValue, "pressing Y should dispatch the bound verdict action")
    }
}
