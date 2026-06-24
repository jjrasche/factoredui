package ai.factoredui.compose.renderer

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import ai.factoredui.compose.schema.SpecNode
import ai.factoredui.compose.schema.SpecNodeType
import ai.factoredui.compose.schema.SpecValue
import ai.factoredui.compose.testing.SpecVisualCheck
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class RenderSpecVisualSubstrateSpike {

    private val spec = SpecNode(
        id = "root",
        type = SpecNodeType.COLUMN,
        children = listOf(
            SpecNode(id = "greeting", type = SpecNodeType.TEXT, props = mapOf("value" to SpecValue.StringValue("hello"))),
            SpecNode(id = "cta", type = SpecNodeType.BUTTON, props = mapOf("label" to SpecValue.StringValue("Go"))),
        ),
    )

    @Test
    fun specRendersToPixelsAndEveryNodeMapsToAnExactRegion() = runComposeUiTest {
        val check = SpecVisualCheck(this)
        check.render(spec)
        check.assertRenderedToPixels()
        check.assertPresent("greeting")
        check.assertOccupiesRegion("greeting")
        check.assertPresent("cta")
        check.assertOccupiesRegion("cta")
        check.assertAbove("greeting", "cta")
    }

    @Test
    fun shadowTreeMapsEverySpecNodeToTypePropsAndRegion() = runComposeUiTest {
        val check = SpecVisualCheck(this)
        check.render(spec)
        val tree = check.shadowTree()
        assertEquals(listOf("root", "greeting", "cta"), tree.map { it.id }, "the whole spec tree is in the shadow tree")
        assertEquals(SpecNodeType.TEXT, check.node("greeting").type)
        assertEquals("hello", check.node("greeting").props["value"], "resolved props ride the shadow node")
        assertTrue(check.node("cta").bounds != null, "a rendered node carries its region")
    }

    @Test
    fun syntheticTapReRendersAndChangesBoundState() = runComposeUiTest {
        val context = RenderContext(initialData = mapOf("enabled" to false))
        val check = SpecVisualCheck(this, context)
        val toggle = SpecNode(id = "sw", type = SpecNodeType.TOGGLE, props = mapOf("value" to SpecValue.StringValue("{enabled}")))
        check.render(toggle)
        assertEquals(false, check.binding("enabled"))
        check.tap("sw")
        assertEquals(true, check.binding("enabled"), "a synthetic tap flips the bound state — interactability is provable headless")
    }
}
