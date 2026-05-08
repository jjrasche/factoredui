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
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Compose UI tests for the form-input + selection primitives.
 *
 * These exercise the binding write-back contract: when the user interacts
 * with a control, the bound path in the data store is updated and any
 * registered action handler fires.
 */
@OptIn(ExperimentalTestApi::class)
class RenderControlsTest {

    private fun newContext(
        initialData: Map<String, Any?> = emptyMap(),
        actions: Map<String, suspend (Map<String, Any?>) -> Unit> = emptyMap(),
    ) = RenderContext(
        actions = actions,
        initialData = initialData,
    )

    @Test
    fun toggleClickFlipsBoundBoolean() = runComposeUiTest {
        val context = newContext(initialData = mapOf("settings" to mapOf("dark" to false)))
        val node = SpecNode(
            id = "toggle-1",
            type = SpecNodeType.TOGGLE,
            props = mapOf(
                "label" to SpecValue.StringValue("Dark mode"),
                "value" to SpecValue.StringValue("{settings.dark}"),
            ),
        )

        setContent { RenderSpec(root = node, context = context) }

        onNodeWithText("Dark mode").assertIsDisplayed()
        onNodeWithContentDescription("toggle-1").performClick()
        waitForIdle()

        val flipped = (context.dataFlow.value["settings"] as? Map<*, *>)?.get("dark")
        assertEquals(true, flipped)
    }

    @Test
    fun toggleFiresActionAfterBindingWrite() = runComposeUiTest {
        var actionFiredWith: Map<String, Any?>? = null
        val context = newContext(
            initialData = mapOf("v" to false),
            actions = mapOf("on-toggle" to { params -> actionFiredWith = params }),
        )
        val node = SpecNode(
            id = "toggle-action",
            type = SpecNodeType.TOGGLE,
            props = mapOf("value" to SpecValue.StringValue("{v}")),
            action = ActionRef(action = "on-toggle"),
        )

        setContent { RenderSpec(root = node, context = context) }
        onNodeWithContentDescription("toggle-action").performClick()
        waitForIdle()

        assertEquals(true, context.dataFlow.value["v"])
        assertTrue(actionFiredWith != null, "action handler did not fire")
    }

    @Test
    fun selectShowsCurrentLabelFromBoundValue() = runComposeUiTest {
        val context = newContext(initialData = mapOf("choice" to "b"))
        val node = SpecNode(
            id = "sel",
            type = SpecNodeType.SELECT,
            props = mapOf(
                "value" to SpecValue.StringValue("{choice}"),
                "options" to SpecValue.ArrayValue(
                    listOf(
                        SpecValue.ObjectValue(mapOf(
                            "label" to SpecValue.StringValue("Apple"),
                            "value" to SpecValue.StringValue("a"),
                        )),
                        SpecValue.ObjectValue(mapOf(
                            "label" to SpecValue.StringValue("Banana"),
                            "value" to SpecValue.StringValue("b"),
                        )),
                    ),
                ),
            ),
        )

        setContent { RenderSpec(root = node, context = context) }

        onNodeWithText("Banana").assertIsDisplayed()
    }

    @Test
    fun tabsRenderAllItemsAndSelectedChild() = runComposeUiTest {
        val context = newContext(initialData = mapOf("idx" to 1))
        val firstChild = SpecNode(
            id = "first",
            type = SpecNodeType.TEXT,
            props = mapOf("value" to SpecValue.StringValue("first body")),
        )
        val secondChild = SpecNode(
            id = "second",
            type = SpecNodeType.TEXT,
            props = mapOf("value" to SpecValue.StringValue("second body")),
        )
        val node = SpecNode(
            id = "tabs",
            type = SpecNodeType.TABS,
            props = mapOf(
                "items" to SpecValue.ArrayValue(
                    listOf(SpecValue.StringValue("First"), SpecValue.StringValue("Second")),
                ),
                "selectedIndex" to SpecValue.StringValue("{idx}"),
            ),
            children = listOf(firstChild, secondChild),
        )

        setContent { RenderSpec(root = node, context = context) }

        onNodeWithText("First").assertIsDisplayed()
        onNodeWithText("Second").assertIsDisplayed()
        onNodeWithText("second body").assertIsDisplayed()
    }

    @Test
    fun tabsTapSwitchesBoundIndex() = runComposeUiTest {
        val context = newContext(initialData = mapOf("idx" to 0))
        val node = SpecNode(
            id = "tabs",
            type = SpecNodeType.TABS,
            props = mapOf(
                "items" to SpecValue.ArrayValue(
                    listOf(SpecValue.StringValue("One"), SpecValue.StringValue("Two")),
                ),
                "selectedIndex" to SpecValue.StringValue("{idx}"),
            ),
            children = listOf(
                SpecNode(id = "c1", type = SpecNodeType.TEXT, props = mapOf("value" to SpecValue.StringValue("body1"))),
                SpecNode(id = "c2", type = SpecNodeType.TEXT, props = mapOf("value" to SpecValue.StringValue("body2"))),
            ),
        )

        setContent { RenderSpec(root = node, context = context) }

        onNodeWithText("Two").performClick()
        waitForIdle()

        assertEquals(1, context.dataFlow.value["idx"])
    }

    @Test
    fun modalRendersTitleAndChildren() = runComposeUiTest {
        val context = newContext()
        val node = SpecNode(
            id = "m",
            type = SpecNodeType.MODAL,
            props = mapOf(
                "title" to SpecValue.StringValue("Heads up"),
                "dismissible" to SpecValue.BooleanValue(true),
            ),
            children = listOf(
                SpecNode(
                    id = "body",
                    type = SpecNodeType.TEXT,
                    props = mapOf("value" to SpecValue.StringValue("modal body")),
                ),
            ),
        )

        setContent { RenderSpec(root = node, context = context) }

        onNodeWithText("Heads up").assertIsDisplayed()
        onNodeWithText("modal body").assertIsDisplayed()
    }
}
